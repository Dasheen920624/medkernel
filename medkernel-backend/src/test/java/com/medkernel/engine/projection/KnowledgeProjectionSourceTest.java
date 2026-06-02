package com.medkernel.engine.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.knowledge.Citation;
import com.medkernel.engine.knowledge.CitationRelation;
import com.medkernel.engine.knowledge.CitationRepository;
import com.medkernel.engine.knowledge.GradeEvidenceQuality;
import com.medkernel.engine.knowledge.GradeRecommendationStrength;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.engine.knowledge.KnowledgeDomain;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityRepository;
import com.medkernel.engine.knowledge.KnowledgeIdentityStatus;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.KnowledgeVersionStatus;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceDocumentRepository;
import com.medkernel.engine.knowledge.SourceFragment;
import com.medkernel.engine.knowledge.SourceFragmentRepository;
import com.medkernel.engine.knowledge.SourceType;
import com.medkernel.engine.knowledge.SourceVersion;
import com.medkernel.engine.knowledge.SourceVersionRepository;

class KnowledgeProjectionSourceTest {

    private final KnowledgeIdentityRepository identities = mock(KnowledgeIdentityRepository.class);
    private final KnowledgeAssetVersionRepository versions = mock(KnowledgeAssetVersionRepository.class);
    private final CitationRepository citations = mock(CitationRepository.class);
    private final SourceFragmentRepository fragments = mock(SourceFragmentRepository.class);
    private final SourceVersionRepository sourceVersions = mock(SourceVersionRepository.class);
    private final SourceDocumentRepository documents = mock(SourceDocumentRepository.class);
    private final KnowledgeProjectionSource source = new KnowledgeProjectionSource(
        identities,
        versions,
        citations,
        fragments,
        sourceVersions,
        documents);

    @Test
    void graphFactsUseOnlyActiveRelationalKnowledgeAndCitationAnchorsWithoutRawExcerpt() {
        wireOneActiveKnowledgeAsset();
        when(versions.findByTenantIdAndStatusOrderByUpdatedAtDescIdDesc("t-1", KnowledgeVersionStatus.ACTIVE))
            .thenReturn(List.of(activeVersion()));

        List<ProjectionFact> facts = source.graphFactsForTenant("t-1");

        assertThat(facts).allMatch(fact -> fact.targetType() == ProjectionTargetType.KNOWLEDGE_GRAPH);
        assertThat(facts).extracting(ProjectionFact::factKey)
            .contains(
                "NODE:KNOWLEDGE_IDENTITY:1",
                "NODE:KNOWLEDGE_VERSION:10",
                "NODE:SOURCE_DOCUMENT:7",
                "NODE:SOURCE_FRAGMENT:100",
                "EDGE:KNOWLEDGE_IDENTITY:1:HAS_ACTIVE_VERSION:KNOWLEDGE_VERSION:10",
                "EDGE:KNOWLEDGE_VERSION:10:CITES_FRAGMENT:SOURCE_FRAGMENT:100",
                "EDGE:SOURCE_FRAGMENT:100:BELONGS_TO_SOURCE:SOURCE_DOCUMENT:7"
            )
            .doesNotContain("NODE:KNOWLEDGE_VERSION:11");
        assertThat(joinPayloads(facts))
            .contains("authorityLevel=A_REGULATION")
            .contains("gradeQuality=HIGH")
            .doesNotContain("完整条文原文");
    }

    @Test
    void searchFactsBuildRebuildableSearchDocumentsFromActiveRelationalKnowledge() {
        wireOneActiveKnowledgeAsset();
        when(versions.findByTenantIdAndStatusOrderByUpdatedAtDescIdDesc("t-1", KnowledgeVersionStatus.ACTIVE))
            .thenReturn(List.of(activeVersion()));

        List<ProjectionFact> facts = source.searchFactsForTenant("t-1");

        assertThat(facts).hasSize(1);
        ProjectionFact document = facts.get(0);
        assertThat(document.targetType()).isEqualTo(ProjectionTargetType.KNOWLEDGE_SEARCH);
        assertThat(document.factKey()).isEqualTo("NODE:KNOWLEDGE_SEARCH_DOCUMENT:10");
        assertThat(document.canonicalPayload())
            .contains("subject=二甲双胍禁忌证")
            .contains("sourceTitle=国家药品说明书")
            .contains("authorityLevel=A_REGULATION")
            .contains("citationCount=1")
            .doesNotContain("完整条文原文");
    }

    private void wireOneActiveKnowledgeAsset() {
        when(identities.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identity()));
        when(citations.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 10L))
            .thenReturn(List.of(citation()));
        when(fragments.findByTenantIdAndId("t-1", 100L)).thenReturn(Optional.of(fragment()));
        when(sourceVersions.findByTenantIdAndId("t-1", 8L)).thenReturn(Optional.of(sourceVersion()));
        when(documents.findByTenantIdAndId("t-1", 7L)).thenReturn(Optional.of(sourceDocument()));
    }

    private KnowledgeIdentity identity() {
        Instant now = now();
        return new KnowledgeIdentity(
            1L, "t-1", "DRUG.METFORMIN", KnowledgeDomain.DRUG, "二甲双胍禁忌证",
            "ENDO", "糖尿病用药禁忌", KnowledgeIdentityStatus.ACTIVE, 10L,
            now, "tester", now, "tester");
    }

    private KnowledgeAssetVersion activeVersion() {
        Instant now = now();
        return new KnowledgeAssetVersion(
            10L, "t-1", 1L, "2026.1", "2026 说明书", 7L, 8L,
            "a".repeat(64), "anchors", KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.HIGH,
            SourceAuthorityLevel.A_REGULATION, GradeEvidenceQuality.HIGH, GradeRecommendationStrength.STRONG,
            "A 法规优先", now, null, "reviewer", now, now, null, null, null,
            now, "tester", now, "tester");
    }

    private Citation citation() {
        return new Citation(
            1L, "t-1", 10L, 100L, CitationRelation.SUPPORTS, 100, 3, 12, now(), "tester");
    }

    private SourceFragment fragment() {
        return new SourceFragment(
            100L, "t-1", 8L, "section-4.3", "禁忌证", "完整条文原文不可进入投影载荷",
            "b".repeat(64), now());
    }

    private SourceVersion sourceVersion() {
        return new SourceVersion(8L, "t-1", 7L, "2026.1", now(), "c".repeat(64), "s3://doc", "zh-CN", now(), "tester");
    }

    private SourceDocument sourceDocument() {
        Instant now = now();
        return new SourceDocument(
            7L, "t-1", "NMPA.METFORMIN", SourceType.DRUG_LABEL,
            SourceAuthorityLevel.A_REGULATION, "国家药监局说明书", "国家药品说明书",
            "国家药监局", "LICENSE", "zh-CN", now, "tester", now, "tester");
    }

    private String joinPayloads(List<ProjectionFact> facts) {
        return facts.stream()
            .map(ProjectionFact::canonicalPayload)
            .reduce("", (left, right) -> left + "\n" + right);
    }

    private Instant now() {
        return Instant.parse("2026-06-01T00:00:00Z");
    }
}
