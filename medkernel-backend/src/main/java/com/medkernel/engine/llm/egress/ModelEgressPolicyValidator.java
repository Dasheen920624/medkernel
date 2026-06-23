package com.medkernel.engine.llm.egress;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 模型出域白名单配置的统一可执行性校验器。
 *
 * <p>readiness 与运行时共用同一规则，避免管理面误报可用、运行时再阻断，或因配置畸形绕过最小化护栏。
 */
public final class ModelEgressPolicyValidator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> SENSITIVITY_LEVELS = Set.of("LOW", "MEDIUM", "HIGH");
    private static final Set<String> OPERATORS =
        Set.of("MASK", "MASK_ALL", "GENERALIZE", "NULLIFY", "NONE");

    private ModelEgressPolicyValidator() {
    }

    /** 校验并解析一条出域策略；失败结果不携带任何原始载荷。 */
    public static Validation validate(ModelEgressWhitelist whitelist) {
        if (whitelist == null) {
            return Validation.invalid("未配置出域字段白名单");
        }
        if (!"Y".equalsIgnoreCase(whitelist.guardrailLockedFlag())) {
            return Validation.invalid("出域最小化护栏未锁定");
        }
        if (!validLevel(whitelist.sensitivityLevel())) {
            return Validation.invalid("出域敏感级别无效");
        }
        if (!validLevel(whitelist.confirmationThresholdLevel())) {
            return Validation.invalid("出域责任确认阈值无效");
        }

        Set<String> allowedFields = new LinkedHashSet<>();
        try {
            JsonNode allowed = OBJECT_MAPPER.readTree(whitelist.allowedFields());
            if (allowed == null || !allowed.isArray()) {
                return Validation.invalid("出域字段白名单必须是 JSON 字符串数组");
            }
            for (JsonNode item : allowed) {
                if (!item.isTextual() || item.asText().isBlank()) {
                    return Validation.invalid("出域字段白名单含空值或非字符串字段");
                }
                allowedFields.add(item.asText().trim());
            }
        } catch (Exception invalidAllowedFields) {
            return Validation.invalid("出域字段白名单不是合法 JSON");
        }
        if (allowedFields.isEmpty()) {
            return Validation.invalid("出域字段白名单不能为空");
        }

        Map<String, String> rules = new LinkedHashMap<>();
        try {
            JsonNode configuredRules = OBJECT_MAPPER.readTree(whitelist.desensitizationRules());
            if (configuredRules == null || !configuredRules.isObject()) {
                return Validation.invalid("出域脱敏规则必须是 JSON 对象");
            }
            var fields = configuredRules.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String field = entry.getKey() == null ? "" : entry.getKey().trim();
                if (field.isBlank() || !allowedFields.contains(field)) {
                    return Validation.invalid("脱敏规则字段不在出域白名单内");
                }
                if (!entry.getValue().isTextual()) {
                    return Validation.invalid("出域脱敏算子必须是字符串");
                }
                String operator = entry.getValue().asText().trim().toUpperCase(Locale.ROOT);
                if (!OPERATORS.contains(operator)) {
                    return Validation.invalid("出域脱敏算子无效");
                }
                rules.put(field, operator);
            }
        } catch (Exception invalidRules) {
            return Validation.invalid("出域脱敏规则不是合法 JSON");
        }
        return Validation.valid(allowedFields, rules);
    }

    private static boolean validLevel(String value) {
        return value != null && SENSITIVITY_LEVELS.contains(value.trim().toUpperCase(Locale.ROOT));
    }

    /** 出域策略校验结果。 */
    public record Validation(
        boolean valid,
        Set<String> allowedFields,
        Map<String, String> desensitizationRules,
        String reason
    ) {
        public Validation {
            allowedFields = allowedFields == null ? Set.of() : Set.copyOf(allowedFields);
            desensitizationRules = desensitizationRules == null ? Map.of() : Map.copyOf(desensitizationRules);
        }

        private static Validation valid(Set<String> allowedFields, Map<String, String> rules) {
            return new Validation(true, allowedFields, rules, null);
        }

        private static Validation invalid(String reason) {
            return new Validation(false, Set.of(), Map.of(), reason);
        }
    }
}
