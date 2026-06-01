package com.medkernel.engine.security.bootstrap;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.security.PlatformCredential;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.RequestContext;

/**
 * 高危操作 MFA 策略：当前用户必须已绑定 MFA，恢复码只保存 SHA-256 摘要。
 */
@Service
public class MfaPolicyService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PlatformCredentialRepository credentials;

    public MfaPolicyService(PlatformCredentialRepository credentials) {
        this.credentials = credentials;
    }

    @Transactional(readOnly = true)
    public void assertHighRiskAllowed(String resourceType, String resourceId) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        String userId = RequestContext.currentUserId().orElse(null);
        if (tenantId == null || tenantId.isBlank() || userId == null || userId.isBlank()) {
            throw new ApiException(ErrorCode.ENG_AUTH_010);
        }
        PlatformCredential credential = credentials.findByTenantIdAndUserId(tenantId, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_AUTH_010));
        if (credential.mfaSecret() == null || credential.mfaSecret().isBlank()) {
            throw new ApiException(ErrorCode.ENG_AUTH_010);
        }
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
        String recoveryCode = generateRecoveryCode();
        Instant now = Instant.now();
        credentials.save(new PlatformCredential(
            credential.id(), credential.credentialId(), credential.tenantId(), credential.userId(),
            credential.username(), credential.passwordHash(), credential.status(), credential.mustChangePwd(),
            BootstrapInitTokenService.sha256(recoveryCode), credential.createdAt(), credential.createdBy(),
            now, userId, credential.traceId()));
        return new BootstrapMfaResponse(true, recoveryCode);
    }

    private String generateRecoveryCode() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
