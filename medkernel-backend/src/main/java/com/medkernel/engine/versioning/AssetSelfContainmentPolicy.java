package com.medkernel.engine.versioning;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 规则与路径正文的单一自包含策略。
 *
 * <p>规则必须直接携带完整条件树；路径必须是单个无环图。共享条件片段、子路径、继承路径和
 * 路径嵌套会使离线完整包依赖来源库中的隐含对象，因此在候选生产和包导出两端统一拒绝。
 */
public final class AssetSelfContainmentPolicy {

    private static final Set<String> CONDITION_FRAGMENT_FIELDS = Set.of(
        "conditionFragments",
        "conditionFragmentRefs",
        "conditionFragmentRef",
        "fragmentRef");
    private static final Set<String> PATHWAY_NESTING_FIELDS = Set.of(
        "subPaths",
        "subPathRefs",
        "subPathway",
        "subPathwayRef",
        "childPathways",
        "inheritedPathway",
        "parentPathway",
        "extendsPathway");

    private AssetSelfContainmentPolicy() {
    }

    /** 校验规则不引用包外共享条件片段。 */
    public static void requireRuleSelfContained(JsonNode content) {
        rejectFields(content, CONDITION_FRAGMENT_FIELDS, "规则必须直接携带完整条件树，禁止共享条件片段");
    }

    /** 校验路径不引用共享片段或嵌套路径，并在存在标准节点边结构时拒绝环。 */
    public static void requirePathwaySelfContained(JsonNode content) {
        rejectFields(content, CONDITION_FRAGMENT_FIELDS, "路径条件必须直接携带完整条件树，禁止共享条件片段");
        rejectFields(content, PATHWAY_NESTING_FIELDS, "路径必须保持单图自包含，禁止子路径或继承嵌套");
        rejectGraphCycle(content);
    }

    private static void rejectFields(JsonNode node, Set<String> forbidden, String message) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if (forbidden.contains(entry.getKey())) {
                    throw invalid(message + ": " + entry.getKey());
                }
                rejectFields(entry.getValue(), forbidden, message);
            });
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> rejectFields(item, forbidden, message));
        }
    }

    private static void rejectGraphCycle(JsonNode content) {
        if (content == null || !content.isObject()) {
            return;
        }
        JsonNode nodes = content.path("nodes");
        JsonNode edges = content.path("edges");
        if (!nodes.isArray() || !edges.isArray()) {
            return;
        }
        Map<String, List<String>> adjacency = new HashMap<>();
        for (JsonNode node : nodes) {
            String code = text(node, "nodeCode");
            if (code != null) {
                adjacency.putIfAbsent(code, new ArrayList<>());
            }
        }
        for (JsonNode edge : edges) {
            String from = text(edge, "fromNodeCode");
            String to = text(edge, "toNodeCode");
            if (from != null && to != null && adjacency.containsKey(from) && adjacency.containsKey(to)) {
                adjacency.get(from).add(to);
            }
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String node : adjacency.keySet()) {
            detectCycle(node, adjacency, visiting, visited, new ArrayDeque<>());
        }
    }

    private static void detectCycle(
            String node,
            Map<String, List<String>> adjacency,
            Set<String> visiting,
            Set<String> visited,
            ArrayDeque<String> path) {
        if (visited.contains(node)) {
            return;
        }
        if (!visiting.add(node)) {
            path.addLast(node);
            throw invalid("路径图存在循环: " + String.join(" -> ", path));
        }
        path.addLast(node);
        for (String next : adjacency.getOrDefault(node, List.of())) {
            detectCycle(next, adjacency, visiting, visited, path);
        }
        path.removeLast();
        visiting.remove(node);
        visited.add(node);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isTextual() || value.asText().isBlank()
            ? null
            : value.asText().trim();
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }
}
