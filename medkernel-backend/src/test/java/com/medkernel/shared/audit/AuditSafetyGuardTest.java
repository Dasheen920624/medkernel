package com.medkernel.shared.audit;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditSafetyGuardTest {

    @Test
    void disablingAuditPersistenceIsRejectedAndAudited() {
        AuditRecorder recorder = mock(AuditRecorder.class);
        AuditSafetyGuard guard = new AuditSafetyGuard(recorder);

        AuditConfigChangeCommand command = new AuditConfigChangeCommand(
            "medkernel.audit.persistence.enabled",
            "true",
            "false",
            "测试关闭审计持久化");

        assertThatThrownBy(() -> guard.assertChangeAllowed(command))
            .isInstanceOf(ApiException.class)
            .satisfies(t -> assertThat(((ApiException) t).errorCode())
                .isEqualTo(ErrorCode.ENG_AUDIT_001));

        ArgumentCaptor<AuditRecordCommand> captor = ArgumentCaptor.forClass(AuditRecordCommand.class);
        verify(recorder).record(captor.capture());
        AuditRecordCommand audit = captor.getValue();
        assertThat(audit.action()).isEqualTo(AuditAction.PERMISSION_CHANGE);
        assertThat(audit.targetType()).isEqualTo("audit_config");
        assertThat(audit.targetId()).isEqualTo("medkernel.audit.persistence.enabled");
        assertThat(audit.summary()).contains("拒绝关闭审计持久化");
    }

    @Test
    void unrelatedAuditSettingChangeIsAllowed() {
        AuditRecorder recorder = mock(AuditRecorder.class);
        AuditSafetyGuard guard = new AuditSafetyGuard(recorder);

        assertThatCode(() -> guard.assertChangeAllowed(new AuditConfigChangeCommand(
            "medkernel.audit.banner",
            "old",
            "new",
            "更新审计页提示")))
            .doesNotThrowAnyException();
    }

    @Test
    void disablingAuditPersistenceRuntimeFeatureFlagIsRejected() {
        AuditRecorder recorder = mock(AuditRecorder.class);
        AuditSafetyGuard guard = new AuditSafetyGuard(recorder);

        AuditConfigChangeCommand command = new AuditConfigChangeCommand(
            "medkernel.runtime.feature-flags.audit-persistence.enabled",
            "true",
            "false",
            "验证配置中心高危护栏");

        assertThatThrownBy(() -> guard.assertChangeAllowed(command))
            .isInstanceOf(ApiException.class)
            .satisfies(t -> assertThat(((ApiException) t).errorCode())
                .isEqualTo(ErrorCode.ENG_AUDIT_001));
    }
}
