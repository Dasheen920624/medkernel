package com.medkernel.engine.embed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.medkernel.engine.recommendation.RecommendationCardDetailResponse;
import com.medkernel.engine.recommendation.RecommendationCardStatus;
import com.medkernel.engine.recommendation.RecommendationClinicalCardResponse;
import com.medkernel.engine.recommendation.RecommendationEngineService;
import com.medkernel.engine.recommendation.RecommendationFeedbackResponse;
import com.medkernel.engine.recommendation.RecommendationFeedbackRequest;
import com.medkernel.engine.recommendation.RecommendationFeedbackType;
import com.medkernel.engine.recommendation.RecommendationTrigger;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

class EmbedEngineExternalHostTest {

    private static final String TRUSTED_ORIGIN = "https://his.hospital.com";
    private static final String TOKEN = "tkn-external-host";

    private EmbedLaunchTokenRepository tokenRepo;
    private EmbedOriginWhitelistRepository originRepo;
    private RecommendationEngineService recommendations;
    private EmbedEngineService service;

    @BeforeEach
    void setUp() {
        tokenRepo = mock(EmbedLaunchTokenRepository.class);
        originRepo = mock(EmbedOriginWhitelistRepository.class);
        recommendations = mock(RecommendationEngineService.class);
        service = new EmbedEngineService(
            tokenRepo,
            originRepo,
            mock(AuditRecorder.class),
            mock(IsolatedAuditPublisher.class),
            recommendations);
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-external-host", OrgScope.tenant("tenant-1"), "doctor-1"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "doctor-1", null, List.of(
                    new SimpleGrantedAuthority("ROLE_CLINICAL_USER"))));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void generateTokenBindsWhitelistedParentOrigin() {
        allowTrustedOrigin();
        when(tokenRepo.save(any(EmbedLaunchToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmbedLaunchTokenResponse response = service.generateToken(new EmbedLaunchTokenRequest(
            "clinical-user",
            "MPI-1001",
            "ENC-2001",
            "patient-view",
            120,
            EmbedIntegrationMode.IFRAME,
            "patient-view",
            "hook-1",
            TRUSTED_ORIGIN));

        assertThat(response.embedUrl()).contains("token=").doesNotContain("parentOrigin");
        verify(tokenRepo).save(org.mockito.ArgumentMatchers.argThat(
            token -> TRUSTED_ORIGIN.equals(token.parentOrigin())));
    }

    @Test
    void generateTokenRejectsUnlistedParentOriginBeforePersisting() {
        when(originRepo.findByTenantIdAndOrigin("tenant-1", "https://evil.example"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateToken(new EmbedLaunchTokenRequest(
            "clinical-user",
            "MPI-1001",
            "ENC-2001",
            "patient-view",
            120,
            EmbedIntegrationMode.IFRAME,
            "patient-view",
            "hook-1",
            "https://evil.example")))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ENG_EMBED_002);

        verify(tokenRepo, never()).save(any());
    }

    @Test
    void generateTokenRejectsRoleNotHeldByAuthenticatedIssuer() {
        allowTrustedOrigin();

        assertThatThrownBy(() -> service.generateToken(new EmbedLaunchTokenRequest(
            "engine-operator",
            "MPI-1001",
            "ENC-2001",
            "patient-view",
            120,
            EmbedIntegrationMode.IFRAME,
            "patient-view",
            "hook-1",
            TRUSTED_ORIGIN)))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ENG_EMBED_005);

        verify(tokenRepo, never()).save(any());
    }

    @Test
    void exchangeUsesTokenBoundOriginWithoutAuthenticationCookieOrOriginHeader() {
        EmbedLaunchToken token = usedOrUnusedToken(EmbedLaunchTokenStatus.UNUSED);
        when(tokenRepo.findByToken(TOKEN)).thenReturn(Optional.of(token));
        when(tokenRepo.consumeUnusedToken(
            eq(TOKEN), eq("tenant-1"), any(), any(), any(), eq("doctor-1")))
            .thenReturn(1);

        EmbedLaunchContextResponse response = service.validateAndExchange(
            new EmbedLaunchRequest(TOKEN, EmbedIntegrationMode.IFRAME, "patient-view", "hook-1"));

        assertThat(response.parentOrigin()).isEqualTo(TRUSTED_ORIGIN);
        assertThat(response.patientId()).isEqualTo("MPI-1001");
    }

    @Test
    void listCardsScopesQueryToTokenPatientEncounterAndTrigger() {
        when(tokenRepo.findByToken(TOKEN)).thenReturn(Optional.of(usedOrUnusedToken(EmbedLaunchTokenStatus.USED)));
        RecommendationClinicalCardResponse card = mock(RecommendationClinicalCardResponse.class);
        when(card.cardId()).thenReturn("card-1");
        when(card.title()).thenReturn("真实临床建议");
        when(card.status()).thenReturn(RecommendationCardStatus.PENDING);
        when(recommendations.listClinicalCards(any(), any())).thenReturn(
            new PageResponse<>(List.of(card), 1, 20, 1, false, false));

        EmbedRecommendationCardsResponse response =
            service.listCards(new EmbedRecommendationCardsRequest(TOKEN));

        assertThat(response.items()).extracting(EmbedRecommendationCardResponse::cardId)
            .containsExactly("card-1");
        verify(recommendations).listClinicalCards(
            org.mockito.ArgumentMatchers.argThat(filter ->
                "MPI-1001".equals(filter.patientId())
                    && "ENC-2001".equals(filter.encounterId())
                    && "patient-view".equals(filter.triggerPoint())),
            any());
    }

    @Test
    void feedbackMapsAllFiveHostActionsToRecommendationStateMachine() {
        EmbedLaunchToken token = usedOrUnusedToken(EmbedLaunchTokenStatus.USED);
        when(tokenRepo.findByToken(TOKEN)).thenReturn(Optional.of(token));
        RecommendationCardDetailResponse detail = mock(RecommendationCardDetailResponse.class);
        RecommendationTrigger trigger = mock(RecommendationTrigger.class);
        when(detail.trigger()).thenReturn(trigger);
        when(trigger.patientId()).thenReturn("MPI-1001");
        when(trigger.encounterId()).thenReturn("ENC-2001");
        when(trigger.triggerType()).thenReturn("patient-view");
        when(recommendations.cardDetail("card-1")).thenReturn(detail);
        when(recommendations.feedback(eq("card-1"), any())).thenReturn(
            new RecommendationFeedbackResponse(
                "feedback-1", "card-1", RecommendationCardStatus.ACCEPTED, "trace-feedback"));

        for (EmbedFeedbackActionType action : EmbedFeedbackActionType.values()) {
            EmbedFeedbackResponse response = service.feedback(new EmbedFeedbackRequest(
                TOKEN, "card-1", action.name(), "外部工作站真实反馈"));
            assertThat(response.cardId()).isEqualTo("card-1");
            assertThat(response.actionType()).isEqualTo(action.name());
        }

        ArgumentCaptor<RecommendationFeedbackRequest> feedbackCaptor =
            ArgumentCaptor.forClass(RecommendationFeedbackRequest.class);
        verify(recommendations, times(5)).feedback(eq("card-1"), feedbackCaptor.capture());
        assertThat(feedbackCaptor.getAllValues())
            .extracting(RecommendationFeedbackRequest::feedbackType)
            .containsExactly(
                RecommendationFeedbackType.ACCEPT,
                RecommendationFeedbackType.REJECT,
                RecommendationFeedbackType.DEFER,
                RecommendationFeedbackType.DISMISS,
                RecommendationFeedbackType.DISMISS);
    }

    @Test
    void feedbackRejectsCardOutsideTokenClinicalContext() {
        when(tokenRepo.findByToken(TOKEN)).thenReturn(Optional.of(usedOrUnusedToken(EmbedLaunchTokenStatus.USED)));
        RecommendationCardDetailResponse detail = mock(RecommendationCardDetailResponse.class);
        RecommendationTrigger trigger = mock(RecommendationTrigger.class);
        when(detail.trigger()).thenReturn(trigger);
        when(trigger.patientId()).thenReturn("MPI-OTHER");
        when(recommendations.cardDetail("card-other")).thenReturn(detail);

        assertThatThrownBy(() -> service.feedback(new EmbedFeedbackRequest(
            TOKEN, "card-other", "ADOPT", "尝试越权反馈")))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ENG_EMBED_005);

        verify(recommendations, never()).feedback(eq("card-other"), any());
    }

    private EmbedLaunchToken usedOrUnusedToken(EmbedLaunchTokenStatus status) {
        Instant now = Instant.now();
        return new EmbedLaunchToken(
            1L,
            TOKEN,
            "tenant-1",
            "doctor-1",
            "clinical-user",
            "MPI-1001",
            "ENC-2001",
            "patient-view",
            status.name(),
            now.plusSeconds(120),
            now,
            "issuer-1",
            now,
            "issuer-1",
            "trace-external-host",
            EmbedIntegrationMode.IFRAME.name(),
            "patient-view",
            "hook-1",
            status == EmbedLaunchTokenStatus.USED ? now : null,
            TRUSTED_ORIGIN);
    }

    private void allowTrustedOrigin() {
        when(originRepo.findByTenantIdAndOrigin("tenant-1", TRUSTED_ORIGIN)).thenReturn(Optional.of(
            new EmbedOriginWhitelist(
                1L, "tenant-1", TRUSTED_ORIGIN, Instant.now(), "doctor-1", Instant.now(), "doctor-1")));
    }
}
