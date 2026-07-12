package com.medkernel.engine.authoring;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ContextFieldPathPolicy;
import com.medkernel.engine.versioning.AssetDependencyDeclaration;
import com.medkernel.engine.versioning.AssetDependencyKind;
import com.medkernel.engine.versioning.AssetSelfContainmentPolicy;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 自动生成路径候选的结构校验。
 *
 * <p>路径保持单个自包含图，可调用规则稳定身份，但禁止条件片段、子路径、路径嵌套和循环图。
 */
@Component
public class GeneratedPathwayCandidateValidator implements GeneratedAssetCandidateValidator {

    private static final String SCHEMA_VERSION = "1.0";
    private static final Set<String> PATHWAY_TRIGGER_PURPOSES =
        Set.of("PATHWAY_ENTRY_CANDIDATE", "PATHWAY_PROGRESS");

    private final ObjectMapper json;

    public GeneratedPathwayCandidateValidator(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public VersionedAssetType assetType() {
        return VersionedAssetType.PATHWAY;
    }

    @Override
    public GeneratedAssetValidation validate(String assetIdentity, JsonNode content) {
        requireSchema(content);
        String pathwayCode = requiredText(content, "pathwayCode");
        if (!pathwayCode.equals(assetIdentity)) {
            throw invalid("路径稳定编码必须与资产身份一致");
        }
        requiredText(content, "name");
        AssetSelfContainmentPolicy.requirePathwaySelfContained(content);
        String startNode = requiredText(content, "startNodeCode");
        Set<String> terminals = requireTerminalNodes(content);
        validateTriggerBindings(content);
        rejectUnknownFields(ContextFieldPathPolicy.ruleDslFields(content));
        Graph graph = validateGraph(content, startNode, terminals);

        Map<String, AssetDependencyDeclaration> dependencies = new LinkedHashMap<>();
        for (String ruleIdentity : ruleReferences(content)) {
            add(dependencies, VersionedAssetType.RULE, ruleIdentity, AssetDependencyKind.RULE);
        }
        graph.ensureAcyclic();
        graph.ensureTerminalReachable();
        return new GeneratedAssetValidation(canonical(content), new ArrayList<>(dependencies.values()));
    }

    private Graph validateGraph(JsonNode content, String startNode, Set<String> terminals) {
        JsonNode nodes = requiredArray(content, "nodes");
        if (nodes.isEmpty()) {
            throw invalid("路径 nodes 不能为空");
        }
        Set<String> nodeCodes = new HashSet<>();
        for (JsonNode node : nodes) {
            requireObject(node, "路径节点");
            String nodeCode = requiredText(node, "nodeCode");
            requiredText(node, "nodeType");
            if (!nodeCodes.add(nodeCode)) {
                throw invalid("路径节点编码重复：" + nodeCode);
            }
            rejectUnknownFields(nodeFields(node));
        }
        if (!nodeCodes.contains(startNode)) {
            throw invalid("路径起点不存在：" + startNode);
        }
        for (String terminal : terminals) {
            if (!nodeCodes.contains(terminal)) {
                throw invalid("路径终点不存在：" + terminal);
            }
        }
        Map<String, List<String>> adjacency = new HashMap<>();
        nodeCodes.forEach(code -> adjacency.put(code, new ArrayList<>()));
        JsonNode edges = requiredArray(content, "edges");
        for (JsonNode edge : edges) {
            requireObject(edge, "路径边");
            requiredText(edge, "edgeCode");
            String from = requiredText(edge, "fromNodeCode");
            String to = requiredText(edge, "toNodeCode");
            if (!nodeCodes.contains(from) || !nodeCodes.contains(to)) {
                throw invalid("路径边引用了不存在的节点：" + from + " -> " + to);
            }
            adjacency.get(from).add(to);
        }
        return new Graph(startNode, terminals, adjacency);
    }

    private List<String> nodeFields(JsonNode node) {
        JsonNode fields = node.path("fields");
        if (fields.isMissingNode() || fields.isNull()) {
            return List.of();
        }
        if (!fields.isArray()) {
            throw invalid("路径节点 fields 必须是数组");
        }
        List<String> result = new ArrayList<>();
        for (JsonNode field : fields) {
            if (!field.isTextual() || field.asText().isBlank()) {
                throw invalid("路径节点 fields 只能包含非空字符串");
            }
            result.add(field.asText().trim());
        }
        return result;
    }

    private void rejectUnknownFields(List<String> fields) {
        List<String> unknown = ContextFieldPathPolicy.unknownFields(fields);
        if (!unknown.isEmpty()) {
            throw invalid("字段目录不存在：" + String.join(", ", unknown));
        }
    }

    private void validateTriggerBindings(JsonNode content) {
        JsonNode triggers = requiredArray(content, "triggerBindings");
        if (triggers.isEmpty()) {
            throw invalid("路径 triggerBindings 不能为空");
        }
        for (JsonNode trigger : triggers) {
            requireObject(trigger, "路径触发绑定");
            requiredText(trigger, "triggerPoint");
            String purpose = requiredText(trigger, "purpose");
            if (!PATHWAY_TRIGGER_PURPOSES.contains(purpose)) {
                throw invalid("路径触发用途必须为 " + PATHWAY_TRIGGER_PURPOSES);
            }
        }
    }

    private Set<String> requireTerminalNodes(JsonNode content) {
        JsonNode terminalNodes = requiredArray(content, "terminalNodeCodes");
        if (terminalNodes.isEmpty()) {
            throw invalid("路径 terminalNodeCodes 不能为空");
        }
        Set<String> terminals = new HashSet<>();
        for (JsonNode terminal : terminalNodes) {
            if (!terminal.isTextual() || terminal.asText().isBlank()) {
                throw invalid("terminalNodeCodes 只能包含非空字符串");
            }
            terminals.add(terminal.asText().trim());
        }
        return terminals;
    }

    private List<String> ruleReferences(JsonNode content) {
        Set<String> refs = new java.util.LinkedHashSet<>();
        JsonNode declared = content.path("ruleReferences");
        if (declared.isArray()) {
            for (JsonNode ref : declared) {
                if (ref.isTextual() && !ref.asText().isBlank()) {
                    refs.add(ref.asText().trim());
                }
            }
        }
        collectRuleIdentities(content, refs);
        return List.copyOf(refs);
    }

    private void collectRuleIdentities(JsonNode node, Set<String> refs) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if (("ruleIdentity".equals(entry.getKey()) || "ruleStableIdentity".equals(entry.getKey()))
                        && entry.getValue().isTextual()
                        && !entry.getValue().asText().isBlank()) {
                    refs.add(entry.getValue().asText().trim());
                }
                collectRuleIdentities(entry.getValue(), refs);
            });
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> collectRuleIdentities(item, refs));
        }
    }

    private void add(
            Map<String, AssetDependencyDeclaration> dependencies,
            VersionedAssetType type,
            String identity,
            AssetDependencyKind kind) {
        dependencies.putIfAbsent(type + "|" + identity + "|" + kind,
            new AssetDependencyDeclaration(type, identity, null, null, kind));
    }

    private void requireSchema(JsonNode content) {
        if (content == null || !content.isObject()) {
            throw invalid("路径生成正文必须是 JSON 对象");
        }
        if (!SCHEMA_VERSION.equals(requiredText(content, "schemaVersion"))) {
            throw invalid("schemaVersion 仅支持 " + SCHEMA_VERSION);
        }
    }

    private JsonNode requiredObject(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isObject()) {
            throw invalid(field + " 必须是 JSON 对象");
        }
        return value;
    }

    private void requireObject(JsonNode node, String label) {
        if (node == null || !node.isObject()) {
            throw invalid(label + " 必须是 JSON 对象");
        }
    }

    private JsonNode requiredArray(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isArray()) {
            throw invalid(field + " 必须是数组");
        }
        return value;
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw invalid(field + " 不能为空");
        }
        return value.asText().trim();
    }

    private String canonical(JsonNode content) {
        try {
            return json.writeValueAsString(content);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "路径生成正文无法序列化", exception);
        }
    }

    private ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }

    private final class Graph {
        private final String startNode;
        private final Set<String> terminals;
        private final Map<String, List<String>> adjacency;

        private Graph(String startNode, Set<String> terminals, Map<String, List<String>> adjacency) {
            this.startNode = startNode;
            this.terminals = terminals;
            this.adjacency = adjacency;
        }

        private void ensureTerminalReachable() {
            Set<String> visited = new HashSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(startNode);
            visited.add(startNode);
            while (!queue.isEmpty()) {
                String node = queue.removeFirst();
                if (terminals.contains(node)) {
                    return;
                }
                for (String next : adjacency.getOrDefault(node, List.of())) {
                    if (visited.add(next)) {
                        queue.addLast(next);
                    }
                }
            }
            throw invalid("路径从起点无法到达任何终点");
        }

        private void ensureAcyclic() {
            Set<String> visiting = new HashSet<>();
            Set<String> visited = new HashSet<>();
            for (String node : adjacency.keySet()) {
                detectCycle(node, visiting, visited, new ArrayDeque<>());
            }
        }

        private void detectCycle(
                String node,
                Set<String> visiting,
                Set<String> visited,
                ArrayDeque<String> stack) {
            if (visited.contains(node)) {
                return;
            }
            if (!visiting.add(node)) {
                throw invalid("路径图存在环: " + node);
            }
            stack.addLast(node);
            for (String next : adjacency.getOrDefault(node, List.of())) {
                detectCycle(next, visiting, visited, stack);
            }
            stack.removeLast();
            visiting.remove(node);
            visited.add(node);
        }
    }
}
