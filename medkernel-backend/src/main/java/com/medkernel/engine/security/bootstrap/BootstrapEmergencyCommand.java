package com.medkernel.engine.security.bootstrap;

import java.io.PrintStream;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.security.PlatformCredential;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEventPublisher;

/**
 * 首发身份应急命令：只在显式启动参数下执行 MFA 重置或账号解锁，必须本机 + 二次确认。
 */
@Component
public class BootstrapEmergencyCommand implements ApplicationRunner {

    private static final String OPTION_COMMAND = "bootstrap-emergency";
    private static final String LOCAL_CONFIRM = "MEDKERNEL_LOCAL_CONSOLE";

    private final PlatformCredentialRepository credentials;
    private final AuditEventPublisher auditPublisher;
    private final PrintStream output;

    @Autowired
    public BootstrapEmergencyCommand(PlatformCredentialRepository credentials,
                                     AuditEventPublisher auditPublisher) {
        this(credentials, auditPublisher, System.out);
    }

    BootstrapEmergencyCommand(PlatformCredentialRepository credentials,
                              AuditEventPublisher auditPublisher,
                              PrintStream output) {
        this.credentials = credentials;
        this.auditPublisher = auditPublisher;
        this.output = output;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!args.containsOption(OPTION_COMMAND)) {
            return;
        }
        String command = required(args, OPTION_COMMAND);
        String tenantId = required(args, "tenant-id");
        String userId = required(args, "user-id");
        String actor = required(args, "actor");
        String reason = required(args, "reason");
        assertLocalConfirmed(args);

        switch (command) {
            case "mfa-reset" -> resetMfa(args, tenantId, userId, actor, reason);
            case "unlock" -> unlock(args, tenantId, userId, actor, reason);
            default -> throw new ApiException(ErrorCode.VALIDATION_FAILED, "未知应急命令：" + command);
        }
    }

    private void resetMfa(ApplicationArguments args, String tenantId, String userId, String actor, String reason) {
        assertSecondConfirmed(args, "RESET_MFA:" + userId);
        PlatformCredential credential = find(tenantId, userId);
        Instant now = Instant.now();
        credentials.save(new PlatformCredential(
            credential.id(), credential.credentialId(), credential.tenantId(), credential.userId(),
            credential.username(), credential.passwordHash(), credential.status(), credential.mustChangePwd(),
            null, credential.createdAt(), credential.createdBy(),
            now, actor, credential.traceId()));
        auditPublisher.publish(AuditAction.EXECUTE, "platform_credential", userId,
            "应急重置 MFA actor=" + actor + " reason=" + reason);
        output.println("bootstrap-emergency=mfa-reset userId=" + userId + " mfaStatus=RESET_REQUIRED");
    }

    private void unlock(ApplicationArguments args, String tenantId, String userId, String actor, String reason) {
        assertSecondConfirmed(args, "UNLOCK:" + userId);
        PlatformCredential credential = find(tenantId, userId);
        Instant now = Instant.now();
        credentials.save(new PlatformCredential(
            credential.id(), credential.credentialId(), credential.tenantId(), credential.userId(),
            credential.username(), credential.passwordHash(), "ACTIVE", credential.mustChangePwd(),
            credential.mfaSecret(), credential.createdAt(), credential.createdBy(),
            now, actor, credential.traceId()));
        auditPublisher.publish(AuditAction.EXECUTE, "platform_credential", userId,
            "应急解锁账号 actor=" + actor + " reason=" + reason);
        output.println("bootstrap-emergency=unlock userId=" + userId + " status=ACTIVE");
    }

    private PlatformCredential find(String tenantId, String userId) {
        return credentials.findByTenantIdAndUserId(tenantId, userId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_AUTH_005));
    }

    private void assertLocalConfirmed(ApplicationArguments args) {
        if (!LOCAL_CONFIRM.equals(option(args, "local-confirm"))) {
            throw new ApiException(ErrorCode.ENG_AUTH_011);
        }
    }

    private void assertSecondConfirmed(ApplicationArguments args, String expected) {
        if (!expected.equals(option(args, "confirm"))) {
            throw new ApiException(ErrorCode.ENG_AUTH_011);
        }
    }

    private String required(ApplicationArguments args, String name) {
        String value = option(args, name);
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.ENG_AUTH_011);
        }
        return value.trim();
    }

    private String option(ApplicationArguments args, String name) {
        java.util.List<String> values = args.getOptionValues(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

}
