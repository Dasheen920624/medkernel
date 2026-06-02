package com.medkernel.engine.terminology;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MED-C1 高危近似术语判别器。
 *
 * <p>只解释数据库中的规则定义：互斥词组、剂量量级、单位强度。
 * 命中结果用于召回待人工复核候选，不代表映射可自动确认。
 */
final class HighRiskTermDetector {

    private static final double HIGH_RISK_RECALL_SCORE = 0.5;
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    private HighRiskTermDetector() {
    }

    static Optional<HighRiskTermMatch> detect(LocalTerm local, StandardTerm standard, List<HighRiskRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return Optional.empty();
        }
        for (HighRiskRule rule : rules) {
            if (rule.status() != HighRiskRuleStatus.ACTIVE || !categoryMatches(rule, local, standard)) {
                continue;
            }
            if (matches(rule, local, standard)) {
                return Optional.of(new HighRiskTermMatch(
                    HIGH_RISK_RECALL_SCORE,
                    "高危近似判别：" + rule.evidenceText()
                        + "，候选仅供人工复核，禁止批量确认，必须逐条二次确认"
                ));
            }
        }
        return Optional.empty();
    }

    private static boolean categoryMatches(HighRiskRule rule, LocalTerm local, StandardTerm standard) {
        if (rule.category() == null) {
            return true;
        }
        return rule.category() == local.category() || rule.category() == standard.category();
    }

    private static boolean matches(HighRiskRule rule, LocalTerm local, StandardTerm standard) {
        return switch (rule.ruleType()) {
            case MUTUALLY_EXCLUSIVE_TERMS -> mutuallyExclusiveTerms(rule, local, standard);
            case DOSE_MAGNITUDE -> doseMagnitude(rule, local, standard);
            case UNIT_STRENGTH -> unitStrength(rule, local, standard);
        };
    }

    private static boolean mutuallyExclusiveTerms(HighRiskRule rule, LocalTerm local, StandardTerm standard) {
        ClinicalText localText = clinicalText(local);
        ClinicalText standardText = clinicalText(standard);
        boolean localLeft = containsAny(localText, rule.leftTerms());
        boolean standardRight = containsAny(standardText, rule.rightTerms());
        boolean localRight = containsAny(localText, rule.rightTerms());
        boolean standardLeft = containsAny(standardText, rule.leftTerms());
        return (localLeft && standardRight) || (localRight && standardLeft);
    }

    private static boolean doseMagnitude(HighRiskRule rule, LocalTerm local, StandardTerm standard) {
        ClinicalText localText = clinicalText(local);
        ClinicalText standardText = clinicalText(standard);
        ClinicalText combinedText = localText.merge(standardText);
        if (!containsAny(combinedText, rule.unitTerms())) {
            return false;
        }
        if (!sameClinicalStem(localText.compact(), standardText.compact(), rule.unitTerms())) {
            return false;
        }
        double threshold = rule.scaleRatio() == null ? 10.0 : rule.scaleRatio();
        for (Double localNumber : numbers(localText.compact())) {
            for (Double standardNumber : numbers(standardText.compact())) {
                double smaller = Math.min(localNumber, standardNumber);
                double larger = Math.max(localNumber, standardNumber);
                if (smaller > 0 && larger / smaller >= threshold) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean unitStrength(HighRiskRule rule, LocalTerm local, StandardTerm standard) {
        ClinicalText localText = clinicalText(local);
        ClinicalText standardText = clinicalText(standard);
        return containsAny(localText, rule.leftTerms())
            && containsAny(standardText, rule.leftTerms())
            && containsAny(localText.merge(standardText), rule.unitTerms());
    }

    private static boolean sameClinicalStem(String localText, String standardText, String unitTerms) {
        String localStem = stripNumbersAndUnits(localText, unitTerms);
        String standardStem = stripNumbersAndUnits(standardText, unitTerms);
        if (localStem.length() < 2 || standardStem.length() < 2) {
            return false;
        }
        return localStem.contains(standardStem) || standardStem.contains(localStem);
    }

    private static String stripNumbersAndUnits(String text, String unitTerms) {
        String stripped = NUMBER_PATTERN.matcher(text).replaceAll("");
        for (RuleTerm term : splitTerms(unitTerms)) {
            stripped = stripped.replace(term.normalized(), "");
        }
        return stripped;
    }

    private static boolean containsAny(ClinicalText text, String terms) {
        if (text == null || text.compact().isBlank()) {
            return false;
        }
        for (RuleTerm term : splitTerms(terms)) {
            if (term.normalized().isBlank()) {
                continue;
            }
            if (term.tokenOnly() && text.tokens().contains(term.normalized())) {
                return true;
            }
            if (!term.tokenOnly()
                && (text.tokens().contains(term.normalized()) || text.compact().contains(term.normalized()))) {
                return true;
            }
        }
        return false;
    }

    private static List<RuleTerm> splitTerms(String terms) {
        List<RuleTerm> values = new ArrayList<>();
        if (terms == null || terms.isBlank()) {
            return values;
        }
        for (String term : terms.split("[|,，;；、]+")) {
            String normalized = canonical(term);
            if (!normalized.isBlank()) {
                values.add(new RuleTerm(normalized, requiresTokenOnly(normalized)));
            }
        }
        return values;
    }

    private static boolean requiresTokenOnly(String term) {
        return "k".equals(term)
            || "na".equals(term)
            || "left".equals(term)
            || "right".equals(term);
    }

    private static List<Double> numbers(String text) {
        List<Double> values = new ArrayList<>();
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        while (matcher.find()) {
            values.add(Double.parseDouble(matcher.group(1)));
        }
        return values;
    }

    private static ClinicalText clinicalText(LocalTerm local) {
        return clinicalText(List.of(
            value(local.localCode()),
            value(local.localName()),
            value(local.normalizedName())
        ));
    }

    private static ClinicalText clinicalText(StandardTerm standard) {
        return clinicalText(List.of(
            value(standard.standardSystem()),
            value(standard.termCode()),
            value(standard.displayName()),
            value(standard.normalizedName()),
            value(standard.evidenceText())
        ));
    }

    private static ClinicalText clinicalText(List<String> fragments) {
        List<String> tokens = new ArrayList<>();
        StringBuilder compact = new StringBuilder();
        for (String fragment : fragments) {
            String normalized = canonical(fragment);
            compact.append(normalized);
            for (String token : fragment.split("[^\\p{IsHan}\\p{Alnum}]+")) {
                String normalizedToken = canonical(token);
                if (!normalizedToken.isBlank()) {
                    tokens.add(normalizedToken);
                }
            }
        }
        return new ClinicalText(compact.toString(), tokens);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String canonical(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{IsHan}\\p{Alnum}.]+", "");
    }
}

record ClinicalText(
    String compact,
    List<String> tokens
) {
    ClinicalText merge(ClinicalText other) {
        List<String> mergedTokens = new ArrayList<>(tokens);
        mergedTokens.addAll(other.tokens);
        return new ClinicalText(compact + other.compact, mergedTokens);
    }
}

record RuleTerm(
    String normalized,
    boolean tokenOnly
) {
}

record HighRiskTermMatch(
    double score,
    String evidence
) {
}
