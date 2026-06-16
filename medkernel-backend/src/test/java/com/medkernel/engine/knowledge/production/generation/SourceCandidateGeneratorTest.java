package com.medkernel.engine.knowledge.production.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.factory.ProfessionalAssetTemplateRegistry;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceFragment;
import com.medkernel.engine.knowledge.SourceType;
import com.medkernel.engine.knowledge.SourceVersion;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.hash.Sha256ContentHash;
import org.junit.jupiter.api.Test;

/** AIK-STD-04 B0 模板桩候选生成器单元测试。 */
class SourceCandidateGeneratorTest {

    private final SourceCandidateGenerator generator =
        new SourceCandidateGenerator(new ProfessionalAssetTemplateRegistry(), new ObjectMapper());

    private SourceDocument document() {
        return new SourceDocument(7L, "t-1", "GL-2024", SourceType.GUIDELINE, SourceAuthorityLevel.B_GUIDELINE,
            "卫健委指南", "高血压基层诊疗指南", "卫健委", "CC-BY", "zh", Instant.EPOCH, "sys", Instant.EPOCH, "sys");
    }

    private SourceVersion version() {
        return new SourceVersion(9L, "t-1", 7L, "v1", Instant.EPOCH,
            "a".repeat(64), "file://gl", "zh", Instant.EPOCH, "sys");
    }

    private List<SourceFragment> fragments() {
        return List.of(
            new SourceFragment(1L, "t-1", 9L, "section-1", "总则", "血压≥140/90 诊断高血压。",
                "b".repeat(64), Instant.EPOCH),
            new SourceFragment(2L, "t-1", 9L, "section-2", "用药", "首选 CCB 或 ACEI。",
                "c".repeat(64), Instant.EPOCH));
    }

    @Test
    void generatesRuleDraftStubWithRealAnchorsAndHash() {
        KnowledgeAssetEnvelope envelope = generator.generate(
            "t-1", document(), version(), fragments(), VersionedAssetType.RULE, "identity:42");

        assertThat(envelope.assetType()).isEqualTo(VersionedAssetType.RULE);
        assertThat(envelope.assetIdentity()).isEqualTo("identity:42");
        assertThat(envelope.subject()).isEqualTo("高血压基层诊疗指南");
        assertThat(envelope.versionLabel()).isEqualTo("draft-from-v1");
        assertThat(envelope.lifecycleStatus()).isEqualTo(AssetVersionStatus.DRAFT);
        assertThat(envelope.trustLevel()).isEqualTo(SourceAuthorityLevel.B_GUIDELINE);
        assertThat(envelope.orgScope()).isEqualTo("t-1");
        // sources≥1，第一条须 intake 可解析格式 sourceCode:versionNo:anchorPath
        assertThat(envelope.sources()).hasSize(2);
        assertThat(envelope.sources().get(0).sourceRef()).isEqualTo("GL-2024:v1:section-1");
        assertThat(envelope.sources().get(0).authorityLevel()).isEqualTo(SourceAuthorityLevel.B_GUIDELINE);
        // 逻辑字段留白不伪造；来源摘要真实
        assertThat(envelope.payload()).contains("待编著").contains("血压≥140/90");
        // contentHash 真实等于 sha256(payload)
        assertThat(envelope.contentHash()).matches("^[0-9a-f]{64}$");
        assertThat(Sha256ContentHash.sha256(envelope.payload(), "x")).isEqualTo(envelope.contentHash());
    }

    @Test
    void generatesEachOfFiveAssetTypes() {
        for (VersionedAssetType type : List.of(VersionedAssetType.RULE, VersionedAssetType.PATHWAY,
            VersionedAssetType.RECOMMENDATION, VersionedAssetType.EVALUATION, VersionedAssetType.FOLLOWUP)) {
            KnowledgeAssetEnvelope envelope = generator.generate(
                "t-1", document(), version(), fragments(), type, "identity:1");
            assertThat(envelope.assetType()).isEqualTo(type);
            assertThat(envelope.sources()).isNotEmpty();
            assertThat(envelope.lifecycleStatus()).isEqualTo(AssetVersionStatus.DRAFT);
        }
    }

    @Test
    void rejectsWhenNoStructuralTemplate() {
        assertThatThrownBy(() -> generator.generate(
            "t-1", document(), version(), fragments(), VersionedAssetType.PACKAGE, "identity:1"))
            .isInstanceOf(ApiException.class);
    }
}
