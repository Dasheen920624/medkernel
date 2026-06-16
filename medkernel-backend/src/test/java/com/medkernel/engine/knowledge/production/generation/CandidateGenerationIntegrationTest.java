package com.medkernel.engine.knowledge.production.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityRepository;
import com.medkernel.engine.knowledge.KnowledgeVersionStatus;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceDocumentRepository;
import com.medkernel.engine.knowledge.SourceFragment;
import com.medkernel.engine.knowledge.SourceFragmentRepository;
import com.medkernel.engine.knowledge.SourceType;
import com.medkernel.engine.knowledge.SourceVersion;
import com.medkernel.engine.knowledge.SourceVersionRepository;
import com.medkernel.engine.knowledge.production.KnowledgeDomain;
import com.medkernel.engine.knowledge.production.MaterializationTarget;
import com.medkernel.engine.knowledge.production.NewIdentitySpec;
import com.medkernel.engine.knowledge.production.TargetPipeline;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * AIK-STD-04 来源→生成→候选落审核链端到端集成测试（真实 H2）。
 *
 * <p>seed 解析后受控源（document + version + 带锚点 fragment）→ 经 {@link CandidateGenerationOrchestrationService}
 * 逐类生成模板桩候选 → 经既有 AIK-STD-13 job+intake 真实落 {@code KnowledgeAssetVersion}（待审）；
 * 验证候选带真实锚点、候选态不入库直接成权威（PENDING_REPLACEMENT_REVIEW）。
 */
@SpringBootTest
@ActiveProfiles("dev")
class CandidateGenerationIntegrationTest {

    private static final String TENANT = "tenant-gen-it";

    @Autowired
    private CandidateGenerationOrchestrationService service;
    @Autowired
    private SourceDocumentRepository sourceDocuments;
    @Autowired
    private SourceVersionRepository sourceVersions;
    @Autowired
    private SourceFragmentRepository sourceFragments;
    @Autowired
    private KnowledgeIdentityRepository identities;
    @Autowired
    private KnowledgeAssetVersionRepository versions;

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void generatesCandidatesFromSourceIntoReviewChain() {
        RequestContext.restore(new RequestContext.Snapshot("trace-gen-it", OrgScope.tenant(TENANT), "user-it"));
        Instant now = Instant.now();
        SourceDocument doc = sourceDocuments.save(new SourceDocument(null, TENANT, "SRC-AIK04",
            SourceType.GUIDELINE, SourceAuthorityLevel.B_GUIDELINE, "依据", "高血压基层诊疗指南",
            "卫健委", "lic", "zh", now, "u", now, "u"));
        SourceVersion version = sourceVersions.save(
            new SourceVersion(null, TENANT, doc.id(), "v1", now, "vh", "uri", "zh", now, "u"));
        sourceFragments.save(new SourceFragment(null, TENANT, version.id(), "section-1", "诊断标准",
            "血压≥140/90 诊断高血压。", "b".repeat(64), now));
        sourceFragments.save(new SourceFragment(null, TENANT, version.id(), "section-2", "用药",
            "首选 CCB 或 ACEI。", "c".repeat(64), now));

        GenerationSummary summary = service.generate(new CandidateGenerationRequest(
            version.id(), TargetPipeline.TENANT_OVERLAY, KnowledgeDomain.CLINICAL,
            List.of(
                new GenerationItem(VersionedAssetType.RULE, new MaterializationTarget(null,
                    new NewIdentitySpec(com.medkernel.engine.knowledge.KnowledgeDomain.GUIDELINE,
                        "高血压诊断规则", "KN-AIK04-RULE"))),
                new GenerationItem(VersionedAssetType.PATHWAY, new MaterializationTarget(null,
                    new NewIdentitySpec(com.medkernel.engine.knowledge.KnowledgeDomain.GUIDELINE,
                        "高血压管理路径", "KN-AIK04-PATH"))))));

        assertThat(summary.skipped()).isEmpty();
        assertThat(summary.candidates()).hasSize(2);
        assertThat(summary.candidates()).allSatisfy(candidate ->
            assertThat(candidate.candidateRef()).startsWith("kv:"));

        // 候选真实落既有版本/审核链（候选态不入库直接成权威）
        KnowledgeIdentity ruleIdentity =
            identities.findByTenantIdAndIdentityCode(TENANT, "KN-AIK04-RULE").orElseThrow();
        List<KnowledgeAssetVersion> ruleVersions =
            versions.findByTenantIdAndIdentityIdOrderByCreatedAtDesc(TENANT, ruleIdentity.id());
        assertThat(ruleVersions).hasSize(1);
        assertThat(ruleVersions.get(0).status()).isEqualTo(KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW);
        assertThat(ruleVersions.get(0).sourceDocumentId()).isEqualTo(doc.id());
    }
}
