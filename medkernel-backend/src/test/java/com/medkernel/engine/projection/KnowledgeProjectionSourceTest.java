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
import com.medkernel.engine.knowledge.diagnosis.DiagnosisCarePointer;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisCarePointerRepository;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisCarePointerType;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisCareTargetType;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisCriterion;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisCriterionRepository;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisDifferential;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisDifferentialRepository;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisDirection;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisWeight;

class KnowledgeProjectionSourceTest {

    private final KnowledgeIdentityRepository identities = mock(KnowledgeIdentityRepository.class);
    private final KnowledgeAssetVersionRepository versions = mock(KnowledgeAssetVersionRepository.class);
    private final CitationRepository citations = mock(CitationRepository.class);
    private final SourceFragmentRepository fragments = mock(SourceFragmentRepository.class);
    private final SourceVersionRepository sourceVersions = mock(SourceVersionRepository.class);
    private final SourceDocumentRepository documents = mock(SourceDocumentRepository.class);
    private final DiagnosisCriterionRepository diagnosisCriteria = mock(DiagnosisCriterionRepository.class);
    private final DiagnosisDifferentialRepository diagnosisDifferentials = mock(DiagnosisDifferentialRepository.class);
    private final DiagnosisCarePointerRepository diagnosisCarePointers = mock(DiagnosisCarePointerRepository.class);
    private final KnowledgeProjectionSource source = new KnowledgeProjectionSource(
        identities,
        versions,
        citations,
        fragments,
        sourceVersions,
        documents,
        diagnosisCriteria,
        diagnosisDifferentials,
        diagnosisCarePointers);

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
            .contains("organizationScope=tenant:t-1")
            .contains("applicableScope=ALL")
            .contains("activeScopeKey=1\\|tenant:t-1\\|ALL")
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
            .contains("organizationScope=tenant:t-1")
            .contains("applicableScope=ALL")
            .contains("activeScopeKey=1\\|tenant:t-1\\|ALL")
            .contains("citationCount=1")
            .doesNotContain("完整条文原文");
    }

    @Test
    void graphFactsProjectDiagnosisCriteriaDifferentialsAndCarePointersFromRelationalAuthority() {
        KnowledgeIdentity diagnosis = new KnowledgeIdentity(
            2L, "t-1", "DX.PNEU", KnowledgeDomain.DIAGNOSIS, "社区获得性肺炎",
            "RESP", "诊断知识", KnowledgeIdentityStatus.ACTIVE, 20L,
            now(), "tester", now(), "tester");
        KnowledgeIdentity tuberculosis = new KnowledgeIdentity(
            3L, "t-1", "DX.TB", KnowledgeDomain.DIAGNOSIS, "肺结核",
            "RESP", "鉴别诊断", KnowledgeIdentityStatus.ACTIVE, 30L,
            now(), "tester", now(), "tester");
        KnowledgeAssetVersion version = new KnowledgeAssetVersion(
            20L, "t-1", 2L, "v1.0", "CAP", 7L, 8L,
            "d".repeat(64), "anchors", KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW,
            SourceAuthorityLevel.B_GUIDELINE, GradeEvidenceQuality.MODERATE, GradeRecommendationStrength.STRONG,
            null, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE,
            KnowledgeAssetVersion.activeScopeKey(2L, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE),
            now(), null, "reviewer", now(), now(), null, null, null,
            now(), "tester", now(), "tester", 12, null);
        when(versions.findByTenantIdAndStatusOrderByUpdatedAtDescIdDesc("t-1", KnowledgeVersionStatus.ACTIVE))
            .thenReturn(List.of(version));
        when(identities.findByTenantIdAndId("t-1", 2L)).thenReturn(Optional.of(diagnosis));
        when(identities.findByTenantIdAndId("t-1", 3L)).thenReturn(Optional.of(tuberculosis));
        when(diagnosisCriteria.findByTenantIdAndDiagnosisVersionId("t-1", 20L)).thenReturn(List.of(
            new DiagnosisCriterion(101L, "t-1", 20L, "FEVER", DiagnosisDirection.REQUIRED,
                DiagnosisWeight.MAJOR, null, null, null, now(), "tester", now(), "tester", "trace")
        ));
        when(diagnosisDifferentials.findByTenantIdAndDiagnosisVersionId("t-1", 20L)).thenReturn(List.of(
            new DiagnosisDifferential(201L, "t-1", 20L, 3L,
                "发热咳嗽需与肺结核鉴别", "胸片/痰涂片", now(), "tester", now(), "tester", "trace")
        ));
        when(diagnosisCarePointers.findByTenantIdAndDiagnosisVersionId("t-1", 20L)).thenReturn(List.of(
            new DiagnosisCarePointer(301L, "t-1", 20L, DiagnosisCarePointerType.WORKUP,
                DiagnosisCareTargetType.RULE, "RULE.CAP.WORKUP", true,
                "医师确认后评估检查建议", now(), "tester", now(), "tester", "trace")
        ));

        List<ProjectionFact> facts = source.graphFactsForTenant("t-1");

        assertThat(facts).extracting(ProjectionFact::factKey)
            .contains(
                "NODE:DIAGNOSIS_CRITERION:101",
                "EDGE:KNOWLEDGE_VERSION:20:HAS_DIAGNOSIS_CRITERION:DIAGNOSIS_CRITERION:101",
                "EDGE:KNOWLEDGE_VERSION:20:DIFFERENTIAL_DIAGNOSIS:KNOWLEDGE_IDENTITY:3",
                "NODE:DIAGNOSIS_CARE_POINTER:301",
                "EDGE:KNOWLEDGE_VERSION:20:HAS_CARE_POINTER:DIAGNOSIS_CARE_POINTER:301"
            );
        assertThat(joinPayloads(facts))
            .contains("findingTermCode=FEVER")
            .contains("predicate=DIFFERENTIAL_DIAGNOSIS")
            .contains("keyPoint=发热咳嗽需与肺结核鉴别")
            .contains("targetRef=RULE.CAP.WORKUP");
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
            "A 法规优先", "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE,
            KnowledgeAssetVersion.activeScopeKey(1L, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE),
            now, null, "reviewer", now, now, null, null, null,
            now, "tester", now, "tester", 12, null);
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
