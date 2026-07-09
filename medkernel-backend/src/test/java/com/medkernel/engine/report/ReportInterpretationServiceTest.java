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
import com.medkernel.engine.recommendation.RecommendationTriggerResponse;
import com.medkernel.engine.recommendation.RecommendationTriggerStatus;
import com.medkernel.engine.versioning.DeclarativeAssetRuntimePort;
import com.medkernel.engine.versioning.ResolvedDeclarativeAsset;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;
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
    private DeclarativeAssetRuntimePort declarativeAssets;
    private ReportInterpretationService service;

    @BeforeEach
    void setUp() {
        snapshots = mock(ContextSnapshotService.class);
        diagnosticItems = mock(RuntimeReleaseDiagnosticItemSelector.class);
        recommendationEngine = mock(RecommendationEngineService.class);
        declarativeAssets = diagnosticReportDeclarativeAssets();
        service = new ReportInterpretationService(
            snapshots,
            diagnosticItems,
            recommendationEngine,
            declarativeAssets,
            new ObjectMapper());
        when(recommendationEngine.trigger(any())).thenReturn(new RecommendationTriggerResponse(
            "rt-report-default",
            RecommendationTriggerStatus.EVALUATED,
            0,
            List.of(),
            "trace-report"));
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
                PlatformTenant.ID,
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
        assertThat(response.advisoryNote()).contains("不自动开嘱");
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
        when(recommendationEngine.trigger(any())).thenReturn(new RecommendationTriggerResponse(
            "rt-report",
            RecommendationTriggerStatus.EVALUATED,
            1,
            List.of("rc-report-current"),
            "trace-report"));

        ReportInterpretationResponse response = service.interpret(new ReportInterpretationRequest("snap-report"));

        ArgumentCaptor<RecommendationTriggerRequest> cap = ArgumentCaptor.forClass(RecommendationTriggerRequest.class);
        verify(recommendationEngine).trigger(cap.capture());
        RecommendationTriggerRequest request = cap.getValue();
        assertThat(response.recommendationCardIds()).containsExactly("rc-report-current");
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
            assertThat(card.explanationJson()).contains(
                "\"runtimeAssetEvidence\"",
                "FIELD.CATALOG.CLINICAL_CONTEXT",
                "ACTION_CARD.REPORT.CRITICAL_VALUE",
                "observations[].criticalFlag",
                "diagnosticReports[].conclusion",
                "\"requiresPhysicianConfirmation\":true");
            assertThat(card.sources()).singleElement().satisfies(source -> {
                assertThat(source.sourceRefId()).isEqualTo("LAB.POTASSIUM");
                assertThat(source.citationLocator())
                    .isEqualTo("knowledge_version:" + PlatformTenant.ID + ":21");
            });
        });
    }

    @Test
    void rejectsInterpretationWhenCurrentRuntimeMissingDiagnosticFieldCatalogOrActionCard() {
        declarativeAssets = DeclarativeAssetRuntimePort.unavailable();
        service = new ReportInterpretationService(
            snapshots,
            diagnosticItems,
            recommendationEngine,
            declarativeAssets,
            new ObjectMapper());
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

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.interpret(new ReportInterpretationRequest("snap-report")))
            .isInstanceOf(com.medkernel.shared.api.error.ApiException.class)
            .hasMessageContaining("机构生效版本缺少报告解读运行资产");
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
    void prefersSpecificRegionalChestCtDiagnosticItemOverGenericImageBoundaryKnowledge() {
        when(snapshots.findById("snap-report")).thenReturn(snapshot(List.of(report(
            "dr-regional-chest-ct",
            "CHEST_CT",
            "区域胸部 CT 报告提示右肺结节，已由远程示范医院签发",
            List.of("胸部 CT 右肺结节", "区域互认目录已核验")))));
        when(diagnosticItems.select("t-1", "runtime-release-report")).thenReturn(List.of(
            new RuntimeDiagnosticItemReference(
                "t-1",
                101L,
                "launch.diagnostic-item.image-boundary",
                "医技项目说明书来源与使用边界",
                22L,
                "2026.06",
                SourceAuthorityLevel.C_CONSENSUS_LITERATURE.name(),
                "hash-image-boundary"),
            new RuntimeDiagnosticItemReference(
                "t-1",
                200L,
                "IMG.CT.CHEST.REGIONAL.MRTEST",
                "区域胸部 CT 互认说明书",
                23L,
                "V1",
                SourceAuthorityLevel.D_HOSPITAL.name(),
                "hash-regional-chest-ct")));

        ReportInterpretationResponse response = service.interpret(new ReportInterpretationRequest("snap-report"));

        assertThat(response.interpretations()).singleElement().satisfies(item -> {
            assertThat(item.reportId()).isEqualTo("dr-regional-chest-ct");
            assertThat(item.itemCode()).isEqualTo("IMG.CT.CHEST.REGIONAL.MRTEST");
            assertThat(item.itemName()).isEqualTo("区域胸部 CT 互认说明书");
            assertThat(item.sourceVersionId()).isEqualTo(23L);
        });
    }

    @Test
    void matchesFiveDiagnosticReportFamiliesToGenericDiagnosticBoundaryKnowledge() {
        when(snapshots.findById("snap-report")).thenReturn(snapshot(List.of(
            report(
                "dr-pacs-chest-ct",
                "胸部 CT 影像报告",
                "胸部 CT 影像报告提示右下肺斑片影，需结合临床上下文人工复核。",
                List.of("PACS/RIS 已签发影像报告")),
            report(
                "dr-ultrasound-abdomen",
                "腹部超声检查报告",
                "腹部超声检查提示胆囊壁增厚，建议结合症状和既往检查人工复核。",
                List.of("超声报告已签发")),
            report(
                "dr-pathology-biopsy",
                "胃镜活检病理报告",
                "病理报告提示慢性活动性炎症伴局灶异型增生，需医师人工复核。",
                List.of("病理报告已签发")),
            report(
                "dr-endoscopy-gastroscopy",
                "胃镜检查报告",
                "内镜检查报告提示胃窦溃疡样改变，建议结合病理和用药史人工复核。",
                List.of("内镜报告已签发")),
            report(
                "dr-ecg-resting",
                "十二导联心电图报告",
                "心电图报告提示 ST-T 改变，需结合症状、肌钙蛋白和既往心电人工复核。",
                List.of("心电报告已签发")))));
        when(diagnosticItems.select("t-1", "runtime-release-report")).thenReturn(List.of(
            new RuntimeDiagnosticItemReference(
                "t-1",
                101L,
                "launch.diagnostic-item.image-boundary.s36",
                "五类医技报告解读通用边界说明书",
                22L,
                "2026.07",
                SourceAuthorityLevel.D_HOSPITAL.name(),
                "hash-report-family-boundary")));

        ReportInterpretationResponse response = service.interpret(new ReportInterpretationRequest("snap-report"));

        assertThat(response.interpretations()).hasSize(5);
        assertThat(response.interpretations()).extracting(ReportInterpretationItem::reportId)
            .containsExactly(
                "dr-pacs-chest-ct",
                "dr-ultrasound-abdomen",
                "dr-pathology-biopsy",
                "dr-endoscopy-gastroscopy",
                "dr-ecg-resting");
        assertThat(response.interpretations()).extracting(ReportInterpretationItem::itemCode)
            .containsOnly("launch.diagnostic-item.image-boundary.s36");
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

    private DeclarativeAssetRuntimePort diagnosticReportDeclarativeAssets() {
        return (tenantId, runtimeReleaseId, assetType, assetIdentity) -> {
            if (assetType == VersionedAssetType.FIELD_CATALOG
                    && "FIELD.CATALOG.CLINICAL_CONTEXT".equals(assetIdentity)) {
                return java.util.Optional.of(new ResolvedDeclarativeAsset(
                    assetType,
                    assetIdentity,
                    "V1",
                    runtimeReleaseId,
                    """
                    {
                      "fields": [
                        {
                          "category": "检验检查",
                          "group": "检验/体征结果",
                          "resourceType": "Observation",
                          "fieldPath": "observations[].criticalFlag",
                          "displayName": "危急值标记",
                          "dataType": "string",
                          "description": "来源系统声明的危急值标记",
                          "derived": false
                        },
                        {
                          "category": "检验检查",
                          "group": "检查报告",
                          "resourceType": "DiagnosticReport",
                          "fieldPath": "diagnosticReports[].conclusion",
                          "displayName": "报告结论",
                          "dataType": "string",
                          "description": "已签发报告结论",
                          "derived": false
                        }
                      ]
                    }
                    """,
                    "f".repeat(64)));
            }
            if (assetType == VersionedAssetType.ACTION_CARD
                    && "ACTION_CARD.REPORT.CRITICAL_VALUE".equals(assetIdentity)) {
                return java.util.Optional.of(new ResolvedDeclarativeAsset(
                    assetType,
                    assetIdentity,
                    "V1",
                    runtimeReleaseId,
                    """
                    {
                      "schemaVersion": "1.0",
                      "title": "危急值报告人工复核提示",
                      "actionCode": "REMIND",
                      "atSeverity": "HIGH",
                      "indicator": "critical",
                      "summary": "危急值报告需人工复核",
                      "detail": "报告解读仅作辅助，不改写已签发报告，不自动开嘱。",
                      "source": {"label": "MedKernel 本地上线演练"},
                      "suggestions": [],
                      "overrideReasons": [],
                      "requiresPhysicianConfirmation": true
                    }
                    """,
                    "e".repeat(64)));
            }
            return java.util.Optional.empty();
        };
    }
}
