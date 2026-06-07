package com.medkernel.engine.pathway;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.rule.ConditionEvaluation;
import com.medkernel.engine.rule.ConditionEvaluator;
import com.medkernel.engine.rule.ConditionEvidence;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 路径确定性推进器。
 *
 * <p>根据路径图、当前节点、推进事件和可选目标节点选择下一节点或终态，只做流程判断，
 * 不读取数据库、不写审计、不生成医疗诊断或医嘱。
 */
@Component
public class PathwayProgressor {

    private final ObjectMapper json;
    private final ConditionEvaluator conditionEvaluator;

    public PathwayProgressor(ObjectMapper json, ConditionEvaluator conditionEvaluator) {
        this.json = json;
        this.conditionEvaluator = conditionEvaluator;
    }

    PathwayProgressor() {
        this(new ObjectMapper(), new ConditionEvaluator(new ObjectMapper()));
    }

    /**
     * 计算一次路径推进决策。
     *
     * <p>规则：退出事件直接进入 {@code EXITED}；无继续节点的变异停留在当前节点并进入
     * {@code VARIANCE}；普通完成事件优先使用请求目标节点，否则选择优先级最高的出边。
     *
     * @throws ApiException 当命令不完整、当前节点不存在或目标节点不可达时抛出 {@code ENG-PATHWAY-006}
     */
    public PathwayProgressDecision advance(PathwayProgressCommand command) {
        if (command == null || command.graph() == null || isBlank(command.currentNodeCode())
                || command.eventType() == null) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_006, "路径推进命令不完整");
        }
        PathwayNode current = findCurrentNode(command.graph(), command.currentNodeCode());
        if (command.eventType() == PathwayAdvanceEventType.EXIT) {
            return new PathwayProgressDecision(current.nodeCode(), null, PatientPathwayStatus.EXITED, null);
        }
        if (command.eventType() == PathwayAdvanceEventType.VARIANCE && isBlank(command.requestedNextNodeCode())) {
            return new PathwayProgressDecision(
                current.nodeCode(), current.nodeCode(), PatientPathwayStatus.VARIANCE, null);
        }

        List<PathwayEdge> outgoing = command.graph().edges().stream()
            .filter(edge -> Objects.equals(edge.fromNodeCode(), current.nodeCode()))
            .sorted(Comparator
                .comparing((PathwayEdge edge) -> edge.priority() == null ? 0 : edge.priority())
                .thenComparing(PathwayEdge::edgeId))
            .toList();
        if (!isBlank(command.requestedNextNodeCode()) && outgoing.isEmpty()) {
            throw new ApiException(
                ErrorCode.ENG_PATHWAY_006,
                "当前节点没有可达出边，不能指定目标节点: " + command.requestedNextNodeCode());
        }
        if (outgoing.isEmpty() || Boolean.TRUE.equals(current.terminalFlag())) {
            return new PathwayProgressDecision(current.nodeCode(), null, PatientPathwayStatus.COMPLETED, null);
        }

        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        PathwayEdge selected = isBlank(command.requestedNextNodeCode())
            ? selectNextEdge(outgoing, command.facts(), evidence)
            : outgoing.stream()
                .filter(edge -> Objects.equals(edge.toNodeCode(), command.requestedNextNodeCode()))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                    ErrorCode.ENG_PATHWAY_006,
                    "目标节点不属于当前节点的可达出边: " + command.requestedNextNodeCode()));
        ensureTargetNodeExists(command.graph(), selected.toNodeCode());
        return new PathwayProgressDecision(
            current.nodeCode(), selected.toNodeCode(), PatientPathwayStatus.NODE_EXECUTING,
            selected.edgeType(), selected.edgeCode(), evidence);
    }

    private PathwayEdge selectNextEdge(List<PathwayEdge> outgoing,
                                       Map<String, Object> facts,
                                       LinkedHashMap<String, Object> evidence) {
        PathwayEdge fallback = null;
        for (PathwayEdge edge : outgoing) {
            if (!hasCondition(edge)) {
                if (fallback == null) {
                    fallback = edge;
                }
                continue;
            }
            if (matchesCondition(edge.conditionJson(), facts, evidence)) {
                return edge;
            }
        }
        if (fallback != null) {
            return fallback;
        }
        throw new ApiException(ErrorCode.ENG_PATHWAY_006, "没有满足条件的路径边");
    }

    private boolean hasCondition(PathwayEdge edge) {
        return !isBlank(edge.conditionJson());
    }

    private boolean matchesCondition(String conditionJson, Map<String, Object> facts,
                                     LinkedHashMap<String, Object> evidence) {
        try {
            JsonNode condition = json.readTree(conditionJson);
            ConditionEvaluation evaluation = conditionEvaluator.evaluate(toConditionGroup(condition), factsToContext(facts));
            recordConditionEvidence(evaluation.evidence(), evidence);
            return evaluation.matched();
        } catch (ApiException exception) {
            if (exception.errorCode() == ErrorCode.INSUFFICIENT_DATA) {
                throw exception;
            }
            throw new ApiException(ErrorCode.ENG_PATHWAY_006, exception.getMessage(), exception);
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_006, "路径条件 JSON 无法解析", exception);
        }
    }

    private JsonNode toConditionGroup(JsonNode condition) {
        if (condition.has("when")) {
            return condition.get("when");
        }
        if (condition.has("all") || condition.has("any") || condition.has("not")) {
            return condition;
        }
        ObjectNode when = json.createObjectNode();
        when.putArray("all").add(condition);
        return when;
    }

    private JsonNode factsToContext(Map<String, Object> facts) {
        ObjectNode root = json.createObjectNode();
        if (facts == null || facts.isEmpty()) {
            return root;
        }
        for (Map.Entry<String, Object> entry : facts.entrySet()) {
            putDottedPath(root, entry.getKey(), entry.getValue());
        }
        return root;
    }

    private void putDottedPath(ObjectNode root, String path, Object value) {
        if (isBlank(path)) {
            return;
        }
        String[] segments = path.split("\\.");
        ObjectNode current = root;
        for (int index = 0; index < segments.length; index += 1) {
            String segment = segments[index];
            if (isBlank(segment)) {
                continue;
            }
            if (index == segments.length - 1) {
                current.set(segment, value == null ? json.nullNode() : json.valueToTree(value));
                return;
            }
            JsonNode child = current.get(segment);
            if (child == null || !child.isObject()) {
                child = json.createObjectNode();
                current.set(segment, child);
            }
            current = (ObjectNode) child;
        }
    }

    private void recordConditionEvidence(List<ConditionEvidence> conditionEvidence, LinkedHashMap<String, Object> evidence) {
        for (ConditionEvidence item : conditionEvidence) {
            String fact = item.fact();
            if (!isBlank(fact)) {
                JsonNode actual = item.actual();
                Object value = actual == null || actual.isMissingNode() ? null : json.convertValue(actual, Object.class);
                evidence.put(fact, value);
            }
        }
    }

    private PathwayNode findCurrentNode(PathwayGraph graph, String currentNodeCode) {
        return graph.nodes().stream()
            .filter(node -> Objects.equals(node.nodeCode(), currentNodeCode))
            .findFirst()
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_PATHWAY_006, "当前节点不存在: " + currentNodeCode));
    }

    private void ensureTargetNodeExists(PathwayGraph graph, String nodeCode) {
        boolean exists = graph.nodes().stream().anyMatch(node -> Objects.equals(node.nodeCode(), nodeCode));
        if (!exists) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_006, "目标节点不存在: " + nodeCode);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
