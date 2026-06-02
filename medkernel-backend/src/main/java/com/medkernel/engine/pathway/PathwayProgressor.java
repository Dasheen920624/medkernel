package com.medkernel.engine.pathway;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final ObjectMapper json = new ObjectMapper();

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
            String fact = condition.path("fact").asText(null);
            String operator = condition.path("operator").asText("equals");
            JsonNode expectedNode = condition.path("value");
            if (isBlank(fact) || expectedNode.isMissingNode()) {
                throw new ApiException(ErrorCode.ENG_PATHWAY_006, "路径条件缺少 fact 或 value");
            }
            Object actual = facts.get(fact);
            evidence.put(fact, actual);
            return compare(actual, expectedNode, operator);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_006, "路径条件 JSON 无法解析", exception);
        }
    }

    private boolean compare(Object actual, JsonNode expectedNode, String operator) {
        return switch (operator) {
            case "equals", "eq" -> Objects.equals(normalize(actual), jsonValue(expectedNode));
            case "notEquals", "not_equals", "ne" -> !Objects.equals(normalize(actual), jsonValue(expectedNode));
            case "gt", "greaterThan" -> numeric(actual).compareTo(numeric(jsonValue(expectedNode))) > 0;
            case "gte", "greaterOrEqual" -> numeric(actual).compareTo(numeric(jsonValue(expectedNode))) >= 0;
            case "lt", "lessThan" -> numeric(actual).compareTo(numeric(jsonValue(expectedNode))) < 0;
            case "lte", "lessOrEqual" -> numeric(actual).compareTo(numeric(jsonValue(expectedNode))) <= 0;
            default -> throw new ApiException(ErrorCode.ENG_PATHWAY_006, "不支持的路径条件操作符: " + operator);
        };
    }

    private Object jsonValue(JsonNode node) {
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isNull()) {
            return null;
        }
        return node.toString();
    }

    private Object normalize(Object value) {
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return value;
    }

    private BigDecimal numeric(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String text && !text.isBlank()) {
            return new BigDecimal(text);
        }
        throw new ApiException(ErrorCode.ENG_PATHWAY_006, "路径条件需要数值事实");
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
