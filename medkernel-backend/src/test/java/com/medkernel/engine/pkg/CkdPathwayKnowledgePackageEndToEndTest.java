package com.medkernel.engine.pkg;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.pathway.PathwayAdvanceEventType;
import com.medkernel.engine.pathway.PathwayEdge;
import com.medkernel.engine.pathway.PathwayEdgeType;
import com.medkernel.engine.pathway.PathwayGraph;
import com.medkernel.engine.pathway.PathwayNode;
import com.medkernel.engine.pathway.PathwayNodeType;
import com.medkernel.engine.pathway.PathwayOutcomeBinding;
import com.medkernel.engine.pathway.PathwayOutcomeScope;
import com.medkernel.engine.pathway.PathwayProgressCommand;
import com.medkernel.engine.pathway.PathwayProgressDecision;
import com.medkernel.engine.pathway.PathwayProgressor;
import com.medkernel.engine.pathway.PatientPathwayStatus;
import com.medkernel.engine.rule.ConditionEvaluator;
import com.medkernel.engine.rule.RuleActionCode;
import com.medkernel.engine.rule.RuleDslEvaluation;
import com.medkernel.engine.rule.RuleDslEvaluator;
import com.medkernel.engine.versioning.SourceTier;
import com.medkernel.engine.versioning.VersionedAssetType;
import org.junit.jupiter.api.Test;

class CkdPathwayKnowledgePackageEndToEndTest {

    private final ObjectMapper json = new ObjectMapper();
    private final RuleDslEvaluator rules = new RuleDslEvaluator(json, new ConditionEvaluator(json));
    private final PathwayProgressor pathways = new PathwayProgressor(json, new ConditionEvaluator(json));

    @Test
    void ckdPathwayKnowledgePackageCoversEntryStagingOrderBlockVarianceAndOutcomeLoop() throws Exception {
        EffectivePackageSnapshot packageSnapshot = ckdPackageSnapshot();

        assertThat(packageSnapshot.items())
            .extracting(EffectivePackageItem::assetType)
            .contains(
                VersionedAssetType.PATHWAY,
                VersionedAssetType.RULE,
                VersionedAssetType.VALUE_SET,
                VersionedAssetType.FIELD_CATALOG,
                VersionedAssetType.FORMULA,
                VersionedAssetType.CONDITION_FRAGMENT,
                VersionedAssetType.EVALUATION);
        assertThat(packageSnapshot.items()).anySatisfy(item -> {
            assertThat(item.assetType()).isEqualTo(VersionedAssetType.RULE);
            assertThat(item.assetId()).isEqualTo("RULE.CKD.NEPHROTOXIC");
            assertThat(item.declaredVersion()).isEqualTo("1");
            assertThat(item.effectiveVersion()).isEqualTo("2");
            assertThat(item.overridden()).isTrue();
        });

        JsonNode context = json.readTree("""
            {
              "diagnosis": {"code": "N18.4"},
              "ckd": {"stage": "G4"},
              "patient": {"age": 70, "sex": "MALE"},
              "labs": {"creatinine": {"value": 3.0, "unit": "mg/dL", "source": "LIS:CREA-1"}},
              "medication": {"atcCode": "J01GB03"}
            }
            """);

        RuleDslEvaluation staging = rules.evaluate(ckdStagingRule(), context);
        assertThat(staging.hit()).isTrue();
        assertThat(staging.explanation().path("conditionEvidence").get(0).path("formula").asText())
            .contains("CKD_EPI_2021_EGFR");

        RuleDslEvaluation orderBlock = rules.evaluate(nephrotoxicOrderBlockRule(), context);
        assertThat(orderBlock.hit()).isTrue();
        assertThat(orderBlock.actions()).singleElement().satisfies(action -> {
            assertThat(action.actionCode()).isEqualTo(RuleActionCode.BLOCK);
            assertThat(action.requiresPhysicianConfirmation()).isTrue();
            assertThat(action.summary()).contains("肾毒性");
        });

        Map<String, Object> facts = json.convertValue(context, Map.class);
        PathwayProgressDecision entry = pathways.advance(new PathwayProgressCommand(
            ckdPathwayGraph(), "ENTRY", PathwayAdvanceEventType.COMPLETE, null, facts));
        assertThat(entry.nextNodeCode()).isEqualTo("STAGE");
        assertThat(entry.status()).isEqualTo(PatientPathwayStatus.NODE_EXECUTING);

        PathwayProgressDecision staged = pathways.advance(new PathwayProgressCommand(
            ckdPathwayGraph(), "STAGE", PathwayAdvanceEventType.COMPLETE, null, facts));
        assertThat(staged.nextNodeCode()).isEqualTo("ORDER_REVIEW");

        PathwayProgressDecision variance = pathways.advance(new PathwayProgressCommand(
            ckdPathwayGraph(), "ORDER_REVIEW", PathwayAdvanceEventType.VARIANCE, "VARIANCE", facts));
        assertThat(variance.nextNodeCode()).isEqualTo("VARIANCE");
        assertThat(variance.edgeCode()).isEqualTo("E-ORDER-VARIANCE");

        PathwayProgressDecision outcome = pathways.advance(new PathwayProgressCommand(
            ckdPathwayGraph(), "VARIANCE", PathwayAdvanceEventType.COMPLETE, null, facts));
        assertThat(outcome.nextNodeCode()).isEqualTo("OUTCOME");

        PathwayOutcomeBinding outcomeBinding = ckdOutcomeBinding();
        assertThat(outcomeBinding.scope()).isEqualTo(PathwayOutcomeScope.TEMPLATE);
        assertThat(outcomeBinding.indicatorCode()).isEqualTo("EVAL.CKD.OUTCOME");
        assertThat(outcomeBinding.packageVersion()).isEqualTo(packageSnapshot.packageVersion());
    }

