package com.medkernel.engine.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.datascope.DataScope;
import com.medkernel.engine.security.bootstrap.MfaSecretCodec;
import com.medkernel.shared.security.AuthSessionClaims;
import com.medkernel.shared.security.MfaRuntimePolicy;

/**
 * 当前用户权限画像接口。
 *
 * <p>前端只用该接口决定菜单可见、按钮可点、高级信息是否展示；真正授权仍在后端
 * {@code @PreAuthorize("@perm.has(...)")} 和 {@link DataScope} 双门禁内完成。
 */
@RestController
@RequestMapping("/api/v1/security")
@DataScope(requireTenant = true)
public class SecurityMeController {

    private final EffectivePermissionService permissionService;
    private final PlatformCredentialRepository credentials;
    private final MfaSecretCodec mfaSecretCodec;
    private final MfaRuntimePolicy mfaRuntimePolicy;

    public SecurityMeController(EffectivePermissionService permissionService,
                                PlatformCredentialRepository credentials,
                                MfaSecretCodec mfaSecretCodec,
                                MfaRuntimePolicy mfaRuntimePolicy) {
        this.permissionService = permissionService;
        this.credentials = credentials;
        this.mfaSecretCodec = mfaSecretCodec;
        this.mfaRuntimePolicy = mfaRuntimePolicy;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<EffectivePermissionProfile> me(Authentication authentication) {
        String userId = RequestContext.currentUserId().orElse(authentication.getName());
        EffectivePermissionProfile profile = permissionService.resolve(
            authentication,
            RequestContext.currentOrgScope(),
            userId
        );
        PlatformCredential credential = credentials
            .findByTenantIdAndUserId(RequestContext.currentOrgScope().tenantId(), userId)
            .orElse(null);
        boolean mustChangePwd = credential != null && "Y".equalsIgnoreCase(credential.mustChangePwd());
        boolean mfaBound = credential != null && mfaSecretCodec.isTotpBound(credential.mfaSecret());
        boolean mfaVerified = authentication.getPrincipal() instanceof Jwt jwt
            && Boolean.TRUE.equals(jwt.getClaim(AuthSessionClaims.MFA_VERIFIED));
        String username = credential == null ? userId : credential.username();
        return ApiResult.ok(profile.withIdentity(username).withBootstrapSecurity(
            mustChangePwd,
            mfaRuntimePolicy.enabled(),
            mfaBound,
            mfaVerified));
    }
}
