package com.medkernel.engine.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContent;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContentResolver;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItem;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.release.ReleaseSourceLayer;
import com.medkernel.engine.versioning.AssetTriggerBinding;
import com.medkernel.engine.versioning.AssetTriggerBindingRepository;
import com.medkernel.engine.versioning.AssetTriggerPurpose;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.PlatformTenant;

class RuntimeReleaseRuleSelectorTest {

    private static final Instant NOW = Instant.parse("2026-06-22T08:00:00Z");

    private final ClinicalRuntimeReleaseContentResolver runtime =
        mock(ClinicalRuntimeReleaseContentResolver.class);
    private final RuleDefinitionRepository definitions = mock(RuleDefinitionRepository.class);
    private final RuleVersionRepository versions = mock(RuleVersionRepository.class);
    private final AssetTriggerBindingRepository triggers =
        mock(AssetTriggerBindingRepository.class);
    private final RuntimeReleaseRuleSelector selector =
        new RuntimeReleaseRuleSelector(runtime, definitions, versions, triggers);

    @Test
    void selectsExactRuleVersionsFromTheHospitalRuntimeRelease() {
        ClinicalRuntimeReleaseItem authorityRule =
            ruleItem(PlatformTenant.ID, "rule-a", "V2", ReleaseSourceLayer.PLATFORM);
        ClinicalRuntimeReleaseItem hospitalRule =
            ruleItem("tenant-A", "rule-b", "V3", ReleaseSourceLayer.HOSPITAL);
        when(runtime.resolve("tenant-A", "release-4")).thenReturn(new ClinicalRuntimeReleaseContent(
            release(), List.of(authorityRule, hospitalRule)));
        when(definitions.findByTenantIdAndRuleCode(PlatformTenant.ID, "RULE.rule-a"))
            .thenReturn(Optional.of(rule(PlatformTenant.ID, "rule-a", "rv-a-current")));
        when(definitions.findByTenantIdAndRuleCode("tenant-A", "RULE.rule-b"))
            .thenReturn(Optional.of(rule("tenant-A", "rule-b", "rv-b-current")));
        when(versions.findByRuleIdAndTenantIdAndVersionNo("rule-a", PlatformTenant.ID, 2))
            .thenReturn(Optional.of(version(PlatformTenant.ID, "rv-a-2", "rule-a", 2)));
        when(versions.findByRuleIdAndTenantIdAndVersionNo("rule-b", "tenant-A", 3))
            .thenReturn(Optional.of(version("tenant-A", "rv-b-3", "rule-b", 3)));
        when(triggers
            .findByTenantIdAndVersionIdAndPurposeAndTriggerPointOrderByTriggerBindingIdAsc(
                PlatformTenant.ID, "asset-version-rule-a",
                AssetTriggerPurpose.RULE_EXECUTION, "patient-view"))
            .thenReturn(List.of(binding(
                PlatformTenant.ID, "asset-version-rule-a", "RULE.rule-a", "patient-view")));
        when(triggers
            .findByTenantIdAndVersionIdAndPurposeAndTriggerPointOrderByTriggerBindingIdAsc(
                "tenant-A", "asset-version-rule-b",
                AssetTriggerPurpose.RULE_EXECUTION, "patient-view"))
            .thenReturn(List.of(binding(
                "tenant-A", "asset-version-rule-b", "RULE.rule-b", "patient-view")));

        RuntimeRuleSelection selection =
            selector.select("tenant-A", "release-4", "patient-view");

        assertThat(selection.runtimeReleaseId()).isEqualTo("release-4");
        assertThat(selection.platformBaselineReleaseId()).isEqualTo("baseline-A8");
        assertThat(selection.rules())
            .extracting(RuntimeRuleReference::tenantId, RuntimeRuleReference::ruleId,
                RuntimeRuleReference::versionId)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(PlatformTenant.ID, "rule-a", "rv-a-2"),
                org.assertj.core.groups.Tuple.tuple("tenant-A", "rule-b", "rv-b-3"));
    }

    @Test
    void ignoresUnboundTriggerButRejectsMissingPinnedRuleVersion() {
        ClinicalRuntimeReleaseItem rule =
            ruleItem(PlatformTenant.ID, "rule-a", "V2", ReleaseSourceLayer.PLATFORM);
        when(runtime.resolve("tenant-A", "release-4")).thenReturn(new ClinicalRuntimeReleaseContent(
            release(), List.of(rule)));
        assertThat(selector.select("tenant-A", "release-4", "patient-view").rules()).isEmpty();

        when(triggers
            .findByTenantIdAndVersionIdAndPurposeAndTriggerPointOrderByTriggerBindingIdAsc(
                PlatformTenant.ID, "asset-version-rule-a",
                AssetTriggerPurpose.RULE_EXECUTION, "patient-view"))
            .thenReturn(List.of(binding(
                PlatformTenant.ID, "asset-version-rule-a", "RULE.rule-a", "patient-view")));
        when(definitions.findByTenantIdAndRuleCode(PlatformTenant.ID, "RULE.rule-a"))
            .thenReturn(Optional.of(rule(PlatformTenant.ID, "rule-a", "rv-current")));
        when(versions.findByRuleIdAndTenantIdAndVersionNo("rule-a", PlatformTenant.ID, 2))
            .thenReturn(Optional.empty());
        assertThatThrownBy(() -> selector.select("tenant-A", "release-4", "patient-view"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("锁定规则版本不存在");
    }

    private ClinicalRuntimeRelease release() {
        return new ClinicalRuntimeRelease(
            4L, "release-4", "tenant-A", "hospital-A", 4L,
            "baseline-A8", "a".repeat(64), null,
            NOW, "tester", NOW, "tester", "trace");
    }

    private ClinicalRuntimeReleaseItem ruleItem(
            String tenantId,
            String ruleId,
            String version,
            ReleaseSourceLayer sourceLayer) {
        return new ClinicalRuntimeReleaseItem(
            1L, "release-4", tenantId, sourceLayer,
            VersionedAssetType.RULE, "RULE." + ruleId, ReleaseEntryState.ACTIVE,
            "asset-version-" + ruleId, version, "b".repeat(64),
            NOW, "tester", "trace");
    }

    private RuleDefinition rule(String tenantId, String ruleId, String activeVersionId) {
        return new RuleDefinition(
            1L, ruleId, tenantId, "RULE." + ruleId, ruleId,
            RuleType.DIAGNOSIS, RuleAuthoringMode.DSL, RuleRiskLevel.MEDIUM,
            100, null, 0, RuleDefinitionStatus.PUBLISHED,
            activeVersionId, null,
            NOW, "tester", NOW, "tester", "trace");
    }

    private RuleVersion version(
            String tenantId,
            String versionId,
            String ruleId,
            int versionNo) {
        return new RuleVersion(
            1L, versionId, tenantId, ruleId, versionNo,
            "GUIDE-1", "测试", """
                {"when":{"==":[1,1]},"then":[],"explain":"测试"}
                """,
            "{}", RuleVersionStatus.PUBLISHED, NOW, "tester", null,
            NOW, "tester", NOW, "tester", "trace");
    }

    private AssetTriggerBinding binding(
            String tenantId,
            String versionId,
            String assetIdentity,
            String triggerPoint) {
        return new AssetTriggerBinding(
            1L,
            "trigger-" + versionId,
            tenantId,
            VersionedAssetType.RULE,
            assetIdentity,
            versionId,
            triggerPoint,
            AssetTriggerPurpose.RULE_EXECUTION,
            "[]",
            NOW,
            "tester",
            NOW,
            "tester",
            "trace"
        );
    }
}
