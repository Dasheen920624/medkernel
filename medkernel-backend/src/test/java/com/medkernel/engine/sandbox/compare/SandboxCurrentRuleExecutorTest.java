package com.medkernel.engine.sandbox.compare;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.pkg.EffectiveKnowledgePackageResponse;
import com.medkernel.engine.pkg.EffectivePackageItem;
import com.medkernel.engine.rule.RuleAuthoringMode;
import com.medkernel.engine.rule.RuleDefinition;
import com.medkernel.engine.rule.RuleDefinitionRepository;
import com.medkernel.engine.rule.RuleDefinitionStatus;
import com.medkernel.engine.rule.RuleDslEvaluator;
import com.medkernel.engine.rule.RuleRiskLevel;
import com.medkernel.engine.rule.RuleType;
import com.medkernel.engine.rule.RuleVersion;
import com.medkernel.engine.rule.RuleVersionRepository;
import com.medkernel.engine.rule.RuleVersionStatus;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.SourceTier;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SandboxCurrentRuleExecutorTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final AssetVersionRepository assets = mock(AssetVersionRepository.class);
    private final RuleDefinitionRepository definitions = mock(RuleDefinitionRepository.class);
    private final RuleVersionRepository versions = mock(RuleVersionRepository.class);
    private final SandboxCurrentRuleExecutor executor = new SandboxCurrentRuleExecutor(
        json, new RuleDslEvaluator(json), assets, definitions, versions);

    @Test
    void executesTheExactFrozenCurrentRuleVersionAgainstProvidedImmutableContext() throws Exception {
        EffectivePackageItem item = item("hash-2");
        when(assets.findByVersionIdAndTenantId("asset-version-2", "tenant-A"))
            .thenReturn(Optional.of(asset("hash-2")));
        when(definitions.findByTenantIdAndRuleCode("tenant-A", "RULE.K"))
            .thenReturn(Optional.of(rule()));
        when(versions.findByRuleIdAndTenantIdAndVersionNo("rule-id", "tenant-A", 2))
            .thenReturn(Optional.of(version()));
        var context = json.readTree("""
            {"resources":{"patient":{"mpi":"DEID-P-1"},
             "observations":[{"code":"K","value":6.8}]}}
            """);

        List<SandboxComparableRuleResult> results = executor.execute(
            effective(item), context);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.ruleCode()).isEqualTo("RULE.K");
            assertThat(result.versionId()).isEqualTo("asset-version-2");
            assertThat(result.assetVersion()).isEqualTo("2");
            assertThat(result.sourceTier()).isEqualTo(SourceTier.ORG);
            assertThat(result.hit()).isTrue();
            assertThat(result.severity()).isEqualTo("CRITICAL");
        });
    }

    @Test
    void rejectsContentHashDriftInsteadOfExecutingCurrentMutableState() {
        when(assets.findByVersionIdAndTenantId("asset-version-2", "tenant-A"))
            .thenReturn(Optional.of(asset("different-hash")));

        assertThatThrownBy(() -> executor.execute(
            effective(item("hash-2")), json.createObjectNode()))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("摘要漂移");
    }

    private static EffectiveKnowledgePackageResponse effective(EffectivePackageItem item) {
        return new EffectiveKnowledgePackageResponse(
            "tenant-A", "hospital-A", "pkg-1", "PKG.SANDBOX", "current",
            List.of(item), List.of(), List.of());
    }

    private static EffectivePackageItem item(String hash) {
        return new EffectivePackageItem(
            VersionedAssetType.RULE, "rule-id", "2", "2", "tenant-A", "/tenant-A",
            SourceTier.ORG, false, false, true, "asset-version-2", hash);
    }

    private static AssetVersion asset(String hash) {
        Instant now = Instant.parse("2026-06-19T00:00:00Z");
        return new AssetVersion(
            1L, "asset-version-2", "tenant-A", VersionedAssetType.RULE, "RULE.K", "2",
            "/tenant-A", "ALL", hash, null, null, AssetVersionStatus.PUBLISHED, "RULE.K|ALL",
            "src", now, null, now, "governor", now, "governor", "trace");
    }

    private static RuleDefinition rule() {
        Instant now = Instant.parse("2026-06-19T00:00:00Z");
        return new RuleDefinition(
            1L, "rule-id", "tenant-A", "RULE.K", "高钾规则", RuleType.LAB,
            RuleAuthoringMode.DSL, RuleRiskLevel.CRITICAL, 100, null, 0,
            RuleDefinitionStatus.PUBLISHED, "rule-version-2", "current", "hospital-A",
            now, "governor", now, "governor", "trace");
    }

    private static RuleVersion version() {
        Instant now = Instant.parse("2026-06-19T00:00:00Z");
        return new RuleVersion(
            1L, "rule-version-2", "tenant-A", "rule-id", 2, "src", "调整阈值",
            """
            {"when":{"all":[{"expr":{"field":"observations[].value"},"operator":"gte","value":6.5}]},
             "then":[{"actionCode":"BLOCK","atSeverity":"CRITICAL","indicator":"critical",
             "summary":"高钾红线","detail":"请人工复核","source":{"label":"规则来源"},
             "suggestions":[],"overrideReasons":["复核结果"],"requiresPhysicianConfirmation":true}]}
            """,
            "{}", RuleVersionStatus.PUBLISHED, now, "governor", null,
            now, "governor", now, "governor", "trace");
    }
}
