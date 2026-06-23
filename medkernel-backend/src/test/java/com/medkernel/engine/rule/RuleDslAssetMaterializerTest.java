package com.medkernel.engine.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.versioning.DeclarativeAssetRuntimePort;
import com.medkernel.engine.versioning.ResolvedDeclarativeAsset;
import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 规则运行前的配置资产物化测试。
 */
class RuleDslAssetMaterializerTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void expandsValueSetFromExactRuntimeReleaseAndRemovesAuthoredMemberCopies() throws Exception {
        DeclarativeAssetRuntimePort assets = (tenantId, runtimeReleaseId, assetType, assetIdentity) ->
            Optional.of(new ResolvedDeclarativeAsset(
                assetType,
                assetIdentity,
                "2",
                runtimeReleaseId,
                """
                    {
                      "schemaVersion":"1.0",
                      "name":"抗凝药物",
                      "codeSystem":"ATC",
                      "members":[
                        {"code":"B01AA03","display":"华法林"},
                        {"code":"B01AF02","display":"阿哌沙班"}
                      ]
                    }
                    """,
                "a".repeat(64)
            ));
        RuleDslAssetMaterializer materializer = new RuleDslAssetMaterializer(json, assets);
        JsonNode dsl = json.readTree("""
            {
              "when": {
                "all": [{
                  "field": "medications[].code",
                  "op": "in",
                  "value": {
                    "valueSet": "VS.ANTICOAGULANT",
                    "members": ["OLD_COPY"]
                  }
                }]
              },
              "then": []
            }
            """);

        JsonNode materialized = materializer.materialize(
            "tenant-A", "release-4", dsl);
        JsonNode value = materialized.path("when").path("all").get(0).path("value");

        assertThat(value.path("valueSet").asText()).isEqualTo("VS.ANTICOAGULANT");
        assertThat(value.path("members")).extracting(JsonNode::asText)
            .containsExactly("B01AA03", "B01AF02");
        assertThat(value.path("expandedCount").asInt()).isEqualTo(2);
        assertThat(value.path("resolvedAssetVersion").asText()).isEqualTo("2");
    }

    @Test
    void resolvesFormulaAssetToControlledRuntimeFunction() throws Exception {
        DeclarativeAssetRuntimePort assets = (tenantId, runtimeReleaseId, assetType, assetIdentity) ->
            Optional.of(new ResolvedDeclarativeAsset(
                assetType,
                assetIdentity,
                "3",
                runtimeReleaseId,
                """
                    {
                      "schemaVersion":"1.0",
                      "name":"体质指数",
                      "runtimeFunction":"BMI",
                      "inputs":[{"name":"height","fieldPath":"observations[].valueNumeric"}],
                      "output":{"dataType":"number","unit":"kg/m2"}
                    }
                    """,
                "b".repeat(64)
            ));
        RuleDslAssetMaterializer materializer = new RuleDslAssetMaterializer(json, assets);
        JsonNode dsl = json.readTree("""
            {
              "when": {
                "field": "patient.patientId",
                "op": "derived",
                "value": {
                  "formula": "FORMULA.BMI",
                  "parameters": {}
                }
              },
              "then": []
            }
            """);

        JsonNode value = materializer.materialize("tenant-A", "release-4", dsl)
            .path("when").path("value");

        assertThat(value.path("formula").asText()).isEqualTo("BMI");
        assertThat(value.path("formulaAsset").asText()).isEqualTo("FORMULA.BMI");
        assertThat(value.path("resolvedAssetVersion").asText()).isEqualTo("3");
    }

    @Test
    void expandsActionCardReferenceToExecutableRuleActionFromRuntimeRelease() throws Exception {
        DeclarativeAssetRuntimePort assets = (tenantId, runtimeReleaseId, assetType, assetIdentity) -> {
            assertThat(assetType).isEqualTo(VersionedAssetType.ACTION_CARD);
            assertThat(assetIdentity).isEqualTo("ACTION.CKD.DOSE_REVIEW");
            return Optional.of(new ResolvedDeclarativeAsset(
                assetType,
                assetIdentity,
                "V4",
                runtimeReleaseId,
                """
                    {
                      "schemaVersion": "1.0",
                      "title": "肾功能下降用药复核",
                      "actionCode": "SUGGEST_ORDER",
                      "atSeverity": "HIGH",
                      "indicator": "critical",
                      "summary": "肾功能下降，需复核药物剂量",
                      "detail": "根据当前检验结果和用药清单，建议医师复核潜在肾毒性药物剂量。",
                      "source": {"label": "CKD 用药安全指南", "evidenceLevel": "GUIDELINE"},
                      "suggestions": [{
                        "label": "打开剂量复核医嘱建议",
                        "actionType": "SUGGEST_ORDER",
                        "payload": {"orderSetRef": "ORDER.CKD.DOSE_REVIEW"}
                      }],
                      "overrideReasons": ["临床获益大于风险", "已调整监测频率"],
                      "requiresPhysicianConfirmation": true
                    }
                    """,
                "c".repeat(64)
            ));
        };
        RuleDslAssetMaterializer materializer = new RuleDslAssetMaterializer(json, assets);
        JsonNode dsl = json.readTree("""
            {
              "when": {"field": "patient.age", "op": "present"},
              "then": [{"actionCardRef": "ACTION.CKD.DOSE_REVIEW"}]
            }
            """);

        JsonNode action = materializer.materialize("tenant-A", "release-4", dsl)
            .path("then").get(0);

        assertThat(action.path("actionCardRef").asText()).isEqualTo("ACTION.CKD.DOSE_REVIEW");
        assertThat(action.path("resolvedActionCardVersion").asText()).isEqualTo("V4");
        assertThat(action.path("actionCode").asText()).isEqualTo("SUGGEST_ORDER");
        assertThat(action.path("atSeverity").asText()).isEqualTo("HIGH");
        assertThat(action.path("requiresPhysicianConfirmation").asBoolean()).isTrue();
        assertThat(action.path("suggestions").get(0).path("payload").path("orderSetRef").asText())
            .isEqualTo("ORDER.CKD.DOSE_REVIEW");
    }

    @Test
    void rejectsMissingAssetsAndManualRuntimeVersion() throws Exception {
        RuleDslAssetMaterializer materializer = new RuleDslAssetMaterializer(
            json,
            (tenantId, runtimeReleaseId, assetType, assetIdentity) -> Optional.empty()
        );
        JsonNode missing = json.readTree("""
            {
              "when": {
                "field": "conditions[].code",
                "op": "in",
                "value": {"valueSet": "VS.MISSING"}
              },
              "then": []
            }
            """);
        assertThatThrownBy(() -> materializer.materialize("tenant-A", "release-4", missing))
            .hasMessageContaining("未解析到值集");

        JsonNode legacyManualVersion = json.readTree("""
            {
              "when": {
                "field": "conditions[].code",
                "op": "in",
                "value": {"valueSet": "VS.TEST", "packageVersion": "2026.08"}
              },
              "then": []
            }
            """);
        assertThatThrownBy(() -> materializer.materialize(
            "tenant-A", "release-4", legacyManualVersion))
            .hasMessageContaining("不得手工携带运行版本");
    }
}
