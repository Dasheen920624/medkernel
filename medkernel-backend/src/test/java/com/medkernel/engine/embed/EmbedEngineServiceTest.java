package com.medkernel.engine.embed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEventPublisher;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

class EmbedEngineServiceTest {

    private static final String TRUSTED_ORIGIN = "https://his.hospital.com";

    private EmbedLaunchTokenRepository tokenRepo;
    private EmbedOriginWhitelistRepository originRepo;
    private AuditEventPublisher auditPublisher;
    private IsolatedAuditPublisher isolatedAudit;
    private EmbedEngineService service;

    @BeforeEach
    void setUp() {
        tokenRepo = mock(EmbedLaunchTokenRepository.class);
        originRepo = mock(EmbedOriginWhitelistRepository.class);
        auditPublisher = mock(AuditEventPublisher.class);
        isolatedAudit = mock(IsolatedAuditPublisher.class);
        service = new EmbedEngineService(tokenRepo, originRepo, auditPublisher, isolatedAudit);

        RequestContext.restore(new RequestContext.Snapshot("trace-1", OrgScope.tenant("tenant-1"), "user-1"));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void generateToken_SucceedsAndSavesUNUSEDToken() {
        EmbedLaunchTokenRequest req = new EmbedLaunchTokenRequest("user-1", "doctor", "P100", "E200", "OUTPATIENT", 60);
        when(tokenRepo.save(any(EmbedLaunchToken.class))).thenAnswer(inv -> inv.getArgument(0));

        EmbedLaunchTokenResponse res = service.generateToken(req);

        assertThat(res.token()).startsWith("tkn-");
        assertThat(res.embedUrl()).contains(res.token());
        assertThat(res.expiredAt()).isAfter(Instant.now());
        verify(tokenRepo).save(any(EmbedLaunchToken.class));
        verify(auditPublisher).publish(eq(AuditAction.CREATE), eq("embed_launch_token"), eq(res.token()), any());
    }

    @Test
    void generateToken_PersistsThreeRouteAndCdsHookContract() {
        EmbedLaunchTokenRequest req = new EmbedLaunchTokenRequest(
            "user-1", "doctor", "P100", "E200", "patient-view", 60,
            EmbedIntegrationMode.SDK, "patient-view", "hook-instance-001");
        when(tokenRepo.save(any(EmbedLaunchToken.class))).thenAnswer(inv -> inv.getArgument(0));

        EmbedLaunchTokenResponse res = service.generateToken(req);

        assertThat(res.integrationMode()).isEqualTo(EmbedIntegrationMode.SDK);
        assertThat(res.launchEndpoint()).isEqualTo("/api/v1/engine/embed/launch");
        verify(tokenRepo).save(org.mockito.ArgumentMatchers.argThat(saved ->
            saved.integrationMode().equals(EmbedIntegrationMode.SDK.name())
                && saved.hook().equals("patient-view")
                && saved.hookInstance().equals("hook-instance-001")
                && saved.status().equals("UNUSED")));
    }

    @Test
    void validateAndExchange_UNUSEDToken_SucceedsAndAtomicallyLocksUSED() {
        String tokenVal = "tkn-123456";
        Instant expiredAt = Instant.now().plusSeconds(60);
        EmbedLaunchToken unused = new EmbedLaunchToken(
            1L, tokenVal, "tenant-1", "user-1", "doctor", "P100", "E200", "OUTPATIENT",
            "UNUSED", expiredAt, Instant.now(), "user-1", Instant.now(), "user-1", "trace-1"
        );

        when(tokenRepo.findByToken(tokenVal)).thenReturn(Optional.of(unused));
        allowTrustedOrigin();
        when(tokenRepo.consumeUnusedToken(eq(tokenVal), eq("tenant-1"), any(), any(), eq("user-1"))).thenReturn(1);

        EmbedLaunchContextResponse res = service.validateAndExchange(
            new EmbedLaunchRequest(tokenVal, EmbedIntegrationMode.IFRAME, "patient-view", "hook-instance-001"),
            TRUSTED_ORIGIN);

        assertThat(res.active()).isTrue();
        assertThat(res.userId()).isEqualTo("user-1");
        assertThat(res.tenantId()).isEqualTo("tenant-1");
        assertThat(res.patientId()).isEqualTo("P100");
        assertThat(res.integrationMode()).isEqualTo(EmbedIntegrationMode.IFRAME);
        assertThat(res.modelStatus()).isEqualTo(EmbedModelStatus.MODEL_DISABLED);
        assertThat(res.connectionStatus()).isEqualTo(EmbedConnectionStatus.CONNECTED);
        assertThat(res.cdsHookVersion()).isEqualTo("1.0");

        verify(tokenRepo).consumeUnusedToken(eq(tokenVal), eq("tenant-1"), any(), any(), eq("user-1"));
        verify(tokenRepo, never()).save(any(EmbedLaunchToken.class));
        verify(auditPublisher).publish(eq(AuditAction.EXECUTE), eq("embed_launch_token"), eq(tokenVal), any());
    }

    @Test
    void validateAndExchange_MissingOriginThrowsForbiddenBeforeConsumingToken() {
        String tokenVal = "tkn-123456";
        EmbedLaunchToken unused = new EmbedLaunchToken(
            1L, tokenVal, "tenant-1", "user-1", "doctor", "P100", "E200", "patient-view",
            "UNUSED", Instant.now().plusSeconds(60), Instant.now(), "user-1", Instant.now(), "user-1", "trace-1",
            EmbedIntegrationMode.IFRAME.name(), "patient-view", "hook-instance-001", null
        );

        when(tokenRepo.findByToken(tokenVal)).thenReturn(Optional.of(unused));

        assertThatThrownBy(() -> service.validateAndExchange(
                new EmbedLaunchRequest(tokenVal, EmbedIntegrationMode.IFRAME, "patient-view", "hook-instance-001"),
                " "))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ENG_EMBED_002);

        verify(originRepo, never()).findByTenantIdAndOrigin(any(), any());
        verify(tokenRepo, never()).consumeUnusedToken(any(), any(), any(), any(), any());
        verify(isolatedAudit).publishInNewTx(any());
    }

