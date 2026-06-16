package com.medkernel.engine.terminology;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 术语候选的确定性语义匹配器。
 *
 * <p>匹配依据只来自真实字典字段：编码、标准化别名、缩写和编码族；
 * 不把字面重叠或编辑距离当作医学语义等价依据。
 */
final class SemanticTermMatcher {

    private SemanticTermMatcher() {
    }

    static Optional<SemanticTermMatch> match(LocalTerm local, StandardTerm standard) {
        Optional<SemanticTermMatch> exactCode = matchExactCode(local, standard);
        if (exactCode.isPresent()) {
            return exactCode;
        }

        Set<String> localAliases = aliases(local.localName(), local.normalizedName(), local.localCode());
        Set<String> standardAliases = aliases(standard.displayName(), standard.normalizedName(), standard.termCode());
        for (String alias : localAliases) {
            if (standardAliases.contains(alias)) {
                return Optional.of(new SemanticTermMatch(
                    0.96,
                    TermRiskLevel.LOW,
                    "确定性语义匹配：同义词/缩写别名命中 `" + alias
                        + "`，院内词=" + local.localName() + "，标准词=" + standard.displayName()
                ));
            }
        }

        String localCode = canonical(local.localCode());
        String standardCode = canonical(standard.termCode());
        if (sameCodeFamily(localCode, standardCode)) {
            return Optional.of(new SemanticTermMatch(
                0.82,
                TermRiskLevel.MEDIUM,
                "确定性语义匹配：编码族命中，院内码=" + local.localCode()
                    + "，标准码=" + standard.termCode()
            ));
        }

        return Optional.empty();
    }

    static Optional<SemanticTermMatch> matchExactCode(LocalTerm local, StandardTerm standard) {
        String localCode = canonical(local.localCode());
        String standardCode = canonical(standard.termCode());
        if (!localCode.isBlank() && localCode.equals(standardCode)) {
            return Optional.of(new SemanticTermMatch(
                1.0,
                TermRiskLevel.LOW,
                "确定性语义匹配：精确编码命中，院内码=" + local.localCode()
                    + "，标准码=" + standard.termCode()
            ));
        }
        return Optional.empty();
    }

    static Set<String> aliases(String... values) {
        Set<String> aliases = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            addAlias(aliases, value);
            for (String part : value.split("[|,，;；/、]+")) {
                addAlias(aliases, part);
            }
        }
        return aliases;
    }

    private static void addAlias(Set<String> aliases, String value) {
        String normalized = canonical(value);
        if (isUsefulAlias(normalized)) {
            aliases.add(normalized);
        }
    }

    private static boolean isUsefulAlias(String value) {
        return value.length() >= 2;
    }

    static String canonical(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{IsHan}\\p{Alnum}]+", "");
    }

    static boolean sameCodeFamily(String localCode, String standardCode) {
        if (localCode.length() < 4 || standardCode.length() < 4) {
            return false;
        }
        return localCode.startsWith(standardCode) || standardCode.startsWith(localCode);
    }
}

record SemanticTermMatch(
    double score,
    TermRiskLevel riskLevel,
    String evidence
) {
}