    private EffectivePackageSnapshot ckdPackageSnapshot() {
        return EffectivePackageSnapshot.from(new EffectiveKnowledgePackageResponse(
            "tenant-A",
            "dept-neph",
            "pkg-ckd",
            "PKG.CKD",
            "2026.06",
            List.of(
                effectiveItem(VersionedAssetType.PATHWAY, "PATH.CKD", "1", "1", false),
                effectiveItem(VersionedAssetType.RULE, "RULE.CKD.NEPHROTOXIC", "1", "2", true),
                effectiveItem(VersionedAssetType.VALUE_SET, "VS.ATC.NEPHROTOXIC", "2026.06", "2026.06", false),
                effectiveItem(VersionedAssetType.VALUE_SET, "VS.LOINC.CREATININE", "2026.06", "2026.06", false),
                effectiveItem(VersionedAssetType.FIELD_CATALOG, "FIELD.CKD.BINDING", "2026.06", "2026.06", false),
                effectiveItem(VersionedAssetType.FORMULA, "CKD_EPI_2021_EGFR", "2026.06", "2026.06", false),
                effectiveItem(VersionedAssetType.CONDITION_FRAGMENT, "FRAG.RENAL_LIMITED", "1", "1", false),
                effectiveItem(VersionedAssetType.EVALUATION, "EVAL.CKD.OUTCOME", "1", "1", false)
            ),
            List.of(),
            List.of()));
    }

    private EffectivePackageItem effectiveItem(
            VersionedAssetType type,
            String assetId,
            String declaredVersion,
            String effectiveVersion,
            boolean overridden) {
        return new EffectivePackageItem(
            type,
            assetId,
            declaredVersion,
            effectiveVersion,
            overridden ? "tenant-A" : "t-1",
            overridden ? "/TENANT-A/HOSP-A/NEPH" : "/PLATFORM/CKD",
            overridden ? SourceTier.ORG : SourceTier.PLATFORM,
            !overridden,
            overridden,
            true,
            "av-" + assetId,
            "a".repeat(64)
        );
    }

    private JsonNode ckdStagingRule() throws Exception {
        return json.readTree("""
            {
              "trigger": "DIAGNOSIS",
              "when": {
                "fact": "derived.egfr",
                "operator": "derived",
                "value": {
                  "formula": "CKD_EPI_2021_EGFR",
                  "comparison": "lt",
                  "value": 30,
                  "unit": "mL/min/1.73m2",
                  "parameters": {
                    "creatinine": "labs.creatinine",
                    "age": "patient.age",
                    "sex": "patient.sex"
                  }
                }
              },
              "then": [{"actionCode": "REMIND", "atSeverity": "HIGH", "indicator": "warning", "summary": "CKD G4 分期", "detail": "eGFR < 30，进入 CKD G4 管理", "source": {"label": "CKD 专病包"}, "suggestions": [], "overrideReasons": []}],
              "explain": {"title": "CKD 分期"}
            }
            """);
    }