    @Test
    void validateAndExchange_ModeMismatchThrowsBadRequestBeforeConsumingToken() {
        String tokenVal = "tkn-123456";
        EmbedLaunchToken unused = new EmbedLaunchToken(
            1L, tokenVal, "tenant-1", "user-1", "doctor", "P100", "E200", "patient-view",
            "UNUSED", Instant.now().plusSeconds(60), Instant.now(), "user-1", Instant.now(), "user-1", "trace-1",
            EmbedIntegrationMode.SDK.name(), "patient-view", "hook-instance-001", null
        );

        when(tokenRepo.findByToken(tokenVal)).thenReturn(Optional.of(unused));
        allowTrustedOrigin();

        assertThatThrownBy(() -> service.validateAndExchange(
                new EmbedLaunchRequest(tokenVal, EmbedIntegrationMode.API, "patient-view", "hook-instance-001"),
                TRUSTED_ORIGIN))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ENG_EMBED_005);

        verify(tokenRepo, never()).consumeUnusedToken(any(), any(), any(), any(), any());
        verify(isolatedAudit).publishInNewTx(any());
    }

    @Test
    void validateAndExchange_RevokedTokenThrowsConflict() {
        String tokenVal = "tkn-revoked";
        EmbedLaunchToken revoked = new EmbedLaunchToken(
            1L, tokenVal, "tenant-1", "user-1", "doctor", "P100", "E200", "patient-view",
            "REVOKED", Instant.now().plusSeconds(60), Instant.now(), "user-1", Instant.now(), "user-1", "trace-1",
            EmbedIntegrationMode.IFRAME.name(), "patient-view", "hook-instance-001", null
        );

        when(tokenRepo.findByToken(tokenVal)).thenReturn(Optional.of(revoked));
        allowTrustedOrigin();

        assertThatThrownBy(() -> service.validateAndExchange(
                new EmbedLaunchRequest(tokenVal, EmbedIntegrationMode.IFRAME, "patient-view", "hook-instance-001"),
                TRUSTED_ORIGIN))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ENG_EMBED_005);

        verify(tokenRepo, never()).consumeUnusedToken(any(), any(), any(), any(), any());
        verify(isolatedAudit).publishInNewTx(any());
    }

    @Test
    void validateAndExchange_AlreadyUSEDToken_ThrowsConflict() {
        String tokenVal = "tkn-123456";
        EmbedLaunchToken used = new EmbedLaunchToken(
            1L, tokenVal, "tenant-1", "user-1", "doctor", "P100", "E200", "OUTPATIENT",
            "USED", Instant.now().plusSeconds(60), Instant.now(), "user-1", Instant.now(), "user-1", "trace-1"
        );

        when(tokenRepo.findByToken(tokenVal)).thenReturn(Optional.of(used));
        allowTrustedOrigin();

        assertThatThrownBy(() -> service.validateAndExchange(
                new EmbedLaunchRequest(tokenVal, EmbedIntegrationMode.IFRAME, "patient-view", "hook-instance-001"),
                TRUSTED_ORIGIN))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ENG_EMBED_003);

        verify(isolatedAudit).publishInNewTx(any());
    }

    @Test
    void validateAndExchange_ExpiredToken_ThrowsExpiredAndSetsEXPIRED() {
        String tokenVal = "tkn-123456";
        EmbedLaunchToken unused = new EmbedLaunchToken(
            1L, tokenVal, "tenant-1", "user-1", "doctor", "P100", "E200", "OUTPATIENT",
            "UNUSED", Instant.now().minusSeconds(10), Instant.now().minusSeconds(100), "user-1", Instant.now().minusSeconds(100), "user-1", "trace-1"
        );

        when(tokenRepo.findByToken(tokenVal)).thenReturn(Optional.of(unused));
        allowTrustedOrigin();
        when(tokenRepo.save(any(EmbedLaunchToken.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.validateAndExchange(
                new EmbedLaunchRequest(tokenVal, EmbedIntegrationMode.IFRAME, "patient-view", "hook-instance-001"),
                TRUSTED_ORIGIN))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ENG_EMBED_001);

        verify(tokenRepo).save(any(EmbedLaunchToken.class));
        verify(isolatedAudit).publishInNewTx(any());
    }

    @Test
    void validateAndExchange_OriginNotInWhitelist_ThrowsForbidden() {
        String tokenVal = "tkn-123456";
        EmbedLaunchToken used = new EmbedLaunchToken(
            1L, tokenVal, "tenant-1", "user-1", "doctor", "P100", "E200", "OUTPATIENT",
            "USED", Instant.now().plusSeconds(60), Instant.now(), "user-1", Instant.now(), "user-1", "trace-1"
        );

        when(tokenRepo.findByToken(tokenVal)).thenReturn(Optional.of(used));
        when(originRepo.findByTenantIdAndOrigin("tenant-1", "https://unauthorized.domain.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validateAndExchange(
                new EmbedLaunchRequest(tokenVal, EmbedIntegrationMode.IFRAME, "patient-view", "hook-instance-001"),
                "https://unauthorized.domain.com"))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ENG_EMBED_002);

        verify(isolatedAudit).publishInNewTx(any());
    }

    @Test
    void feedback_SucceedsAndPublishesAudit() {
        String tokenVal = "tkn-123456";
        EmbedLaunchToken used = new EmbedLaunchToken(
            1L, tokenVal, "tenant-1", "user-1", "doctor", "P100", "E200", "OUTPATIENT",
            "USED", Instant.now().plusSeconds(60), Instant.now(), "user-1", Instant.now(), "user-1", "trace-1"
        );

        when(tokenRepo.findByToken(tokenVal)).thenReturn(Optional.of(used));
        EmbedFeedbackRequest req = new EmbedFeedbackRequest(tokenVal, "ADOPT", "患者风险已确认，安排开医嘱");

        EmbedFeedbackResponse response = service.feedback(req);

        assertThat(response.actionType()).isEqualTo("ADOPT");
        assertThat(response.callbackStatus()).isEqualTo(EmbedConnectionStatus.NOT_CONNECTED);
        assertThat(response.traceId()).isEqualTo("trace-1");
        verify(auditPublisher).publish(eq(AuditAction.FEEDBACK), eq("embed_launch_token"), eq(tokenVal), any());
    }

    @Test
    void addAndGetOrigins_Succeeds() {
        when(originRepo.findByTenantIdAndOrigin("tenant-1", "https://his.hospital.com")).thenReturn(Optional.empty());
        when(originRepo.save(any(EmbedOriginWhitelist.class))).thenAnswer(inv -> inv.getArgument(0));
        when(originRepo.findByTenantId("tenant-1")).thenReturn(List.of(
            new EmbedOriginWhitelist(1L, "tenant-1", "https://his.hospital.com", Instant.now(), "user-1", Instant.now(), "user-1")
        ));

        service.addOrigin(new EmbedOriginRequest("https://his.hospital.com"));
        List<String> list = service.getOrigins();

        assertThat(list).containsExactly("https://his.hospital.com");
        verify(originRepo).save(any(EmbedOriginWhitelist.class));
    }

    private void allowTrustedOrigin() {
        when(originRepo.findByTenantIdAndOrigin("tenant-1", TRUSTED_ORIGIN)).thenReturn(Optional.of(
            new EmbedOriginWhitelist(1L, "tenant-1", TRUSTED_ORIGIN, Instant.now(), "user-1", Instant.now(), "user-1")
        ));
    }
}
