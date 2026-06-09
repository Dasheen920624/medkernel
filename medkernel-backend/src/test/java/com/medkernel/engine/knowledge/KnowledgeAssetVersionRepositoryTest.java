package com.medkernel.engine.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;

/** 知识版本仓储：验证诊断运行查询与复审队列分页查询。 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:kav-diagnosis-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class KnowledgeAssetVersionRepositoryTest {

    @Autowired
    KnowledgeIdentityRepository identityRepo;

    @Autowired
    KnowledgeAssetVersionRepository versionRepo;

    @AfterEach
    void wipe() {
        versionRepo.deleteAll();
        identityRepo.deleteAll();
    }

    @Test
    void findActiveDiagnosisVersionsReturnsOnlyActiveDiagnosis() {
        KnowledgeIdentity dx = identityRepo.save(identity("DX.PNEUMONIA", KnowledgeDomain.DIAGNOSIS, "社区获得性肺炎"));
        versionRepo.save(activeVersion(dx.id()));
        KnowledgeIdentity gl = identityRepo.save(identity("GL.SEPSIS", KnowledgeDomain.GUIDELINE, "脓毒症指南"));
        versionRepo.save(activeVersion(gl.id())); // 非诊断域，不应返回

        List<KnowledgeAssetVersion> result = versionRepo.findActiveDiagnosisVersions("t-1");

        assertThat(result).singleElement().satisfies(v -> {
            assertThat(v.identityId()).isEqualTo(dx.id());
            assertThat(v.status()).isEqualTo(KnowledgeVersionStatus.ACTIVE);
        });
    }

    @Test
    void pageReviewDueReturnsOnlyWindowedActiveVersionsInDueOrder() {
        Instant now = Instant.parse("2026-06-09T00:00:00Z");
        KnowledgeIdentity overdueIdentity =
            identityRepo.save(identity("plat:drug:overdue", KnowledgeDomain.DRUG, "已逾期知识"));
        KnowledgeIdentity upcomingIdentity =
            identityRepo.save(identity("plat:drug:upcoming", KnowledgeDomain.DRUG, "临近复审知识"));
        KnowledgeIdentity futureIdentity =
            identityRepo.save(identity("plat:drug:future", KnowledgeDomain.DRUG, "远期复审知识"));
        versionRepo.save(withReviewAt(activeVersion(overdueIdentity.id()), now.minusSeconds(86_400)));
        KnowledgeAssetVersion upcoming =
            versionRepo.save(withReviewAt(activeVersion(upcomingIdentity.id()), now.plusSeconds(86_400)));
        versionRepo.save(withReviewAt(activeVersion(futureIdentity.id()), now.plusSeconds(86_400L * 40)));
        Instant threshold = now.plusSeconds(86_400L * 30);

        assertThat(versionRepo.countReviewDueByTenantId("t-1", threshold)).isEqualTo(2L);
        assertThat(versionRepo.pageReviewDueByTenantId("t-1", threshold, 1, 1))
            .singleElement()
            .extracting(KnowledgeAssetVersion::id)
            .isEqualTo(upcoming.id());
    }

    private KnowledgeIdentity identity(String code, KnowledgeDomain domain, String subject) {
        Instant now = Instant.now();
        return new KnowledgeIdentity(null, "t-1", code, domain, subject, null, null,
            KnowledgeIdentityStatus.ACTIVE, null, now, "system", now, "system");
    }

    private KnowledgeAssetVersion activeVersion(Long identityId) {
        Instant now = Instant.now();
        String orgScope = "tenant:t-1";
        String appScope = KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE;
        return new KnowledgeAssetVersion(
            null, "t-1", identityId, "v1.0", null, null, null, "hash-" + identityId, "[]",
            KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW,
            SourceAuthorityLevel.B_GUIDELINE, GradeEvidenceQuality.MODERATE, GradeRecommendationStrength.WEAK, null,
            orgScope, appScope, KnowledgeAssetVersion.activeScopeKey(identityId, orgScope, appScope),
            null, null, null, null, now, null, null, null,
            now, "system", now, "system", 12, null);
    }

    private KnowledgeAssetVersion withReviewAt(KnowledgeAssetVersion source, Instant nextReviewAt) {
        return new KnowledgeAssetVersion(
            source.id(), source.tenantId(), source.identityId(), source.versionNo(), source.versionLabel(),
            source.sourceDocumentId(), source.sourceVersionId(), source.contentHash(), source.anchors(),
            source.status(), source.riskLevel(), source.authorityLevel(), source.gradeQuality(), source.gradeStrength(),
            source.conflictArbitration(), source.organizationScope(), source.applicableScope(), source.activeScopeKey(),
            source.effectiveFrom(), source.effectiveTo(), source.reviewedBy(), source.reviewedAt(),
            source.activatedAt(), source.supersededAt(), source.withdrawnAt(), source.withdrawnReason(),
            source.createdAt(), source.createdBy(), source.updatedAt(), source.updatedBy(),
            source.reviewCycleMonths(), nextReviewAt);
    }
}
