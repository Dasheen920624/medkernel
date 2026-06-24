package com.medkernel.engine.llm.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.medkernel.engine.cdss.risk.CdssReviewRequirement;
import com.medkernel.engine.recommendation.RecommendationRiskLevel;
import com.medkernel.engine.safety.ClinicalRedlineCategory;
import com.medkernel.engine.safety.ClinicalRedlineRepository;
import com.medkernel.engine.safety.ClinicalRedlineRule;
import com.medkernel.engine.safety.ClinicalRedlineStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RegressionBaselineProjectionServiceTest {

    private final ClinicalRedlineRepository redlineRepository = mock(ClinicalRedlineRepository.class);
    private final MedicalRegressionCaseRepository caseRepository = mock(MedicalRegressionCaseRepository.class);
    private final RegressionBaselineProjectionService service =
        new RegressionBaselineProjectionService(redlineRepository, caseRepository);

    @Test
    void projectsActiveRedlinesIntoGroundedEnabledCases() {
        ClinicalRedlineRule redline = redline("tenant-A", "redline-dose-limit", "RDL-DOSE-001",
            ClinicalRedlineCategory.DOSE_LIMIT);
        when(redlineRepository.findTenantIdsWithActiveRedlines()).thenReturn(List.of("tenant-A"));
        when(redlineRepository.findByTenantIdAndStatusOrderByCategoryAscRedlineKeyAscUpdatedAtDesc(
            "tenant-A", ClinicalRedlineStatus.ACTIVE)).thenReturn(List.of(redline));
        when(caseRepository.findByTenantIdAndCapabilityCodeAndCaseInput(
            "tenant-A", "rule.draft", expectedCaseInput(redline))).thenReturn(Optional.empty());

        int projected = service.projectAllActiveTenants();

        ArgumentCaptor<MedicalRegressionCase> saved = ArgumentCaptor.forClass(MedicalRegressionCase.class);
        verify(caseRepository).save(saved.capture());
        assertThat(projected).isEqualTo(1);
        assertThat(saved.getValue()).satisfies(regressionCase -> {
            assertThat(regressionCase.tenantId()).isEqualTo("tenant-A");
            assertThat(regressionCase.capabilityCode()).isEqualTo("rule.draft");
            assertThat(regressionCase.caseInput()).contains(
                "儿童用药剂量不得超过已审上限",
                "超出已审剂量上限可能导致严重不良反应",
                "{\"field\":\"dose.amount\",\"operator\":\"lte\"}",
                "source-version:77#dose-limit");
            assertThat(regressionCase.expectedPhrase()).isEqualTo("儿童用药剂量不得超过已审上限");
            assertThat(regressionCase.redLineType()).isEqualTo("DOSE_LIMIT");
            assertThat(regressionCase.citationRequired()).isEqualTo("Y");
            assertThat(regressionCase.caseVersion()).isEqualTo("2026.1");
            assertThat(regressionCase.enabledFlag()).isEqualTo("Y");
            assertThat(regressionCase.createdBy()).isEqualTo("regression-baseline-seeder");
            assertThat(regressionCase.updatedBy()).isEqualTo("regression-baseline-seeder");
        });
    }

    @Test
    void skipsAlreadyProjectedRedlineCaseInput() {
        ClinicalRedlineRule redline = redline("tenant-A", "redline-dose-limit", "RDL-DOSE-001",
            ClinicalRedlineCategory.DOSE_LIMIT);
        MedicalRegressionCase existing = new MedicalRegressionCase(9L, "tenant-A", "rule.draft",
            "rule", expectedCaseInput(redline), "儿童用药剂量不得超过已审上限", "[]", "[]", 100, "DOSE_LIMIT",
            "source-version:77#dose-limit", "Y", "2026.1", "Y", Instant.now(),
            "regression-baseline-seeder", Instant.now(), "regression-baseline-seeder");
        when(redlineRepository.findByTenantIdAndStatusOrderByCategoryAscRedlineKeyAscUpdatedAtDesc(
            "tenant-A", ClinicalRedlineStatus.ACTIVE)).thenReturn(List.of(redline));
        when(caseRepository.findByTenantIdAndCapabilityCodeAndCaseInput(
            "tenant-A", "rule.draft", expectedCaseInput(redline))).thenReturn(Optional.of(existing));

        int projected = service.projectTenant("tenant-A");

        assertThat(projected).isZero();
        verify(caseRepository, never()).save(any(MedicalRegressionCase.class));
    }

    @Test
    void boundsProjectedCaseInputWithoutDroppingEvidenceAnchor() {
        ClinicalRedlineRule redline = redline("tenant-A", "redline-dose-limit", "RDL-DOSE-001",
            ClinicalRedlineCategory.DOSE_LIMIT, "x".repeat(2500));
        when(redlineRepository.findByTenantIdAndStatusOrderByCategoryAscRedlineKeyAscUpdatedAtDesc(
            "tenant-A", ClinicalRedlineStatus.ACTIVE)).thenReturn(List.of(redline));
        when(caseRepository.findByTenantIdAndCapabilityCodeAndCaseInput(
            eq("tenant-A"), eq("rule.draft"), anyString())).thenReturn(Optional.empty());

        service.projectTenant("tenant-A");

        ArgumentCaptor<MedicalRegressionCase> saved = ArgumentCaptor.forClass(MedicalRegressionCase.class);
        verify(caseRepository).save(saved.capture());
        assertThat(saved.getValue().caseInput())
            .hasSizeLessThanOrEqualTo(2000)
            .contains("条件DSL：")
            .contains("source-version:77#dose-limit");
    }

    private static String expectedCaseInput(ClinicalRedlineRule redline) {
        return """
            请依据已审临床安全红线判断候选知识是否必须阻断。
            红线ID：%s
            红线类目：%s
            红线标题：%s
            危害说明：%s
            条件DSL：%s
            证据来源：%s
            证据引用：%s
            """.formatted(
            redline.redlineId(),
            redline.category().name(),
            redline.title(),
            redline.clinicalHazard(),
            redline.conditionDsl(),
            redline.evidenceSource(),
            redline.evidenceReference()).trim();
    }

    private static ClinicalRedlineRule redline(
            String tenantId,
            String redlineId,
            String redlineKey,
            ClinicalRedlineCategory category) {
        return redline(tenantId, redlineId, redlineKey, category,
            "{\"field\":\"dose.amount\",\"operator\":\"lte\"}");
    }

    private static ClinicalRedlineRule redline(
            String tenantId,
            String redlineId,
            String redlineKey,
            ClinicalRedlineCategory category,
            String conditionDsl) {
        Instant now = Instant.parse("2026-06-16T00:00:00Z");
        return new ClinicalRedlineRule(
            null,
            redlineId,
            tenantId,
            category,
            "knowledge-candidate-review",
            "TENANT",
            tenantId,
            tenantId + "|" + category.name() + "|knowledge-candidate-review|" + redlineKey,
            redlineKey,
            "2026.1",
            ClinicalRedlineStatus.ACTIVE,
            RecommendationRiskLevel.CRITICAL,
            "risk-matrix-dose",
            "4",
            CdssReviewRequirement.PHYSICIAN_CONFIRMATION,
            168,
            "OPT04_REDLINE_SILENT_TRIAL",
            "儿童用药剂量不得超过已审上限",
            "超出已审剂量上限可能导致严重不良反应",
            conditionDsl,
            "药品说明书与儿科用药指南证据",
            "source-version:77#dose-limit",
            77L,
            false,
            now,
            "tester",
            now,
            "tester",
            "trace-redline");
    }
}
