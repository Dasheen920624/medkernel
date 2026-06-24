package com.medkernel.engine.pathway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.authoring.AuthoringFeatureGate;
import com.medkernel.engine.rule.ConditionEvaluator;
import com.medkernel.engine.versioning.ResolvedDeclarativeAsset;
import com.medkernel.engine.versioning.VersionedAssetType;
import org.junit.jupiter.api.Test;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

class PathwayProgressorTest {

    private final PathwayProgressor progressor = new PathwayProgressor();

    @Test
    void followsDefaultEdgeWhenNodeCompletes() {
        PathwayProgressDecision decision = progressor.advance(new PathwayProgressCommand(
            graph(), "ASSESS", PathwayAdvanceEventType.COMPLETE, null));

        assertThat(decision.previousNodeCode()).isEqualTo("ASSESS");
        assertThat(decision.nextNodeCode()).isEqualTo("LAB");
        assertThat(decision.status()).isEqualTo(PatientPathwayStatus.NODE_EXECUTING);
    }

    @Test
    void respectsExplicitTargetNodeWhenItIsReachable() {
        PathwayProgressDecision decision = progressor.advance(new PathwayProgressCommand(
            graph(), "ASSESS", PathwayAdvanceEventType.COMPLETE, "SURGERY"));

        assertThat(decision.nextNodeCode()).isEqualTo("SURGERY");
        assertThat(decision.edgeType()).isEqualTo(PathwayEdgeType.CONDITION);
    }

    @Test
    void skipsUnmatchedConditionEdgeAndFallsBackToDefaultEdge() {
        PathwayProgressDecision decision = progressor.advance(new PathwayProgressCommand(
            conditionGraph(), "REVIEW", PathwayAdvanceEventType.COMPLETE, null,
            Map.of("context.readyForFollowup", false)));

        assertThat(decision.previousNodeCode()).isEqualTo("REVIEW");
        assertThat(decision.nextNodeCode()).isEqualTo("PLAN");
        assertThat(decision.edgeType()).isEqualTo(PathwayEdgeType.DEFAULT);
        assertThat(decision.evidence()).containsEntry("context.readyForFollowup", false);
    }

    @Test
    void recordsMissingConditionFactAndFallsBackToDefaultEdge() {
        PathwayProgressDecision decision = progressor.advance(new PathwayProgressCommand(
            conditionGraph(), "REVIEW", PathwayAdvanceEventType.COMPLETE, null,
            Map.of()));

        assertThat(decision.nextNodeCode()).isEqualTo("PLAN");
        assertThat(decision.evidence()).containsKey("context.readyForFollowup");
        assertThat(decision.evidence().get("context.readyForFollowup")).isNull();
    }

    @Test
    void prefersMatchedConditionEdgeOverDefaultFallback() {
        String tenantId = "tenant-A";
        String templateId = "pt-" + tenantId;
        PathwayGraph graph = new PathwayGraph(
            List.of(
                node("REVIEW", 10, false),
                node("PLAN", 20, false),
                node("FOLLOWUP", 30, true)
            ),
            List.of(
                edge("e-default", tenantId, templateId, "REVIEW", "PLAN",
                    PathwayEdgeType.DEFAULT, 1),
                edge("e-condition", tenantId, templateId, "REVIEW", "FOLLOWUP",
                    PathwayEdgeType.CONDITION, 2,
                    "{\"fact\":\"context.readyForFollowup\",\"operator\":\"equals\",\"value\":true}")
            )
        );

        PathwayProgressDecision decision = progressor.advance(new PathwayProgressCommand(
            graph, "REVIEW", PathwayAdvanceEventType.COMPLETE, null,
            Map.of("context.readyForFollowup", true)));

        assertThat(decision.nextNodeCode()).isEqualTo("FOLLOWUP");
        assertThat(decision.edgeType()).isEqualTo(PathwayEdgeType.CONDITION);
    }

