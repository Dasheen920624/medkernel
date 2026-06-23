package com.medkernel.engine.knowledge.production.initialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;

/** 初始化发行清单纯确定性校验测试。 */
class KnowledgeInitializationManifestValidatorTest {

    private final KnowledgeInitializationCatalog catalog = new KnowledgeInitializationCatalog();
    private final KnowledgeInitializationManifestValidator validator =
        new KnowledgeInitializationManifestValidator(catalog);

    @Test
    void foundationRequiresEveryCoverageDimension() {
        InitializationManifestDraft draft = foundationDraft(
            Set.of(FoundationCoverageDimension.SOURCE_LICENSE_MANIFEST),
            List.of(item("KNOWGEN-29", "SOURCE.CATALOG", List.of(), false)));

        assertThatThrownBy(() -> validator.validate(draft))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("基础发行覆盖不完整");
    }

    @Test
    void foundationRejectsModelGeneratedCanonicalData() {
        InitializationManifestDraft draft = foundationDraft(
            Set.copyOf(Arrays.asList(FoundationCoverageDimension.values())),
            List.of(item("KNOWGEN-26", "DATA_ELEMENT.PATIENT_ID", List.of(), true)));

        assertThatThrownBy(() -> validator.validate(draft))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("基础 canonical 数据禁止由模型生成");
    }

    @Test
    void foundationF8RequiresEveryFoundationCatalogDomain() {
        InitializationManifestDraft draft = foundationDraft(
            InitializationPhase.F8,
            completeCoverage(),
            List.of(item("KNOWGEN-26", "DATA_ELEMENT.PATIENT_ID", List.of(), false)));

        assertThatThrownBy(() -> validator.validate(draft))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("基础发行总装缺少目录域")
            .hasMessageContaining("KNOWGEN-29");
    }

    @Test
    void rejectsDuplicateOrphanAndCyclicDependencies() {
        InitializationManifestDraft duplicate = foundationDraft(
            completeCoverage(),
            List.of(
                item("KNOWGEN-26", "DATA_ELEMENT.A", List.of(), false),
                item("KNOWGEN-27", "DATA_ELEMENT.A", List.of(), false)));
        assertThatThrownBy(() -> validator.validate(duplicate))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("canonical ID 重复");

        InitializationManifestDraft orphan = foundationDraft(
            completeCoverage(),
            List.of(item("KNOWGEN-26", "DATA_ELEMENT.A", List.of("MISSING.B"), false)));
        assertThatThrownBy(() -> validator.validate(orphan))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("孤儿依赖");

        InitializationManifestDraft cycle = foundationDraft(
            completeCoverage(),
            List.of(
                item("KNOWGEN-26", "DATA_ELEMENT.A", List.of("VALUE_SET.B"), false),
                item("KNOWGEN-27", "VALUE_SET.B", List.of("DATA_ELEMENT.A"), false)));
        assertThatThrownBy(() -> validator.validate(cycle))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("循环依赖");
    }

    @Test
    void clinicalReleaseRequiresCompletedCompatibleFoundation() {
        InitializationManifestDraft draft = new InitializationManifestDraft(
            InitializationReleaseType.CLINICAL_CONTENT,
            "1.0.0",
            "1.0.0",
            InitializationPhase.F3,
            "template-v1",
            null,
            "临床内容发行",
            1,
            1,
            Set.of(),
            Set.of(),
            false,
            List.of(item("KNOWGEN-03", "GUIDELINE.HTN", List.of("DATA_ELEMENT.BP"), false)));

        assertThatThrownBy(() -> validator.validate(draft))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("基础发行版未完成");
    }

    @Test
    void validManifestProducesStableThreeLevelHashes() {
        InitializationManifestDraft draft = foundationDraft(
            completeCoverage(),
            List.of(
                item("KNOWGEN-26", "DATA_ELEMENT.BP", List.of(), false),
                item("KNOWGEN-27", "VALUE_SET.BP", List.of("DATA_ELEMENT.BP"), false)));

        InitializationManifestValidation result = validator.validate(draft);

        assertThat(result.sourceManifestHash()).matches("[0-9a-f]{64}");
        assertThat(result.candidateManifestHash()).matches("[0-9a-f]{64}");
        assertThat(result.overallHash()).matches("[0-9a-f]{64}");
        assertThat(result.sourceManifestHash()).isNotEqualTo(result.candidateManifestHash());
    }

    @Test
    void governanceChangeMustChangeFrozenCandidateAndOverallHashes() {
        InitializationManifestDraftItem original =
            item("KNOWGEN-26", "DATA_ELEMENT.BP", List.of(), false);
        InitializationManifestDraftItem changed = new InitializationManifestDraftItem(
            original.catalogCode(),
            original.assetType(),
            original.canonicalId(),
            original.namespace(),
            original.assetVersion(),
            original.sourceVersionId(),
            original.sourceHash(),
            original.candidateRef(),
            original.candidateContentHash(),
            original.riskLevel(),
            original.generatedByModel(),
            original.dependencyCanonicalIds(),
            original.parentCanonicalId(),
            original.unitDimension(),
            original.conversionTargetCanonicalId(),
            original.sourcePolicy(),
            original.reviewPolicy(),
            original.testEvidenceRef(),
            original.ownerRole(),
            original.runtimeConsumers(),
            "rollback:changed",
            original.changeType(),
            original.replacementCanonicalId(),
            original.effectiveTo());

        InitializationManifestValidation first = validator.validate(
            foundationDraft(completeCoverage(), List.of(original)));
        InitializationManifestValidation second = validator.validate(
            foundationDraft(completeCoverage(), List.of(changed)));

        assertThat(second.candidateManifestHash()).isNotEqualTo(first.candidateManifestHash());
        assertThat(second.overallHash()).isNotEqualTo(first.overallHash());
    }

