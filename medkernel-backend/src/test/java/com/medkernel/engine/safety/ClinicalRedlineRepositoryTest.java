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

    @AfterEach
    void wipe() {
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
}
