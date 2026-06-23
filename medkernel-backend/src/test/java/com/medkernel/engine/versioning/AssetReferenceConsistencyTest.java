package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ContextFieldCatalogAssets;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import org.junit.jupiter.api.Test;

class AssetReferenceConsistencyTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void recognizesStableRuleReferenceWithoutEmbeddingManualRuntimeVersion() throws Exception {
        JsonNode condition = json.readTree("""
            {
              "ruleRef": "RULE.CKD.HIGH_RISK",
              "ruleAssetId": "rule-high-risk"
            }
            """);

        AssetReferenceConsistency.requireStableAssetReferences(
            condition, ErrorCode.ENG_PATHWAY_004, "路径边 E-HIGH");

        assertThat(AssetReferenceConsistency.referenceSummaries(condition))
            .containsExactly("RULE:RULE.CKD.HIGH_RISK");
        assertThat(AssetReferenceConsistency.ruleReferences(condition))
            .containsExactly(new AssetReferenceConsistency.RuleReference(
                "RULE.CKD.HIGH_RISK", "rule-high-risk", "$"));
    }

    @Test
    void rejectsEmbeddedRuntimeSelectorsBecauseRuntimeReleaseOwnsExactVersions() throws Exception {
        JsonNode condition = json.readTree("""
            {
              "ruleRef": "RULE.CKD.HIGH_RISK",
              "ruleAssetId": "rule-high-risk",
              "packageCode": "PKG.CKD",
              "packageVersion": "2026.07"
            }
            """);

        assertThatThrownBy(() -> AssetReferenceConsistency.requireStableAssetReferences(
            condition, ErrorCode.ENG_PATHWAY_004, "路径边 E-HIGH"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("不得手工携带运行定位字段")
            .hasMessageContaining("RULE.CKD.HIGH_RISK");
    }

    @Test
    void rejectsRuleReferenceWithoutDeclaredAssetIdentity() throws Exception {
        JsonNode condition = json.readTree("""
            {
              "ruleRef": "RULE.CKD.HIGH_RISK"
            }
            """);

        assertThatThrownBy(() -> AssetReferenceConsistency.requireStableAssetReferences(
            condition, ErrorCode.ENG_PATHWAY_004, "路径边 E-HIGH"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("ruleAssetId");
    }

    @Test
    void extractsTypedRuntimeAssetReferencesFromNestedDefinitions() throws Exception {
        JsonNode definition = json.readTree("""
            {
              "when": {
                "all": [
                  {"field": "observations[].code", "operator": "in", "valueSet": "VS.LAB.CREATININE"},
                  {"formula": "FORMULA.EGFR", "operator": "lt", "value": 60}
                ]
              },
              "then": [{"actionCardRef": "CARD.RENAL.REVIEW"}],
              "nodes": [{"orderSetRef": "ORDER.RENAL.CHECK"}]
            }
            """);

        assertThat(AssetReferenceConsistency.assetReferences(definition))
            .containsExactlyInAnyOrder(
                new AssetReferenceConsistency.AssetReference(
                    VersionedAssetType.FIELD_CATALOG,
                    ContextFieldCatalogAssets.CLINICAL_CONTEXT_IDENTITY,
                    null,
                    "$.when.all[0]"),
                new AssetReferenceConsistency.AssetReference(
                    VersionedAssetType.VALUE_SET, "VS.LAB.CREATININE", null, "$.when.all[0]"),
                new AssetReferenceConsistency.AssetReference(
                    VersionedAssetType.FORMULA, "FORMULA.EGFR", null, "$.when.all[1]"),
                new AssetReferenceConsistency.AssetReference(
                    VersionedAssetType.ACTION_CARD, "CARD.RENAL.REVIEW", null, "$.then[0]"),
                new AssetReferenceConsistency.AssetReference(
                    VersionedAssetType.ORDER_SET, "ORDER.RENAL.CHECK", null, "$.nodes[0]")
            );
    }

    @Test
    void convertsTypedReferencesIntoDistinctStableDependencyDeclarations() throws Exception {
        JsonNode definition = json.readTree("""
            {
              "when": {"all": [
                {"valueSet": "VS.LAB.CREATININE"},
                {"valueSet": "VS.LAB.CREATININE"}
              ]},
              "edge": {
                "ruleRef": "RULE.CKD.HIGH_RISK",
                "ruleAssetId": "rule-high-risk"
              }
            }
            """);

        assertThat(AssetReferenceConsistency.dependencyDeclarations(definition))
            .containsExactlyInAnyOrder(
                new AssetDependencyDeclaration(
                    VersionedAssetType.VALUE_SET,
                    "VS.LAB.CREATININE",
                    null,
                    null,
                    AssetDependencyKind.RUNTIME_ASSET),
                new AssetDependencyDeclaration(
                    VersionedAssetType.RULE,
                    "RULE.CKD.HIGH_RISK",
                    null,
                    null,
                    AssetDependencyKind.RULE)
            );
    }

    @Test
    void convertsFieldReferencesIntoTheUnifiedFieldCatalogDependency() throws Exception {
        JsonNode definition = json.readTree("""
            {
              "when": {"all": [
                {"field": "observations[].code", "operator": "equals", "value": "718-7"},
                {"fact": "context.conditions[].code", "operator": "in", "value": ["I10"]}
              ]}
            }
            """);

        assertThat(AssetReferenceConsistency.dependencyDeclarations(definition))
            .containsExactly(new AssetDependencyDeclaration(
                VersionedAssetType.FIELD_CATALOG,
                ContextFieldCatalogAssets.CLINICAL_CONTEXT_IDENTITY,
                null,
                null,
                AssetDependencyKind.FIELD));
    }
}
