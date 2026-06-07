package com.medkernel.engine.pathway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
        Instant now = Instant.now();
        return new PathwayNode(
            null, "pn-" + code, "tenant-A", "pt-tenant-A", code, code,
            PathwayNodeType.ASSESSMENT, sortOrder, "医生", null, 120, terminal,
            null, now, "tester", now, "tester", "trace-pathway");
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
