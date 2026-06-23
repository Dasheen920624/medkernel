package com.medkernel.engine.pathway;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.authoring.AuthoringFeatureFlag;
import com.medkernel.engine.authoring.AuthoringFeatureGate;
import com.medkernel.engine.rule.ConditionEvaluation;
import com.medkernel.engine.rule.ConditionEvaluator;
import com.medkernel.engine.rule.ConditionEvidence;
import com.medkernel.engine.versioning.DeclarativeAssetRuntimePort;
import com.medkernel.engine.versioning.ResolvedDeclarativeAsset;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.config.SystemConfigService;

/**
 * 路径确定性推进器。
 *
 * <p>根据路径图、当前节点、推进事件和可选目标节点选择下一节点或终态，只做流程判断。
 * 内嵌条件在本地确定性执行；规则守卫通过单向端口解析当前有效发布版本。推进器本身不写审计，
 * 不生成医疗诊断或医嘱。
 */
@Component
public class PathwayProgressor {

    private final ObjectMapper json;
    private final ConditionEvaluator conditionEvaluator;
    private final AuthoringFeatureGate featureGate;
    private final PathwayRuleGuardEvaluator ruleGuardEvaluator;
    private final DeclarativeAssetRuntimePort declarativeAssets;

    public PathwayProgressor(ObjectMapper json, ConditionEvaluator conditionEvaluator) {
        this(
            json,
            conditionEvaluator,
            AuthoringFeatureGate.alwaysEnabled(),
            PathwayRuleGuardEvaluator.unavailable(),
            DeclarativeAssetRuntimePort.unavailable());
    }

    public PathwayProgressor(ObjectMapper json,
                             ConditionEvaluator conditionEvaluator,
                             AuthoringFeatureGate featureGate) {
        this(json, conditionEvaluator, featureGate, PathwayRuleGuardEvaluator.unavailable(),
            DeclarativeAssetRuntimePort.unavailable());
    }

    public PathwayProgressor(ObjectMapper json,
                             ConditionEvaluator conditionEvaluator,
                             AuthoringFeatureGate featureGate,
                             PathwayRuleGuardEvaluator ruleGuardEvaluator) {
        this(json, conditionEvaluator, featureGate, ruleGuardEvaluator, DeclarativeAssetRuntimePort.unavailable());
    }

