package com.medkernel.engine.knowledge.production.gate;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.medkernel.engine.cdss.risk.CdssReviewRequirement;
import com.medkernel.engine.factory.AssetSourceRef;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceDocumentRepository;
import com.medkernel.engine.knowledge.SourceType;
import com.medkernel.engine.knowledge.SourceVersion;
import com.medkernel.engine.knowledge.SourceVersionRepository;
import com.medkernel.engine.recommendation.RecommendationRiskLevel;
import com.medkernel.engine.safety.ClinicalRedlineCategory;
import com.medkernel.engine.safety.ClinicalRedlineRepository;
import com.medkernel.engine.safety.ClinicalRedlineRule;
import com.medkernel.engine.safety.ClinicalRedlineStatus;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.hash.Sha256ContentHash;

/**
 * AIK-STD-05 候选安全门禁结果持久化与审计查询端到端集成测试（真实 H2）。
 *
 * <p>验证门禁逐项结果真实落 {@code mk_aik_gate_result} 并可经 {@code listResults} 回溯（FR-5）；
 * 坏候选诚实判不过、好候选全过。
 */
@SpringBootTest
@ActiveProfiles("dev")
class CandidateSafetyGateIntegrationTest {

    private static final String TENANT = "tenant-gate-it";
    private static final String PAYLOAD = "{\"template\":\"RULE\",\"sections\":{}}";

    @Autowired
    private CandidateSafetyGateService gateService;
    @Autowired
    private SourceDocumentRepository sourceDocuments;
    @Autowired
    private SourceVersionRepository sourceVersions;
    @Autowired
    private ClinicalRedlineRepository redlines;

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private KnowledgeAssetEnvelope envelope(List<AssetSourceRef> sources) {
        return new KnowledgeAssetEnvelope(VersionedAssetType.RULE, "identity:1", "高血压规则", "draft-from-v1",
            sources, SourceAuthorityLevel.B_GUIDELINE, null, null, KnowledgeRiskLevel.MEDIUM, TENANT,
            Sha256ContentHash.sha256(PAYLOAD, "x"), PAYLOAD, AssetVersionStatus.DRAFT);
    }

    @Test
    void badCandidateBlockedResultsPersistedAndQueryable() {
        RequestContext.restore(new RequestContext.Snapshot("trace-it", OrgScope.tenant(TENANT), "user-it"));

        GateOutcome outcome = gateService.evaluate(envelope(List.of()), new GateContext(TENANT, "job-gate-bad"));

        assertThat(outcome.passed()).isFalse();
        assertThat(outcome.failedItems()).extracting(GateItemResult::code).contains(SourcePresentGate.CODE);

        List<AikGateResult> results = gateService.listResults("job-gate-bad");
        assertThat(results).hasSize(9);
        assertThat(results).anyMatch(row -> row.gateCode().equals(SourcePresentGate.CODE) && !row.passed());
    }

    @Test
    void goodCandidatePassesAllGates() {
        RequestContext.restore(new RequestContext.Snapshot("trace-it", OrgScope.tenant(TENANT), "user-it"));
        seedSource();
        seedRedlines();

        GateOutcome outcome = gateService.evaluate(
            envelope(List.of(new AssetSourceRef("GL:v1:s1", SourceAuthorityLevel.B_GUIDELINE))),
            new GateContext(TENANT, "job-gate-good"));

        assertThat(outcome.passed()).isTrue();
        List<AikGateResult> results = gateService.listResults("job-gate-good");
        assertThat(results).hasSize(9);
        assertThat(results).allMatch(AikGateResult::passed);
    }

    private void seedSource() {
        Instant now = Instant.now();
        SourceDocument document = sourceDocuments.save(new SourceDocument(null, TENANT, "GL", SourceType.GUIDELINE,
            SourceAuthorityLevel.B_GUIDELINE, "依据", "指南", "出版者", "公开许可", "zh",
            now, "u", now, "u"));
        sourceVersions.save(new SourceVersion(null, TENANT, document.id(), "v1", now,
            "h".repeat(64), "uri://gl", "zh", now, "u"));
    }

    private void seedRedlines() {
        for (ClinicalRedlineCategory category : ClinicalRedlineCategory.requiredSafetyCategories()) {
            redlines.save(redline(category));
        }
    }

    private ClinicalRedlineRule redline(ClinicalRedlineCategory category) {
        Instant now = Instant.now();
        String key = "RDL-" + category.name();
        return new ClinicalRedlineRule(null, "rl-" + category.name(), TENANT, category,
            "medication-prescribe", "TENANT", TENANT,
            TENANT + "|" + category.name() + "|medication-prescribe|" + key,
            key, "v1", ClinicalRedlineStatus.ACTIVE, RecommendationRiskLevel.CRITICAL,
            "risk-matrix", "v1", CdssReviewRequirement.DUAL_REVIEW, 168,
            "OPT04_REDLINE_SILENT_TRIAL", "红线 " + category.name(), "安全危害",
            "{\"field\":\"medications[].code\",\"operator\":\"in\"}", "依据", "ref", 42L,
            false, now, "tester", now, "tester", "trace");
    }
}
