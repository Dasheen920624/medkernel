package com.medkernel.engine.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.cdss.risk.CdssReviewRequirement;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotResources;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.context.ContextSnapshotStatus;
import com.medkernel.engine.context.QualityStatus;
import com.medkernel.engine.context.canonical.CanonicalPatient;
import com.medkernel.engine.recommendation.RecommendationCardRequest;
import com.medkernel.engine.recommendation.RecommendationCardType;
import com.medkernel.engine.recommendation.RecommendationInterruptLevel;
import com.medkernel.engine.recommendation.RecommendationRiskLevel;
import com.medkernel.engine.recommendation.RecommendationSourceRequest;
import com.medkernel.engine.recommendation.RecommendationSourceType;
import com.medkernel.engine.recommendation.RecommendationTriggerRequest;
import com.medkernel.engine.rule.RuleDslEvaluator;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ClinicalRedlineMatcherTest {

    private final ContextSnapshotService snapshots = mock(ContextSnapshotService.class);
    private final RuntimeReleaseClinicalRedlineSelector runtimeRedlines =
        mock(RuntimeReleaseClinicalRedlineSelector.class);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final ClinicalRedlineMatcher matcher = new ClinicalRedlineMatcher(
        snapshots,
        runtimeRedlines,
        new RuleDslEvaluator(json),
        json
    );

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void matchesActiveClinicalRedlineAsStrongNonAiCandidateWithRecallSource() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-redline-match", OrgScope.tenant("tenant-A"), "doctor-1"));
        when(snapshots.findById("snapshot-1")).thenReturn(snapshot());
        when(runtimeRedlines.select("tenant-A", "runtime-release-test"))
            .thenReturn(List.of(redline("tenant-A", "redline-ddi-warfarin-nsaid")));

        List<RecommendationCardRequest> cards = matcher.match(triggerRequest());

        assertThat(cards).hasSize(1);
        RecommendationCardRequest card = cards.get(0);
        assertThat(card.cardCode()).isEqualTo("REDLINE.RDL-DDI-001.v2026.2");
        assertThat(card.cardType()).isEqualTo(RecommendationCardType.MEDICATION);
        assertThat(card.riskLevel()).isEqualTo(RecommendationRiskLevel.CRITICAL);
        assertThat(card.interruptLevel()).isEqualTo(RecommendationInterruptLevel.STRONG_INTERRUPTIVE);
        assertThat(card.requiresPhysicianConfirmation()).isTrue();
        assertThat(card.aiGenerated()).isFalse();
        assertThat(card.fatigueKey()).isEqualTo("REDLINE:RDL-DDI-001");
        assertThat(card.sourceSummary()).contains("临床安全红线").contains("RDL-DDI-001");
        assertThat(card.explanationJson())
            .contains("\"matchType\":\"CLINICAL_REDLINE\"")
            .contains("\"redlineId\":\"redline-ddi-warfarin-nsaid\"")
            .contains("conditionEvidence");
        assertThat(card.sources())
            .extracting(RecommendationSourceRequest::sourceType)
            .containsExactly(
                RecommendationSourceType.REDLINE,
                RecommendationSourceType.KNOWLEDGE,
                RecommendationSourceType.CONTEXT
            );
        assertThat(card.sources())
            .extracting(RecommendationSourceRequest::sourceRefId)
            .containsExactly("redline-ddi-warfarin-nsaid", "knowledge-version:42", "snapshot-1");
    }

    @Test
    void includesPlatformRedlineWhenTenantHasNoLocalOverride() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-redline-match", OrgScope.tenant("tenant-A"), "doctor-1"));
        when(snapshots.findById("snapshot-1")).thenReturn(snapshot());
        when(runtimeRedlines.select("tenant-A", "runtime-release-test"))
            .thenReturn(List.of(redline("t-1", "platform-redline-ddi-warfarin-nsaid")));

        List<RecommendationCardRequest> cards = matcher.match(triggerRequest());

        assertThat(cards).singleElement()
            .extracting(RecommendationCardRequest::cardCode)
            .isEqualTo("REDLINE.RDL-DDI-001.v2026.2");
    }

    @Test
    void matcherOnlyConsumesRedlinesSelectedByRuntimeRelease() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-redline-match", OrgScope.tenant("tenant-A"), "doctor-1"));
        when(snapshots.findById("snapshot-1")).thenReturn(snapshot());
        when(runtimeRedlines.select("tenant-A", "runtime-release-test"))
            .thenReturn(List.of(redline("t-1", "platform-redline-ddi-warfarin-nsaid", "FEMALE")));

        List<RecommendationCardRequest> cards = matcher.match(triggerRequest());

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).sources())
            .extracting(RecommendationSourceRequest::sourceRefId)
            .startsWith("platform-redline-ddi-warfarin-nsaid");
    }

    private RecommendationTriggerRequest triggerRequest() {
        return new RecommendationTriggerRequest(
            "TRG.ORDER", "order-sign", "event-1", "snapshot-1",
            "patient-1", "enc-1", null, "WARD_ORDER",
            "sha256:trigger", Instant.now(), List.of());
    }

    private ContextSnapshotResponse snapshot() {
        CanonicalPatient patient = new CanonicalPatient(
            "mpi-1", "测试患者", null, "FEMALE", List.of(),
            "HIS", "patient-1", "v1", Instant.now(), Instant.now(), QualityStatus.VALID);
        return new ContextSnapshotResponse(
            "snapshot-1", ContextSnapshotStatus.ACTIVE,
            new ContextSnapshotResources(patient, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                ContextSnapshotResources.emptyExtensions()),
            "runtime-release-test",
            QualityStatus.VALID, List.of(), java.util.Map.of(), Instant.now(), "trace-redline-match");
    }

    private ClinicalRedlineRule redline(String tenantId, String redlineId) {
        return redline(tenantId, redlineId, "FEMALE");
    }

    private ClinicalRedlineRule redline(String tenantId, String redlineId, String patientGender) {
        Instant now = Instant.parse("2026-06-04T02:00:00Z");
        return new ClinicalRedlineRule(
            null,
            redlineId,
            tenantId,
            ClinicalRedlineCategory.DRUG_INTERACTION,
            "order-sign",
            "TENANT",
            tenantId,
            tenantId + "|DRUG_INTERACTION|order-sign|RDL-DDI-001",
            "RDL-DDI-001",
            "2026.2",
            ClinicalRedlineStatus.ACTIVE,
            RecommendationRiskLevel.CRITICAL,
            "risk-matrix-critical-ddi",
            "4",
            CdssReviewRequirement.PHYSICIAN_CONFIRMATION,
            168,
            "OPT04_REDLINE_SILENT_TRIAL",
            "华法林合并非甾体抗炎药出血风险",
            "合用可能显著增加出血风险",
            """
            {
              "when": {
                "fact": "patient.gender",
                "operator": "equals",
                "value": "%s"
              },
              "then": [
                {"actionCode": "BLOCK", "atSeverity": "CRITICAL", "indicator": "critical", "summary": "命中临床安全红线：需立即复核", "detail": "命中临床安全红线：需立即复核", "source": {"label": "规则测试来源"}, "suggestions": [], "overrideReasons": []}
              ],
              "explain": {
                "summary": "红线命中"
              }
            }
            """.formatted(patientGender),
            "药品说明书与临床指南证据",
            "source-version:42#section-1",
            42L,
            false,
            now,
            "tester",
            now,
            "tester",
            "trace-redline");
    }
}
