package com.medkernel.engine.security.bootstrap;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.medkernel.engine.security.PlatformCredential;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.config.HighRiskChangeGuard;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.security.AuthSessionClaims;
import com.medkernel.shared.security.MfaRuntimePolicy;

/**
 * 高危操作 MFA 策略：当前用户必须已完成 TOTP 绑定，secret 加密保存，恢复码只保存摘要。
 */
@Service
public class MfaPolicyService implements HighRiskChangeGuard {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PlatformCredentialRepository credentials;
    private final TotpService totpService;
    private final MfaSecretCodec secretCodec;
    private final MfaRuntimePolicy runtimePolicy;

    public MfaPolicyService(PlatformCredentialRepository credentials,
                            TotpService totpService,
                            MfaSecretCodec secretCodec,
                            MfaRuntimePolicy runtimePolicy) {
        this.credentials = credentials;
        this.totpService = totpService;
        this.secretCodec = secretCodec;
        this.runtimePolicy = runtimePolicy;
    }

    @Transactional(readOnly = true)
    @Override
    public void assertHighRiskAllowed(String resourceType, String resourceId) {
        if (!runtimePolicy.enabled()) {
            return;
        }
        if (!currentSessionMfaVerified()) {
            throw new ApiException(ErrorCode.ENG_AUTH_010, "当前会话尚未完成 MFA 验证");
        }
        String tenantId = RequestContext.currentOrgScope().tenantId();
        String userId = RequestContext.currentUserId().orElse(null);
        if (tenantId == null || tenantId.isBlank() || userId == null || userId.isBlank()) {
            throw new ApiException(ErrorCode.ENG_AUTH_010);
        }
        PlatformCredential credential = credentials.findByTenantIdAndUserId(tenantId, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_AUTH_010));
        if (!secretCodec.isTotpBound(credential.mfaSecret())) {
            throw new ApiException(ErrorCode.ENG_AUTH_010);
        }
    }

    private boolean currentSessionMfaVerified() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = null;
        if (authentication instanceof JwtAuthenticationToken token) {
            jwt = token.getToken();
        } else if (authentication != null && authentication.getPrincipal() instanceof Jwt principal) {
            jwt = principal;
        }
        return jwt != null && Boolean.TRUE.equals(jwt.getClaim(AuthSessionClaims.MFA_VERIFIED));
    }

    @Transactional
    public BootstrapMfaResponse bindForCurrentUser(BootstrapMfaRequest request) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        String userId = RequestContext.currentUserId().orElse(null);
        if (tenantId == null || tenantId.isBlank() || userId == null || userId.isBlank()) {
            throw new ApiException(ErrorCode.ENG_AUTH_010);
        }
        PlatformCredential credential = credentials.findByTenantIdAndUserId(tenantId, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_AUTH_005));
        if (request.secret() == null || request.secret().isBlank() || request.code() == null || request.code().isBlank()) {
            String secret = totpService.generateSecret();
            String account = accountLabel(credential, request.label());
            return new BootstrapMfaResponse(
                false,
                secret,
                totpService.otpauthUri("MedKernel", account, secret),
                null);
        }
        String secret = normalizeSecret(request.secret());
        if (!totpService.verify(secret, request.code())) {
            throw new ApiException(ErrorCode.ENG_AUTH_010, "MFA 验证码不正确");
        }
        String recoveryCode = generateRecoveryCode();
        Instant now = Instant.now();
        credentials.save(new PlatformCredential(
            credential.id(), credential.credentialId(), credential.tenantId(), credential.userId(),
            credential.username(), credential.passwordHash(), credential.status(), credential.mustChangePwd(),
            secretCodec.encode(secret, recoveryCode), credential.createdAt(), credential.createdBy(),
            now, userId, credential.traceId()));
        return new BootstrapMfaResponse(true, recoveryCode);
    }

    @Transactional(readOnly = true)
    public BootstrapMfaVerifyResponse verifyForCurrentUser(BootstrapMfaVerifyRequest request) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        String userId = RequestContext.currentUserId().orElse(null);
        if (tenantId == null || tenantId.isBlank() || userId == null || userId.isBlank()) {
            throw new ApiException(ErrorCode.ENG_AUTH_010);
        }
        PlatformCredential credential = credentials.findByTenantIdAndUserId(tenantId, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_AUTH_010));
        String secret = secretCodec.decodeTotpSecret(credential.mfaSecret())
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_AUTH_010));
        if (!totpService.verify(secret, request.code())) {
            throw new ApiException(ErrorCode.ENG_AUTH_010, "MFA 验证码不正确");
        }
        return new BootstrapMfaVerifyResponse(true);
    }

    private String generateRecoveryCode() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeSecret(String secret) {
        return secret.trim().replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private String accountLabel(PlatformCredential credential, String label) {
        if (label != null && !label.isBlank()) {
            return label.trim();
        }
        return credential.tenantId() + ":" + credential.username();
    }
}
