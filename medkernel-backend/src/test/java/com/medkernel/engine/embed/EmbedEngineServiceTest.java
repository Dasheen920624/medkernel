package com.medkernel.engine.embed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.medkernel.engine.recommendation.RecommendationEngineService;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

class EmbedEngineServiceTest {

    private static final String TRUSTED_ORIGIN = "https://his.hospital.com";

    private EmbedLaunchTokenRepository tokenRepo;
    private EmbedOriginWhitelistRepository originRepo;
    private IsolatedAuditPublisher isolatedAudit;
    private EmbedEngineService service;

    @BeforeEach
    void setUp() {
        tokenRepo = mock(EmbedLaunchTokenRepository.class);
        originRepo = mock(EmbedOriginWhitelistRepository.class);
        isolatedAudit = mock(IsolatedAuditPublisher.class);
        service = new EmbedEngineService(
            tokenRepo,
            originRepo,
            mock(AuditRecorder.class),
            isolatedAudit,
            mock(RecommendationEngineService.class));
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-1", OrgScope.tenant("tenant-1"), "doctor-1"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "doctor-1", null, java.util.List.of(
                    new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER"))));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @EnumSource(EmbedIntegrationMode.class)
    void generateTokenPersistsCanonicalCdsHookContract(EmbedIntegrationMode mode) {
        if (mode != EmbedIntegrationMode.API) {
            allowTrustedOrigin();
        }
        when(tokenRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EmbedLaunchTokenResponse response = service.generateToken(new EmbedLaunchTokenRequest(
            "clinical-decision-user",
            "MPI-1001",
            "ENC-2001",
            "ORDER_SIGN",
            60,
            mode,
            null,
            null,
            mode == EmbedIntegrationMode.API ? null : TRUSTED_ORIGIN));

        assertThat(response.token()).startsWith("tkn-");
        assertThat(response.integrationMode()).isEqualTo(mode);
        assertThat(response.hook()).isEqualTo("order-sign");
        assertThat(response.hookInstance()).isEqualTo("trace-1");
        verify(tokenRepo).save(org.mockito.ArgumentMatchers.argThat(saved ->
            saved.status().equals(EmbedLaunchTokenStatus.UNUSED.name())
                && saved.integrationMode().equals(mode.name())
                && saved.triggerPoint().equals("order-sign")
                && saved.hook().equals("order-sign")
                && saved.hookInstance().equals("trace-1")));
    }

    @Test
    void generateTokenRejectsUnsupportedHookBeforeSaving() {
        assertThatThrownBy(() -> service.generateToken(new EmbedLaunchTokenRequest(
            "clinical-decision-user",
            "MPI-1001",
            "ENC-2001",
            "OUTPATIENT",
            60,
            EmbedIntegrationMode.IFRAME,
            null,
            null,
            TRUSTED_ORIGIN)))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ENG_EMBED_005);

        verify(tokenRepo, never()).save(any());
        verify(isolatedAudit).publishInNewTx(any());
    }

    @Test
    void exchangeConsumesTokenOnceAndExtendsSessionExpiry() {
        String tokenValue = "tkn-exchange";
        EmbedLaunchToken token = token(
            tokenValue, EmbedLaunchTokenStatus.UNUSED, Instant.now().plusSeconds(60),
            EmbedIntegrationMode.IFRAME, "patient-view", TRUSTED_ORIGIN);
        when(tokenRepo.findByToken(tokenValue)).thenReturn(Optional.of(token));
        when(tokenRepo.consumeUnusedToken(
            eq(tokenValue), eq("tenant-1"), any(), any(), any(), eq("doctor-1"))).thenReturn(1);

        EmbedLaunchContextResponse response = service.validateAndExchange(
            new EmbedLaunchRequest(tokenValue, EmbedIntegrationMode.IFRAME, "patient-view", "hook-1"));

        assertThat(response.active()).isTrue();
        assertThat(response.parentOrigin()).isEqualTo(TRUSTED_ORIGIN);
        verify(tokenRepo).consumeUnusedToken(
            eq(tokenValue), eq("tenant-1"), any(), any(), any(), eq("doctor-1"));
    }

    @Test
    void exchangeRejectsUsedExpiredAndModeMismatchTokens() {
        assertExchangeError(
            token("tkn-used", EmbedLaunchTokenStatus.USED, Instant.now().plusSeconds(60),
                EmbedIntegrationMode.IFRAME, "patient-view", TRUSTED_ORIGIN),
            new EmbedLaunchRequest("tkn-used", EmbedIntegrationMode.IFRAME, "patient-view", "hook-1"),
            ErrorCode.ENG_EMBED_003);
        assertExchangeError(
            token("tkn-expired", EmbedLaunchTokenStatus.UNUSED, Instant.now().minusSeconds(1),
                EmbedIntegrationMode.IFRAME, "patient-view", TRUSTED_ORIGIN),
            new EmbedLaunchRequest("tkn-expired", EmbedIntegrationMode.IFRAME, "patient-view", "hook-1"),
            ErrorCode.ENG_EMBED_001);
        assertExchangeError(
            token("tkn-mode", EmbedLaunchTokenStatus.UNUSED, Instant.now().plusSeconds(60),
                EmbedIntegrationMode.SDK, "patient-view", TRUSTED_ORIGIN),
            new EmbedLaunchRequest("tkn-mode", EmbedIntegrationMode.IFRAME, "patient-view", "hook-1"),
            ErrorCode.ENG_EMBED_005);
    }

    @Test
    void exchangeRejectsAtomicReplayRace() {
        String tokenValue = "tkn-race";
        EmbedLaunchToken unused = token(
            tokenValue, EmbedLaunchTokenStatus.UNUSED, Instant.now().plusSeconds(60),
            EmbedIntegrationMode.IFRAME, "patient-view", TRUSTED_ORIGIN);
        EmbedLaunchToken used = token(
            tokenValue, EmbedLaunchTokenStatus.USED, Instant.now().plusSeconds(1800),
            EmbedIntegrationMode.IFRAME, "patient-view", TRUSTED_ORIGIN);
        when(tokenRepo.findByToken(tokenValue)).thenReturn(Optional.of(unused), Optional.of(used));
        when(tokenRepo.consumeUnusedToken(
            eq(tokenValue), eq("tenant-1"), any(), any(), any(), eq("doctor-1"))).thenReturn(0);

        assertThatThrownBy(() -> service.validateAndExchange(
            new EmbedLaunchRequest(tokenValue, EmbedIntegrationMode.IFRAME, "patient-view", "hook-1")))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ENG_EMBED_003);
    }

