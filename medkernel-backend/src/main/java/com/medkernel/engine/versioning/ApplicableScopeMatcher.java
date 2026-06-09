package com.medkernel.engine.versioning;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 解析并匹配配置资产的结构化适用范围。
 *
 * <p>候选范围声明的维度必须是查询维度的子集；未声明维度视为通配。
 * 非结构化范围不做别名或模糊匹配，只允许完全相等。
 */
public final class ApplicableScopeMatcher {

    private static final String ALL = "ALL";

    private ApplicableScopeMatcher() {
    }

    public static String canonicalQuery(
            String specialty,
            String scenario,
            String careSetting,
            String cohort,
            String role) {
        List<String> parts = new ArrayList<>();
        append(parts, ScopeDimension.SPECIALTY, specialty);
        append(parts, ScopeDimension.SCENARIO, scenario);
        append(parts, ScopeDimension.CARE_SETTING, careSetting);
        append(parts, ScopeDimension.COHORT, cohort);
        append(parts, ScopeDimension.ROLE, role);
        return parts.isEmpty() ? ALL : String.join(";", parts);
    }

    public static String validateDeclaration(String scope) {
        ParsedScope parsed = parse(scope);
        if (parsed.wildcard()) {
            return ALL;
        }
        return parsed.normalized();
    }

    public static boolean matches(String candidateScope, String queryScope) {
        ParsedScope candidate = parse(candidateScope);
        ParsedScope query = parse(queryScope);
        if (candidate.wildcard()) {
            return true;
        }
        if (candidate.opaque() || query.opaque()) {
            return candidate.normalized().equals(query.normalized());
        }
        for (Map.Entry<ScopeDimension, Set<String>> entry : candidate.dimensions().entrySet()) {
            Set<String> queryValues = query.dimensions().get(entry.getKey());
            if (queryValues == null || queryValues.stream().noneMatch(entry.getValue()::contains)) {
                return false;
            }
        }
        return true;
    }

    public static int specificityOf(String scope) {
        ParsedScope parsed = parse(scope);
        if (parsed.wildcard()) {
            return 0;
        }
        if (parsed.opaque()) {
            return 1;
        }
        int valueCount = parsed.dimensions().values().stream().mapToInt(Set::size).sum();
        return parsed.dimensions().size() * 1000 - valueCount;
    }

    private static ParsedScope parse(String rawScope) {
        String normalized = normalize(rawScope);
        if (normalized == null || ALL.equalsIgnoreCase(normalized)) {
            return new ParsedScope(ALL, true, false, Map.of());
        }
        if (!normalized.contains("=")) {
            if (normalized.contains(";")) {
                throw invalid("applicableScope 格式不合法: " + normalized);
            }
            if (ScopeDimension.fromWireName(normalized).isPresent()) {
                throw invalid("applicableScope 格式不合法: " + normalized);
            }
            return new ParsedScope(normalized, false, true, Map.of());
        }

        Map<ScopeDimension, Set<String>> dimensions = new EnumMap<>(ScopeDimension.class);
        for (String segment : normalized.split(";")) {
            String part = segment.trim();
            int separator = part.indexOf('=');
            if (separator <= 0 || separator == part.length() - 1 || part.indexOf('=', separator + 1) >= 0) {
                throw invalid("applicableScope 格式不合法: " + normalized);
            }
            String key = part.substring(0, separator).trim();
            ScopeDimension dimension = ScopeDimension.fromWireName(key)
                .orElseThrow(() -> invalid("未知作用域维度: " + key));
            if (dimensions.containsKey(dimension)) {
                throw invalid("applicableScope 维度重复: " + key);
            }
            Set<String> values = new LinkedHashSet<>();
            for (String rawValue : part.substring(separator + 1).split(",")) {
                String value = normalize(rawValue);
                if (value == null || !values.add(value)) {
                    throw invalid("applicableScope 维度值为空或重复: " + key);
                }
            }
            dimensions.put(dimension, Set.copyOf(values));
        }
        return new ParsedScope(normalized, false, false, Map.copyOf(dimensions));
    }

    private static void append(List<String> parts, ScopeDimension dimension, String value) {
        String normalized = normalize(value);
        if (normalized != null) {
            if (normalized.indexOf(';') >= 0
                    || normalized.indexOf(',') >= 0
                    || normalized.indexOf('=') >= 0) {
                throw invalid("作用域维度值不能包含保留分隔符: " + dimension.wireName());
            }
            parts.add(dimension.wireName() + "=" + normalized);
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }

    private record ParsedScope(
        String normalized,
        boolean wildcard,
        boolean opaque,
        Map<ScopeDimension, Set<String>> dimensions
    ) {
    }
}