    @Test
    void conditionEdgeReusesUnifiedRuleDslClinicalOperator() {
        String tenantId = "tenant-A";
        String templateId = "pt-" + tenantId;
        PathwayGraph graph = new PathwayGraph(
            List.of(
                node("REVIEW", 10, false),
                node("FOLLOWUP", 20, true),
                node("PLAN", 30, false)
            ),
            List.of(
                edge("e-condition", tenantId, templateId, "REVIEW", "FOLLOWUP",
                    PathwayEdgeType.CONDITION, 1,
                    """
                    {
                      "fact": "lab.potassium",
                      "operator": "not_between",
                      "value": {"min": 3.5, "max": 5.5, "unit": "mmol/L"}
                    }
                    """),
                edge("e-default", tenantId, templateId, "REVIEW", "PLAN",
                    PathwayEdgeType.DEFAULT, 2)
            )
        );

        PathwayProgressDecision decision = progressor.advance(new PathwayProgressCommand(
            graph, "REVIEW", PathwayAdvanceEventType.COMPLETE, null,
            Map.of("lab.potassium", Map.of("value", 6.0, "unit", "mmol/L"))));

        assertThat(decision.nextNodeCode()).isEqualTo("FOLLOWUP");
        assertThat(decision.edgeType()).isEqualTo(PathwayEdgeType.CONDITION);
        assertThat(decision.evidence()).containsKey("lab.potassium");
    }

    @Test
    void conditionEdgeAcceptsValuelessUnifiedRuleDslOperator() {
        String tenantId = "tenant-A";
        String templateId = "pt-" + tenantId;
        PathwayGraph graph = new PathwayGraph(
            List.of(
                node("REVIEW", 10, false),
                node("FOLLOWUP", 20, true),
                node("PLAN", 30, false)
            ),
            List.of(
                edge("e-condition", tenantId, templateId, "REVIEW", "FOLLOWUP",
                    PathwayEdgeType.CONDITION, 1,
                    "{\"fact\":\"lab.potassium\",\"operator\":\"is_missing\"}"),
                edge("e-default", tenantId, templateId, "REVIEW", "PLAN",
                    PathwayEdgeType.DEFAULT, 2)
            )
        );

        PathwayProgressDecision decision = progressor.advance(new PathwayProgressCommand(
            graph, "REVIEW", PathwayAdvanceEventType.COMPLETE, null, Map.of()));

        assertThat(decision.nextNodeCode()).isEqualTo("FOLLOWUP");
        assertThat(decision.edgeType()).isEqualTo(PathwayEdgeType.CONDITION);
    }

    @Test
    void conditionEdgeAcceptsUnifiedNotGroupGuard() {
        String tenantId = "tenant-A";
        String templateId = "pt-" + tenantId;
        PathwayGraph graph = new PathwayGraph(
            List.of(
                node("REVIEW", 10, false),
                node("FOLLOWUP", 20, true),
                node("PLAN", 30, false)
            ),
            List.of(
                edge("e-condition", tenantId, templateId, "REVIEW", "FOLLOWUP",
                    PathwayEdgeType.CONDITION, 1,
                    """
                    {
                      "all": [
                        {"fact": "context.readyForFollowup", "operator": "equals", "value": true},
                        {"not": {"fact": "allergyIntolerances[].code", "operator": "contains", "value": "PENICILLIN"}}
                      ]
                    }
                    """),
                edge("e-default", tenantId, templateId, "REVIEW", "PLAN",
                    PathwayEdgeType.DEFAULT, 2)
            )
        );

        PathwayProgressDecision decision = progressor.advance(new PathwayProgressCommand(
            graph, "REVIEW", PathwayAdvanceEventType.COMPLETE, null,
            Map.of(
                "context.readyForFollowup", true,
                "allergyIntolerances", List.of(Map.of("code", "SULFA")))));

        assertThat(decision.nextNodeCode()).isEqualTo("FOLLOWUP");
        assertThat(decision.edgeType()).isEqualTo(PathwayEdgeType.CONDITION);
        assertThat(decision.evidence()).containsKey("allergyIntolerances[].code");
    }

