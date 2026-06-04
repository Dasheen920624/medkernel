package com.medkernel.engine.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import com.medkernel.engine.cdss.risk.CdssReviewRequirement;
import com.medkernel.engine.recommendation.RecommendationRiskLevel;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ClinicalRedlineServiceTest {

    private ClinicalRedlineRepository repository;
    private ClinicalRedlineService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(ClinicalRedlineRepository.class);
        service = new ClinicalRedlineService(repository);
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
}
