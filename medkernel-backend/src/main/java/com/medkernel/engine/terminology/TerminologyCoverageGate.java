package com.medkernel.engine.terminology;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.engine.context.ContextFieldCatalogService;
import com.medkernel.engine.context.ContextFieldDescriptor;

/**
 * 规则/路径发布前的标准编码对照覆盖门禁。
 *
 * <p>只对上下文字段目录中明确绑定标准字典的字段生效；未知字段不臆测，已知编码字段必须
 * 具备标准术语且至少一条已确认院内对照，避免上线后真实院内数据无法归一命中。
 */
@Service
public class TerminologyCoverageGate {

    private static final String CONTEXT_PREFIX = "context.";

    private final ContextFieldCatalogService fieldCatalogService;
    private final TerminologyService terminologyService;
    private final boolean disabled;

    @Autowired
    public TerminologyCoverageGate(
        ContextFieldCatalogService fieldCatalogService,
        TerminologyService terminologyService) {
        this(fieldCatalogService, terminologyService, false);
    }

    private TerminologyCoverageGate(
        ContextFieldCatalogService fieldCatalogService,
        TerminologyService terminologyService,
        boolean disabled) {
        this.fieldCatalogService = fieldCatalogService;
        this.terminologyService = terminologyService;
        this.disabled = disabled;
    }

    public static TerminologyCoverageGate noop() {
        return new TerminologyCoverageGate(null, null, true);
    }

    public List<TerminologyCoverageIssue> checkConditionCoverage(JsonNode condition) {
        if (disabled || condition == null || condition.isMissingNode() || !condition.isObject()) {
            return List.of();
        }
        Map<String, String> codeSystemByFieldPath = codeSystemByFieldPath();
        if (codeSystemByFieldPath.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, LinkedHashSet<String>> codesBySystem = new LinkedHashMap<>();
        LinkedHashMap<String, String> fieldPathBySystemCode = new LinkedHashMap<>();
        collectConditionCodes(condition, codeSystemByFieldPath, codesBySystem, fieldPathBySystemCode);
        if (codesBySystem.isEmpty()) {
            return List.of();
        }

        List<TerminologyCoverageIssue> issues = new ArrayList<>();
        for (Map.Entry<String, LinkedHashSet<String>> entry : codesBySystem.entrySet()) {
            String codeSystem = entry.getKey();
            List<MappingCoverageItem> coverage =
                terminologyService.evaluateCoverage(codeSystem, List.copyOf(entry.getValue()));
            for (MappingCoverageItem item : coverage) {
                if (MappingCoverageItem.COVERED.equals(item.status())) {
                    continue;
                }
                issues.add(new TerminologyCoverageIssue(
                    fieldPathBySystemCode.getOrDefault(systemCodeKey(codeSystem, item.code()), ""),
                    codeSystem,
                    item.code(),
                    item.status(),
                    item.mappedLocalCount()));
            }
        }
        return issues;
    }

    public static String describeIssues(List<TerminologyCoverageIssue> issues) {
        return issues.stream()
            .map(issue -> issue.codeSystem() + ":" + issue.code()
                + "（" + issue.status() + "，字段 " + issue.fieldPath() + "）")
            .toList()
            .toString();
    }

    private Map<String, String> codeSystemByFieldPath() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (ContextFieldDescriptor field : fieldCatalogService.query(null, null)) {
            if (isBlank(field.fieldPath()) || isBlank(field.codeSystem())) {
                continue;
            }
            result.put(normalizeFact(field.fieldPath()), field.codeSystem().trim());
        }
        return result;
    }

    private void collectConditionCodes(
        JsonNode node,
        Map<String, String> codeSystemByFieldPath,
        LinkedHashMap<String, LinkedHashSet<String>> codesBySystem,
        LinkedHashMap<String, String> fieldPathBySystemCode) {
        if (node == null || !node.isObject()) {
            return;
        }
        collectChildren(node.get("all"), codeSystemByFieldPath, codesBySystem, fieldPathBySystemCode);
        collectChildren(node.get("any"), codeSystemByFieldPath, codesBySystem, fieldPathBySystemCode);
        JsonNode not = node.get("not");
        if (not != null && not.isObject()) {
            collectConditionCodes(not, codeSystemByFieldPath, codesBySystem, fieldPathBySystemCode);
        }
        String fieldPath = normalizeFact(node.path("fact").asText(null));
        String codeSystem = codeSystemByFieldPath.get(fieldPath);
        if (isBlank(codeSystem)) {
            return;
        }
        for (String code : codeValues(node.get("value"))) {
            codesBySystem.computeIfAbsent(codeSystem, ignored -> new LinkedHashSet<>()).add(code);
            fieldPathBySystemCode.putIfAbsent(systemCodeKey(codeSystem, code), fieldPath);
        }
    }

    private void collectChildren(
        JsonNode children,
        Map<String, String> codeSystemByFieldPath,
        LinkedHashMap<String, LinkedHashSet<String>> codesBySystem,
        LinkedHashMap<String, String> fieldPathBySystemCode) {
        if (children == null || !children.isArray()) {
            return;
        }
        for (JsonNode child : children) {
            collectConditionCodes(child, codeSystemByFieldPath, codesBySystem, fieldPathBySystemCode);
        }
    }

    private List<String> codeValues(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return List.of();
        }
        if (value.isArray()) {
            List<String> values = new ArrayList<>();
            value.forEach(item -> values.addAll(codeValues(item)));
            return values;
        }
        if (value.isObject()) {
            return List.of("code", "termCode", "standardCode", "value").stream()
                .map(value::get)
                .filter(Objects::nonNull)
                .flatMap(node -> codeValues(node).stream())
                .toList();
        }
        String code = value.asText(null);
        return isBlank(code) ? List.of() : List.of(code.trim());
    }

    private String normalizeFact(String fact) {
        if (fact == null) {
            return "";
        }
        String normalized = fact.trim();
        if (normalized.startsWith(CONTEXT_PREFIX)) {
            return normalized.substring(CONTEXT_PREFIX.length());
        }
        return normalized;
    }

    private String systemCodeKey(String codeSystem, String code) {
        return codeSystem + "\u0000" + code;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
