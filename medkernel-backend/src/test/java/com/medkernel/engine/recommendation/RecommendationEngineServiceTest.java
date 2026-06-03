package com.medkernel.engine.recommendation;

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
import java.util.Map;
import java.util.Optional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.audit.AuditEventPublisher;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.observability.BusinessMetrics;
import com.medkernel.shared.observability.DiagnoseResponse;
import com.medkernel.shared.observability.DiagnoseResponseAssembler;
import com.medkernel.shared.observability.StateTransitionRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RecommendationEngineServiceTest {

    private RecommendationTriggerRepository triggers;
    private RecommendationCardRepository cards;
    private RecommendationSourceRepository sources;
    private RecommendationFeedbackRepository feedback;
    private RecommendationFatigueSignalRepository fatigueSignals;
    private AuditEventPublisher auditPublisher;
    private IsolatedAuditPublisher isolatedAudit;
    private StateTransitionRecorder transitions;
    private DiagnoseResponseAssembler diagnoseAssembler;
    private BusinessMetrics businessMetrics;
    private RecommendationDeterministicMatcher deterministicMatcher;
    private RecommendationFatiguePolicyResolver fatiguePolicyResolver;
    private RecommendationEngineService service;

    @BeforeEach
    void setUp() {
        triggers = mock(RecommendationTriggerRepository.class);
        cards = mock(RecommendationCardRepository.class);
        sources = mock(RecommendationSourceRepository.class);
        feedback = mock(RecommendationFeedbackRepository.class);
        fatigueSignals = mock(RecommendationFatigueSignalRepository.class);
        auditPublisher = mock(AuditEventPublisher.class);
        isolatedAudit = mock(IsolatedAuditPublisher.class);
        transitions = mock(StateTransitionRecorder.class);
        diagnoseAssembler = mock(DiagnoseResponseAssembler.class);
        businessMetrics = mock(BusinessMetrics.class);
        deterministicMatcher = mock(RecommendationDeterministicMatcher.class);
        fatiguePolicyResolver = mock(RecommendationFatiguePolicyResolver.class);
        service = new RecommendationEngineService(
            triggers, cards, sources, feedback, fatigueSignals,
            auditPublisher, transitions, diagnoseAssembler, isolatedAudit, businessMetrics, deterministicMatcher,
            fatiguePolicyResolver);

        when(triggers.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cards.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sources.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(feedback.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fatigueSignals.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(deterministicMatcher.match(any())).thenReturn(List.of());
        when(fatiguePolicyResolver.resolve(any())).thenReturn(Optional.empty());

        RequestContext.restore(new RequestContext.Snapshot(
            "trace-rec", OrgScope.tenant("tenant-A"), "doctor-1"));
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void triggerPersistsCardsSourcesAndFatigueSignal() {
        RecommendationTriggerResponse response = service.trigger(triggerRequest(List.of(cardRequest(
            RecommendationRiskLevel.HIGH,
            RecommendationInterruptLevel.WEAK_INTERRUPTIVE,
            true,
            List.of(sourceRequest())))));

        assertThat(response.triggerId()).startsWith("rt-");
        assertThat(response.status()).isEqualTo(RecommendationTriggerStatus.EVALUATED);
        assertThat(response.cardCount()).isEqualTo(1);
        assertThat(response.traceId()).isEqualTo("trace-rec");

        ArgumentCaptor<RecommendationTrigger> triggerCap = ArgumentCaptor.forClass(RecommendationTrigger.class);
        ArgumentCaptor<RecommendationCard> cardCap = ArgumentCaptor.forClass(RecommendationCard.class);
        ArgumentCaptor<RecommendationSource> sourceCap = ArgumentCaptor.forClass(RecommendationSource.class);
        ArgumentCaptor<RecommendationFatigueSignal> signalCap =
            ArgumentCaptor.forClass(RecommendationFatigueSignal.class);

        verify(triggers).save(triggerCap.capture());
        verify(cards).save(cardCap.capture());
        verify(sources).save(sourceCap.capture());
        verify(fatigueSignals).save(signalCap.capture());

        assertThat(triggerCap.getValue().tenantId()).isEqualTo("tenant-A");
        assertThat(cardCap.getValue().requiresPhysicianConfirmation()).isTrue();
        assertThat(sourceCap.getValue().cardId()).isEqualTo(cardCap.getValue().cardId());
        assertThat(signalCap.getValue().signalType()).isEqualTo(RecommendationFatigueSignalType.SHOWN);
        verify(auditPublisher).publish(AuditAction.EXECUTE, "recommendation_trigger",
            response.triggerId(), "接收推荐触发 TRG.ORDER");
        // CDSS-M-03：单卡触发应计入一次 CDSS 提醒指标
        verify(businessMetrics).incCdssAlerts();
    }

    @Test
    void triggerWithoutCardsIsRecordedAsNoCard() {
        RecommendationTriggerResponse response = service.trigger(triggerRequest(List.of()));

        assertThat(response.status()).isEqualTo(RecommendationTriggerStatus.NO_CARD);
        assertThat(response.cardCount()).isZero();
        ArgumentCaptor<RecommendationTrigger> triggerCap = ArgumentCaptor.forClass(RecommendationTrigger.class);
        verify(triggers).save(triggerCap.capture());
        assertThat(triggerCap.getValue().status()).isEqualTo(RecommendationTriggerStatus.NO_CARD);
        verify(cards, never()).save(any());
    }

    @Test
    void evaluateReturnsDeterministicCardsOnlyWhenModelDisabled() {
        RecommendationCardRequest deterministic = cardRequest(
            "CARD.DETERMINISTIC", false, RecommendationRiskLevel.MEDIUM,
            RecommendationInterruptLevel.INFO, false, List.of(sourceRequest()));
        RecommendationCardRequest modelGenerated = cardRequest(
            "CARD.MODEL", true, RecommendationRiskLevel.MEDIUM,
            RecommendationInterruptLevel.INFO, false, List.of(sourceRequest()));

        RecommendationEvaluationResponse response = service.evaluate(
            triggerRequest(List.of(deterministic, modelGenerated)));

        assertThat(response.modelStatus()).isEqualTo(RecommendationModelStatus.MODEL_DISABLED);
        assertThat(response.totalCardCount()).isEqualTo(1);
        assertThat(response.visibleCardCount()).isEqualTo(1);
        assertThat(response.suppressedCardCount()).isZero();
        assertThat(response.cards())
            .extracting(RecommendationCard::cardCode)
            .containsExactly("CARD.DETERMINISTIC");
        verify(cards, times(1)).save(any());
    }

    @Test
    void evaluateUsesDeterministicReplayWhenModelEnhancementIsRequestedButUnavailable() {
        RecommendationCardRequest deterministic = cardRequest(
            "RULE.REPLAY.v3", false, RecommendationRiskLevel.MEDIUM,
            RecommendationInterruptLevel.INFO, false, List.of(sourceRequest()));
        RecommendationCardRequest modelGenerated = cardRequest(
            "AI.REPLAY", true, RecommendationRiskLevel.MEDIUM,
            RecommendationInterruptLevel.INFO, false, List.of(sourceRequest()));
        RecommendationTriggerRequest request = triggerRequest(
            List.of(modelGenerated), null, null, true);
        when(deterministicMatcher.match(request)).thenReturn(List.of(deterministic));

        RecommendationEvaluationResponse response = service.evaluate(request);

        assertThat(response.modelStatus()).isEqualTo(RecommendationModelStatus.MODEL_DISABLED);
        assertThat(response.totalCardCount()).isEqualTo(1);
        assertThat(response.visibleCardCount()).isEqualTo(1);
        assertThat(response.cards())
            .extracting(RecommendationCard::cardCode)
            .containsExactly("RULE.REPLAY.v3");
        verify(deterministicMatcher).match(request);
        verify(cards, times(1)).save(any());
    }

    @Test
    void evaluatePersistsCardsGeneratedFromPublishedAssets() {
        RecommendationCardRequest generated = cardRequest(
            "RULE.RISK.v1", false, RecommendationRiskLevel.MEDIUM,
            RecommendationInterruptLevel.INFO, false, List.of(
                new RecommendationSourceRequest(
                    RecommendationSourceType.RULE, "rule-risk", "1", "风险评估规则",
                    "rule_version:rv-risk-v1", null, "规则命中"),
                new RecommendationSourceRequest(
                    RecommendationSourceType.CONTEXT, "snapshot-1", "1.0.0", "标准临床上下文",
                    "context_snapshot:snapshot-1", null, "本次评估上下文")
            ));
        RecommendationTriggerRequest request = triggerRequest(List.of());
        when(deterministicMatcher.match(request)).thenReturn(List.of(generated));

        RecommendationEvaluationResponse response = service.evaluate(request);

        assertThat(response.totalCardCount()).isEqualTo(1);
        assertThat(response.visibleCardCount()).isEqualTo(1);
        assertThat(response.cards()).extracting(RecommendationCard::cardCode)
            .containsExactly("RULE.RISK.v1");
        verify(deterministicMatcher).match(request);
        verify(sources, times(2)).save(any());
    }

    @Test
    void evaluateSuppressesLowValueRepeatedCardByRequestThreshold() {
        when(fatigueSignals.countLowValueSignals(eq("tenant-A"), eq("patient-1"),
                eq("WARD_ORDER:ANTICOAG"), any()))
            .thenReturn(3L);
        RecommendationTriggerRequest request = triggerRequest(
            List.of(cardRequest(RecommendationRiskLevel.MEDIUM, RecommendationInterruptLevel.INFO,
                false, List.of(sourceRequest()))),
            3,
            24,
            false);
        when(fatiguePolicyResolver.resolve(request))
            .thenReturn(Optional.of(new RecommendationFatiguePolicy(3, 24, "REQUEST")));

        RecommendationEvaluationResponse response = service.evaluate(request);

        assertThat(response.visibleCardCount()).isZero();
        assertThat(response.suppressedCardCount()).isEqualTo(1);
        assertThat(response.cards()).isEmpty();

        ArgumentCaptor<RecommendationCard> cardCap = ArgumentCaptor.forClass(RecommendationCard.class);
        ArgumentCaptor<RecommendationFatigueSignal> signalCap =
            ArgumentCaptor.forClass(RecommendationFatigueSignal.class);
        verify(cards).save(cardCap.capture());
        verify(fatigueSignals).save(signalCap.capture());
        assertThat(cardCap.getValue().status()).isEqualTo(RecommendationCardStatus.SUPPRESSED);
        assertThat(signalCap.getValue().signalType()).isEqualTo(RecommendationFatigueSignalType.SUPPRESSED);
        verify(businessMetrics, never()).incCdssAlerts();
    }

    @Test
    void evaluateSuppressesByConfiguredDepartmentPolicyWhenRequestThresholdIsAbsent() {
        when(fatigueSignals.countLowValueSignals(eq("tenant-A"), eq("patient-1"),
                eq("WARD_ORDER:ANTICOAG"), any()))
            .thenReturn(2L);
        RecommendationTriggerRequest request = triggerRequest(
            List.of(cardRequest(RecommendationRiskLevel.MEDIUM, RecommendationInterruptLevel.INFO,
                false, List.of(sourceRequest()))),
            null,
            null,
            false);
        when(fatiguePolicyResolver.resolve(request))
            .thenReturn(Optional.of(new RecommendationFatiguePolicy(2, 12, "CONFIG_CENTER")));

        RecommendationEvaluationResponse response = service.evaluate(request);

        assertThat(response.visibleCardCount()).isZero();
        assertThat(response.suppressedCardCount()).isEqualTo(1);
        verify(fatiguePolicyResolver).resolve(request);
        verify(fatigueSignals).countLowValueSignals(eq("tenant-A"), eq("patient-1"),
            eq("WARD_ORDER:ANTICOAG"), any());
    }

    @Test
    void evaluateDoesNotSuppressHighRiskCardEvenWhenFatigueThresholdReached() {
        when(fatigueSignals.countLowValueSignals(eq("tenant-A"), eq("patient-1"),
                eq("WARD_ORDER:ANTICOAG"), any()))
            .thenReturn(99L);
        RecommendationTriggerRequest request = triggerRequest(
            List.of(cardRequest(RecommendationRiskLevel.HIGH, RecommendationInterruptLevel.WEAK_INTERRUPTIVE,
                true, List.of(sourceRequest()))),
            3,
            24,
            false);

        RecommendationEvaluationResponse response = service.evaluate(request);

        assertThat(response.visibleCardCount()).isEqualTo(1);
        assertThat(response.suppressedCardCount()).isZero();
        assertThat(response.cards()).extracting(RecommendationCard::status)
            .containsExactly(RecommendationCardStatus.PENDING);
        verify(businessMetrics).incCdssAlerts();
    }

    @Test
    void evaluateDoesNotSuppressCriticalRedlineCardEvenWhenConfiguredThresholdReached() {
        RecommendationTriggerRequest request = triggerRequest(
            List.of(cardRequest(RecommendationRiskLevel.CRITICAL, RecommendationInterruptLevel.STRONG_INTERRUPTIVE,
                true, List.of(sourceRequest()))),
            null,
            null,
            false);
        when(fatiguePolicyResolver.resolve(request))
            .thenReturn(Optional.of(new RecommendationFatiguePolicy(1, 24, "CONFIG_CENTER")));

        RecommendationEvaluationResponse response = service.evaluate(request);

        assertThat(response.visibleCardCount()).isEqualTo(1);
        assertThat(response.suppressedCardCount()).isZero();
        assertThat(response.cards()).extracting(RecommendationCard::riskLevel)
            .containsExactly(RecommendationRiskLevel.CRITICAL);
        verify(fatigueSignals, never()).countLowValueSignals(any(), any(), any(), any());
    }

    @Test
    void triggerRejectsCardWithoutSources() {
        RecommendationCardRequest request = cardRequest(
            RecommendationRiskLevel.MEDIUM,
            RecommendationInterruptLevel.INFO,
            false,
            List.of());

        assertThatThrownBy(() -> service.trigger(triggerRequest(List.of(request))))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_REC_005);
        // CDSS-M-01：医疗安全校验失败也必须发 FAILED 审计留痕
        verify(isolatedAudit).publishInNewTx(any(AuditEvent.class));
    }

    @Test
    void triggerRejectsHighRiskWithoutPhysicianConfirmation() {
        RecommendationCardRequest request = cardRequest(
            RecommendationRiskLevel.CRITICAL,
            RecommendationInterruptLevel.STRONG_INTERRUPTIVE,
            false,
            List.of(sourceRequest()));

        assertThatThrownBy(() -> service.trigger(triggerRequest(List.of(request))))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_REC_006);
        // CDSS-M-01：高风险未确认被拒，同样发 FAILED 审计
        verify(isolatedAudit).publishInNewTx(any(AuditEvent.class));
    }

    @Test
    void feedbackUpdatesCardAndWritesFatigueSignal() {
        RecommendationCard pending = card("card-1", RecommendationCardStatus.PENDING);
        when(cards.findByCardIdAndTenantId("card-1", "tenant-A")).thenReturn(Optional.of(pending));
        when(triggers.findByTriggerIdAndTenantId("trigger-1", "tenant-A"))
            .thenReturn(Optional.of(trigger("trigger-1", RecommendationTriggerStatus.EVALUATED)));

        RecommendationFeedbackResponse response = service.feedback("card-1", new RecommendationFeedbackRequest(
            RecommendationFeedbackType.ACCEPT, "CONFIRMED", "已确认风险", "DOCTOR"));

        assertThat(response.cardStatus()).isEqualTo(RecommendationCardStatus.ACCEPTED);
        assertThat(response.feedbackId()).startsWith("rf-");
        ArgumentCaptor<RecommendationCard> cardCap = ArgumentCaptor.forClass(RecommendationCard.class);
        ArgumentCaptor<RecommendationFeedback> feedbackCap = ArgumentCaptor.forClass(RecommendationFeedback.class);
        ArgumentCaptor<RecommendationFatigueSignal> signalCap =
            ArgumentCaptor.forClass(RecommendationFatigueSignal.class);
        verify(cards).save(cardCap.capture());
        verify(feedback).save(feedbackCap.capture());
        verify(fatigueSignals).save(signalCap.capture());
        assertThat(cardCap.getValue().status()).isEqualTo(RecommendationCardStatus.ACCEPTED);
        assertThat(feedbackCap.getValue().operatorId()).isEqualTo("doctor-1");
        assertThat(signalCap.getValue().signalType()).isEqualTo(RecommendationFatigueSignalType.ACCEPTED);
        verify(auditPublisher).publish(AuditAction.FEEDBACK, "recommendation_card",
            "card-1", "推荐卡反馈 ACCEPT");
    }

    @Test
    void feedbackRequiresStructuredReasonForAcceptRejectAndDismiss() {
        RecommendationCard pending = card("card-1", RecommendationCardStatus.PENDING);
        when(cards.findByCardIdAndTenantId("card-1", "tenant-A")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.feedback("card-1", new RecommendationFeedbackRequest(
                RecommendationFeedbackType.ACCEPT, "", "已确认风险", "DOCTOR", null)))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_REC_007);

        assertThatThrownBy(() -> service.feedback("card-1", new RecommendationFeedbackRequest(
                RecommendationFeedbackType.REJECT, "FALSE_POSITIVE", " ", "DOCTOR", null)))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_REC_007);

        assertThatThrownBy(() -> service.feedback("card-1", new RecommendationFeedbackRequest(
                RecommendationFeedbackType.DISMISS, null, null, "DOCTOR", null)))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_REC_007);

        verify(feedback, never()).save(any());
        verify(cards, never()).save(any());
    }

    @Test
    void feedbackReturnsExistingIdempotentResultBeforeClosedCheck() {
        RecommendationCard closed = card("card-1", RecommendationCardStatus.ACCEPTED);
        RecommendationFeedback existing = feedback("feedback-1", "card-1", "idem-1",
            RecommendationFeedbackType.ACCEPT);
        when(cards.findByCardIdAndTenantId("card-1", "tenant-A")).thenReturn(Optional.of(closed));
        when(feedback.findByCardIdAndTenantIdAndIdempotencyKey("card-1", "tenant-A", "idem-1"))
            .thenReturn(Optional.of(existing));

        RecommendationFeedbackResponse response = service.feedback("card-1", new RecommendationFeedbackRequest(
            RecommendationFeedbackType.ACCEPT, "CONFIRMED", "已确认风险", "DOCTOR", "idem-1"));

        assertThat(response.feedbackId()).isEqualTo("feedback-1");
        assertThat(response.cardStatus()).isEqualTo(RecommendationCardStatus.ACCEPTED);
        verify(cards, never()).save(any());
        verify(feedback, never()).save(any());
        verify(fatigueSignals, never()).save(any());
    }

    @Test
    void feedbackRejectsClosedCard() {
        when(cards.findByCardIdAndTenantId("card-1", "tenant-A"))
            .thenReturn(Optional.of(card("card-1", RecommendationCardStatus.ACCEPTED)));

        assertThatThrownBy(() -> service.feedback("card-1", new RecommendationFeedbackRequest(
                RecommendationFeedbackType.REJECT, "FALSE_POSITIVE", "不适用", "DOCTOR")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_REC_004);
    }

    @Test
    void diagnoseAssemblesFromRecommendationTrigger() {
        RecommendationTrigger trigger = trigger("trigger-1", RecommendationTriggerStatus.EVALUATED);
        RecommendationCard card = card("card-1", RecommendationCardStatus.PENDING);
        RecommendationFeedback userFeedback = feedback("feedback-1", "card-1");
        RecommendationFatigueSignal signal = signal("signal-1", "trigger-1", "card-1",
            RecommendationFatigueSignalType.SHOWN);
        DiagnoseResponse expected = new DiagnoseResponse(
            "recommendation_trigger", "trigger-1", "tenant-A", "EVALUATED",
            trigger, List.of(), List.of(),
            Map.of("cards", List.of("card-1"), "feedback", List.of("feedback-1"), "fatigueSignals", List.of("signal-1")),
            null, "trace-rec", null);
        when(triggers.findByTriggerIdAndTenantId("trigger-1", "tenant-A")).thenReturn(Optional.of(trigger));
        when(cards.findByTriggerIdAndTenantIdOrderByCreatedAtAsc("trigger-1", "tenant-A"))
            .thenReturn(List.of(card));
        when(feedback.findByCardIdAndTenantIdOrderByCreatedAtAsc("card-1", "tenant-A"))
            .thenReturn(List.of(userFeedback));
        when(fatigueSignals.findByTriggerIdAndTenantIdOrderByCreatedAtAsc("trigger-1", "tenant-A"))
            .thenReturn(List.of(signal));
        when(diagnoseAssembler.assemble(eq("recommendation_trigger"), eq("trigger-1"), eq("tenant-A"),
            eq("EVALUATED"), eq(trigger), eq(List.of()), any(), any(), eq("trace-rec")))
            .thenReturn(expected);

        DiagnoseResponse actual = service.diagnose("trigger-1");

        assertThat(actual).isSameAs(expected);
    }

    private RecommendationTriggerRequest triggerRequest(List<RecommendationCardRequest> candidateCards) {
        return new RecommendationTriggerRequest(
            "TRG.ORDER", "ORDER_SIGN", "event-1", "snapshot-1",
            "patient-1", "enc-1", "pathway-1", "WARD_ORDER",
            "1.0.0", "sha256:trigger", Instant.now(), candidateCards);
    }

    private RecommendationTriggerRequest triggerRequest(
            List<RecommendationCardRequest> candidateCards,
            Integer fatigueSuppressionThreshold,
            Integer fatigueWindowHours,
            boolean modelEnhancementEnabled) {
        return new RecommendationTriggerRequest(
            "TRG.ORDER", "ORDER_SIGN", "event-1", "snapshot-1",
            "patient-1", "enc-1", "pathway-1", "WARD_ORDER",
            "1.0.0", "sha256:trigger", Instant.now(), candidateCards,
            fatigueSuppressionThreshold, fatigueWindowHours, modelEnhancementEnabled);
    }

    private RecommendationCardRequest cardRequest(
            RecommendationRiskLevel riskLevel,
            RecommendationInterruptLevel interruptLevel,
            boolean requiresConfirmation,
            List<RecommendationSourceRequest> sourceRequests) {
        return cardRequest("CARD.ANTICOAG", false, riskLevel, interruptLevel, requiresConfirmation, sourceRequests);
    }

    private RecommendationCardRequest cardRequest(
            String cardCode,
            boolean aiGenerated,
            RecommendationRiskLevel riskLevel,
            RecommendationInterruptLevel interruptLevel,
            boolean requiresConfirmation,
            List<RecommendationSourceRequest> sourceRequests) {
        return new RecommendationCardRequest(
            cardCode, RecommendationCardType.MEDICATION,
            "抗凝用药风险提醒", "患者当前医嘱满足抗凝风险规则", "请确认出血风险评估",
            riskLevel, interruptLevel, requiresConfirmation, aiGenerated,
            "来源：抗凝用药规则 v1", "{\"reason\":\"规则命中\"}",
            "WARD_ORDER:ANTICOAG", Instant.now().plusSeconds(3600), sourceRequests);
    }

    private RecommendationSourceRequest sourceRequest() {
        return new RecommendationSourceRequest(
            RecommendationSourceType.RULE, "rule-1", "v1", "抗凝用药规则",
            "§2.1", "sha256:source", "规则命中抗凝药品类别");
    }

    private RecommendationTrigger trigger(String triggerId, RecommendationTriggerStatus status) {
        Instant now = Instant.now();
        return new RecommendationTrigger(
            null, triggerId, "tenant-A", "TRG.ORDER", "ORDER_SIGN",
            "event-1", "snapshot-1", "patient-1", "enc-1", "pathway-1",
            "WARD_ORDER", "1.0.0", "sha256:trigger", status, null,
            now, now, "tester", now, "tester", "trace-rec");
    }

    private RecommendationCard card(String cardId, RecommendationCardStatus status) {
        Instant now = Instant.now();
        return new RecommendationCard(
            null, cardId, "tenant-A", "trigger-1", "CARD.ANTICOAG",
            RecommendationCardType.MEDICATION, "抗凝用药风险提醒",
            "患者当前医嘱满足抗凝风险规则", "请确认出血风险评估",
            RecommendationRiskLevel.HIGH, RecommendationInterruptLevel.WEAK_INTERRUPTIVE,
            status, true, false, "来源：抗凝用药规则 v1",
            "{\"reason\":\"规则命中\"}", "WARD_ORDER:ANTICOAG", now.plusSeconds(3600),
            now, "tester", now, "tester", "trace-rec");
    }

    private RecommendationFeedback feedback(String feedbackId, String cardId) {
        return feedback(feedbackId, cardId, null, RecommendationFeedbackType.VIEW_SOURCE);
    }

    private RecommendationFeedback feedback(
            String feedbackId,
            String cardId,
            String idempotencyKey,
            RecommendationFeedbackType feedbackType) {
        Instant now = Instant.now();
        return new RecommendationFeedback(
            null, feedbackId, "tenant-A", cardId, idempotencyKey, feedbackType,
            null, null, "doctor-1", "DOCTOR",
            now, "doctor-1", now, "doctor-1", "trace-rec");
    }

    private RecommendationFatigueSignal signal(String signalId, String triggerId, String cardId,
                                               RecommendationFatigueSignalType type) {
        Instant now = Instant.now();
        return new RecommendationFatigueSignal(
            null, signalId, "tenant-A", triggerId, cardId, "WARD_ORDER:ANTICOAG",
            "patient-1", "enc-1", "doctor-1", type, 1,
            now.minusSeconds(300), now, "doctor-1", now, "doctor-1", "trace-rec");
    }
}
