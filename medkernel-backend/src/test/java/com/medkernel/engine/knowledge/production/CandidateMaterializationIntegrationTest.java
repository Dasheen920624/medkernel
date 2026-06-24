package com.medkernel.engine.knowledge.production;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.medkernel.engine.factory.AssetSourceRef;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.knowledge.CandidateClassification;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityRepository;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.KnowledgeVersionStatus;
import com.medkernel.engine.knowledge.CandidateClassificationRepository;
import com.medkernel.engine.knowledge.ReviewAssignment;
import com.medkernel.engine.knowledge.ReviewAssignmentRepository;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceDocumentRepository;
import com.medkernel.engine.knowledge.SourceType;
import com.medkernel.engine.knowledge.SourceVersion;
import com.medkernel.engine.knowledge.SourceVersionRepository;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.org.OrgUnitStatus;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.hash.Sha256ContentHash;

/**
 * 候选真实物化端到端集成测试（AIK-STD-13 PR4，真实 H2）。
 *
 * <p>验证 discovery-origin 候选经 {@link MaterializingCandidateIntake} 真实落 {@code KnowledgeAssetVersion}（待审）
 * + {@code CandidateClassification} + 建立医疗引擎运营员单负责人 {@code ReviewAssignment}，
 * 自动进现审核台审/发链。
 */
@SpringBootTest
@ActiveProfiles("dev")
class CandidateMaterializationIntegrationTest {

    private static final String TENANT = "tenant-mat-it";

    @Autowired
    private MaterializingCandidateIntake intake;
    @Autowired
    private SourceDocumentRepository sourceDocuments;
    @Autowired
    private SourceVersionRepository sourceVersions;
    @Autowired
    private KnowledgeIdentityRepository identities;
    @Autowired
    private KnowledgeAssetVersionRepository versions;
    @Autowired
    private CandidateClassificationRepository classifications;
    @Autowired
    private ReviewAssignmentRepository reviewAssignments;
    @Autowired
    private OrgUnitRepository organizations;

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void materializesDiscoveryCandidateIntoReviewChainWithRoutedAssignments() {
        RequestContext.restore(new RequestContext.Snapshot("trace-it", OrgScope.tenant(TENANT), "user-it"));
        Instant now = Instant.now();
        seedTenantRoot(now);
        SourceDocument doc = sourceDocuments.save(new SourceDocument(null, TENANT, "SRC-MAT", SourceType.GUIDELINE,
            SourceAuthorityLevel.A_REGULATION, "依据", "标题", "出版者", "lic", "zh", now, "u", now, "u"));
        sourceVersions.save(new SourceVersion(null, TENANT, doc.id(), "v1", now, "vh", "uri", "zh", now, "u"));

        String payload = "二甲双胍用药受控候选正文";
        KnowledgeAssetEnvelope envelope = new KnowledgeAssetEnvelope(VersionedAssetType.KNOWLEDGE,
            "discovery:SRC-MAT:v1:root/0", "二甲双胍说明书", "run-1",
            List.of(new AssetSourceRef("SRC-MAT:v1:root/0", SourceAuthorityLevel.A_REGULATION)),
            SourceAuthorityLevel.A_REGULATION, null, null, KnowledgeRiskLevel.HIGH, TENANT,
            Sha256ContentHash.sha256(payload, "x"), payload, AssetVersionStatus.DRAFT);
        KnowledgeProductionJob job = new KnowledgeProductionJob(1L, TENANT, "job-mat", "run-1",
            VersionedAssetType.KNOWLEDGE, KnowledgeProducer.MANUAL, TargetPipeline.TENANT_OVERLAY,
            KnowledgeDomain.PHARMACY, null, ProductionJobStatus.RUNNING, 0, null, now, "u", now, "u", "t");
        ReviewRoutingDecision routing = new ReviewRoutingDecision(
            RoleCode.ENGINE_OPERATOR, KnowledgeDomain.PHARMACY);
        MaterializationTarget target = new MaterializationTarget(null,
            new NewIdentitySpec(com.medkernel.engine.knowledge.KnowledgeDomain.DRUG, "二甲双胍说明书", "KN-MAT-METFORMIN"));

        String ref = intake.intake(job, envelope, target, routing);

        assertThat(ref).startsWith("kv:");
        KnowledgeIdentity identity = identities.findByTenantIdAndIdentityCode(TENANT, "KN-MAT-METFORMIN").orElseThrow();
        List<KnowledgeAssetVersion> persistedVersions =
            versions.findByTenantIdAndIdentityIdOrderByCreatedAtDesc(TENANT, identity.id());
        assertThat(persistedVersions).hasSize(1);
        assertThat(persistedVersions.get(0).status()).isEqualTo(KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW);
        assertThat(persistedVersions.get(0).sourceDocumentId()).isEqualTo(doc.id());

        List<CandidateClassification> persistedClassifications =
            classifications.findByTenantIdAndIdentityIdOrderByCreatedAtDescIdDesc(TENANT, identity.id());
        assertThat(persistedClassifications).hasSize(1);

        List<ReviewAssignment> assignments =
            reviewAssignments.findByTenantIdAndIdentityIdOrderByCreatedAtDescIdDesc(TENANT, identity.id());
        assertThat(assignments).extracting(ReviewAssignment::assignedTo)
            .containsExactly(RoleCode.ENGINE_OPERATOR.code());
    }

    private void seedTenantRoot(Instant now) {
        if (organizations.findByTenantIdAndParentIdIsNull(TENANT).isPresent()) {
            return;
        }
        organizations.save(new OrgUnit(
            null,
            null,
            TENANT,
            "/" + TENANT,
            OrgLevel.TENANT,
            "TENANT-MAT-IT",
            "候选物化测试租户",
            null,
            null,
            null,
            OrgUnitStatus.ACTIVE,
            now,
            "user-it",
            now,
            "user-it"
        ));
    }
}
