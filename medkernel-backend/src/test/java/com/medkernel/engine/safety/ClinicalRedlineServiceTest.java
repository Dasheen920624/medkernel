package com.medkernel.engine.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.medkernel.engine.cdss.risk.CdssReviewRequirement;
import com.medkernel.engine.recommendation.RecommendationRiskLevel;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEventPublisher;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ClinicalRedlineServiceTest {

    private ClinicalRedlineRepository repository;
    private ClinicalRedlineTrialRepository trialRepository;
    private AuditEventPublisher auditPublisher;
    private ClinicalRedlineService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(ClinicalRedlineRepository.class);
        trialRepository = Mockito.mock(ClinicalRedlineTrialRepository.class);
        auditPublisher = Mockito.mock(AuditEventPublisher.class);
        service = new ClinicalRedlineService(repository, trialRepository, auditPublisher);
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-redline", OrgScope.tenant("tenant-A"), "medical-admin-1"));
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void requiredCategoriesCoverTheClinicalSafetyRedlineScope() {
        assertThat(ClinicalRedlineCategory.requiredSafetyCategories())
            .containsExactlyInAnyOrder(
                ClinicalRedlineCategory.DRUG_INTERACTION,
                ClinicalRedlineCategory.CRITICAL_VALUE,
                ClinicalRedlineCategory.DOSE_LIMIT,
                ClinicalRedlineCategory.ANTIMICROBIAL_RESTRICTION,
                ClinicalRedlineCategory.SPECIAL_POPULATION_CONTRAINDICATION);
    }

    @Test
    void activeCatalogListsOnlyDatabaseBackedVersionedRedlinesWithHazardAndRiskMatrixTrace() {
        ClinicalRedlineRule ddi = redline(
            "redline-ddi-warfarin-nsaid",
            ClinicalRedlineCategory.DRUG_INTERACTION,
            "RDL-DDI-001",
            "2026.1",
            ClinicalRedlineStatus.ACTIVE);
        when(repository.findByTenantIdAndStatusOrderByCategoryAscRedlineKeyAscUpdatedAtDesc(
                "tenant-A", ClinicalRedlineStatus.ACTIVE))
            .thenReturn(List.of(ddi));

        ClinicalRedlineCatalogResponse response = service.activeCatalog(null);

        assertThat(response.contentStatus()).isEqualTo(ClinicalRedlineContentStatus.CONFIGURED);
        assertThat(response.traceId()).isEqualTo("trace-redline");
        assertThat(response.redlines()).singleElement().satisfies(item -> {
            assertThat(item.redlineId()).isEqualTo("redline-ddi-warfarin-nsaid");
            assertThat(item.category()).isEqualTo(ClinicalRedlineCategory.DRUG_INTERACTION);
            assertThat(item.redlineVersion()).isEqualTo("2026.1");
            assertThat(item.hazardSeverity()).isEqualTo(RecommendationRiskLevel.CRITICAL);
            assertThat(item.riskMatrixId()).isEqualTo("risk-matrix-critical-ddi");
            assertThat(item.riskMatrixVersion()).isEqualTo("4");
            assertThat(item.reviewRequirement()).isEqualTo(CdssReviewRequirement.DUAL_REVIEW);
            assertThat(item.silentRunHours()).isEqualTo(168);
            assertThat(item.releaseGate()).isEqualTo("OPT04_REDLINE_SILENT_TRIAL");
            assertThat(item.conditionDsl()).contains("medications[].code");
            assertThat(item.evidenceSource()).isEqualTo("药品说明书与临床指南证据");
            assertThat(item.sourceVersionId()).isEqualTo(42L);
            assertThat(item.lowerTenantOverrideAllowed()).isFalse();
        });
    }

    @Test
    void activeCatalogCanFilterByControlledCategory() {
        ClinicalRedlineRule rule = redline(
            "redline-critical-potassium",
            ClinicalRedlineCategory.CRITICAL_VALUE,
            "RDL-LAB-001",
            "2026.1",
            ClinicalRedlineStatus.ACTIVE);
        when(repository.findByTenantIdAndCategoryAndStatusOrderByRedlineKeyAscUpdatedAtDesc(
                "tenant-A", ClinicalRedlineCategory.CRITICAL_VALUE, ClinicalRedlineStatus.ACTIVE))
            .thenReturn(List.of(rule));

        ClinicalRedlineCatalogResponse response =
            service.activeCatalog(ClinicalRedlineCategory.CRITICAL_VALUE);

        assertThat(response.redlines()).singleElement()
            .extracting(ClinicalRedlineResponse::category)
            .isEqualTo(ClinicalRedlineCategory.CRITICAL_VALUE);
    }

    @Test
    void emptyRepositoryReturnsNotConfiguredInsteadOfHardcodedMedicalConstants() {
        when(repository.findByTenantIdAndStatusOrderByCategoryAscRedlineKeyAscUpdatedAtDesc(
                "tenant-A", ClinicalRedlineStatus.ACTIVE))
            .thenReturn(List.of());

        ClinicalRedlineCatalogResponse response = service.activeCatalog(null);

        assertThat(response.contentStatus()).isEqualTo(ClinicalRedlineContentStatus.NOT_CONFIGURED);
        assertThat(response.redlines()).isEmpty();
    }

    @Test
    void dryRunRecordsRealSilentWindowEvidenceAndMovesDraftIntoSilentRunning() {
        ClinicalRedlineRule draft = redline(
            "redline-ddi-warfarin-nsaid",
            ClinicalRedlineCategory.DRUG_INTERACTION,
            "RDL-DDI-001",
            "2026.2",
            ClinicalRedlineStatus.DRAFT);
        when(repository.findByTenantIdAndRedlineId("tenant-A", "redline-ddi-warfarin-nsaid"))
            .thenReturn(Optional.of(draft));
        when(repository.save(any(ClinicalRedlineRule.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(trialRepository.save(any(ClinicalRedlineTrial.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ClinicalRedlineTrialResponse response = service.dryRun(new ClinicalRedlineDryRunRequest(
            "redline-ddi-warfarin-nsaid",
            Instant.parse("2026-05-26T00:00:00Z"),
            Instant.parse("2026-06-03T00:00:00Z"),
            1200,
            18,
            1,
            0,
            "evidence://silent-trials/redline-ddi-warfarin-nsaid/2026.2",
            "试运行窗口来自真实临床事件回放统计"));

        assertThat(response.status()).isEqualTo(ClinicalRedlineTrialStatus.PASSED);
        assertThat(response.actualSilentHours()).isEqualTo(192);
        assertThat(response.requiredSilentHours()).isEqualTo(168);
        assertThat(response.traceId()).isEqualTo("trace-redline");
        verify(repository).save(any(ClinicalRedlineRule.class));
        verify(trialRepository).save(any(ClinicalRedlineTrial.class));
        verify(auditPublisher).publish(
            AuditAction.EXECUTE,
            "mk_engine_clinical_redline_trial",
            response.trialId(),
            "记录临床安全红线静默试运行证据");
    }

    @Test
    void dryRunRejectsRuleWithoutHazardEvidenceOrRiskMatrixBinding() {
        ClinicalRedlineRule incomplete = new ClinicalRedlineRule(
            null,
            "redline-incomplete",
            "tenant-A",
            ClinicalRedlineCategory.DOSE_LIMIT,
            "medication-prescribe",
            "TENANT",
            "tenant-A",
            null,
            "RDL-DOSE-001",
            "2026.1",
            ClinicalRedlineStatus.DRAFT,
            RecommendationRiskLevel.CRITICAL,
            "",
            "",
            CdssReviewRequirement.DUAL_REVIEW,
            168,
            "OPT04_REDLINE_SILENT_TRIAL",
            "剂量上限红线",
            "",
            "{\"field\":\"medications[].dose\"}",
            "",
            "",
            null,
            false,
            Instant.parse("2026-06-04T02:00:00Z"),
            "tester",
            Instant.parse("2026-06-04T02:00:00Z"),
            "tester",
            "trace-redline");
        when(repository.findByTenantIdAndRedlineId("tenant-A", "redline-incomplete"))
            .thenReturn(Optional.of(incomplete));

        assertThatThrownBy(() -> service.dryRun(validDryRunRequest("redline-incomplete")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("危害分析、证据来源和风险矩阵绑定不能为空");
    }

    @Test
    void promoteRejectsBeforeSilentRunEvidenceMeetsRequiredHours() {
        ClinicalRedlineRule silent = redline(
            "redline-ddi-warfarin-nsaid",
            ClinicalRedlineCategory.DRUG_INTERACTION,
            "RDL-DDI-001",
            "2026.2",
            ClinicalRedlineStatus.SILENT_RUNNING);
        ClinicalRedlineTrial shortTrial = trial(
            "trial-short",
            silent,
            ClinicalRedlineTrialStatus.FAILED,
            72,
            1);
        when(repository.findByTenantIdAndRedlineId("tenant-A", "redline-ddi-warfarin-nsaid"))
            .thenReturn(Optional.of(silent));
        when(trialRepository.findByTenantIdAndRedlineIdAndTrialId(
                "tenant-A", "redline-ddi-warfarin-nsaid", "trial-short"))
            .thenReturn(Optional.of(shortTrial));

        assertThatThrownBy(() -> service.promote(new ClinicalRedlinePromoteRequest(
                "redline-ddi-warfarin-nsaid",
                "trial-short",
                "2026.2",
                "试运行未达到红线门槛，禁止上线")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("静默试运行未达标");
    }

    @Test
    void promoteActivatesOnlyAfterPassedSilentTrialEvidence() {
        ClinicalRedlineRule silent = redline(
            "redline-ddi-warfarin-nsaid",
            ClinicalRedlineCategory.DRUG_INTERACTION,
            "RDL-DDI-001",
            "2026.2",
            ClinicalRedlineStatus.SILENT_RUNNING);
        ClinicalRedlineTrial passedTrial = trial(
            "trial-pass",
            silent,
            ClinicalRedlineTrialStatus.PASSED,
            192,
            0);
        when(repository.findByTenantIdAndRedlineId("tenant-A", "redline-ddi-warfarin-nsaid"))
            .thenReturn(Optional.of(silent));
        when(trialRepository.findByTenantIdAndRedlineIdAndTrialId(
                "tenant-A", "redline-ddi-warfarin-nsaid", "trial-pass"))
            .thenReturn(Optional.of(passedTrial));
        when(repository.findByTenantIdAndActiveScopeKeyAndStatus(
                "tenant-A",
                "tenant-A|DRUG_INTERACTION|medication-prescribe|RDL-DDI-001",
                ClinicalRedlineStatus.ACTIVE))
            .thenReturn(Optional.empty());
        when(repository.save(any(ClinicalRedlineRule.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ClinicalRedlineResponse response = service.promote(new ClinicalRedlinePromoteRequest(
            "redline-ddi-warfarin-nsaid",
            "trial-pass",
            "2026.2",
            "静默试运行达标，按 OPT-04 上线"));

        assertThat(response.status()).isEqualTo(ClinicalRedlineStatus.ACTIVE);
        assertThat(response.redlineVersion()).isEqualTo("2026.2");
        verify(repository).save(any(ClinicalRedlineRule.class));
        verify(auditPublisher).publish(
            AuditAction.PUBLISH,
            "mk_engine_clinical_redline",
            "redline-ddi-warfarin-nsaid",
            "临床安全红线静默试运行达标后上线");
    }

    private ClinicalRedlineRule redline(
            String redlineId,
            ClinicalRedlineCategory category,
            String redlineKey,
            String redlineVersion,
            ClinicalRedlineStatus status) {
        Instant now = Instant.parse("2026-06-04T02:00:00Z");
        return new ClinicalRedlineRule(
            null,
            redlineId,
            "tenant-A",
            category,
            "medication-prescribe",
            "TENANT",
            "tenant-A",
            "tenant-A|" + category.name() + "|medication-prescribe|" + redlineKey,
            redlineKey,
            redlineVersion,
            status,
            RecommendationRiskLevel.CRITICAL,
            "risk-matrix-critical-ddi",
            "4",
            CdssReviewRequirement.DUAL_REVIEW,
            168,
            "OPT04_REDLINE_SILENT_TRIAL",
            "华法林合并非甾体抗炎药出血风险",
            "合用可能显著增加出血风险",
            """
            {"all":[{"field":"medications[].code","operator":"in","value":["ATC:B01AA03","ATC:M01A"]}]}
            """,
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

    private ClinicalRedlineDryRunRequest validDryRunRequest(String redlineId) {
        return new ClinicalRedlineDryRunRequest(
            redlineId,
            Instant.parse("2026-05-26T00:00:00Z"),
            Instant.parse("2026-06-03T00:00:00Z"),
            1200,
            18,
            1,
            0,
            "evidence://silent-trials/" + redlineId,
            "试运行窗口来自真实临床事件回放统计");
    }

    private ClinicalRedlineTrial trial(
            String trialId,
            ClinicalRedlineRule rule,
            ClinicalRedlineTrialStatus status,
            long actualSilentHours,
            long safetyIncidentCount) {
        Instant completedAt = Instant.parse("2026-06-03T00:00:00Z");
        return new ClinicalRedlineTrial(
            null,
            trialId,
            rule.tenantId(),
            rule.redlineId(),
            rule.redlineKey(),
            rule.redlineVersion(),
            status,
            completedAt.minus(Duration.ofHours(actualSilentHours)),
            completedAt,
            rule.silentRunHours(),
            actualSilentHours,
            1200,
            18,
            1,
            safetyIncidentCount,
            status == ClinicalRedlineTrialStatus.PASSED,
            "evidence://silent-trials/" + trialId,
            "试运行窗口来自真实临床事件回放统计",
            Instant.parse("2026-06-04T02:00:00Z"),
            "tester",
            "trace-redline");
    }
}
