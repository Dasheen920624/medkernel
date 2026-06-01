package com.medkernel.engine.security.auth;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.security.PlatformCredential;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditEventPublisher;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.config.SystemConfigService;
import com.medkernel.shared.crypto.SmCryptoService;

/**
 * 受控密码重置服务：管理员发放一次性 token，公开端点消费 token 后强制改密。
 */
@Service
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PasswordResetTokenRepository resetTokens;
    private final PlatformCredentialRepository credentials;
    private final PasswordPolicyService passwordPolicy;
    private final CredentialPasswordService credentialPasswords;
    private final LoginAttemptService loginAttempts;
    private final SystemConfigService configService;
    private final SmCryptoService crypto;
    private final AuditEventPublisher auditPublisher;
    private final IsolatedAuditPublisher isolatedAudit;

    public PasswordResetService(PasswordResetTokenRepository resetTokens,
                                PlatformCredentialRepository credentials,
                                PasswordPolicyService passwordPolicy,
                                CredentialPasswordService credentialPasswords,
                                LoginAttemptService loginAttempts,
                                SystemConfigService configService,
                                SmCryptoService crypto,
                                AuditEventPublisher auditPublisher,
                                IsolatedAuditPublisher isolatedAudit) {
        this.resetTokens = resetTokens;
        this.credentials = credentials;
        this.passwordPolicy = passwordPolicy;
        this.credentialPasswords = credentialPasswords;
        this.loginAttempts = loginAttempts;
        this.configService = configService;
        this.crypto = crypto;
        this.auditPublisher = auditPublisher;
        this.isolatedAudit = isolatedAudit;
    }

    @Transactional
    public PasswordResetTokenResponse issue(PlatformCredential credential, String actor, String traceId) {
        String token = newToken();
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(configService.runtimePasswordResetTokenTtlSeconds());
        resetTokens.save(new PasswordResetToken(
            null,
            "prt-" + newToken(),
            credential.tenantId(),
            credential.userId(),
            credential.username(),
            tokenHash(token),
            expiresAt,
            null,
            now,
            actor,
            now,
            actor,
            traceId));
        auditPublisher.publish(AuditAction.EXECUTE, "platform_credential", credential.userId(),
            "发放受控密码重置 token username=" + credential.username());
        return new PasswordResetTokenResponse(token, expiresAt);
    }

    @Transactional
    public void consume(PasswordResetRequest request) {
        String tenantId = request.tenantId().trim();
        String username = request.username().trim();
        PlatformCredential credential = credentials.findByTenantIdAndUsername(tenantId, username)
            .orElse(null);
        if (credential == null) {
            publishFailure(username, ErrorCode.ENG_AUTH_016.code(), "密码重置失败：token 无效 username=" + username);
            throw new ApiException(ErrorCode.ENG_AUTH_016);
        }
        PasswordResetToken reset = resetTokens
            .findByTenantIdAndUserIdAndTokenHashAndUsedAtIsNull(tenantId, credential.userId(), tokenHash(request.token()))
            .orElse(null);
        Instant now = Instant.now();
        if (reset == null || !reset.usableAt(now)) {
            publishFailure(credential.userId(), ErrorCode.ENG_AUTH_016.code(),
                "密码重置失败：token 无效或已过期 userId=" + credential.userId());
            throw new ApiException(ErrorCode.ENG_AUTH_016);
        }
        passwordPolicy.assertCompliant(request.newPassword());
        String actor = "password-reset";
        resetTokens.save(new PasswordResetToken(
            reset.id(), reset.resetId(), reset.tenantId(), reset.userId(), reset.username(),
            reset.tokenHash(), reset.expiresAt(), now, reset.createdAt(), reset.createdBy(),
            now, actor, reset.traceId()));
        loginAttempts.clearStateForCredential(credential, actor);
        credentials.save(new PlatformCredential(
            credential.id(), credential.credentialId(), credential.tenantId(), credential.userId(),
            credential.username(), credentialPasswords.encode(request.newPassword()), credential.status(), "Y",
            credential.mfaSecret(), credential.createdAt(), credential.createdBy(), now, actor, credential.traceId()));
        auditPublisher.publish(AuditAction.EXECUTE, "platform_credential", credential.userId(),
            "受控密码重置成功 username=" + credential.username());
    }

    private void publishFailure(String resourceId, String errorCode, String summary) {
        isolatedAudit.publishInNewTx(AuditEvent.failure(
            AuditAction.EXECUTE,
            "platform_credential",
            resourceId == null || resourceId.isBlank() ? "unknown" : resourceId,
            errorCode,
            summary));
    }

    private String tokenHash(String token) {
        return "sm3:" + crypto.sm3Hex(token == null ? "" : token.trim());
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
