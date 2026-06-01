package com.medkernel.engine.security.auth;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.security.PlatformCredential;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.shared.config.AuthLoginPolicy;
import com.medkernel.shared.config.SystemConfigService;
import com.medkernel.shared.context.RequestContext;

/**
 * 登录失败锁定与限流服务，阈值由配置中心热读取。
 */
@Service
public class LoginAttemptService {

    private final LoginAttemptStateRepository attempts;
    private final PlatformCredentialRepository credentials;
    private final SystemConfigService configService;

    public LoginAttemptService(LoginAttemptStateRepository attempts,
                               PlatformCredentialRepository credentials,
                               SystemConfigService configService) {
        this.attempts = attempts;
        this.credentials = credentials;
        this.configService = configService;
    }

    @Transactional
    public FailureOutcome recordFailure(String tenantId, String username, PlatformCredential credential) {
        AuthLoginPolicy policy = configService.runtimeAuthLoginPolicy();
        Instant now = Instant.now();
        LoginAttemptState current = attempts.findByTenantIdAndUsername(tenantId, username).orElse(null);
        int previousCount = withinWindow(current, policy, now) ? current.failedCount() : 0;
        int failedCount = previousCount + 1;
        Instant lockedUntil = null;
        FailureOutcome outcome = FailureOutcome.FAILED;

        if (credential != null && failedCount >= policy.maxFailedAttempts()) {
            lockedUntil = now.plusSeconds(policy.lockoutSeconds());
            lockCredential(credential, now);
            outcome = FailureOutcome.LOCKED;
        } else if (failedCount >= policy.rateLimitAttempts()) {
            lockedUntil = now.plusSeconds(policy.rateLimitWindowSeconds());
            outcome = FailureOutcome.RATE_LIMITED;
        }

        attempts.save(rewrite(current, tenantId, username, credential, failedCount, lockedUntil, now));
        return outcome;
    }

    @Transactional
    public PlatformCredential unlockExpiredAutoLock(PlatformCredential credential) {
        if (!"LOCKED".equalsIgnoreCase(credential.status())) {
            return credential;
        }
        LoginAttemptState attempt = attempts
            .findByTenantIdAndUsername(credential.tenantId(), credential.username())
            .orElse(null);
        Instant now = Instant.now();
        if (attempt == null || attempt.lockedUntil() == null || attempt.lockedUntil().isAfter(now)) {
            return credential;
        }
        resetOnSuccess(credential.tenantId(), credential.username());
        PlatformCredential active = rewriteCredential(credential, "ACTIVE", now);
        return credentials.save(active);
    }

    @Transactional
    public void resetOnSuccess(String tenantId, String username) {
        attempts.findByTenantIdAndUsername(tenantId, username)
            .ifPresent(attempt -> attempts.save(resetAttempt(attempt, "auth-login")));
    }

    @Transactional
    public void clearStateForCredential(PlatformCredential credential, String actor) {
        attempts.findByTenantIdAndUsername(credential.tenantId(), credential.username())
            .ifPresent(attempt -> attempts.save(resetAttempt(attempt, actor)));
    }

    private boolean withinWindow(LoginAttemptState attempt, AuthLoginPolicy policy, Instant now) {
        return attempt != null
            && attempt.lastFailedAt() != null
            && attempt.lastFailedAt().plusSeconds(policy.rateLimitWindowSeconds()).isAfter(now);
    }

    private LoginAttemptState rewrite(LoginAttemptState current,
                                      String tenantId,
                                      String username,
                                      PlatformCredential credential,
                                      int failedCount,
                                      Instant lockedUntil,
                                      Instant now) {
        if (current == null) {
            return new LoginAttemptState(
                null,
                "lat-" + UUID.randomUUID(),
                tenantId,
                username,
                credential == null ? null : credential.credentialId(),
                failedCount,
                lockedUntil,
                now,
                now,
                "auth-login",
                now,
                "auth-login",
                RequestContext.currentTraceId());
        }
        return new LoginAttemptState(
            current.id(),
            current.attemptId(),
            current.tenantId(),
            current.username(),
            credential == null ? current.credentialId() : credential.credentialId(),
            failedCount,
            lockedUntil,
            now,
            current.createdAt(),
            current.createdBy(),
            now,
            "auth-login",
            RequestContext.currentTraceId());
    }

    private LoginAttemptState resetAttempt(LoginAttemptState attempt, String actor) {
        return new LoginAttemptState(
            attempt.id(),
            attempt.attemptId(),
            attempt.tenantId(),
            attempt.username(),
            attempt.credentialId(),
            0,
            null,
            null,
            attempt.createdAt(),
            attempt.createdBy(),
            Instant.now(),
            actor,
            RequestContext.currentTraceId());
    }

    private void lockCredential(PlatformCredential credential, Instant now) {
        credentials.save(rewriteCredential(credential, "LOCKED", now));
    }

    private PlatformCredential rewriteCredential(PlatformCredential credential, String status, Instant now) {
        return new PlatformCredential(
            credential.id(),
            credential.credentialId(),
            credential.tenantId(),
            credential.userId(),
            credential.username(),
            credential.passwordHash(),
            status,
            credential.mustChangePwd(),
            credential.mfaSecret(),
            credential.createdAt(),
            credential.createdBy(),
            now,
            "auth-login",
            credential.traceId());
    }

    public enum FailureOutcome {
        FAILED,
        LOCKED,
        RATE_LIMITED
    }
}
