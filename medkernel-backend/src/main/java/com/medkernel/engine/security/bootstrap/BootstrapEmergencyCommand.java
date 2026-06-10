package com.medkernel.engine.security.bootstrap;

import java.io.PrintStream;
import java.time.Instant;
import java.util.function.IntConsumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.security.PlatformCredential;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.engine.security.TenantUser;
import com.medkernel.engine.security.TenantUserRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;

/**
 * 首发身份应急命令：只在显式启动参数下执行 MFA 重置或账号解锁，必须本机 + 二次确认。
 *
 * <p>救命通道以非 Web 模式（{@code web-application-type=none}）旁路启动，不绑定业务端口。
 * 命令执行（{@code @Transactional} 写入并提交）完成后，本组件在 {@link ApplicationReadyEvent}
 * 阶段——即全部 {@link ApplicationRunner} 与其事务都已提交之后——请求干净退出 JVM，使应急命令
 * 成为一发即走的一次性操作，无需运维手动 kill（{@code @EnableScheduling} 等非守护线程会让进程常驻）。
 */
@Component
public class BootstrapEmergencyCommand implements ApplicationRunner, ApplicationListener<ApplicationReadyEvent> {

    private static final String OPTION_COMMAND = "bootstrap-emergency";
    private static final String LOCAL_CONFIRM = "MEDKERNEL_LOCAL_CONSOLE";

    private final PlatformCredentialRepository credentials;
    private final TenantUserRepository users;
    private final AuditRecorder auditRecorder;
    private final PrintStream output;
    private final IntConsumer exitHandler;

    private boolean emergencyExecuted;

    @Autowired
    public BootstrapEmergencyCommand(PlatformCredentialRepository credentials,
                                     TenantUserRepository users,
                                     AuditRecorder auditRecorder,
                                     ConfigurableApplicationContext context) {
        this(credentials, users, auditRecorder, System.out,
            code -> System.exit(SpringApplication.exit(context, () -> code)));
    }

    BootstrapEmergencyCommand(PlatformCredentialRepository credentials,
                              TenantUserRepository users,
                              AuditRecorder auditRecorder,
                              PrintStream output,
                              IntConsumer exitHandler) {
        this.credentials = credentials;
        this.users = users;
        this.auditRecorder = auditRecorder;
        this.output = output;
        this.exitHandler = exitHandler;
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
        emergencyExecuted = true;
    }

    /**
     * 上下文就绪（所有 ApplicationRunner 事务已提交）后，若本次为应急命令启动则请求干净退出。
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        exitIfEmergencyExecuted();
    }

    void exitIfEmergencyExecuted() {
        if (emergencyExecuted) {
            exitHandler.accept(0);
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
        auditRecorder.record(AuditAction.EXECUTE, "platform_credential", userId,
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
        users.findByTenantIdAndUserId(tenantId, userId).ifPresent(user ->
            users.save(new TenantUser(
                user.id(), user.tenantId(), user.userId(), user.displayName(),
                "ACTIVE", user.version() + 1L, user.createdAt(), user.createdBy(),
                now, actor, credential.traceId())));
        auditRecorder.record(AuditAction.EXECUTE, "platform_credential", userId,
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
