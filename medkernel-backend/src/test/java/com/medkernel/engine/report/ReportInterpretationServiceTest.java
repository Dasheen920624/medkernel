package com.medkernel.engine.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ContextSnapshotResources;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.context.ContextSnapshotStatus;
import com.medkernel.engine.context.QualityStatus;
import com.medkernel.engine.context.canonical.CanonicalDiagnosticReport;
import com.medkernel.engine.context.canonical.CanonicalEncounter;
import com.medkernel.engine.context.canonical.CanonicalPatient;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.recommendation.RecommendationCardType;
import com.medkernel.engine.recommendation.RecommendationEngineService;
import com.medkernel.engine.recommendation.RecommendationRiskLevel;
import com.medkernel.engine.recommendation.RecommendationTriggerRequest;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReportInterpretationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-24T08:00:00Z");

    private ContextSnapshotService snapshots;
    private RuntimeReleaseDiagnosticItemSelector diagnosticItems;
    private RecommendationEngineService recommendationEngine;
    private ReportInterpretationService service;

    @BeforeEach
    void setUp() {
        snapshots = mock(ContextSnapshotService.class);
        diagnosticItems = mock(RuntimeReleaseDiagnosticItemSelector.class);
        recommendationEngine = mock(RecommendationEngineService.class);
        service = new ReportInterpretationService(
            snapshots,
            diagnosticItems,
            recommendationEngine,
            new ObjectMapper());
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-report",
            OrgScope.tenant("t-1"),
            "doctor-1"));
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void interpretsDiagnosticReportsWithDiagnosticItemKnowledgeFromLockedRuntimeRelease() {
        when(snapshots.findById("snap-report")).thenReturn(snapshot(List.of(report(
            "report-k-1",
            "LAB.POTASSIUM",
            "血钾 6.3 mmol/L，危急值，已复核",
            List.of("血钾升高", "危急值")))));
        when(diagnosticItems.select("t-1", "runtime-release-report")).thenReturn(List.of(
            new RuntimeDiagnosticItemReference(
                "t-1",
                100L,
                "LAB.POTASSIUM",
                "血钾检验说明书",
                21L,
                "v1.0",
                SourceAuthorityLevel.B_GUIDELINE.name(),
                "hash-potassium")));

        ReportInterpretationResponse response = service.interpret(new ReportInterpretationRequest("snap-report"));

        assertThat(response.runtimeReleaseId()).isEqualTo("runtime-release-report");
        assertThat(response.interpretations()).singleElement().satisfies(item -> {
            assertThat(item.reportId()).isEqualTo("report-k-1");
            assertThat(item.itemCode()).isEqualTo("LAB.POTASSIUM");
            assertThat(item.itemName()).isEqualTo("血钾检验说明书");
            assertThat(item.sourceVersionId()).isEqualTo(21L);
            assertThat(item.criticalRisk()).isTrue();
            assertThat(item.abnormalHighlights()).contains("血钾升高", "危急值");
            assertThat(item.summary()).contains("已签发报告", "当前机构生效版本");
            assertThat(item.recommendations()).allSatisfy(text ->
                assertThat(text).contains("不自动").doesNotContain("已自动"));
        });
        assertThat(response.advisoryNote()).contains("不改写已签发报告");
    }

    @Test
    void persistsInterpretationAsRecommendationCardWithoutChangingSignedReport() {
        when(snapshots.findById("snap-report")).thenReturn(snapshot(List.of(report(
            "report-k-1",
            "LAB.POTASSIUM",
            "血钾 6.3 mmol/L，危急值，已复核",
            List.of("血钾升高", "危急值")))));
        when(diagnosticItems.select("t-1", "runtime-release-report")).thenReturn(List.of(
            new RuntimeDiagnosticItemReference(
                "t-1",
                100L,
                "LAB.POTASSIUM",
                "血钾检验说明书",
                21L,
                "v1.0",
                SourceAuthorityLevel.B_GUIDELINE.name(),
                "hash-potassium")));

        service.interpret(new ReportInterpretationRequest("snap-report"));

        ArgumentCaptor<RecommendationTriggerRequest> cap = ArgumentCaptor.forClass(RecommendationTriggerRequest.class);
        verify(recommendationEngine).trigger(cap.capture());
        RecommendationTriggerRequest request = cap.getValue();
        assertThat(request.triggerType()).isEqualTo("result-review");
        assertThat(request.scenarioCode()).isEqualTo("S36");
        assertThat(request.contextSnapshotId()).isEqualTo("snap-report");
        assertThat(request.candidateCards()).singleElement().satisfies(card -> {
            assertThat(card.cardType()).isEqualTo(RecommendationCardType.LAB);
            assertThat(card.riskLevel()).isEqualTo(RecommendationRiskLevel.HIGH);
            assertThat(card.requiresPhysicianConfirmation()).isTrue();
            assertThat(card.aiGenerated()).isFalse();
            assertThat(card.suggestedAction()).contains("不改写已签发报告", "不自动开立医嘱");
            assertThat(card.sourceSummary()).contains("医技项目说明书", "v1.0");
            assertThat(card.explanationJson()).contains("report-k-1", "runtime-release-report", "hash-potassium");
            assertThat(card.sources()).singleElement().satisfies(source ->
                assertThat(source.sourceRefId()).isEqualTo("21"));
        });
    }

    @Test
    void matchesUserFriendlyLabReportToSpecificAndGenericDiagnosticItemKnowledge() {
        when(snapshots.findById("snap-report")).thenReturn(snapshot(List.of(
            report(
                "report-k-1",
                "血钾检验",
                "血钾 6.3 mmol/L，危急值，已复核",
                List.of("血钾升高", "危急值")),
            report(
                "report-generic-1",
                "LAB.UNKNOWN",
                "血常规结果异常，建议结合病情复核",
                List.of("检验结果异常")))));
        when(diagnosticItems.select("t-1", "runtime-release-report")).thenReturn(List.of(
            new RuntimeDiagnosticItemReference(
                "t-1",
                100L,
                "LAB.POTASSIUM",
                "血钾检验说明书",
                21L,
                "v1.0",
                SourceAuthorityLevel.B_GUIDELINE.name(),
                "hash-potassium"),
            new RuntimeDiagnosticItemReference(
                "t-1",
                101L,
                "launch.diagnostic-item.lab-test-boundary",
                "检验项目说明书来源与使用边界",
                22L,
                "2026.06",
                SourceAuthorityLevel.C_CONSENSUS_LITERATURE.name(),
                "hash-lab-boundary")));

        ReportInterpretationResponse response = service.interpret(new ReportInterpretationRequest("snap-report"));

        assertThat(response.interpretations()).hasSize(2);
        assertThat(response.interpretations()).extracting(ReportInterpretationItem::reportId)
            .containsExactly("report-k-1", "report-generic-1");
        assertThat(response.interpretations()).extracting(ReportInterpretationItem::itemName)
            .containsExactly("血钾检验说明书", "检验项目说明书来源与使用边界");
    }

    @Test
    void emptyStateDoesNotPersistRecommendationCard() {
        when(snapshots.findById("snap-report")).thenReturn(snapshot(List.of(report(
            "report-plain-1",
            "LAB.UNKNOWN",
            "检验结果未提示异常",
            List.of()))));
        when(diagnosticItems.select("t-1", "runtime-release-report")).thenReturn(List.of());

        ReportInterpretationResponse response = service.interpret(new ReportInterpretationRequest("snap-report"));

        assertThat(response.interpretations()).isEmpty();
        assertThat(response.advisoryNote()).contains("当前机构生效版本没有匹配的医技项目说明书");
        verify(recommendationEngine, never()).trigger(any());
    }

    private ContextSnapshotResponse snapshot(List<CanonicalDiagnosticReport> reports) {
        return new ContextSnapshotResponse(
            "snap-report",
            ContextSnapshotStatus.ACTIVE,
            new ContextSnapshotResources(
                new CanonicalPatient(
                    "patient-1",
                    "测试患者",
                    LocalDate.parse("1980-01-01"),
                    "UNKNOWN",
                    List.of(),
                    "HIS",
                    "Patient/patient-1",
                    "v1",
                    NOW,
                    NOW,
                    QualityStatus.VALID),
                List.of(),
                List.of(new CanonicalEncounter(
                    "enc-1",
                    "INPATIENT",
                    NOW,
                    null,
                    "dept-1",
                    "doctor-1",
                    "bed-1",
                    "HIS",
                    "Encounter/enc-1",
                    "v1",
                    NOW,
                    NOW,
                    QualityStatus.VALID)),
                List.of(),
                List.of(),
                List.of(),
                reports,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                ContextSnapshotResources.emptyExtensions()),
            "runtime-release-report",
            QualityStatus.VALID,
            List.of(),
            Map.of(),
            NOW,
            "trace-report");
    }

    private CanonicalDiagnosticReport report(
            String reportId,
            String reportType,
            String conclusion,
            List<String> keyFindings) {
        return new CanonicalDiagnosticReport(
            reportId,
            reportType,
            conclusion,
            keyFindings,
            "tech-1",
            NOW,
            "LIS",
            "DiagnosticReport/" + reportId,
            "v1",
            NOW,
            NOW,
            QualityStatus.VALID);
    }
}
