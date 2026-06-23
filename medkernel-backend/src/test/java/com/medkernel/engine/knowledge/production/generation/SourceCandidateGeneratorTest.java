package com.medkernel.engine.knowledge.production.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.factory.ProfessionalAssetTemplateRegistry;
import com.medkernel.engine.knowledge.KnowledgeDomain;
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

    private final ObjectMapper json = new ObjectMapper();
    private final SourceCandidateGenerator generator =
        new SourceCandidateGenerator(new ProfessionalAssetTemplateRegistry(), json);

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
    void generatesRuleDraftTemplateFromControlledSource() throws Exception {
        KnowledgeAssetEnvelope envelope = generator.generate(
            "t-1", document(), version(), fragments(), VersionedAssetType.RULE,
            null, "RULE-HTN-1");

        JsonNode payload = json.readTree(envelope.payload());
        assertThat(envelope.assetType()).isEqualTo(VersionedAssetType.RULE);
        assertThat(envelope.lifecycleStatus()).isEqualTo(AssetVersionStatus.DRAFT);
        assertThat(envelope.sources()).hasSize(2);
        assertThat(payload.path("schemaVersion").asText()).isEqualTo("1.0");
        assertThat(payload.path("ruleCode").asText()).isEqualTo("RULE-HTN-1");
        assertThat(payload.path("generationMode").asText()).isEqualTo("B0_TEMPLATE");
        assertThat(payload.path("medicalContentStatus").asText()).isEqualTo("PENDING_AUTHORING");
        assertThat(payload.path("fieldCatalogIdentity").asText())
            .isEqualTo("FIELD.CATALOG.CLINICAL_CONTEXT");
        assertThat(payload.path("triggerBindings").get(0).path("purpose").asText())
            .isEqualTo("RULE_EXECUTION");
        assertThat(payload.path("dsl").path("when").path("all").get(0).path("field").asText())
            .isEqualTo("patient.age");
        assertThat(payload.path("dsl").path("then").isArray()).isTrue();
        assertThat(payload.path("dsl").path("then").get(0).path("actionCardRef").asText())
            .isEqualTo("ACTION.AUTHORING_REVIEW");
        assertThat(payload.path("sourceEvidence").get(0).path("excerpt").asText())
            .contains("血压≥140/90");
        assertThat(envelope.payload())
            .doesNotContain("conditionFragment")
            .doesNotContain("packageVersion")
            .doesNotContain("versionNo");
        assertThat(Sha256ContentHash.sha256(envelope.payload(), "x")).isEqualTo(envelope.contentHash());
    }

    @Test
    void generatesPathwayDraftTemplateFromControlledSource() throws Exception {
        KnowledgeAssetEnvelope envelope = generator.generate(
            "t-1", document(), version(), fragments(), VersionedAssetType.PATHWAY,
            null, "PATHWAY-HTN-1");

        JsonNode payload = json.readTree(envelope.payload());
        assertThat(envelope.assetType()).isEqualTo(VersionedAssetType.PATHWAY);
        assertThat(envelope.lifecycleStatus()).isEqualTo(AssetVersionStatus.DRAFT);
        assertThat(payload.path("schemaVersion").asText()).isEqualTo("1.0");
        assertThat(payload.path("pathwayCode").asText()).isEqualTo("PATHWAY-HTN-1");
        assertThat(payload.path("startNodeCode").asText()).isEqualTo("start");
        assertThat(payload.path("terminalNodeCodes").get(0).asText()).isEqualTo("end");
        assertThat(payload.path("triggerBindings").get(0).path("purpose").asText())
            .isEqualTo("PATHWAY_ENTRY_CANDIDATE");
        assertThat(payload.path("nodes")).hasSize(2);
        assertThat(payload.path("edges")).hasSize(1);
        assertThat(payload.path("sourceEvidence").get(1).path("anchorPath").asText())
            .isEqualTo("section-2");
        assertThat(envelope.payload())
            .doesNotContain("subPath")
            .doesNotContain("conditionFragment")
            .doesNotContain("packageVersion")
            .doesNotContain("versionNo");
        assertThat(Sha256ContentHash.sha256(envelope.payload(), "x")).isEqualTo(envelope.contentHash());
    }

    @Test
    void rejectsUnsupportedAssetTypes() {
        for (VersionedAssetType type : List.of(
            VersionedAssetType.ACTION_CARD,
            VersionedAssetType.EVALUATION,
            VersionedAssetType.FOLLOWUP,
            VersionedAssetType.FORMULA,
            VersionedAssetType.VALUE_SET)) {
            assertThatThrownBy(() -> generator.generate(
                "t-1", document(), version(), fragments(), type, null, "identity:1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("仅支持知识、规则和路径");
        }
    }

    @Test
    void selectsKnowledgeTemplateByExplicitMedicalDomain() {
        KnowledgeAssetEnvelope envelope = generator.generate(
            "t-1", document(), version(), fragments(), VersionedAssetType.KNOWLEDGE,
            KnowledgeDomain.DRUG, "identity:drug-1");

        assertThat(envelope.assetType()).isEqualTo(VersionedAssetType.KNOWLEDGE);
        assertThat(envelope.lifecycleStatus()).isEqualTo(AssetVersionStatus.DRAFT);
        assertThat(envelope.sources()).hasSize(2);
        assertThat(envelope.sources().get(0).sourceRef()).isEqualTo("GL-2024:v1:section-1");
        assertThat(envelope.payload())
            .contains("\"template\":\"DRUG\"")
            .contains("\"dosage\"")
            .contains("\"generationMode\":\"B0_TEMPLATE\"")
            .contains("血压≥140/90")
            .doesNotContain("\"recommendation\"");
        assertThat(Sha256ContentHash.sha256(envelope.payload(), "x")).isEqualTo(envelope.contentHash());
    }

    @Test
    void rejectsKnowledgeGenerationWithoutMedicalDomain() {
        assertThatThrownBy(() -> generator.generate(
            "t-1", document(), version(), fragments(), VersionedAssetType.KNOWLEDGE,
            null, "identity:1"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("知识领域");
    }
}
