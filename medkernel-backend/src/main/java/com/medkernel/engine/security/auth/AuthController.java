package com.medkernel.engine.security.auth;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.config.SystemConfigService;
import com.medkernel.engine.security.bootstrap.BootstrapMfaRequest;
import com.medkernel.engine.security.bootstrap.BootstrapMfaResponse;
import com.medkernel.engine.security.bootstrap.BootstrapMfaVerifyRequest;
import com.medkernel.engine.security.bootstrap.BootstrapMfaVerifyResponse;
import com.medkernel.engine.security.bootstrap.MfaPolicyService;
import com.medkernel.shared.security.AuthCookieProperties;
import com.medkernel.shared.security.AuthMode;
import com.medkernel.shared.security.CsrfDoubleSubmitFilter;

import jakarta.validation.Valid;

/**
 * 平台账号登录 / 登出控制器。/auth/login 与 /auth/logout 在 SecurityConfig 中 permitAll；
 * 登录成功写 httpOnly cookie，登出清 cookie。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    static final String XSRF_COOKIE = CsrfDoubleSubmitFilter.XSRF_COOKIE;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final List<String> DELEGATED_PROVIDERS = List.of("OIDC", "CAS", "SAML", "国密CA");

    private final AuthService authService;
    private final AuthSessionService sessionService;
    private final AuthCookieProperties cookieProps;
    private final SystemConfigService configService;
    private final MfaPolicyService mfaPolicyService;
    private final PasswordResetService passwordResetService;
    private final LoginTenantDirectoryService loginTenantDirectoryService;

    public AuthController(AuthService authService,
                          AuthSessionService sessionService,
                          AuthCookieProperties cookieProps,
                          SystemConfigService configService,
                          MfaPolicyService mfaPolicyService,
                          PasswordResetService passwordResetService,
                          LoginTenantDirectoryService loginTenantDirectoryService) {
        this.authService = authService;
        this.sessionService = sessionService;
        this.cookieProps = cookieProps;
        this.configService = configService;
        this.mfaPolicyService = mfaPolicyService;
        this.passwordResetService = passwordResetService;
        this.loginTenantDirectoryService = loginTenantDirectoryService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResult<LoginResponse>> login(@Valid @RequestBody LoginRequest req) {
        AuthService.AuthResult result = authService.login(req.tenantOrDefault(), req.username(), req.password());
        AuthCookieProperties runtimeCookie = configService.runtimeCookieProperties(cookieProps);
        SessionStatusResponse status = sessionService.status(result.issuedJwt());
        ResponseCookie cookie = buildCookie(
            result.jwt(), runtimeCookie, sessionService.cookieMaxAgeSeconds(result.issuedJwt(), runtimeCookie));
        ResponseCookie xsrf = buildXsrfCookie(
            newXsrfToken(), runtimeCookie, sessionService.cookieMaxAgeSeconds(result.issuedJwt(), runtimeCookie));
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString(), xsrf.toString())
            .body(ApiResult.ok(result.response().withSession(status)));
    }

    @GetMapping("/delegated/status")
    public ApiResult<DelegatedAuthStatusResponse> delegatedStatus() {
        AuthMode mode = configService.runtimeAuthMode();
        boolean enabled = mode.allowsDelegatedLogin();
        return ApiResult.ok(new DelegatedAuthStatusResponse(
            mode.name(),
            enabled,
            enabled ? "NOT_CONNECTED" : "DISABLED",
            DELEGATED_PROVIDERS,
            enabled
                ? "院方统一身份入口已按认证模式开放，但当前未配置真实 IdP 连接器。"
                : "当前认证模式未开放院方统一身份入口。"));
    }

    @GetMapping("/login-tenants")
    public ApiResult<LoginTenantDirectoryResponse> loginTenants() {
        return ApiResult.ok(loginTenantDirectoryService.directory());
    }

    @PostMapping("/delegated/callback")
    public ApiResult<Void> delegatedCallback() {
        if (!configService.runtimeAuthMode().allowsDelegatedLogin()) {
            throw new ApiException(ErrorCode.ENG_AUTH_013, "当前认证模式未开放院方统一身份入口");
        }
        throw new ApiException(ErrorCode.ENG_AUTH_014);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResult<Void>> logout(@AuthenticationPrincipal Jwt jwt) {
        authService.logout(jwt == null ? null : jwt.getSubject());
        ResponseCookie cleared = buildCookie("", configService.runtimeCookieProperties(cookieProps), 0);
        ResponseCookie xsrfCleared = buildXsrfCookie("", configService.runtimeCookieProperties(cookieProps), 0);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cleared.toString(), xsrfCleared.toString())
            .body(ApiResult.ok(null));
    }

    /** 查询当前会话剩余时间和无操作安全策略。 */
    @GetMapping("/session")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<SessionStatusResponse> session(@AuthenticationPrincipal Jwt jwt) {
        return ApiResult.ok(sessionService.status(jwt));
    }

    /** 在最大会话时长内滑动续期当前 httpOnly Cookie 会话。 */
    @PostMapping("/session/renew")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResult<SessionStatusResponse>> renewSession(@AuthenticationPrincipal Jwt jwt) {
        AuthSessionService.RenewedSession renewed = sessionService.renew(jwt);
        AuthCookieProperties runtimeCookie = configService.runtimeCookieProperties(cookieProps);
        ResponseCookie cookie = buildCookie(
            renewed.issuedJwt().token(),
            runtimeCookie,
            sessionService.cookieMaxAgeSeconds(renewed.issuedJwt(), runtimeCookie));
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(ApiResult.ok(renewed.status()));
    }

    /** 自助改密（需登录态）：从 JWT 取当前用户与租户，校验原密码后设新密码。 */
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<Void> changePassword(@AuthenticationPrincipal Jwt jwt,
                                          @Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(
            jwt.getClaimAsString("tenant_id"), jwt.getSubject(), req.oldPassword(), req.newPassword());
        return ApiResult.ok(null);
    }

    @PostMapping("/mfa/bind")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<BootstrapMfaResponse> bindMfa(@Valid @RequestBody BootstrapMfaRequest request) {
        return ApiResult.ok(mfaPolicyService.bindForCurrentUser(request));
    }

    @PostMapping("/mfa/verify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResult<BootstrapMfaVerifyResponse>> verifyMfa(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BootstrapMfaVerifyRequest request) {
        BootstrapMfaVerifyResponse response = mfaPolicyService.verifyForCurrentUser(request);
        AuthSessionService.RenewedSession renewed = sessionService.completeMfa(jwt);
        AuthCookieProperties runtimeCookie = configService.runtimeCookieProperties(cookieProps);
        ResponseCookie cookie = buildCookie(
            renewed.issuedJwt().token(),
            runtimeCookie,
            sessionService.cookieMaxAgeSeconds(renewed.issuedJwt(), runtimeCookie));
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(ApiResult.ok(response));
    }

    @PostMapping("/password-reset")
    public ApiResult<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.consume(request);
        return ApiResult.ok(null);
    }

    private ResponseCookie buildCookie(String value, AuthCookieProperties runtimeCookie, long maxAge) {
        return ResponseCookie.from(runtimeCookie.name(), value)
            .httpOnly(true)
            .secure(runtimeCookie.secure())
            .sameSite(runtimeCookie.sameSite())
            .path(runtimeCookie.path())
            .maxAge(maxAge)
            .build();
    }

    private ResponseCookie buildXsrfCookie(String value, AuthCookieProperties runtimeCookie, long maxAge) {
        return ResponseCookie.from(XSRF_COOKIE, value)
            .httpOnly(false)
            .secure(runtimeCookie.secure())
            .sameSite(runtimeCookie.sameSite())
            .path("/")
            .maxAge(maxAge)
            .build();
    }

    private String newXsrfToken() {
        byte[] random = new byte[32];
        SECURE_RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }
}
