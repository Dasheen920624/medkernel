package com.medkernel.shared.architecture;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 领域模块所有权声明，用于 SYS-02 架构契约测试。
 */
public record DomainModule(
    String id,
    Set<String> ownedPackages,
    Set<String> tablePrefixes,
    Set<String> tableNames
) {
    public DomainModule {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("领域模块 id 不能为空");
        }
        ownedPackages = normalizePackages(ownedPackages);
        tablePrefixes = normalizeTokens(tablePrefixes);
        tableNames = normalizeTokens(tableNames);
    }

    public boolean ownsPackage(String packageName) {
        if (packageName == null || packageName.isBlank()) {
            return false;
        }
        return ownedPackages.stream()
            .anyMatch(root -> packageName.equals(root) || packageName.startsWith(root + "."));
    }

    public boolean ownsTable(String tableName) {
        String normalized = normalize(tableName);
        return tableNames.contains(normalized)
            || tablePrefixes.stream().anyMatch(normalized::startsWith);
    }

    private static Set<String> normalizePackages(Set<String> values) {
        return values == null ? Set.of() : values.stream()
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> normalizeTokens(Set<String> values) {
        return values == null ? Set.of() : values.stream()
            .map(DomainModule::normalize)
            .filter(value -> !value.isBlank())
            .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
