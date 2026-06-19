package com.medkernel.engine.sandbox.compare;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.cdshook.CdsHookSource;
import com.medkernel.engine.rule.RuleActionCode;
import com.medkernel.engine.rule.RuleActionResult;
import com.medkernel.engine.rule.RuleRiskLevel;
import com.medkernel.engine.versioning.SourceTier;

import static org.assertj.core.api.Assertions.assertThat;

class SandboxComparisonServiceTest {

    private final SandboxComparisonService service = new SandboxComparisonService();

    @Test
    void reportsHitSeverityActionSourceAndVersionChangesByStableRuleCode() {
        List<SandboxComparableRuleResult> historical = List.of(
            result("new-hit", false, null, "old", SourceTier.PLATFORM, "platform", "v1", "1", "a"),
            result("gone-hit", true, "MEDIUM", "old", SourceTier.ORG, "tenant-a", "v1", "1", "a"),
            result("risk-up", true, "LOW", "old", SourceTier.ORG, "tenant-a", "v1", "1", "a"),
            result("action", true, "MEDIUM", "old", SourceTier.ORG, "tenant-a", "v1", "1", "a"),
            result("source", true, "MEDIUM", "same", SourceTier.PLATFORM, "platform", "v1", "1", "a"),
            result("version", true, "MEDIUM", "same", SourceTier.ORG, "tenant-a", "v1", "1", "a"));
        List<SandboxComparableRuleResult> current = List.of(
            result("version", true, "MEDIUM", "same", SourceTier.ORG, "tenant-a", "v2", "2", "b"),
            result("source", true, "MEDIUM", "same", SourceTier.ORG, "tenant-a", "v1", "1", "a"),
            result("action", true, "MEDIUM", "new", SourceTier.ORG, "tenant-a", "v1", "1", "a"),
            result("risk-up", true, "CRITICAL", "old", SourceTier.ORG, "tenant-a", "v1", "1", "a"),
            result("gone-hit", false, null, "old", SourceTier.ORG, "tenant-a", "v1", "1", "a"),
            result("new-hit", true, "MEDIUM", "old", SourceTier.PLATFORM, "platform", "v1", "1", "a"));

        SandboxComparisonResponse response = service.compare("context-hash", historical, current);

        assertThat(response.contextHash()).isEqualTo("context-hash");
        assertThat(response.differences()).extracting(SandboxRuleComparison::ruleCode)
            .containsExactly("risk-up", "new-hit", "gone-hit", "action", "source", "version");
        assertThat(changes(response, "new-hit")).containsExactly(SandboxRuleDifferenceType.NEW_HIT);
        assertThat(changes(response, "gone-hit")).containsExactly(SandboxRuleDifferenceType.NO_LONGER_HIT);
        assertThat(changes(response, "risk-up")).containsExactly(SandboxRuleDifferenceType.SEVERITY_INCREASED);
        assertThat(changes(response, "action")).containsExactly(SandboxRuleDifferenceType.ACTION_CHANGED);
        assertThat(changes(response, "source")).containsExactly(SandboxRuleDifferenceType.SOURCE_CHANGED);
        assertThat(changes(response, "version")).containsExactly(SandboxRuleDifferenceType.VERSION_CHANGED);
        assertThat(response.summary().newHitCount()).isEqualTo(1);
        assertThat(response.summary().noLongerHitCount()).isEqualTo(1);
        assertThat(response.summary().highRiskChangeCount()).isEqualTo(1);
    }

    @Test
    void reportsMissingAssetAsNonComparableAndKeepsUnchangedRowsCollapsedByDefault() {
        SandboxComparableRuleResult onlyHistorical =
            result("old-only", true, "MEDIUM", "old", SourceTier.ORG, "tenant-a", "v1", "1", "a");
        SandboxComparableRuleResult unchanged =
            result("same", false, null, "same", SourceTier.ORG, "tenant-a", "v1", "1", "a");

        SandboxComparisonResponse response = service.compare(
            "context-hash",
            List.of(onlyHistorical, unchanged),
            List.of(unchanged));

        assertThat(response.differences()).hasSize(1);
        SandboxRuleComparison missing = response.differences().getFirst();
        assertThat(missing.ruleCode()).isEqualTo("old-only");
        assertThat(missing.comparable()).isFalse();
        assertThat(missing.changes()).containsExactly(SandboxRuleDifferenceType.ASSET_MISSING);
        assertThat(missing.nonComparableReason()).contains("当前基线缺少规则资产");
        assertThat(response.unchangedCount()).isEqualTo(1);
        assertThat(response.summary().nonComparableCount()).isEqualTo(1);
    }

    @Test
    void doesNotCompareDeidentifiedHistoricalOrganizationAliasWithCurrentTenantId() {
        SandboxComparableRuleResult historical = result(
            "same-tier", true, "MEDIUM", "same", SourceTier.ORG,
            "sha256:" + "a".repeat(64), "v1", "1", "a");
        SandboxComparableRuleResult current = result(
            "same-tier", true, "MEDIUM", "same", SourceTier.ORG,
            "tenant-A", "v1", "1", "a");

        SandboxComparisonResponse response = service.compare(
            "context-hash", List.of(historical), List.of(current));

        assertThat(response.differences()).isEmpty();
        assertThat(response.unchangedCount()).isEqualTo(1);
    }

    private static List<SandboxRuleDifferenceType> changes(
            SandboxComparisonResponse response,
            String ruleCode) {
        return response.differences().stream()
            .filter(item -> ruleCode.equals(item.ruleCode()))
            .findFirst()
            .orElseThrow()
            .changes();
    }

    private static SandboxComparableRuleResult result(
            String ruleCode,
            boolean hit,
            String severity,
            String actionSummary,
            SourceTier sourceTier,
            String sourceTenantId,
            String versionId,
            String assetVersion,
            String contentHash) {
        List<RuleActionResult> actions = hit
            ? List.of(new RuleActionResult(
                RuleActionCode.REMIND,
                severity == null ? RuleRiskLevel.LOW : RuleRiskLevel.valueOf(severity),
                "warning",
                actionSummary,
                "detail",
                new CdsHookSource("source", null, null),
                List.of(),
                List.of("已人工复核"),
                true))
            : List.of();
        return new SandboxComparableRuleResult(
            ruleCode,
            ruleCode,
            versionId,
            assetVersion,
            sourceTier,
            sourceTenantId,
            contentHash,
            hit,
            severity,
            actions,
            new ObjectMapper().createObjectNode());
    }
}
