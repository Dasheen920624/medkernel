package com.medkernel.engine.cdss.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.medkernel.engine.recommendation.RecommendationRiskLevel;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CdssRiskMatrixServiceTest {

    private CdssRiskMatrixRepository matrixRepository;
    private AuditRecorder auditRecorder;
    private CdssRiskMatrixService service;

    @BeforeEach
    void setUp() {
        matrixRepository = mock(CdssRiskMatrixRepository.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new CdssRiskMatrixService(matrixRepository, auditRecorder);
        when(matrixRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-risk-matrix", OrgScope.tenant("tenant-A"), "medical-admin-1"));
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void assessUsesActiveMatrixRuleToEscalateAutomatedHighHarmCdss() {
        CdssRiskMatrixRule activeRule = rule(
            "matrix-order-auto-v3",
            "order-sign",
            RecommendationRiskLevel.LOW,
            CdssAutomationLevel.AUTOMATED,
            RecommendationRiskLevel.CRITICAL,
            CdssReviewRequirement.DUAL_REVIEW,
            168,
            "OPT04_REDLINE_SILENT_TRIAL",
            false);
        when(matrixRepository.findActiveRule(
                "tenant-A", "order-sign", RecommendationRiskLevel.LOW, CdssAutomationLevel.AUTOMATED))
            .thenReturn(Optional.of(activeRule));

        CdssRiskAssessment assessment = service.assess(
            "order-sign", RecommendationRiskLevel.LOW, CdssAutomationLevel.AUTOMATED);

        assertThat(assessment.riskLevel()).isEqualTo(RecommendationRiskLevel.CRITICAL);
        assertThat(assessment.reviewRequirement()).isEqualTo(CdssReviewRequirement.DUAL_REVIEW);
        assertThat(assessment.requiresPhysicianConfirmation()).isTrue();
        assertThat(assessment.silentRunHours()).isEqualTo(168);
        assertThat(assessment.autoExecutionAllowed()).isFalse();
        assertThat(assessment.samdClassification()).isEqualTo("NMPA_RESERVED");
        assertThat(assessment.regulatoryEvidence()).isEqualTo("RISK_ANALYSIS_REQUIRED");
        assertThat(assessment.riskMatrixVersion()).isEqualTo("3");
    }

    @Test
    void updateRejectsMatrixThatDowngradesTheBuiltInSafetyBaseline() {
        CdssRiskMatrixUpdateRequest request = new CdssRiskMatrixUpdateRequest(
            "4",
            "试图把自动化医嘱签署提醒降为低风险",
            CdssRiskMatrixStatus.ACTIVE,
            List.of(new CdssRiskMatrixEntryRequest(
                "order-sign",
                RecommendationRiskLevel.LOW,
                CdssAutomationLevel.AUTOMATED,
                RecommendationRiskLevel.LOW,
                CdssReviewRequirement.OPTIONAL_REVIEW,
                0,
                "NO_GATE",
                true,
                "NMPA_RESERVED",
                "NOT_ASSESSED",
                "低估自动化医嘱风险")));

        assertThatThrownBy(() -> service.updateMatrix(request))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ENG_REC_001);

        verify(matrixRepository, never()).save(any());
        verify(auditRecorder, never()).record(any(), any(), any(), any());
    }

    @Test
    void updateRejectsMatrixThatWeakensReviewOrSilentRunGate() {
        CdssRiskMatrixUpdateRequest request = new CdssRiskMatrixUpdateRequest(
            "4",
            "试图降低自动化提醒复核强度",
            CdssRiskMatrixStatus.ACTIVE,
            List.of(new CdssRiskMatrixEntryRequest(
                "order-sign",
                RecommendationRiskLevel.LOW,
                CdssAutomationLevel.AUTOMATED,
                RecommendationRiskLevel.CRITICAL,
                CdssReviewRequirement.PHYSICIAN_CONFIRMATION,
                72,
                "OPT04_REDLINE_SILENT_TRIAL",
                false,
                "NMPA_RESERVED",
                "RISK_ANALYSIS_REQUIRED",
                "自动化医嘱提醒必须保留红线门槛")));

        assertThatThrownBy(() -> service.updateMatrix(request))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ENG_REC_001);

        verify(matrixRepository, never()).save(any());
        verify(auditRecorder, never()).record(any(), any(), any(), any());
    }

    @Test
    void updateRejectsDuplicateScopeInSameMatrixVersion() {
        CdssRiskMatrixEntryRequest entry = new CdssRiskMatrixEntryRequest(
            "result-review",
            RecommendationRiskLevel.HIGH,
            CdssAutomationLevel.INTERRUPTIVE,
            RecommendationRiskLevel.HIGH,
            CdssReviewRequirement.PHYSICIAN_CONFIRMATION,
            72,
            "OPT04_SILENT_TRIAL",
            false,
            "NMPA_RESERVED",
            "TRACEABLE_EVIDENCE_REQUIRED",
            "高危检验结果复核必须人工确认");
        CdssRiskMatrixUpdateRequest request = new CdssRiskMatrixUpdateRequest(
            "4",
            "重复提交同一规则维度",
            CdssRiskMatrixStatus.ACTIVE,
            List.of(entry, entry));

        assertThatThrownBy(() -> service.updateMatrix(request))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ENG_REC_001);

        verify(matrixRepository, never()).save(any());
        verify(auditRecorder, never()).record(any(), any(), any(), any());
    }

    @Test
    void updatePersistsVersionedActiveRulesAndWritesAudit() {
        CdssRiskMatrixUpdateRequest request = new CdssRiskMatrixUpdateRequest(
            "4",
            "更新静默试运行门槛",
            CdssRiskMatrixStatus.ACTIVE,
            List.of(new CdssRiskMatrixEntryRequest(
                "result-review",
                RecommendationRiskLevel.HIGH,
                CdssAutomationLevel.INTERRUPTIVE,
                RecommendationRiskLevel.HIGH,
                CdssReviewRequirement.PHYSICIAN_CONFIRMATION,
                72,
                "OPT04_SILENT_TRIAL",
                false,
                "NMPA_RESERVED",
                "TRACEABLE_EVIDENCE_REQUIRED",
                "高危检验结果复核必须人工确认")));

        CdssRiskMatrixResponse response = service.updateMatrix(request);

        ArgumentCaptor<CdssRiskMatrixRule> ruleCaptor = ArgumentCaptor.forClass(CdssRiskMatrixRule.class);
        verify(matrixRepository).save(ruleCaptor.capture());
        CdssRiskMatrixRule saved = ruleCaptor.getValue();
        assertThat(saved.tenantId()).isEqualTo("tenant-A");
        assertThat(saved.matrixVersion()).isEqualTo("4");
        assertThat(saved.status()).isEqualTo(CdssRiskMatrixStatus.ACTIVE);
        assertThat(saved.silentRunHours()).isEqualTo(72);
        assertThat(response.rules()).containsExactly(saved);
        verify(auditRecorder).record(
            AuditAction.UPDATE,
            "mk_engine_cdss_risk_matrix",
            "4",
            "更新 CDSS 风险分级矩阵(ACTIVE) 更新静默试运行门槛");
    }

    @Test
    void updateCanPersistPublishedRulesForSevenStepFlowWithoutMakingThemActive() {
        CdssRiskMatrixUpdateRequest request = new CdssRiskMatrixUpdateRequest(
            "5",
            "发布候选矩阵等待生效",
            CdssRiskMatrixStatus.PUBLISHED,
            List.of(new CdssRiskMatrixEntryRequest(
                "result-review",
                RecommendationRiskLevel.HIGH,
                CdssAutomationLevel.INTERRUPTIVE,
                RecommendationRiskLevel.HIGH,
                CdssReviewRequirement.PHYSICIAN_CONFIRMATION,
                72,
                "OPT04_SILENT_TRIAL",
                false,
                "NMPA_RESERVED",
                "TRACEABLE_EVIDENCE_REQUIRED",
                "高危检验结果复核必须人工确认")));

        CdssRiskMatrixResponse response = service.updateMatrix(request);

        assertThat(response.rules()).singleElement()
            .extracting(CdssRiskMatrixRule::status)
            .isEqualTo(CdssRiskMatrixStatus.PUBLISHED);
        verify(auditRecorder).record(
            AuditAction.UPDATE,
            "mk_engine_cdss_risk_matrix",
            "5",
            "更新 CDSS 风险分级矩阵(PUBLISHED) 发布候选矩阵等待生效");
    }

    @Test
    void activeMatrixUsesLatestRuleByUpdatedAtInsteadOfStringVersionOrder() {
        Instant older = Instant.parse("2026-06-01T01:00:00Z");
        Instant newer = Instant.parse("2026-06-01T02:00:00Z");
        CdssRiskMatrixRule versionNine = rule(
            9L, "matrix-v9", "order-sign", RecommendationRiskLevel.HIGH,
            CdssAutomationLevel.INTERRUPTIVE, RecommendationRiskLevel.HIGH,
            CdssReviewRequirement.PHYSICIAN_CONFIRMATION, 72, "OPT04_SILENT_TRIAL",
            false, "9", older);
        CdssRiskMatrixRule versionTen = rule(
            10L, "matrix-v10", "order-sign", RecommendationRiskLevel.HIGH,
            CdssAutomationLevel.INTERRUPTIVE, RecommendationRiskLevel.HIGH,
            CdssReviewRequirement.PHYSICIAN_CONFIRMATION, 96, "OPT04_SILENT_TRIAL",
            false, "10", newer);
        when(matrixRepository.findByTenantIdAndStatusOrderByTriggerPointAscSeverityLevelAscAutomationLevelAsc(
                "tenant-A", CdssRiskMatrixStatus.ACTIVE))
            .thenReturn(List.of(versionNine, versionTen));

        CdssRiskMatrixResponse response = service.activeMatrix();

        assertThat(response.rules()).containsExactly(versionTen);
    }

    private CdssRiskMatrixRule rule(
            String matrixId,
            String triggerPoint,
            RecommendationRiskLevel severity,
            CdssAutomationLevel automationLevel,
            RecommendationRiskLevel riskLevel,
            CdssReviewRequirement reviewRequirement,
            int silentRunHours,
            String releaseGate,
            boolean autoExecutionAllowed) {
        return rule(1L, matrixId, triggerPoint, severity, automationLevel, riskLevel, reviewRequirement,
            silentRunHours, releaseGate, autoExecutionAllowed, "3", Instant.now());
    }

    private CdssRiskMatrixRule rule(
            Long id,
            String matrixId,
            String triggerPoint,
            RecommendationRiskLevel severity,
            CdssAutomationLevel automationLevel,
            RecommendationRiskLevel riskLevel,
            CdssReviewRequirement reviewRequirement,
            int silentRunHours,
            String releaseGate,
            boolean autoExecutionAllowed,
            String matrixVersion,
            Instant updatedAt) {
        Instant now = Instant.now();
        return new CdssRiskMatrixRule(
            id,
            matrixId,
            "tenant-A",
            triggerPoint,
            severity,
            automationLevel,
            riskLevel,
            reviewRequirement,
            silentRunHours,
            releaseGate,
            autoExecutionAllowed,
            "NMPA_RESERVED",
            "RISK_ANALYSIS_REQUIRED",
            CdssRiskMatrixStatus.ACTIVE,
            matrixVersion,
            "矩阵规则命中",
            now,
            "tester",
            updatedAt,
            "tester",
            "trace-risk-matrix");
    }
}