    @Test
    void decisionNodeUsesPublishedRuleGuardAndRecordsActualRuleVersion() {
        ObjectMapper json = new ObjectMapper();
        PathwayProgressor ruleProgressor = new PathwayProgressor(
            json,
            new ConditionEvaluator(json),
            AuthoringFeatureGate.alwaysEnabled(),
            (reference, context, runtimeReleaseId) -> new PathwayRuleGuardEvaluation(
                true,
                reference.path("ruleRef").asText(),
                "rule-high-risk",
                "rv-high-risk-v2",
                2,
                runtimeReleaseId,
                "tenant-A",
                com.medkernel.engine.release.ReleaseSourceLayer.HOSPITAL));
        String tenantId = "tenant-A";
        String templateId = "pt-" + tenantId;
        PathwayGraph graph = new PathwayGraph(
            List.of(
                node("DECIDE", PathwayNodeType.DECISION, 10, false, null, null, 120),
                node("HIGH", PathwayNodeType.NURSING, 20, true),
                node("ROUTINE", PathwayNodeType.NURSING, 30, true)
            ),
            List.of(
                edge("e-rule", tenantId, templateId, "DECIDE", "HIGH",
                    PathwayEdgeType.CONDITION, 1,
                    """
                    {
                      "ruleRef": "RULE.CKD.HIGH_RISK",
                      "ruleAssetId": "rule-high-risk"
                    }
                    """),
                edge("e-default", tenantId, templateId, "DECIDE", "ROUTINE",
                    PathwayEdgeType.DEFAULT, 2)
            )
        );

        PathwayProgressDecision decision = ruleProgressor.advance(new PathwayProgressCommand(
            graph, "DECIDE", PathwayAdvanceEventType.COMPLETE, null,
            Map.of("patient.riskLevel", "HIGH"), "release-hospital-7"));

        assertThat(decision.nextNodeCode()).isEqualTo("HIGH");
        assertThat(decision.evidence())
            .containsEntry("pathway.ruleRef", "RULE.CKD.HIGH_RISK")
            .containsEntry("pathway.ruleId", "rule-high-risk")
            .containsEntry("pathway.ruleVersionId", "rv-high-risk-v2")
            .containsEntry("pathway.ruleVersionNo", 2)
            .containsEntry("pathway.runtimeReleaseId", "release-hospital-7")
            .containsEntry("pathway.ruleSourceTenantId", "tenant-A")
            .containsEntry("pathway.ruleSourceLayer", "HOSPITAL")
            .containsEntry("pathway.ruleMatched", true);
    }

    @Test
    void decisionNodeFallsBackToDefaultWhenPublishedRuleGuardDoesNotMatch() {
        ObjectMapper json = new ObjectMapper();
        PathwayProgressor ruleProgressor = new PathwayProgressor(
            json,
            new ConditionEvaluator(json),
            AuthoringFeatureGate.alwaysEnabled(),
            (reference, context, runtimeReleaseId) -> new PathwayRuleGuardEvaluation(
                false,
                reference.path("ruleRef").asText(),
                "rule-high-risk",
                "rv-high-risk-v3",
                3,
                runtimeReleaseId,
                "platform",
                com.medkernel.engine.release.ReleaseSourceLayer.PLATFORM));
        String tenantId = "tenant-A";
        String templateId = "pt-" + tenantId;
        PathwayGraph graph = new PathwayGraph(
            List.of(
                node("DECIDE", PathwayNodeType.DECISION, 10, false, null, null, 120),
                node("HIGH", PathwayNodeType.NURSING, 20, true),
                node("ROUTINE", PathwayNodeType.NURSING, 30, true)
            ),
            List.of(
                edge("e-rule", tenantId, templateId, "DECIDE", "HIGH",
                    PathwayEdgeType.CONDITION, 1,
                    """
                    {
                      "ruleRef": "RULE.CKD.HIGH_RISK",
                      "ruleAssetId": "rule-high-risk"
                    }
                    """),
                edge("e-default", tenantId, templateId, "DECIDE", "ROUTINE",
                    PathwayEdgeType.DEFAULT, 2)
            )
        );

        PathwayProgressDecision decision = ruleProgressor.advance(new PathwayProgressCommand(
            graph, "DECIDE", PathwayAdvanceEventType.COMPLETE, null,
            Map.of("patient.riskLevel", "LOW"), "release-hospital-7"));

        assertThat(decision.nextNodeCode()).isEqualTo("ROUTINE");
        assertThat(decision.edgeType()).isEqualTo(PathwayEdgeType.DEFAULT);
        assertThat(decision.evidence())
            .containsEntry("pathway.ruleVersionId", "rv-high-risk-v3")
            .containsEntry("pathway.ruleVersionNo", 3)
            .containsEntry("pathway.ruleMatched", false)
            .containsEntry("pathway.selectedEdgeCode", "e-default");
    }