    private void assertExchangeError(
            EmbedLaunchToken token,
            EmbedLaunchRequest request,
            ErrorCode errorCode) {
        when(tokenRepo.findByToken(token.token())).thenReturn(Optional.of(token));
        when(tokenRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.validateAndExchange(request))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", errorCode);
    }

    private EmbedLaunchToken token(
            String tokenValue,
            EmbedLaunchTokenStatus status,
            Instant expiredAt,
            EmbedIntegrationMode mode,
            String hook,
            String parentOrigin) {
        Instant now = Instant.now();
        return new EmbedLaunchToken(
            1L,
            tokenValue,
            "tenant-1",
            "doctor-1",
            "clinical-decision-user",
            "MPI-1001",
            "ENC-2001",
            hook,
            status.name(),
            expiredAt,
            now,
            "issuer-1",
            now,
            "issuer-1",
            "trace-1",
            mode.name(),
            hook,
            "hook-1",
            status == EmbedLaunchTokenStatus.USED ? now : null,
            parentOrigin);
    }

    private void allowTrustedOrigin() {
        when(originRepo.findByTenantIdAndOrigin("tenant-1", TRUSTED_ORIGIN)).thenReturn(Optional.of(
            new EmbedOriginWhitelist(
                1L, "tenant-1", TRUSTED_ORIGIN, Instant.now(), "doctor-1", Instant.now(), "doctor-1")));
    }
}
