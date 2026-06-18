package com.medkernel.engine.safety;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import com.medkernel.engine.cdss.risk.CdssReviewRequirement;
import com.medkernel.engine.recommendation.RecommendationRiskLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;

@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:clinical-redline-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class ClinicalRedlineRepositoryTest {

    @Autowired
    ClinicalRedlineRepository repository;

    @Autowired
    ClinicalRedlineTrialRepository trialRepository;

    @AfterEach
    void wipe() {
        trialRepository.deleteAll();
        repository.deleteAll();
    }

    @Test
    void persistsVersionedRedlinesAndReadsOnlyActiveRowsForTenant() {
        repository.save(redline(
            "tenant-A", "redline-ddi-warfarin-nsaid", "RDL-DDI-001", "2026.1",
            ClinicalRedlineCategory.DRUG_INTERACTION, ClinicalRedlineStatus.ACTIVE));
        repository.save(redline(
            "tenant-A", "redline-ddi-warfarin-nsaid-draft", "RDL-DDI-001", "2026.2-draft",
            ClinicalRedlineCategory.DRUG_INTERACTION, ClinicalRedlineStatus.DRAFT));
        repository.save(redline(
            "tenant-B", "redline-ddi-warfarin-nsaid-tenant-b", "RDL-DDI-001", "2026.1",
            ClinicalRedlineCategory.DRUG_INTERACTION, ClinicalRedlineStatus.ACTIVE));

        List<ClinicalRedlineRule> active = repository
            .findByTenantIdAndStatusOrderByCategoryAscRedlineKeyAscUpdatedAtDesc(
                "tenant-A", ClinicalRedlineStatus.ACTIVE);

        assertThat(active).singleElement().satisfies(rule -> {
            assertThat(rule.tenantId()).isEqualTo("tenant-A");
            assertThat(rule.status()).isEqualTo(ClinicalRedlineStatus.ACTIVE);
            assertThat(rule.redlineVersion()).isEqualTo("2026.1");
            assertThat(rule.lowerTenantOverrideAllowed()).isFalse();
        });
    }

    @Test
    void filtersActiveRedlinesByCategory() {
        repository.save(redline(
            "tenant-A", "redline-ddi-warfarin-nsaid", "RDL-DDI-001", "2026.1",
            ClinicalRedlineCategory.DRUG_INTERACTION, ClinicalRedlineStatus.ACTIVE));
        repository.save(redline(
            "tenant-A", "redline-critical-potassium", "RDL-LAB-001", "2026.1",
            ClinicalRedlineCategory.CRITICAL_VALUE, ClinicalRedlineStatus.ACTIVE));

        List<ClinicalRedlineRule> criticalValues = repository
            .findByTenantIdAndCategoryAndStatusOrderByRedlineKeyAscUpdatedAtDesc(
                "tenant-A", ClinicalRedlineCategory.CRITICAL_VALUE, ClinicalRedlineStatus.ACTIVE);

        assertThat(criticalValues).singleElement()
            .extracting(ClinicalRedlineRule::redlineId)
            .isEqualTo("redline-critical-potassium");
    }

    @Test
    void findsTenantsWithActiveRedlinesOnlyOnce() {
        repository.save(redline(
            "tenant-A", "redline-ddi-warfarin-nsaid", "RDL-DDI-001", "2026.1",
            ClinicalRedlineCategory.DRUG_INTERACTION, ClinicalRedlineStatus.ACTIVE));
        repository.save(redline(
            "tenant-A", "redline-critical-potassium", "RDL-LAB-001", "2026.1",
            ClinicalRedlineCategory.CRITICAL_VALUE, ClinicalRedlineStatus.ACTIVE));
        repository.save(redline(
            "tenant-B", "redline-dose-limit", "RDL-DOSE-001", "2026.1",
            ClinicalRedlineCategory.DOSE_LIMIT, ClinicalRedlineStatus.ACTIVE));
        repository.save(redline(
            "tenant-C", "redline-dose-limit-draft", "RDL-DOSE-001", "2026.2-draft",
            ClinicalRedlineCategory.DOSE_LIMIT, ClinicalRedlineStatus.DRAFT));

        assertThat(repository.findTenantIdsWithActiveRedlines())
            .containsExactly("tenant-A", "tenant-B");
    }

    @Test
    void persistsSilentTrialEvidenceForVersionedRedline() {
        ClinicalRedlineRule savedRule = repository.save(redline(
            "tenant-A", "redline-ddi-warfarin-nsaid", "RDL-DDI-001", "2026.2",
            ClinicalRedlineCategory.DRUG_INTERACTION, ClinicalRedlineStatus.SILENT_RUNNING));
        ClinicalRedlineTrial savedTrial = trialRepository.save(trial(
            "trial-redline-ddi-2026-2",
            savedRule,
            ClinicalRedlineTrialStatus.PASSED,
            192,
            0));

        ClinicalRedlineTrial found = trialRepository
            .findByTenantIdAndRedlineIdAndTrialId(
                "tenant-A", "redline-ddi-warfarin-nsaid", "trial-redline-ddi-2026-2")
            .orElseThrow();

        assertThat(found.id()).isEqualTo(savedTrial.id());
        assertThat(found.status()).isEqualTo(ClinicalRedlineTrialStatus.PASSED);
        assertThat(found.requiredSilentHours()).isEqualTo(168);
        assertThat(found.actualSilentHours()).isEqualTo(192);
        assertThat(found.gatePassed()).isTrue();
        assertThat(found.evidenceReference())
            .isEqualTo("evidence://silent-trials/redline-ddi-warfarin-nsaid/2026.2");
    }

    private ClinicalRedlineRule redline(
            String tenantId,
            String redlineId,
            String redlineKey,
            String redlineVersion,
            ClinicalRedlineCategory category,
            ClinicalRedlineStatus status) {
        Instant now = Instant.now();
        return new ClinicalRedlineRule(
            null,
            redlineId,
            tenantId,
            category,
            "medication-prescribe",
            "TENANT",
            tenantId,
            status == ClinicalRedlineStatus.ACTIVE
                ? tenantId + "|" + category.name() + "|medication-prescribe|" + redlineKey
                : null,
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
            "{\"field\":\"medications[].code\",\"operator\":\"in\"}",
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
            completedAt.minusSeconds(actualSilentHours * 3600),
            completedAt,
            rule.silentRunHours(),
            actualSilentHours,
            1200,
            18,
            1,
            safetyIncidentCount,
            status == ClinicalRedlineTrialStatus.PASSED,
            "evidence://silent-trials/" + rule.redlineId() + "/" + rule.redlineVersion(),
            "试运行窗口来自真实临床事件回放统计",
            Instant.parse("2026-06-04T02:00:00Z"),
            "tester",
            "trace-redline");
    }
}