    @Test
    void decisionNodeRecordsRichTypeAndChoosesMatchedGuardedBranch() {
        String tenantId = "tenant-A";
        String templateId = "pt-" + tenantId;
        PathwayGraph graph = new PathwayGraph(
            List.of(
                node("DECIDE", PathwayNodeType.DECISION, 10, false, null, null, 120),
                node("ICU", PathwayNodeType.NURSING, 20, false),
                node("WARD", PathwayNodeType.NURSING, 30, false)
            ),
            List.of(
                edge("e-high-risk", tenantId, templateId, "DECIDE", "ICU",
                    PathwayEdgeType.CONDITION, 1,
                    "{\"fact\":\"risk.level\",\"operator\":\"equals\",\"value\":\"HIGH\"}"),
                edge("e-default", tenantId, templateId, "DECIDE", "WARD",
                    PathwayEdgeType.DEFAULT, 2)
            )
        );

        PathwayProgressDecision decision = progressor.advance(new PathwayProgressCommand(
            graph, "DECIDE", PathwayAdvanceEventType.COMPLETE, null,
            Map.of("risk.level", "HIGH")));

        assertThat(decision.nextNodeCode()).isEqualTo("ICU");
        assertThat(decision.edgeType()).isEqualTo(PathwayEdgeType.CONDITION);
        assertThat(decision.evidence()).containsEntry("pathway.currentNodeType", "DECISION");
        assertThat(decision.evidence()).containsEntry("risk.level", "HIGH");
    }

    @Test
    void decisionNodeChoosesLowestPriorityMatchedGuardAndRecordsSelectionEvidence() {
        String tenantId = "tenant-A";
        String templateId = "pt-" + tenantId;
        PathwayGraph graph = new PathwayGraph(
            List.of(
                node("DECIDE", PathwayNodeType.DECISION, 10, false, null, null, 120),
                node("ICU", PathwayNodeType.NURSING, 20, false),
                node("OBSERVE", PathwayNodeType.NURSING, 30, false),
                node("WARD", PathwayNodeType.NURSING, 40, false)
            ),
            List.of(
                edge("e-observe", tenantId, templateId, "DECIDE", "OBSERVE",
                    PathwayEdgeType.CONDITION, 20,
                    "{\"fact\":\"risk.score\",\"operator\":\"gte\",\"value\":3}"),
                edge("EDGE.DECIDE.ICU", tenantId, templateId, "DECIDE", "ICU",
                    PathwayEdgeType.CONDITION, 10,
                    "{\"fact\":\"risk.score\",\"operator\":\"gte\",\"value\":5}"),
                edge("e-default", tenantId, templateId, "DECIDE", "WARD",
                    PathwayEdgeType.DEFAULT, 30)
            )
        );

        PathwayProgressDecision decision = progressor.advance(new PathwayProgressCommand(
            graph, "DECIDE", PathwayAdvanceEventType.COMPLETE, null,
            Map.of("risk.score", 6)));

        assertThat(decision.nextNodeCode()).isEqualTo("ICU");
        assertThat(decision.evidence()).containsEntry("pathway.selectedEdgeCode", "EDGE.DECIDE.ICU");
        assertThat(decision.evidence()).containsEntry("pathway.selectedEdgePriority", 10);
        assertThat(decision.evidence()).containsEntry("pathway.decisionGuardMatched", true);
    }