    @Autowired
    public PathwayProgressor(ObjectMapper json,
                             ConditionEvaluator conditionEvaluator,
                             AuthoringFeatureGate featureGate,
                             PathwayRuleGuardEvaluator ruleGuardEvaluator,
                             DeclarativeAssetRuntimePort declarativeAssets) {
        this.json = json;
        this.conditionEvaluator = conditionEvaluator;
        this.featureGate = featureGate == null ? AuthoringFeatureGate.alwaysEnabled() : featureGate;
        this.ruleGuardEvaluator = ruleGuardEvaluator == null
            ? PathwayRuleGuardEvaluator.unavailable()
            : ruleGuardEvaluator;
        this.declarativeAssets = declarativeAssets == null
            ? DeclarativeAssetRuntimePort.unavailable()
            : declarativeAssets;
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
        ensureRichNodeFeatureEnabled(current);
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
        evidence.put("pathway.currentNodeType", current.nodeType().name());
        validateRichNode(current, command, evidence);
        boolean requestedTarget = !isBlank(command.requestedNextNodeCode());
        PathwayEdge selected = !requestedTarget
            ? selectNextEdge(
                outgoing, command.facts(), evidence, canUseDefaultFallback(current),
                isWaitTimer(current), current, command.runtimeReleaseId())
            : outgoing.stream()
                .filter(edge -> Objects.equals(edge.toNodeCode(), command.requestedNextNodeCode()))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                    ErrorCode.ENG_PATHWAY_006,
                    "目标节点不属于当前节点的可达出边: " + command.requestedNextNodeCode()));
        ensureTargetNodeExists(command.graph(), selected.toNodeCode());
        validateSelectedEdge(
            command.graph(), current, selected, command.facts(), evidence,
            requestedTarget, command.runtimeReleaseId());
        if (!evidence.containsKey("pathway.selectedEdgeCode")) {
            recordSelectedEdge(selected, evidence, null);
        }
        return new PathwayProgressDecision(
            current.nodeCode(), selected.toNodeCode(), PatientPathwayStatus.NODE_EXECUTING,
            selected.edgeType(), selected.edgeCode(), evidence);
    }

    private PathwayEdge selectNextEdge(List<PathwayEdge> outgoing,
                                       Map<String, Object> facts,
                                       LinkedHashMap<String, Object> evidence,
                                       boolean allowDefaultFallback,
                                       boolean waitTimerNode,
                                       PathwayNode current,
                                       String runtimeReleaseId) {
        PathwayEdge fallback = null;
        for (PathwayEdge edge : outgoing) {
            if (!hasCondition(edge)) {
                if (edge.edgeType() != PathwayEdgeType.CONDITION && fallback == null) {
                    fallback = edge;
                }
                continue;
            }
            if (matchesCondition(edge, facts, evidence, runtimeReleaseId)) {
                recordSelectedEdge(edge, evidence, true);
                return edge;
            }
        }
        if (fallback != null && allowDefaultFallback) {
            recordSelectedEdge(fallback, evidence, false);
            return fallback;
        }
        if (waitTimerNode) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_006, "等待计时节点尚未满足推进条件");
        }
        if (current.nodeType() == PathwayNodeType.DECISION) {
            throw new ApiException(
                ErrorCode.ENG_PATHWAY_006,
                "决策节点 " + current.nodeCode() + " 没有命中守卫分支，路径停留当前节点");
        }
        throw new ApiException(ErrorCode.ENG_PATHWAY_006, "没有满足条件的路径边");
    }

    private void validateRichNode(PathwayNode current,
                                  PathwayProgressCommand command,
                                  LinkedHashMap<String, Object> evidence) {
        switch (current.nodeType()) {
            case MANUAL_GATE -> validateManualGate(current, command, evidence);
            case WAIT_TIMER -> recordWaitTimerEvidence(current, evidence);
            case ORDER_SET -> recordOrderSetEvidence(current, command, evidence);
            default -> {
                // 普通活动节点不需要额外语义。
            }
        }
    }

    private void recordOrderSetEvidence(PathwayNode current,
                                        PathwayProgressCommand command,
                                        LinkedHashMap<String, Object> evidence) {
        String orderSetRef = requiredConfigText(current, "orderSetRef", "医嘱集节点缺少 orderSetRef");
        evidence.put("pathway.orderSetRef", orderSetRef);
        if (isBlank(command.tenantId()) || isBlank(command.runtimeReleaseId())) {
            return;
        }
        ResolvedDeclarativeAsset resolved = declarativeAssets
            .resolve(command.tenantId(), command.runtimeReleaseId(), VersionedAssetType.ORDER_SET, orderSetRef)
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_PATHWAY_006,
                "当前医院运行修订未包含医嘱套餐资产: " + orderSetRef + "@" + command.runtimeReleaseId()));
        JsonNode content = parseOrderSetContent(resolved);
        JsonNode items = content.path("items");
        if (!items.isArray() || items.isEmpty()) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_006, "医嘱套餐正文缺少 items: " + orderSetRef);
        }
        if (!content.path("requiresPhysicianConfirmation").asBoolean(false)) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_006, "医嘱套餐必须由医师确认: " + orderSetRef);
        }
        evidence.put("pathway.orderSetVersion", resolved.assetVersion());
        evidence.put("pathway.orderSetHash", resolved.contentHash());
        evidence.put("pathway.orderSetName", requiredOrderSetText(content, "name", orderSetRef));
        evidence.put("pathway.orderSetRequiresPhysicianConfirmation", true);
        evidence.put("pathway.orderSetItemCount", items.size());
        evidence.put("pathway.orderSetItems", orderSetItems(items));
    }

    private JsonNode parseOrderSetContent(ResolvedDeclarativeAsset resolved) {
        try {
            JsonNode content = json.readTree(resolved.contentJson());
            if (content == null || !content.isObject()) {
                throw new ApiException(ErrorCode.ENG_PATHWAY_006,
                    "医嘱套餐正文必须是 JSON 对象: " + resolved.assetIdentity());
            }
            return content;
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                ErrorCode.ENG_PATHWAY_006,
                "医嘱套餐正文不是合法 JSON: " + resolved.assetIdentity(),
                exception);
        }
    }

    private String requiredOrderSetText(JsonNode content, String field, String identity) {
        JsonNode value = content.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_006, "医嘱套餐正文缺少 " + field + ": " + identity);
        }
        return value.asText().trim();
    }

    private List<Map<String, Object>> orderSetItems(JsonNode items) {
        ArrayList<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode item : items) {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("itemType", requiredOrderSetText(item, "itemType", "items"));
            row.put("codeSystem", requiredOrderSetText(item, "codeSystem", "items"));
            row.put("code", requiredOrderSetText(item, "code", "items"));
            row.put("display", requiredOrderSetText(item, "display", "items"));
            row.put("required", item.path("required").asBoolean(false));
            result.add(row);
        }
        return List.copyOf(result);
    }

    private void validateManualGate(PathwayNode current,
                                    PathwayProgressCommand command,
                                    LinkedHashMap<String, Object> evidence) {
        if (isBlank(current.responsibleRole())) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_006, "人工闸门节点缺少责任角色");
        }
        if (command.eventType() == PathwayAdvanceEventType.COMPLETE && isBlank(command.requestedNextNodeCode())) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_006, "人工闸门节点需要显式确认目标节点");
        }
        evidence.put("pathway.manualGateConfirmed", !isBlank(command.requestedNextNodeCode()));
        evidence.put("pathway.manualGateRole", current.responsibleRole());
    }

    private void recordWaitTimerEvidence(PathwayNode current, LinkedHashMap<String, Object> evidence) {
        String clock = configText(current, "clock");
        if (!isBlank(clock)) {
            evidence.put("pathway.timerClock", clock);
        }
        if (current.timeWindowMinutes() != null) {
            evidence.put("pathway.timerWindowMinutes", current.timeWindowMinutes());
        }
        if (isBlank(clock) && current.timeWindowMinutes() == null) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_006, "等待计时节点缺少 clock 或 timeWindowMinutes");
        }
    }

    private boolean canUseDefaultFallback(PathwayNode current) {
        return !isWaitTimer(current);
    }

    private boolean isWaitTimer(PathwayNode current) {
        return current.nodeType() == PathwayNodeType.WAIT_TIMER;
    }

    private void ensureRichNodeFeatureEnabled(PathwayNode current) {
        if (!isRichNode(current.nodeType()) || featureGate.enabled(AuthoringFeatureFlag.PATHWAY_RICH_NODES)) {
            return;
        }
        throw new ApiException(
            ErrorCode.ENG_PATHWAY_006,
            AuthoringFeatureFlag.PATHWAY_RICH_NODES.displayName()
                + "能力开关未启用: "
                + SystemConfigService.runtimeFeatureFlagConfigKey(AuthoringFeatureFlag.PATHWAY_RICH_NODES.key())
                + "，当前节点类型 " + current.nodeType());
    }

    private boolean isRichNode(PathwayNodeType nodeType) {
        return switch (nodeType) {
            case DECISION, PARALLEL, WAIT_TIMER, MANUAL_GATE, ORDER_SET -> true;
            default -> false;
        };
    }

    private void validateSelectedEdge(PathwayGraph graph,
                                      PathwayNode current,
                                      PathwayEdge selected,
                                      Map<String, Object> facts,
                                      LinkedHashMap<String, Object> evidence,
                                      boolean requestedTarget,
                                      String runtimeReleaseId) {
        if (requestedTarget && current.nodeType() == PathwayNodeType.DECISION
                && selected.edgeType() == PathwayEdgeType.CONDITION) {
            if (!hasCondition(selected)) {
                throw new ApiException(ErrorCode.ENG_PATHWAY_006, "目标决策分支缺少守卫条件: " + selected.edgeCode());
            }
            if (!matchesCondition(selected, facts, evidence, runtimeReleaseId)) {
                throw new ApiException(ErrorCode.ENG_PATHWAY_006, "目标决策分支守卫未命中: " + selected.edgeCode());
            }
            recordSelectedEdge(selected, evidence, true);
        }
        if (selected.edgeType() == PathwayEdgeType.JOIN) {
            validateJoinEdge(graph, current, facts, evidence);
        }
    }

    private void recordSelectedEdge(PathwayEdge edge, LinkedHashMap<String, Object> evidence,
                                    Boolean guardMatched) {
        evidence.put("pathway.selectedEdgeCode", edge.edgeCode());
        evidence.put("pathway.selectedEdgePriority", edge.priority());
        if (guardMatched != null) {
            evidence.put("pathway.decisionGuardMatched", guardMatched);
        }
    }

    private void validateJoinEdge(PathwayGraph graph,
                                  PathwayNode current,
                                  Map<String, Object> facts,
                                  LinkedHashMap<String, Object> evidence) {
        List<String> requiredNodeCodes = graph.edges().stream()
            .filter(edge -> Objects.equals(edge.toNodeCode(), current.nodeCode()))
            .filter(edge -> edge.edgeType() != PathwayEdgeType.JOIN)
            .map(PathwayEdge::fromNodeCode)
            .filter(code -> !isBlank(code))
            .distinct()
            .toList();
        List<String> completedNodeCodes = completedNodeCodes(current.nodeCode(), facts);
        evidence.put("pathway.joinRequiredNodeCodes", requiredNodeCodes);
        evidence.put("pathway.joinCompletedNodeCodes", completedNodeCodes);
        if (!new LinkedHashSet<>(completedNodeCodes).containsAll(requiredNodeCodes)) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_006, "并行汇合节点仍有未完成前置分支");
        }
    }

    private List<String> completedNodeCodes(String joinNodeCode, Map<String, Object> facts) {
        if (facts == null || facts.isEmpty()) {
            return List.of();
        }
        Object value = facts.get("pathway.join." + joinNodeCode + ".completedNodeCodes");
        if (value == null) {
            value = facts.get("pathway.parallel.completedNodeCodes");
        }
        if (value instanceof Iterable<?> iterable) {
            ArrayList<String> codes = new ArrayList<>();
            for (Object item : iterable) {
                if (item != null && !isBlank(String.valueOf(item))) {
                    codes.add(String.valueOf(item));
                }
            }
            return List.copyOf(codes);
        }
        if (value instanceof String text && !isBlank(text)) {
            return List.of(text);
        }
        return List.of();
    }

    private String requiredConfigText(PathwayNode node, String field, String message) {
        String value = configText(node, field);
        if (isBlank(value)) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_006, message);
        }
        return value;
    }

    private String configText(PathwayNode node, String field) {
        if (isBlank(node.configJson())) {
            return null;
        }
        try {
            JsonNode value = json.readTree(node.configJson()).get(field);
            if (value == null || value.isNull()) {
                return null;
            }
            return value.asText();
        } catch (Exception exception) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_006, "路径节点配置 JSON 无法解析", exception);
        }
    }

    private boolean hasCondition(PathwayEdge edge) {
        return !isBlank(edge.conditionJson());
    }

    private boolean matchesCondition(
            PathwayEdge edge,
            Map<String, Object> facts,
            LinkedHashMap<String, Object> evidence,
            String runtimeReleaseId) {
        try {
            JsonNode condition = json.readTree(edge.conditionJson());
            if (condition.has("ruleRef")) {
                PathwayRuleGuardEvaluation evaluation =
                    ruleGuardEvaluator.evaluate(
                        condition, factsToContext(facts), runtimeReleaseId);
                recordRuleGuardEvidence(evaluation, evidence);
                return evaluation.matched();
            }
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

    private void recordRuleGuardEvidence(
            PathwayRuleGuardEvaluation evaluation,
            LinkedHashMap<String, Object> evidence) {
        evidence.put("pathway.ruleRef", evaluation.ruleCode());
        evidence.put("pathway.ruleId", evaluation.ruleId());
        evidence.put("pathway.ruleVersionId", evaluation.versionId());
        evidence.put("pathway.ruleVersionNo", evaluation.versionNo());
        evidence.put("pathway.runtimeReleaseId", evaluation.runtimeReleaseId());
        evidence.put("pathway.ruleSourceTenantId", evaluation.sourceTenantId());
        evidence.put("pathway.ruleSourceLayer", evaluation.sourceLayer().name());
        evidence.put("pathway.ruleMatched", evaluation.matched());
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
