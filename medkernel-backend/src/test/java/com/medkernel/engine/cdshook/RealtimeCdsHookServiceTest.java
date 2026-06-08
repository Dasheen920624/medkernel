package com.medkernel.engine.cdshook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.cdss.risk.CdssAutomationLevel;
import com.medkernel.engine.cdss.risk.CdssReviewRequirement;
import com.medkernel.engine.context.ClinicalEventTriggerPoint;
import com.medkernel.engine.recommendation.RecommendationCard;
import com.medkernel.engine.recommendation.RecommendationCardStatus;
import com.medkernel.engine.recommendation.RecommendationCardType;
import com.medkernel.engine.recommendation.RecommendationEngineService;
import com.medkernel.engine.recommendation.RecommendationEvaluationResponse;
import com.medkernel.engine.recommendation.RecommendationInterruptLevel;
import com.medkernel.engine.recommendation.RecommendationModelStatus;
import com.medkernel.engine.recommendation.RecommendationRiskLevel;
import com.medkernel.engine.recommendation.RecommendationTriggerRequest;
import com.medkernel.engine.recommendation.RecommendationTriggerStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/**
 * P13-5 实时 CDS Hook 入口：复用推荐评估主链路，并在 order-sign 预算内诚实降级。
 */
class RealtimeCdsHookServiceTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void orderSignReturnsRecommendationCardsWithinConfiguredBudget() {
        RecommendationEngineService recommendations = mock(RecommendationEngineService.class);
        when(recommendations.evaluate(any(RecommendationTriggerRequest.class)))
            .thenReturn(evaluation(card()));
        RealtimeCdsHookService service = new RealtimeCdsHookService(
            recommendations,
            new SimpleAsyncTaskExecutor("realtime-cds-test-"),
            new RealtimeCdsProperties(Duration.ofSeconds(2), Duration.ofSeconds(1)));

        CdsHookResponse response = service.evaluate(orderSignRequest());

        assertThat(response.cdsHookVersion()).isEqualTo(CdsHookContract.CURRENT_VERSION);
        assertThat(response.systemActions()).isEmpty();
        assertThat(response.cards()).hasSize(1);
        assertThat(response.cards().getFirst().uuid()).isEqualTo("card-order-risk");
        assertThat(response.cards().getFirst().indicator()).isEqualTo("critical");
        assertThat(response.cards().getFirst().requiresPhysicianConfirmation()).isTrue();
        ArgumentCaptor<RecommendationTriggerRequest> requestCaptor =
            ArgumentCaptor.forClass(RecommendationTriggerRequest.class);
        verify(recommendations).evaluate(requestCaptor.capture());
        RecommendationTriggerRequest request = requestCaptor.getValue();
        assertThat(request.triggerCode()).isEqualTo("hook-order-001");
        assertThat(request.triggerType()).isEqualTo("order-sign");
        assertThat(request.contextSnapshotId()).isEqualTo("ctx-active-001");
        assertThat(request.patientId()).isEqualTo("MPI-1");
        assertThat(request.encounterId()).isEqualTo("ENC-1");
        assertThat(request.scenarioCode()).isEqualTo("order-sign");
        assertThat(request.packageVersion()).isEqualTo("pkg-2026.06");
        assertThat(request.candidateCards()).isEmpty();
    }

    @Test
    void orderSignTimeoutReturnsCriticalManualReviewCardWithoutSystemAction() throws Exception {
        RecommendationEngineService recommendations = mock(RecommendationEngineService.class);
        CountDownLatch interrupted = new CountDownLatch(1);
        when(recommendations.evaluate(any(RecommendationTriggerRequest.class))).thenAnswer(invocation -> {
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException exception) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
            return evaluation(card());
        });
        RealtimeCdsHookService service = new RealtimeCdsHookService(
            recommendations,
            new SimpleAsyncTaskExecutor("realtime-cds-timeout-test-"),
            new RealtimeCdsProperties(Duration.ofSeconds(2), Duration.ofMillis(30)));

        long startedNanos = System.nanoTime();
        CdsHookResponse response = service.evaluate(orderSignRequest());
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);

        assertThat(elapsedMillis).isLessThan(1_000);
        assertThat(response.systemActions()).isEmpty();
        assertThat(response.cards()).hasSize(1);
        CdsHookCard card = response.cards().getFirst();
        assertThat(card.uuid()).isEqualTo("hook-order-001-cds-unavailable");
        assertThat(card.indicator()).isEqualTo("critical");
        assertThat(card.summary()).contains("CDS 求值不可用");
        assertThat(card.detail()).contains("order-sign").contains("30ms").contains("人工核查");
        assertThat(card.overrideReasons()).contains("已完成人工核查并记录原因");
        assertThat(card.requiresPhysicianConfirmation()).isTrue();
        assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
    }

    private CdsHookRequest orderSignRequest() {
        ObjectNode context = json.createObjectNode()
            .put("patientId", "MPI-1")
            .put("encounterId", "ENC-1")
            .put("packageVersion", "pkg-2026.06")
            .put("contextSnapshotId", "ctx-active-001")
            .put("sourceEventId", "evt-order-001");
        context.set("orders", json.createArrayNode().add(json.createObjectNode()
            .put("orderCode", "ORDER.ACEI")
            .put("display", "ACEI 药物医嘱")));
        return new CdsHookRequest(
            ClinicalEventTriggerPoint.ORDER_SIGN,
            "hook-order-001",
            "MPI-1",
            "ENC-1",
            "pkg-2026.06",
            "HIS",
            context,
            null,
            null);
    }

    private RecommendationEvaluationResponse evaluation(RecommendationCard card) {
        return new RecommendationEvaluationResponse(
            "trigger-order-001",
            RecommendationTriggerStatus.EVALUATED,
            1,
            1,
            0,
            RecommendationModelStatus.MODEL_DISABLED,
            List.of(card),
            "trace-order-001");
    }

    private RecommendationCard card() {
        return new RecommendationCard(
            null,
            "card-order-risk",
            "tenant-A",
            "trigger-order-001",
            "CARD.CKD.ORDER",
            RecommendationCardType.MEDICATION,
            "CKD 用药风险提醒",
            "患者当前医嘱满足 CKD 高危用药规则",
            "请人工核查肾功能与剂量",
            RecommendationRiskLevel.CRITICAL,
            RecommendationInterruptLevel.STRONG_INTERRUPTIVE,
            RecommendationCardStatus.PENDING,
            true,
            false,
            "来源：CKD 专病包规则 v1",
            "{\"reason\":\"规则命中\"}",
            "order-sign:CKD",
            Instant.parse("2026-06-01T02:00:00Z"),
            Instant.parse("2026-06-01T01:00:00Z"),
            "tester",
            Instant.parse("2026-06-01T01:00:00Z"),
            "tester",
            "trace-order-001",
            "builtin-risk-baseline",
            "baseline",
            CdssAutomationLevel.INTERRUPTIVE,
            CdssReviewRequirement.PHYSICIAN_CONFIRMATION,
            72,
            "OPT04_SILENT_TRIAL",
            false,
            "NMPA_RESERVED",
            "TRACEABLE_EVIDENCE_REQUIRED",
            "高危 CDSS 输出必须医师确认");
    }
}
