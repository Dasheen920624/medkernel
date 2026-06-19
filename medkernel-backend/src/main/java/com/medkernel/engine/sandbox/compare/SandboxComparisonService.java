package com.medkernel.engine.sandbox.compare;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.medkernel.engine.rule.RuleActionResult;
import com.medkernel.engine.rule.RuleRiskLevel;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/** 在同一上下文上按规则业务编码比较两套冻结执行结果。 */
@Service
public class SandboxComparisonService {

    public SandboxComparisonResponse compare(
            String contextHash,
            List<SandboxComparableRuleResult> historical,
            List<SandboxComparableRuleResult> current) {
        if (contextHash == null || contextHash.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "对比上下文摘要不能为空");
        }
        Map<String, SandboxComparableRuleResult> historicalByCode = index(historical, "历史");
        Map<String, SandboxComparableRuleResult> currentByCode = index(current, "当前");
        Set<String> ruleCodes = new LinkedHashSet<>(historicalByCode.keySet());
        ruleCodes.addAll(currentByCode.keySet());

        List<SandboxRuleComparison> differences = new ArrayList<>();
        int unchanged = 0;
        for (String ruleCode : ruleCodes) {
            SandboxRuleComparison comparison = compareRule(
                ruleCode, historicalByCode.get(ruleCode), currentByCode.get(ruleCode));
            if (comparison.changes().isEmpty()) {
                unchanged++;
            } else {
                differences.add(comparison);
            }
        }
        differences.sort(Comparator
            .comparingInt(SandboxComparisonService::priority)
            .thenComparing(SandboxRuleComparison::ruleCode));
        return new SandboxComparisonResponse(
            contextHash.trim(), summary(differences), differences, unchanged);
    }

    private static SandboxRuleComparison compareRule(
            String ruleCode,
            SandboxComparableRuleResult historical,
            SandboxComparableRuleResult current) {
        String name = current != null ? current.ruleName() : historical.ruleName();
        if (historical == null || current == null) {
            String reason = historical == null
                ? "历史基线缺少规则资产，无法判断医学效果变化"
                : "当前基线缺少规则资产，无法判断医学效果变化";
            return new SandboxRuleComparison(
                ruleCode, name, false, reason,
                List.of(SandboxRuleDifferenceType.ASSET_MISSING), historical, current);
        }

        List<SandboxRuleDifferenceType> changes = new ArrayList<>();
        if (!historical.hit() && current.hit()) {
            changes.add(SandboxRuleDifferenceType.NEW_HIT);
        } else if (historical.hit() && !current.hit()) {
            changes.add(SandboxRuleDifferenceType.NO_LONGER_HIT);
        }
        int severityChange = severityRank(current.severity()) - severityRank(historical.severity());
        if (historical.hit() && current.hit() && severityChange > 0) {
            changes.add(SandboxRuleDifferenceType.SEVERITY_INCREASED);
        } else if (historical.hit() && current.hit() && severityChange < 0) {
            changes.add(SandboxRuleDifferenceType.SEVERITY_DECREASED);
        }
        if (historical.hit() && current.hit() && !equivalentActions(historical.actions(), current.actions())) {
            changes.add(SandboxRuleDifferenceType.ACTION_CHANGED);
        }
        if (historical.sourceTier() != current.sourceTier()) {
            changes.add(SandboxRuleDifferenceType.SOURCE_CHANGED);
        }
        if (!Objects.equals(historical.versionId(), current.versionId())
                || !Objects.equals(historical.assetVersion(), current.assetVersion())
                || !Objects.equals(historical.contentHash(), current.contentHash())) {
            changes.add(SandboxRuleDifferenceType.VERSION_CHANGED);
        }
        return new SandboxRuleComparison(ruleCode, name, true, null, changes, historical, current);
    }

    private static Map<String, SandboxComparableRuleResult> index(
            List<SandboxComparableRuleResult> source,
            String side) {
        Map<String, SandboxComparableRuleResult> indexed = new LinkedHashMap<>();
        for (SandboxComparableRuleResult item : source == null ? List.<SandboxComparableRuleResult>of() : source) {
            if (item == null || item.ruleCode() == null || item.ruleCode().isBlank()) {
                throw new ApiException(ErrorCode.CONFLICT, side + "基线存在无业务编码规则");
            }
            if (indexed.putIfAbsent(item.ruleCode(), item) != null) {
                throw new ApiException(ErrorCode.CONFLICT, side + "基线存在重复规则编码：" + item.ruleCode());
            }
        }
        return indexed;
    }

    private static SandboxComparisonSummary summary(List<SandboxRuleComparison> differences) {
        int newHits = count(differences, SandboxRuleDifferenceType.NEW_HIT);
        int goneHits = count(differences, SandboxRuleDifferenceType.NO_LONGER_HIT);
        int highRisk = count(differences, SandboxRuleDifferenceType.SEVERITY_INCREASED);
        int missing = count(differences, SandboxRuleDifferenceType.ASSET_MISSING);
        return new SandboxComparisonSummary(differences.size(), newHits, goneHits, highRisk, missing);
    }

    private static int count(
            List<SandboxRuleComparison> differences,
            SandboxRuleDifferenceType type) {
        return (int) differences.stream().filter(item -> item.changes().contains(type)).count();
    }

    private static int priority(SandboxRuleComparison item) {
        if (item.changes().contains(SandboxRuleDifferenceType.SEVERITY_INCREASED)) {
            return 0;
        }
        if (item.changes().contains(SandboxRuleDifferenceType.NEW_HIT)) {
            return 1;
        }
        if (item.changes().contains(SandboxRuleDifferenceType.NO_LONGER_HIT)) {
            return 2;
        }
        if (item.changes().contains(SandboxRuleDifferenceType.ACTION_CHANGED)) {
            return 3;
        }
        if (item.changes().contains(SandboxRuleDifferenceType.SOURCE_CHANGED)) {
            return 4;
        }
        if (item.changes().contains(SandboxRuleDifferenceType.VERSION_CHANGED)) {
            return 5;
        }
        return 6;
    }

    private static int severityRank(String value) {
        if (value == null || value.isBlank()) {
            return -1;
        }
        try {
            return RuleRiskLevel.valueOf(value).ordinal();
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.CONFLICT, "规则严重度不在闭集内：" + value, exception);
        }
    }

    private static boolean equivalentActions(
            List<RuleActionResult> historical,
            List<RuleActionResult> current) {
        if (historical.size() != current.size()) {
            return false;
        }
        for (int index = 0; index < historical.size(); index++) {
            RuleActionResult left = historical.get(index);
            RuleActionResult right = current.get(index);
            if (left.actionCode() != right.actionCode()
                    || !Objects.equals(left.indicator(), right.indicator())
                    || !Objects.equals(left.summary(), right.summary())
                    || !Objects.equals(left.detail(), right.detail())
                    || !Objects.equals(left.source(), right.source())
                    || !Objects.equals(left.suggestions(), right.suggestions())
                    || !Objects.equals(left.overrideReasons(), right.overrideReasons())
                    || left.requiresPhysicianConfirmation() != right.requiresPhysicianConfirmation()) {
                return false;
            }
        }
        return true;
    }
}