    private JsonNode nephrotoxicOrderBlockRule() throws Exception {
        return json.readTree("""
            {
              "trigger": "order-sign",
              "when": {
                "all": [
                  {
                    "fact": "derived.egfr",
                    "operator": "derived",
                    "value": {
                      "formula": "CKD_EPI_2021_EGFR",
                      "comparison": "lt",
                      "value": 30,
                      "unit": "mL/min/1.73m2",
                      "parameters": {
                        "creatinine": "labs.creatinine",
                        "age": "patient.age",
                        "sex": "patient.sex"
                      }
                    }
                  },
                  {
                    "fact": "medication.atcCode",
                    "operator": "in",
                    "value": {
                      "valueSet": "VS.ATC.NEPHROTOXIC",
                      "expandedCount": 2,
                      "members": ["J01GB03", "M01AB01"]
                    }
                  }
                ]
              },
              "then": [{"actionCode": "BLOCK", "atSeverity": "CRITICAL", "indicator": "critical", "summary": "肾毒性用药阻断", "detail": "eGFR < 30 且医嘱命中肾毒性 ATC 值集", "source": {"label": "CKD 专病包"}, "suggestions": [], "overrideReasons": ["完成肾内科会诊后继续"]}],
              "explain": {"title": "CKD 肾毒性用药"}
            }
            """);
    }

    private PathwayGraph ckdPathwayGraph() {
        return new PathwayGraph(
            List.of(
                node("ENTRY", "CKD 入径", PathwayNodeType.ASSESSMENT, false),
                node("STAGE", "eGFR 分期", PathwayNodeType.DECISION, false),
                node("ORDER_REVIEW", "肾毒性医嘱复核", PathwayNodeType.ORDER_SET, false),
                node("VARIANCE", "变异登记", PathwayNodeType.ASSESSMENT, false),
                node("OUTCOME", "结局指标闭环", PathwayNodeType.QUALITY, true)
            ),
            List.of(
                edge("E-ENTRY-STAGE", "ENTRY", "STAGE", PathwayEdgeType.CONDITION,
                    "{\"fact\":\"diagnosis.code\",\"operator\":\"equals\",\"value\":\"N18.4\"}", 1),
                edge("E-STAGE-ORDER", "STAGE", "ORDER_REVIEW", PathwayEdgeType.CONDITION,
                    "{\"fact\":\"ckd.stage\",\"operator\":\"equals\",\"value\":\"G4\"}", 1),
                edge("E-ORDER-VARIANCE", "ORDER_REVIEW", "VARIANCE", PathwayEdgeType.PHYSICIAN_DECISION,
                    null, 1),
                edge("E-VARIANCE-OUTCOME", "VARIANCE", "OUTCOME", PathwayEdgeType.DEFAULT,
                    null, 1)
            )
        );
    }

    private PathwayNode node(String code, String name, PathwayNodeType type, boolean terminal) {
        Instant now = Instant.parse("2026-06-08T00:00:00Z");
        String config = type == PathwayNodeType.ORDER_SET ? "{\"orderSetRef\":\"ORDER.CKD.RENAL_DOSE\"}" : "{}";
        return new PathwayNode(
            null, "node-" + code, "tenant-A", "PATH.CKD", code, name, type,
            "CKD", 1, "NEPHROLOGIST", null, null, terminal, config,
            now, "tester", now, "tester", "trace-ckd"
        );
    }

    private PathwayEdge edge(
            String code,
            String from,
            String to,
            PathwayEdgeType type,
            String conditionJson,
            int priority) {
        Instant now = Instant.parse("2026-06-08T00:00:00Z");
        return new PathwayEdge(
            null, "edge-" + code, "tenant-A", "PATH.CKD", code, from, to, type,
            conditionJson, priority, now, "tester", now, "tester", "trace-ckd"
        );
    }

    private PathwayOutcomeBinding ckdOutcomeBinding() {
        Instant now = Instant.parse("2026-06-08T00:00:00Z");
        return new PathwayOutcomeBinding(
            null, "outcome-ckd", "tenant-A", "PATH.CKD", PathwayOutcomeScope.TEMPLATE,
            "PATH.CKD", "EVAL.CKD.OUTCOME", "2026.06", now, "tester", now, "tester", "trace-ckd"
        );
    }
}