    @Test
    void releaseMetadataChangeMustChangeOnlyTheOverallHash() {
        InitializationManifestDraft original = foundationDraft(
            completeCoverage(),
            List.of(item("KNOWGEN-26", "DATA_ELEMENT.BP", List.of(), false)));
        InitializationManifestDraft changed = new InitializationManifestDraft(
            original.releaseType(),
            original.releaseVersion(),
            original.foundationReleaseVersion(),
            original.phase(),
            "template-v2",
            "model-v2",
            "基础发行元数据更新",
            original.declaredSourceFileCount(),
            original.declaredEntryCount(),
            original.coverage(),
            original.availableCanonicalIds(),
            original.foundationReleaseComplete(),
            original.items());

        InitializationManifestValidation first = validator.validate(original);
        InitializationManifestValidation second = validator.validate(changed);

        assertThat(second.sourceManifestHash()).isEqualTo(first.sourceManifestHash());
        assertThat(second.candidateManifestHash()).isEqualTo(first.candidateManifestHash());
        assertThat(second.overallHash()).isNotEqualTo(first.overallHash());
    }

    @Test
    void replacementFieldsAreAllowedOnlyForDeprecationAndCannotRedirectToSelf() {
        InitializationManifestDraftItem original =
            item("KNOWGEN-26", "DATA_ELEMENT.BP", List.of(), false);
        InitializationManifestDraftItem unexpectedReplacement = copyWithChange(
            original,
            InitializationChangeType.NEW,
            "DATA_ELEMENT.REPLACEMENT",
            Instant.parse("2027-01-01T00:00:00Z"));
        assertThatThrownBy(() -> validator.validate(
            foundationDraft(completeCoverage(), List.of(unexpectedReplacement))))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("仅废止资产可声明 replacement");

        InitializationManifestDraftItem selfRedirect = copyWithChange(
            original,
            InitializationChangeType.DEPRECATION,
            original.canonicalId(),
            Instant.parse("2027-01-01T00:00:00Z"));
        assertThatThrownBy(() -> validator.validate(
            foundationDraft(completeCoverage(), List.of(selfRedirect))))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("不得指向自身");
    }

    private InitializationManifestDraft foundationDraft(
            Set<FoundationCoverageDimension> coverage,
            List<InitializationManifestDraftItem> items) {
        return foundationDraft(InitializationPhase.F1, coverage, items);
    }

    private InitializationManifestDraft foundationDraft(
            InitializationPhase phase,
            Set<FoundationCoverageDimension> coverage,
            List<InitializationManifestDraftItem> items) {
        return new InitializationManifestDraft(
            InitializationReleaseType.FOUNDATION,
            "1.0.0",
            null,
            phase,
            "template-v1",
            null,
            "基础发行测试",
            items.stream().map(InitializationManifestDraftItem::sourceVersionId).distinct().toList().size(),
            items.size(),
            coverage,
            Set.of(),
            true,
            items);
    }

    private Set<FoundationCoverageDimension> completeCoverage() {
        return Set.copyOf(Arrays.asList(FoundationCoverageDimension.values()));
    }

    private InitializationManifestDraftItem item(
            String catalogCode,
            String canonicalId,
            List<String> dependencies,
            boolean generatedByModel) {
        long sourceVersionId = Math.abs(canonicalId.hashCode()) + 1L;
        return new InitializationManifestDraftItem(
            catalogCode,
            VersionedAssetType.KNOWLEDGE,
            canonicalId,
            "urn:medkernel:test",
            "1.0.0",
            sourceVersionId,
            "a".repeat(64),
            "kv:1:" + canonicalId,
            "b".repeat(64),
            KnowledgeRiskLevel.MEDIUM,
            generatedByModel,
            dependencies,
            null,
            null,
            null,
            "APPROVED_SOURCE_ONLY",
            "RISK_TIERED_REVIEW",
            "test:" + canonicalId,
            "engine-operator",
            "knowledge-runtime",
            "rollback:" + canonicalId,
            InitializationChangeType.NEW,
            null,
            null);
    }

    private InitializationManifestDraftItem copyWithChange(
            InitializationManifestDraftItem original,
            InitializationChangeType changeType,
            String replacementCanonicalId,
            Instant effectiveTo) {
        return new InitializationManifestDraftItem(
            original.catalogCode(),
            original.assetType(),
            original.canonicalId(),
            original.namespace(),
            original.assetVersion(),
            original.sourceVersionId(),
            original.sourceHash(),
            original.candidateRef(),
            original.candidateContentHash(),
            original.riskLevel(),
            original.generatedByModel(),
            original.dependencyCanonicalIds(),
            original.parentCanonicalId(),
            original.unitDimension(),
            original.conversionTargetCanonicalId(),
            original.sourcePolicy(),
            original.reviewPolicy(),
            original.testEvidenceRef(),
            original.ownerRole(),
            original.runtimeConsumers(),
            original.rollbackStrategy(),
            changeType,
            replacementCanonicalId,
            effectiveTo);
    }
}
