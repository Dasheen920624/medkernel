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
    void rejectsMissingRuntimeRevisionIdWithCurrentTerminology() throws Exception {
        RuleDslAssetMaterializer materializer = new RuleDslAssetMaterializer(
            json,
            (tenantId, runtimeReleaseId, assetType, assetIdentity) -> Optional.empty()
        );
        JsonNode dsl = json.readTree("""
            {
              "when": {"field": "patient.age", "op": "present"},
              "then": []
            }
            """);

        assertThatThrownBy(() -> materializer.materialize("tenant-A", " ", dsl))
            .hasMessageContaining("机构生效版本 ID");
    }

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
    void recordsPathwayReferenceAsRuntimeEvidenceWithoutCopyingPathwayBody() throws Exception {
        DeclarativeAssetRuntimePort assets = (tenantId, runtimeReleaseId, assetType, assetIdentity) -> {
            assertThat(assetType).isEqualTo(VersionedAssetType.PATHWAY);
            assertThat(assetIdentity).isEqualTo("PATHWAY.CRITICAL.EMERGENCY.ICU.ESCALATION");
            return Optional.of(new ResolvedDeclarativeAsset(
                assetType,
                assetIdentity,
                "V7",
                runtimeReleaseId,
                """
                    {
                      "schemaVersion": "1.0",
                      "pathwayCode": "PATHWAY.CRITICAL.EMERGENCY.ICU.ESCALATION",
                      "name": "急危重症升级路径",
                      "entryMode": "MANUAL_CONFIRM",
                      "nodes": [
                        {"nodeCode": "TRIAGE", "nodeType": "MANUAL_GATE"},
                        {"nodeCode": "ICU_REVIEW", "nodeType": "MANUAL_GATE"}
                      ]
                    }
                    """,
                "d".repeat(64)
            ));
        };
        RuleDslAssetMaterializer materializer = new RuleDslAssetMaterializer(json, assets);
        JsonNode dsl = json.readTree("""
            {
              "when": {"fact": "extensions.local.emergencyTriage.triageLevel", "operator": "equals", "value": "LEVEL_1"},
              "then": [{
                "pathwayRef": "PATHWAY.CRITICAL.EMERGENCY.ICU.ESCALATION",
                "actionCode": "STRONG_REMINDER"
              }]
            }
            """);

        JsonNode materialized = materializer.materialize("tenant-A", "release-critical", dsl);
        JsonNode action = materialized.path("then").get(0);
        JsonNode evidence = materialized.path("runtimeAssetEvidence");

        assertThat(action.path("pathwayRef").asText()).isEqualTo(
            "PATHWAY.CRITICAL.EMERGENCY.ICU.ESCALATION");
        assertThat(action.path("resolvedPathwayVersion").asText()).isEqualTo("V7");
        assertThat(action.path("resolvedPathwayHash").asText()).isEqualTo("d".repeat(64));
        assertThat(action.has("nodes")).isFalse();
        assertThat(evidence).hasSize(1);
        assertThat(evidence.get(0).path("assetType").asText()).isEqualTo("PATHWAY");
        assertThat(evidence.get(0).path("assetIdentity").asText()).isEqualTo(
            "PATHWAY.CRITICAL.EMERGENCY.ICU.ESCALATION");
        assertThat(evidence.get(0).path("assetVersion").asText()).isEqualTo("V7");
        assertThat(evidence.get(0).path("runtimeReleaseId").asText()).isEqualTo("release-critical");
        assertThat(evidence.get(0).path("contentHash").asText()).isEqualTo("d".repeat(64));
    }

    @Test
    void appendsRuntimeAssetEvidenceAndOverridesAuthoredRuntimeEvidence() throws Exception {
        DeclarativeAssetRuntimePort assets = (tenantId, runtimeReleaseId, assetType, assetIdentity) -> {
            if (assetType == VersionedAssetType.VALUE_SET) {
                return Optional.of(new ResolvedDeclarativeAsset(
                    assetType,
                    assetIdentity,
                    "V2",
                    runtimeReleaseId,
                    """
                        {
                          "schemaVersion":"1.0",
                          "name":"氨基糖苷类",
                          "codeSystem":"ATC",
                          "members":[{"code":"J01GB03","display":"庆大霉素"}]
                        }
                        """,
                    "a".repeat(64)
                ));
            }
            if (assetType == VersionedAssetType.FORMULA) {
                return Optional.of(new ResolvedDeclarativeAsset(
                    assetType,
                    assetIdentity,
                    "V3",
                    runtimeReleaseId,
                    """
                        {
                          "schemaVersion":"1.0",
                          "name":"BMI",
                          "runtimeFunction":"BMI",
                          "inputs":[
                            {"name":"height","fieldPath":"patient.heightCm","unit":"cm"},
                            {"name":"weight","fieldPath":"patient.weightKg","unit":"kg"}
                          ],
                          "output":{"dataType":"number","unit":"kg/m2"}
                        }
                        """,
                    "b".repeat(64)
                ));
            }
            if (assetType == VersionedAssetType.ACTION_CARD) {
                return Optional.of(new ResolvedDeclarativeAsset(
                    assetType,
                    assetIdentity,
                    "V4",
                    runtimeReleaseId,
                    """
                        {
                          "schemaVersion": "1.0",
                          "title": "用药复核",
                          "actionCode": "STRONG_REMINDER",
                          "atSeverity": "HIGH",
                          "indicator": "critical",
                          "summary": "需人工复核",
                          "detail": "当前规则命中运行时临床提示卡。",
                          "source": {"label": "本地上线演练"},
                          "suggestions": [{"label": "确认已复核", "actionType": "ACKNOWLEDGE"}],
                          "overrideReasons": ["医师判断继续执行"],
                          "requiresPhysicianConfirmation": true
                        }
                        """,
                    "c".repeat(64)
                ));
            }
            return Optional.empty();
        };
        RuleDslAssetMaterializer materializer = new RuleDslAssetMaterializer(json, assets);
        JsonNode dsl = json.readTree("""
            {
              "runtimeAssetEvidence": [{"assetType": "VALUE_SET", "assetIdentity": "FORGED"}],
              "when": {
                "all": [
                  {
                    "fact": "medications[].code",
                    "operator": "in",
                    "value": {"valueSet": "VALUE_SET.CDSS.RUNTIME"}
                  },
                  {
                    "fact": "patient.patientId",
                    "operator": "derived",
                    "value": {"formula": "FORMULA.CDSS.RUNTIME", "parameters": {}}
                  }
                ]
              },
              "then": [{"actionCardRef": "ACTION_CARD.CDSS.RUNTIME"}]
            }
            """);

        JsonNode evidence = materializer.materialize("tenant-A", "release-12", dsl)
            .path("runtimeAssetEvidence");

        assertThat(evidence).hasSize(3);
        assertThat(evidence.get(0).path("assetType").asText()).isEqualTo("VALUE_SET");
        assertThat(evidence.get(0).path("assetIdentity").asText()).isEqualTo("VALUE_SET.CDSS.RUNTIME");
        assertThat(evidence.get(0).path("assetVersion").asText()).isEqualTo("V2");
        assertThat(evidence.get(0).path("contentHash").asText()).isEqualTo("a".repeat(64));
        assertThat(evidence.get(0).path("expandedCount").asInt()).isEqualTo(1);
        assertThat(evidence.get(1).path("assetType").asText()).isEqualTo("FORMULA");
        assertThat(evidence.get(1).path("assetIdentity").asText()).isEqualTo("FORMULA.CDSS.RUNTIME");
        assertThat(evidence.get(1).path("runtimeFunction").asText()).isEqualTo("BMI");
        assertThat(evidence.get(2).path("assetType").asText()).isEqualTo("ACTION_CARD");
        assertThat(evidence.get(2).path("actionCardRef").asText()).isEqualTo(
            "ACTION_CARD.CDSS.RUNTIME");
        assertThat(evidence.get(2).path("contentHash").asText()).isEqualTo("c".repeat(64));
        assertThat(evidence.toString()).doesNotContain("FORGED");
    }

    @Test
    void materializedDeclarativeAssetsHitFrontdeskContextMedicationAndBmi() throws Exception {
        DeclarativeAssetRuntimePort assets = (tenantId, runtimeReleaseId, assetType, assetIdentity) -> {
            if (assetType == VersionedAssetType.VALUE_SET) {
                return Optional.of(new ResolvedDeclarativeAsset(
                    assetType,
                    assetIdentity,
                    "V1",
                    runtimeReleaseId,
                    """
                        {
                          "schemaVersion":"1.0",
                          "name":"氨基糖苷类",
                          "codeSystem":"ATC",
                          "members":[{"code":"J01GB03","display":"庆大霉素"}]
                        }
                        """,
                    "a".repeat(64)
                ));
            }
            if (assetType == VersionedAssetType.FORMULA) {
                return Optional.of(new ResolvedDeclarativeAsset(
                    assetType,
                    assetIdentity,
                    "V1",
                    runtimeReleaseId,
                    """
                        {
                          "schemaVersion":"1.0",
                          "name":"BMI",
                          "runtimeFunction":"BMI",
                          "inputs":[
                            {"name":"heightCm","fieldPath":"extensions.local.frontdeskContext.heightCm","unit":"cm"},
                            {"name":"weightKg","fieldPath":"extensions.local.frontdeskContext.weightKg","unit":"kg"}
                          ],
                          "output":{"dataType":"number","unit":"kg/m2"}
                        }
                        """,
                    "b".repeat(64)
                ));
            }
            if (assetType == VersionedAssetType.ACTION_CARD) {
                return Optional.of(new ResolvedDeclarativeAsset(
                    assetType,
                    assetIdentity,
                    "V1",
                    runtimeReleaseId,
                    """
                        {
                          "schemaVersion": "1.0",
                          "title": "用药复核",
                          "actionCode": "STRONG_REMINDER",
                          "atSeverity": "HIGH",
                          "indicator": "critical",
                          "summary": "需人工复核",
                          "detail": "当前规则命中运行时临床提示卡。",
                          "source": {"label": "本地上线演练"},
                          "suggestions": [{"label": "确认已复核", "actionType": "ACKNOWLEDGE"}],
                          "overrideReasons": ["医师判断继续执行"],
                          "requiresPhysicianConfirmation": true
                        }
                        """,
                    "c".repeat(64)
                ));
            }
            return Optional.empty();
        };
        RuleDslAssetMaterializer materializer = new RuleDslAssetMaterializer(json, assets);
        RuleDslEvaluator evaluator = new RuleDslEvaluator(
            json,
            new ConditionEvaluator(json),
            materializer);
        JsonNode dsl = json.readTree("""
            {
              "when": {
                "all": [
                  {
                    "fact": "medications[].code",
                    "operator": "in",
                    "value": {"valueSet": "VALUE_SET.CDSS.RUNTIME"}
                  },
                  {"fact": "patient.age", "operator": "gte", "value": 18},
                  {
                    "fact": "patient.age",
                    "operator": "derived",
                    "value": {
                      "formula": "FORMULA.CDSS.RUNTIME",
                      "parameters": {
                        "heightCm": "extensions.local.frontdeskContext.heightCm",
                        "weightKg": "extensions.local.frontdeskContext.weightKg"
                      },
                      "comparison": "gte",
                      "value": 20,
                      "unit": "kg/m2"
                    }
                  }
                ]
              },
              "then": [{"actionCardRef": "ACTION_CARD.CDSS.RUNTIME"}]
            }
            """);
        JsonNode context = json.readTree("""
            {
              "patient": {"age": 64},
              "encounters": [{"encounterType": "OUTPATIENT"}],
              "medications": [{"code": "J01GB03", "prescriptionStatus": "ACTIVE"}],
              "extensions": {
                "local": {
                  "frontdeskContext": {
                    "heightCm": 170,
                    "weightKg": 82
                  }
                }
              }
            }
            """);

        RuleDslEvaluation evaluation = evaluator.evaluate(dsl, context, "tenant-A", "runtime-s5");

        assertThat(evaluation.hit()).isTrue();
        assertThat(evaluation.actions()).singleElement()
            .satisfies(action -> assertThat(action.actionCode())
                .isEqualTo(RuleActionCode.STRONG_REMINDER));
        assertThat(evaluation.explanation().path("runtimeAssetEvidence")).hasSize(3);
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
            .hasMessageContaining("当前机构生效版本未解析到值集");

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
            .hasMessageContaining("由机构生效版本统一锁定版本");
    }
}
