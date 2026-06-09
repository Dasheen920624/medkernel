package com.medkernel.engine.safety;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.medkernel.engine.knowledge.GradeEvidenceQuality;
import com.medkernel.engine.knowledge.GradeRecommendationStrength;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.KnowledgeVersionStatus;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.pathway.PathwayEntryMode;
import com.medkernel.engine.pathway.PathwayTemplate;
import com.medkernel.engine.pathway.PathwayTemplateLevel;
import com.medkernel.engine.pathway.PathwayTemplateStatus;
import com.medkernel.engine.recommendation.RecommendationSourceRequest;
import com.medkernel.engine.recommendation.RecommendationSourceType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClinicalSafetyGuardTest {

    private KnowledgeAssetVersionRepository versions;
    private ClinicalSafetyGuard guard;

    @BeforeEach
    void setUp() {
        versions = mock(KnowledgeAssetVersionRepository.class);
        guard = new ClinicalSafetyGuard(versions);
    }

    @Test
    void recommendationSourceRejectsWithdrawnKnowledgeVersion() {
        when(versions.findByTenantIdAndId("tenant-A", 5L))
            .thenReturn(Optional.of(version(5L, KnowledgeVersionStatus.WITHDRAWN)));

        assertThatThrownBy(() -> guard.assertRecommendationSourcesAllowed("tenant-A", List.of(
                new RecommendationSourceRequest(
                    RecommendationSourceType.KNOWLEDGE, "knowledge-version:5", "v1", "抗凝禁忌",
                    "knowledge_version:5", "sha256:source", "旧版命中"))))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT);
    }

    @Test
    void recommendationSourceAllowsActiveKnowledgeAndIgnoresRuleSource() {
        when(versions.findByTenantIdAndId("tenant-A", 5L))
            .thenReturn(Optional.of(version(5L, KnowledgeVersionStatus.ACTIVE)));

        assertThatCode(() -> guard.assertRecommendationSourcesAllowed("tenant-A", List.of(
                new RecommendationSourceRequest(
                    RecommendationSourceType.KNOWLEDGE, "knowledge-version:5", "v1", "抗凝禁忌",
                    null, "sha256:source", "当前权威版本"),
                new RecommendationSourceRequest(
                    RecommendationSourceType.RULE, "rule-1", "v1", "规则",
                    null, "sha256:rule", "规则命中"))))
            .doesNotThrowAnyException();
    }

    @Test
    void pathwayTemplateRejectsWithdrawnKnowledgeSourceRef() {
        when(versions.findByTenantIdAndId("tenant-A", 5L))
            .thenReturn(Optional.of(version(5L, KnowledgeVersionStatus.WITHDRAWN)));

        assertThatThrownBy(() -> guard.assertPathwayTemplateAllowed(template("knowledge-version:5")))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT);
    }

    private KnowledgeAssetVersion version(Long versionId, KnowledgeVersionStatus status) {
        Instant now = Instant.now();
        return new KnowledgeAssetVersion(
            versionId, "tenant-A", 1L, "v1", "抗凝禁忌指南", 1L, 1L, "sha256:version",
            "anchors", status, KnowledgeRiskLevel.HIGH, SourceAuthorityLevel.B_GUIDELINE,
            GradeEvidenceQuality.MODERATE, GradeRecommendationStrength.STRONG, null,
            "tenant:tenant-A", "ALL", status == KnowledgeVersionStatus.ACTIVE ? "1|tenant:tenant-A|ALL" : "version:" + versionId,
            now.minusSeconds(3600), now.plusSeconds(3600), "reviewer", now.minusSeconds(1800),
            status == KnowledgeVersionStatus.ACTIVE ? now.minusSeconds(1200) : null,
            null, status == KnowledgeVersionStatus.WITHDRAWN ? now.minusSeconds(60) : null,
            status == KnowledgeVersionStatus.WITHDRAWN ? "上游召回" : null,
            now.minusSeconds(7200), "creator", now, "reviewer", 12, null);
    }

    private PathwayTemplate template(String sourceRef) {
        Instant now = Instant.now();
        return new PathwayTemplate(
            1L, "pt-1", "tenant-A", "sp-1", "TPL.COPD", "稳定期随访路径",
            "COPD", 1, PathwayTemplateLevel.STANDARD, PathwayTemplateStatus.PUBLISHED,
            PathwayEntryMode.AUTO_SUGGEST, "ASSESS",
            sourceRef, "路径引用知识版本", "{}", "{}", now, "tester", now, "tester", "trace-path");
    }
}
