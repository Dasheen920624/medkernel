package com.medkernel.engine.cdshook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.cdss.risk.CdssAutomationLevel;
import com.medkernel.engine.cdss.risk.CdssReviewRequirement;
import com.medkernel.engine.context.ClinicalEventRequest;
import com.medkernel.engine.context.ClinicalEventTriggerPoint;
import com.medkernel.engine.context.ClinicalEventType;
import com.medkernel.engine.recommendation.RecommendationCard;
import com.medkernel.engine.recommendation.RecommendationCardStatus;
import com.medkernel.engine.recommendation.RecommendationCardType;
import com.medkernel.engine.recommendation.RecommendationEvaluationResponse;
import com.medkernel.engine.recommendation.RecommendationInterruptLevel;
import com.medkernel.engine.recommendation.RecommendationModelStatus;
import com.medkernel.engine.recommendation.RecommendationRiskLevel;
import com.medkernel.engine.recommendation.RecommendationTriggerStatus;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * OPT-02 CDS Hooks 风格事件契约锁。
 */
class CdsHookContractTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void requestNormalizesVersionAndChecksHookRequiredContextFields() {
        ObjectNode context = JsonNodeFactory.instance.objectNode()
            .put("patientId", "MPI-1")
            .put("encounterId", "ENC-1")
            .put("packageVersion", "pkg-2026.06");
        context.set("orders", JsonNodeFactory.instance.arrayNode());
        CdsHookRequest request = new CdsHookRequest(
            ClinicalEventTriggerPoint.ORDER_SIGN,
            "hook-order-001",
            "MPI-1",
            "ENC-1",
            "pkg-2026.06",
            "HIS",
            context,
            null,
            "1");

        assertThat(request.cdsHookVersion()).isEqualTo(CdsHookContract.CURRENT_VERSION);
        assertThat(CdsHookContract.missingRequiredContextFields(request)).isEmpty();

        CdsHookRequest missingMedication = new CdsHookRequest(
            ClinicalEventTriggerPoint.MEDICATION_PRESCRIBE,
            "hook-med-001",
            "MPI-1",
            "ENC-1",
            "pkg-2026.06",
            "HIS",
            context,
            null,
            null);
        assertThat(CdsHookContract.missingRequiredContextFields(missingMedication))
            .containsExactly("medications");
    }

    @Test
    void unsupportedContractVersionIsRejected() {
        assertThatThrownBy(() -> CdsHookContract.normalizeVersion("2.0"))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ENG_EVENT_001);
    }

    @Test
    void clinicalEventRequestCanBeViewedAsCdsHookRequestWithoutDuplicatingTriggerSemantics() {
        ClinicalEventRequest event = new ClinicalEventRequest(
            "evt-1",
            ClinicalEventType.ORDER,
            "MPI-1",
            "ENC-1",
            "HIS",
            "pkg-2026.06",
            ClinicalEventTriggerPoint.ORDER_SIGN,
            "hook-order-001",
            null,
            json.createObjectNode().set("orders", json.createArrayNode()),
            Instant.parse("2026-06-01T01:00:00Z"));

        CdsHookRequest request = event.toCdsHookRequest();

        assertThat(request.hook()).isEqualTo(ClinicalEventTriggerPoint.ORDER_SIGN);
        assertThat(request.hookInstance()).isEqualTo("hook-order-001");
        assertThat(request.context().path("eventId").asText()).isEqualTo("evt-1");
        assertThat(request.context().path("orders").isArray()).isTrue();
        assertThat(CdsHookContract.missingRequiredContextFields(request)).isEmpty();
    }

    @Test
    void recommendationEvaluationMapsToCdsHookCardsResponse() {
        RecommendationCard card = new RecommendationCard(
            null,
            "card-1",
            "tenant-A",
            "trigger-1",
            "CARD.ANTICOAG",
            RecommendationCardType.MEDICATION,
            "抗凝用药风险提醒",
            "患者当前医嘱满足抗凝风险规则",
            "请确认出血风险评估",
            RecommendationRiskLevel.HIGH,
            RecommendationInterruptLevel.WEAK_INTERRUPTIVE,
            RecommendationCardStatus.PENDING,
            true,
            false,
            "来源：抗凝用药规则 v1",
            "{\"reason\":\"规则命中\"}",
            "WARD_ORDER:ANTICOAG",
            Instant.parse("2026-06-01T02:00:00Z"),
            Instant.parse("2026-06-01T01:00:00Z"),
            "tester",
            Instant.parse("2026-06-01T01:00:00Z"),
            "tester",
            "trace-1",
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
        RecommendationEvaluationResponse evaluation = new RecommendationEvaluationResponse(
            "trigger-1",
            RecommendationTriggerStatus.EVALUATED,
            1,
            1,
            0,
            RecommendationModelStatus.MODEL_DISABLED,
            List.of(card),
            "trace-1");

        CdsHookResponse response = CdsHookResponse.fromRecommendationEvaluation(evaluation);

        assertThat(response.cdsHookVersion()).isEqualTo(CdsHookContract.CURRENT_VERSION);
        assertThat(response.cards()).hasSize(1);
        assertThat(response.cards().get(0).uuid()).isEqualTo("card-1");
        assertThat(response.cards().get(0).indicator()).isEqualTo("warning");
        assertThat(response.systemActions()).isEmpty();
    }

    @Test
    void clinicalTriggerPointDeclaresSixHookContextContracts() {
        assertThat(ClinicalEventTriggerPoint.values())
            .extracting(ClinicalEventTriggerPoint::wireValue)
            .containsExactlyInAnyOrder(
                "patient-view",
                "order-sign",
                "medication-prescribe",
                "result-review",
                "discharge-sign",
                "followup-alert");
        assertThat(ClinicalEventTriggerPoint.MEDICATION_PRESCRIBE.requiredContextFields())
            .contains("patientId", "encounterId", "packageVersion", "medications");
        assertThat(ClinicalEventTriggerPoint.FOLLOWUP_ALERT.requiredContextFields())
            .contains("patientId", "packageVersion", "followupPlanId");
    }

    @SuppressWarnings("unused")
    private JsonNode unusedTypeAnchor() {
        return JsonNodeFactory.instance.objectNode();
    }
}