    @Test
    void decisionNodeRejectsRequestedGuardedTargetWhenGuardDoesNotMatch() {
        String tenantId = "tenant-A";
        String templateId = "pt-" + tenantId;
        PathwayGraph graph = new PathwayGraph(
            List.of(
                node("DECIDE", PathwayNodeType.DECISION, 10, false, null, null, 120),
                node("ICU", PathwayNodeType.NURSING, 20, false),
                node("WARD", PathwayNodeType.NURSING, 30, false)
            ),
            List.of(
                edge("e-icu", tenantId, templateId, "DECIDE", "ICU",
                    PathwayEdgeType.CONDITION, 1,
                    "{\"fact\":\"risk.level\",\"operator\":\"equals\",\"value\":\"HIGH\"}"),
                edge("e-default", tenantId, templateId, "DECIDE", "WARD",
                    PathwayEdgeType.DEFAULT, 2)
            )
        );

        assertThatThrownBy(() -> progressor.advance(new PathwayProgressCommand(
            graph, "DECIDE", PathwayAdvanceEventType.COMPLETE, "ICU",
            Map.of("risk.level", "LOW"))))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("目标决策分支守卫未命中")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PATHWAY_006);
    }

    @Test
    void manualGateRequiresExplicitConfirmedTarget() {
        String tenantId = "tenant-A";
        String templateId = "pt-" + tenantId;
        PathwayGraph graph = new PathwayGraph(
            List.of(
                node("GATE", PathwayNodeType.MANUAL_GATE, 10, false, null, "临床负责人", 120),
                node("NEXT", PathwayNodeType.ASSESSMENT, 20, false)
            ),
            List.of(edge("e-default", tenantId, templateId, "GATE", "NEXT", PathwayEdgeType.DEFAULT, 1))
        );

        assertThatThrownBy(() -> progressor.advance(new PathwayProgressCommand(
            graph, "GATE", PathwayAdvanceEventType.COMPLETE, null)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("人工闸门节点需要显式确认目标节点")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PATHWAY_006);

        PathwayProgressDecision decision = progressor.advance(new PathwayProgressCommand(
            graph, "GATE", PathwayAdvanceEventType.COMPLETE, "NEXT"));

        assertThat(decision.nextNodeCode()).isEqualTo("NEXT");
        assertThat(decision.evidence()).containsEntry("pathway.manualGateConfirmed", true);
    }

    @Test
    void waitTimerNodeRequiresMatchedTimerGuardBeforeAdvancing() {
        String tenantId = "tenant-A";
        String templateId = "pt-" + tenantId;
        PathwayGraph graph = new PathwayGraph(
            List.of(
                node("WAIT24H", PathwayNodeType.WAIT_TIMER, 10, false, "{\"clock\":\"AFTER_24H\"}", "护士", 1440),
                node("RECHECK", PathwayNodeType.LAB, 20, false),
                node("HOLD", PathwayNodeType.NURSING, 30, false)
            ),
            List.of(
                edge("e-timer-ready", tenantId, templateId, "WAIT24H", "RECHECK",
                    PathwayEdgeType.CONDITION, 1,
                    "{\"fact\":\"pathway.timer.WAIT24H.ready\",\"operator\":\"equals\",\"value\":true}"),
                edge("e-default-hold", tenantId, templateId, "WAIT24H", "HOLD",
                    PathwayEdgeType.DEFAULT, 2)
            )
        );

        assertThatThrownBy(() -> progressor.advance(new PathwayProgressCommand(
            graph, "WAIT24H", PathwayAdvanceEventType.COMPLETE, null,
            Map.of("pathway.timer.WAIT24H.ready", false))))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("等待计时节点尚未满足推进条件")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PATHWAY_006);

        PathwayProgressDecision decision = progressor.advance(new PathwayProgressCommand(
            graph, "WAIT24H", PathwayAdvanceEventType.COMPLETE, null,
            Map.of("pathway.timer.WAIT24H.ready", true)));

        assertThat(decision.nextNodeCode()).isEqualTo("RECHECK");
        assertThat(decision.evidence()).containsEntry("pathway.timerClock", "AFTER_24H");
    }

    @Test
    void joinEdgeWaitsForAllIncomingParallelBranches() {
        String tenantId = "tenant-A";
        String templateId = "pt-" + tenantId;
        PathwayGraph graph = new PathwayGraph(
            List.of(
                node("FORK", PathwayNodeType.PARALLEL, 10, false),
                node("LAB", PathwayNodeType.LAB, 20, false),
                node("IMAGE", PathwayNodeType.EXAM, 30, false),
                node("JOIN", PathwayNodeType.PARALLEL, 40, false),
                node("NEXT", PathwayNodeType.ASSESSMENT, 50, false)
            ),
            List.of(
                edge("e-lab", tenantId, templateId, "FORK", "LAB", PathwayEdgeType.DEFAULT, 1),
                edge("e-image", tenantId, templateId, "FORK", "IMAGE", PathwayEdgeType.DEFAULT, 2),
                edge("e-lab-join", tenantId, templateId, "LAB", "JOIN", PathwayEdgeType.DEFAULT, 1),
                edge("e-image-join", tenantId, templateId, "IMAGE", "JOIN", PathwayEdgeType.DEFAULT, 1),
                edge("e-join-next", tenantId, templateId, "JOIN", "NEXT", PathwayEdgeType.JOIN, 1)
            )
        );

        assertThatThrownBy(() -> progressor.advance(new PathwayProgressCommand(
            graph, "JOIN", PathwayAdvanceEventType.COMPLETE, null,
            Map.of("pathway.join.JOIN.completedNodeCodes", List.of("LAB")))))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("并行汇合节点仍有未完成前置分支")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PATHWAY_006);

        PathwayProgressDecision decision = progressor.advance(new PathwayProgressCommand(
            graph, "JOIN", PathwayAdvanceEventType.COMPLETE, null,
            Map.of("pathway.join.JOIN.completedNodeCodes", List.of("LAB", "IMAGE"))));

        assertThat(decision.nextNodeCode()).isEqualTo("NEXT");
        assertThat(decision.edgeType()).isEqualTo(PathwayEdgeType.JOIN);
        assertThat(decision.evidence()).containsEntry("pathway.joinRequiredNodeCodes", List.of("LAB", "IMAGE"));
        assertThat(decision.evidence()).containsEntry("pathway.joinCompletedNodeCodes", List.of("LAB", "IMAGE"));
    }

    @Test
    void orderSetNodeRequiresConfigReferenceAndRecordsEvidence() {
        String tenantId = "tenant-A";
        String templateId = "pt-" + tenantId;
        PathwayGraph graph = new PathwayGraph(
            List.of(
                node("ORDER", PathwayNodeType.ORDER_SET, 10, false, "{\"orderSetRef\":\"sepsis-order-set\"}", "医生", 120),
                node("DONE", PathwayNodeType.FOLLOWUP, 20, true)
            ),
            List.of(
                edge("e-order-done", tenantId, templateId, "ORDER", "DONE", PathwayEdgeType.DEFAULT, 1)
            )
        );

        PathwayProgressDecision orderDecision = progressor.advance(new PathwayProgressCommand(
            graph, "ORDER", PathwayAdvanceEventType.COMPLETE, null));

        assertThat(orderDecision.evidence()).containsEntry("pathway.orderSetRef", "sepsis-order-set");
    }

    @Test
    void orderSetNodeResolvesRuntimeOrderSetBodyAndRecordsExecutableEvidence() {
        ObjectMapper mapper = new ObjectMapper();
        PathwayProgressor resolvingProgressor = new PathwayProgressor(
            mapper,
            new ConditionEvaluator(mapper),
            AuthoringFeatureGate.alwaysEnabled(),
            PathwayRuleGuardEvaluator.unavailable(),
            (tenantId, runtimeReleaseId, assetType, assetIdentity) -> {
                assertThat(tenantId).isEqualTo("tenant-A");
                assertThat(runtimeReleaseId).isEqualTo("release-4");
                assertThat(assetType).isEqualTo(VersionedAssetType.ORDER_SET);
                assertThat(assetIdentity).isEqualTo("ORDER.CKD.REVIEW");
                return Optional.of(new ResolvedDeclarativeAsset(
                    assetType,
                    assetIdentity,
                    "V2",
                    runtimeReleaseId,
                    """
                        {
                          "schemaVersion":"1.0",
                          "name":"肾功能复核医嘱套餐",
                          "requiresPhysicianConfirmation":true,
                          "items":[{
                            "itemType":"LAB",
                            "codeSystem":"LOINC",
                            "code":"2160-0",
                            "display":"血肌酐",
                            "required":true
                          }]
                        }
                        """,
                    "d".repeat(64)
                ));
            });
        String tenantId = "tenant-A";
        String templateId = "pt-" + tenantId;
        PathwayGraph graph = new PathwayGraph(
            List.of(
                node("ORDER", PathwayNodeType.ORDER_SET, 10, false, "{\"orderSetRef\":\"ORDER.CKD.REVIEW\"}", "医生", 120),
                node("DONE", PathwayNodeType.FOLLOWUP, 20, true)
            ),
            List.of(edge("e-order-done", tenantId, templateId, "ORDER", "DONE", PathwayEdgeType.DEFAULT, 1))
        );

        PathwayProgressDecision decision = resolvingProgressor.advance(new PathwayProgressCommand(
            graph, "ORDER", PathwayAdvanceEventType.COMPLETE, null,
            Map.of(), "release-4", "tenant-A"));

        assertThat(decision.evidence()).containsEntry("pathway.orderSetRef", "ORDER.CKD.REVIEW");
        assertThat(decision.evidence()).containsEntry("pathway.orderSetVersion", "V2");
        assertThat(decision.evidence()).containsEntry("pathway.orderSetName", "肾功能复核医嘱套餐");
        assertThat(decision.evidence()).containsEntry("pathway.orderSetRequiresPhysicianConfirmation", true);
        assertThat(decision.evidence()).containsEntry("pathway.orderSetItemCount", 1);
        assertThat(decision.evidence()).containsKey("pathway.orderSetItems");
    }

    @Test
    void completesPathwayWhenCurrentNodeHasNoOutgoingEdge() {
        PathwayProgressDecision decision = progressor.advance(new PathwayProgressCommand(
            graph(), "FOLLOWUP", PathwayAdvanceEventType.COMPLETE, null));

        assertThat(decision.nextNodeCode()).isNull();
        assertThat(decision.status()).isEqualTo(PatientPathwayStatus.COMPLETED);
    }

    @Test
    void varianceCanContinueToRequestedReachableNode() {
        PathwayProgressDecision decision = progressor.advance(new PathwayProgressCommand(
            graph(), "LAB", PathwayAdvanceEventType.VARIANCE, "FOLLOWUP"));

        assertThat(decision.nextNodeCode()).isEqualTo("FOLLOWUP");
        assertThat(decision.status()).isEqualTo(PatientPathwayStatus.NODE_EXECUTING);
    }

    @Test
    void varianceWithoutContinuationStaysAtCurrentNode() {
        PathwayProgressDecision decision = progressor.advance(new PathwayProgressCommand(
            graph(), "LAB", PathwayAdvanceEventType.VARIANCE, null));

        assertThat(decision.nextNodeCode()).isEqualTo("LAB");
        assertThat(decision.status()).isEqualTo(PatientPathwayStatus.VARIANCE);
    }

    @Test
    void rejectsTargetNodeOutsideCurrentOutgoingEdges() {
        assertThatThrownBy(() -> progressor.advance(new PathwayProgressCommand(
            graph(), "ASSESS", PathwayAdvanceEventType.COMPLETE, "UNKNOWN")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PATHWAY_006);
    }

    @Test
    void rejectsExplicitTargetWhenCurrentNodeHasNoOutgoingEdge() {
        assertThatThrownBy(() -> progressor.advance(new PathwayProgressCommand(
            graph(), "FOLLOWUP", PathwayAdvanceEventType.COMPLETE, "ASSESS")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PATHWAY_006);
    }

    private PathwayGraph graph() {
        String tenantId = "tenant-A";
        String templateId = "pt-" + tenantId;
        return new PathwayGraph(
            List.of(
                node("ASSESS", 10, false),
                node("LAB", 20, false),
                node("SURGERY", 30, false),
                node("FOLLOWUP", 40, true)
            ),
            List.of(
                edge("e1", tenantId, templateId, "ASSESS", "LAB", PathwayEdgeType.DEFAULT, 10),
                edge("e2", tenantId, templateId, "ASSESS", "SURGERY", PathwayEdgeType.CONDITION, 20),
                edge("e3", tenantId, templateId, "LAB", "FOLLOWUP", PathwayEdgeType.DEFAULT, 10),
                edge("e4", tenantId, templateId, "SURGERY", "FOLLOWUP", PathwayEdgeType.DEFAULT, 10)
            )
        );
    }

    private PathwayGraph conditionGraph() {
        String tenantId = "tenant-A";
        String templateId = "pt-" + tenantId;
        return new PathwayGraph(
            List.of(
                node("REVIEW", 10, false),
                node("FOLLOWUP", 20, true),
                node("PLAN", 30, false)
            ),
            List.of(
                edge("e-condition", tenantId, templateId, "REVIEW", "FOLLOWUP",
                    PathwayEdgeType.CONDITION, 1,
                    "{\"fact\":\"context.readyForFollowup\",\"operator\":\"equals\",\"value\":true}"),
                edge("e-default", tenantId, templateId, "REVIEW", "PLAN",
                    PathwayEdgeType.DEFAULT, 2)
            )
        );
    }

    private PathwayNode node(String code, int sortOrder, boolean terminal) {
        return node(code, PathwayNodeType.ASSESSMENT, sortOrder, terminal);
    }

    private PathwayNode node(String code, PathwayNodeType type, int sortOrder, boolean terminal) {
        return node(code, type, sortOrder, terminal, null, null, 120);
    }

    private PathwayNode node(String code, PathwayNodeType type, int sortOrder, boolean terminal,
                             String configJson, String responsibleRole, Integer timeWindowMinutes) {
        Instant now = Instant.now();
        return new PathwayNode(
            null, "pn-" + code, "tenant-A", "pt-tenant-A", code, code,
            type, null, sortOrder, responsibleRole == null ? "医生" : responsibleRole, null, timeWindowMinutes, terminal,
            configJson, now, "tester", now, "tester", "trace-pathway");
    }

    private PathwayEdge edge(String edgeId, String tenantId, String templateId,
                             String from, String to, PathwayEdgeType type, int priority) {
        return edge(edgeId, tenantId, templateId, from, to, type, priority, null);
    }

    private PathwayEdge edge(String edgeId, String tenantId, String templateId,
                             String from, String to, PathwayEdgeType type, int priority,
                             String conditionJson) {
        Instant now = Instant.now();
        return new PathwayEdge(
            null, edgeId, tenantId, templateId, edgeId, from, to, type,
            conditionJson, priority, now, "tester", now, "tester", "trace-pathway");
    }
}
