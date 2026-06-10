package com.medkernel.engine.security.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import com.medkernel.engine.security.PlatformCredential;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.engine.security.TenantUserRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;

class BootstrapEmergencyCommandTest {

    private PlatformCredentialRepository credentials;
    private AuditRecorder auditRecorder;
    private TenantUserRepository users;
    private ByteArrayOutputStream output;
    private BootstrapEmergencyCommand command;
    private AtomicReference<PlatformCredential> saved;
    private AtomicReference<Integer> exitCode;

    @BeforeEach
    void setUp() {
        credentials = mock(PlatformCredentialRepository.class);
        users = mock(TenantUserRepository.class);
        auditRecorder = mock(AuditRecorder.class);
        output = new ByteArrayOutputStream();
        exitCode = new AtomicReference<>();
        command = new BootstrapEmergencyCommand(credentials, users, auditRecorder,
            new PrintStream(output, true, StandardCharsets.UTF_8), exitCode::set);
        saved = new AtomicReference<>();
        when(credentials.save(any())).thenAnswer(inv -> {
            PlatformCredential credential = inv.getArgument(0);
            saved.set(credential);
            return credential;
        });
    }

    @Test
    void noEmergencyCommandDoesNothingDuringNormalStartup() throws Exception {
        command.run(new DefaultApplicationArguments("--server.port=18080"));

        verify(credentials, never()).save(any());
    }

    @Test
    void normalStartupNeverRequestsExit() {
        command.run(new DefaultApplicationArguments("--server.port=18080"));
        command.exitIfEmergencyExecuted();

        assertThat(exitCode.get()).as("正常启动不得触发救命通道退出").isNull();
    }

    @Test
    void successfulEmergencyRequestsCleanExitAfterCommit() {
        when(credentials.findByTenantIdAndUserId("t-1", "platform-owner"))
            .thenReturn(Optional.of(credential("LOCKED", "old-mfa-hash")));

        command.run(args(
            "--bootstrap-emergency=mfa-reset",
            "--tenant-id=t-1",
            "--user-id=platform-owner",
            "--actor=ops",
            "--reason=值班应急",
            "--local-confirm=MEDKERNEL_LOCAL_CONSOLE",
            "--confirm=RESET_MFA:platform-owner"));
        command.exitIfEmergencyExecuted();

        assertThat(exitCode.get()).as("救命通道执行完成后应一发干净退出").isZero();
    }

    @Test
    void mfaResetRequiresLocalAndSecondConfirmation() {
        assertThatThrownBy(() -> command.run(args(
                "--bootstrap-emergency=mfa-reset",
                "--tenant-id=t-1",
                "--user-id=platform-owner",
                "--actor=ops",
                "--reason=值班应急")))
            .isInstanceOfSatisfying(ApiException.class, e ->
                assertThat(e.errorCode()).isEqualTo(ErrorCode.ENG_AUTH_011));

        verify(credentials, never()).save(any());
    }

    @Test
    void mfaResetClearsTotpBindingAndRequiresRebind() throws Exception {
        when(credentials.findByTenantIdAndUserId("t-1", "platform-owner"))
            .thenReturn(Optional.of(credential("LOCKED", "old-mfa-hash")));

        command.run(args(
            "--bootstrap-emergency=mfa-reset",
            "--tenant-id=t-1",
            "--user-id=platform-owner",
            "--actor=ops",
            "--reason=值班应急",
            "--local-confirm=MEDKERNEL_LOCAL_CONSOLE",
            "--confirm=RESET_MFA:platform-owner"));

        String text = output.toString(StandardCharsets.UTF_8);
        assertThat(text).contains("bootstrap-emergency=mfa-reset").contains("mfaStatus=RESET_REQUIRED");
        assertThat(saved.get().mfaSecret()).isNull();
        verify(auditRecorder).record(
            AuditAction.EXECUTE, "platform_credential", "platform-owner",
            "应急重置 MFA actor=ops reason=值班应急");
    }

    @Test
    void unlockRequiresMatchingSecondConfirmationAndSetsActive() throws Exception {
        when(credentials.findByTenantIdAndUserId("t-1", "platform-owner"))
            .thenReturn(Optional.of(credential("LOCKED", "mfa-hash")));

        command.run(args(
            "--bootstrap-emergency=unlock",
            "--tenant-id=t-1",
            "--user-id=platform-owner",
            "--actor=ops",
            "--reason=值班应急",
            "--local-confirm=MEDKERNEL_LOCAL_CONSOLE",
            "--confirm=UNLOCK:platform-owner"));

        assertThat(saved.get().status()).isEqualTo("ACTIVE");
        assertThat(output.toString(StandardCharsets.UTF_8)).contains("bootstrap-emergency=unlock");
    }

    private DefaultApplicationArguments args(String... args) {
        return new DefaultApplicationArguments(args);
    }

    private PlatformCredential credential(String status, String mfaSecret) {
        Instant now = Instant.parse("2026-06-01T08:00:00Z");
        return new PlatformCredential(
            1L, "cred-platform-owner", "t-1", "platform-owner", "platform-owner",
            "$2a$10$hash", status, "N", mfaSecret,
            now, "test", now, "test", "trace-test");
    }
}
