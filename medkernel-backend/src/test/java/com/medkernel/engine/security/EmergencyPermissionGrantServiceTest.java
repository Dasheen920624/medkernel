package com.medkernel.engine.security;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmergencyPermissionGrantServiceTest {

    private final EmergencyPermissionGrantRepository repository = mock(EmergencyPermissionGrantRepository.class);
    private final AuditRecorder auditRecorder = mock(AuditRecorder.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-31T12:00:00Z"), ZoneOffset.UTC);
    private final EmergencyPermissionGrantService service =
        new EmergencyPermissionGrantService(repository, auditRecorder, clock);

    @Test
    void grantEmergencyPermissionPersistsTimeboxedRecordAndPublishesAudit() {
        Instant expiresAt = clock.instant().plusSeconds(1800);
        when(repository.save(any(EmergencyPermissionGrant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmergencyPermissionGrant saved = service.grant(
            "t-1",
            "doctor-1",
            expiresAt,
            "抢救高危患者需要临时访问应急环境",
            "chief-1");

        ArgumentCaptor<EmergencyPermissionGrant> grantCaptor =
            ArgumentCaptor.forClass(EmergencyPermissionGrant.class);
        verify(repository).save(grantCaptor.capture());
        EmergencyPermissionGrant grant = grantCaptor.getValue();

        assertThat(saved).isEqualTo(grant);
        assertThat(grant.tenantId()).isEqualTo("t-1");
        assertThat(grant.userId()).isEqualTo("doctor-1");
        assertThat(grant.permissionCode()).isEqualTo(PermissionCode.ENV_EMERGENCY.code());
        assertThat(grant.activeFlag()).isEqualTo("Y");
        assertThat(grant.grantedAt()).isEqualTo(clock.instant());
        assertThat(grant.expiresAt()).isEqualTo(expiresAt);
        assertThat(grant.activeAt(clock.instant())).isTrue();
        verify(auditRecorder).record(
            eq(AuditAction.PERMISSION_CHANGE),
            eq("emergency_permission_grant"),
            eq("t-1:doctor-1:env.emergency"),
            eq("应急权限授予 user=doctor-1 expiresAt=2026-05-31T12:30:00Z"));
    }

    @Test
    void grantRejectsMissingReasonOrExpiredWindow() {
        assertThatThrownBy(() -> service.grant(
            "t-1",
            "doctor-1",
            clock.instant().plusSeconds(300),
            " ",
            "chief-1"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("应急权限原因不能为空");

        assertThatThrownBy(() -> service.grant(
            "t-1",
            "doctor-1",
            clock.instant(),
            "抢救高危患者需要临时访问应急环境",
            "chief-1"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("应急权限过期时间必须晚于当前时间");
    }
}
