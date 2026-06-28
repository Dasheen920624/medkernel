package com.medkernel.engine.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.InheritanceResolver;
import com.medkernel.engine.versioning.PlatformAuthority;
import com.medkernel.engine.versioning.ResolvedAssetVersion;
import com.medkernel.engine.versioning.SourceTier;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RuleEffectiveVersionResolverTest {

    private final RuleDefinitionRepository definitions = mock(RuleDefinitionRepository.class);
    private final RuleVersionRepository versions = mock(RuleVersionRepository.class);
    private final AssetVersionRepository assetVersions = mock(AssetVersionRepository.class);
    private final InheritanceResolver inheritanceResolver = mock(InheritanceResolver.class);
    private final RuleEffectiveVersionResolver resolver =
        new RuleEffectiveVersionResolver(definitions, versions, assetVersions, inheritanceResolver);

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void noOrgContextUsesPublishedUnifiedVersionInsteadOfAuthoringPointer() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace", OrgScope.tenant("tenant-A"), "clinical-user"));
        RuleDefinition rule = rule("tenant-A", "rv-old");
        RuleVersion unifiedContent = version("tenant-A", 2, "rv-v2");
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
            "tenant-A", VersionedAssetType.RULE, "RULE.A", AssetVersionStatus.PUBLISHED))
            .thenReturn(List.of(asset("tenant-A", "2", "tenant:tenant-A")));
        when(definitions.findByTenantIdAndRuleCode("tenant-A", "RULE.A"))
            .thenReturn(Optional.of(rule));
        when(versions.findByRuleIdAndTenantIdAndVersionNo("rule-a", "tenant-A", 2))
            .thenReturn(Optional.of(unifiedContent));

        RuleEffectiveVersionResolver.ResolvedRuleVersion resolved =
            resolver.resolve("tenant-A", "RULE.A", "ALL").orElseThrow();

        assertThat(resolved.version().versionId()).isEqualTo("rv-v2");
        assertThat(resolved.version().versionId()).isNotEqualTo(rule.activeVersionId());
    }

    @Test
    void noLocalPublishedVersionFallsBackToPlatformAuthority() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace", OrgScope.tenant("tenant-A"), "clinical-user"));
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
            "tenant-A", VersionedAssetType.RULE, "RULE.A", AssetVersionStatus.PUBLISHED))
            .thenReturn(List.of());
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
            PlatformAuthority.PLATFORM_TENANT_ID,
            VersionedAssetType.RULE,
            "RULE.A",
            AssetVersionStatus.PUBLISHED))
            .thenReturn(List.of(asset(
                PlatformAuthority.PLATFORM_TENANT_ID,
                "3",
                PlatformAuthority.PLATFORM_ORG_PATH)));
        RuleDefinition platform = rule(PlatformAuthority.PLATFORM_TENANT_ID, null);
        RuleVersion platformVersion = version(PlatformAuthority.PLATFORM_TENANT_ID, 3, "rv-platform-v3");
        when(definitions.findByTenantIdAndRuleCode(
            PlatformAuthority.PLATFORM_TENANT_ID, "RULE.A"))
            .thenReturn(Optional.of(platform));
        when(versions.findByRuleIdAndTenantIdAndVersionNo(
            "rule-a", PlatformAuthority.PLATFORM_TENANT_ID, 3))
            .thenReturn(Optional.of(platformVersion));

        RuleEffectiveVersionResolver.ResolvedRuleVersion resolved =
            resolver.resolve("tenant-A", "RULE.A", "ALL").orElseThrow();

        assertThat(resolved.assetVersion().tenantId())
            .isEqualTo(PlatformAuthority.PLATFORM_TENANT_ID);
        assertThat(resolved.version().versionId()).isEqualTo("rv-platform-v3");
    }

    @Test
    void platformContextDoesNotUsePublishedVersionFromAnotherOrganization() {
        RequestContext.restore(new RequestContext.Snapshot("trace", null, "clinical-user"));
        RuleDefinition rule = rule(PlatformAuthority.PLATFORM_TENANT_ID, null);
        RuleVersion content = version(PlatformAuthority.PLATFORM_TENANT_ID, 2, "rv-v2");
        when(definitions.findByTenantIdAndRuleCode(
            PlatformAuthority.PLATFORM_TENANT_ID, "RULE.A"))
            .thenReturn(Optional.of(rule));
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
            PlatformAuthority.PLATFORM_TENANT_ID,
            VersionedAssetType.RULE,
            "RULE.A",
            AssetVersionStatus.PUBLISHED))
            .thenReturn(List.of(asset(
                PlatformAuthority.PLATFORM_TENANT_ID,
                "2",
                "tenant:tenant-A/hospital:hospital-2")));
        when(versions.findByRuleIdAndTenantIdAndVersionNo(
            "rule-a", PlatformAuthority.PLATFORM_TENANT_ID, 2))
            .thenReturn(Optional.of(content));

        assertThat(resolver.resolve(
            PlatformAuthority.PLATFORM_TENANT_ID, "RULE.A", "ALL")).isEmpty();
    }

    @Test
    void orgContextMapsInheritanceResultToDomainContent() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace",
            new OrgScope("tenant-A", null, "hospital-1", null, null, null, null, null),
            "clinical-user"));
        AssetVersion selected = asset("tenant-A", "4", "tenant:tenant-A/hospital:hospital-1");
        when(inheritanceResolver.resolve(org.mockito.ArgumentMatchers.any()))
            .thenReturn(new ResolvedAssetVersion(
                selected, selected.organizationScope(), false, false, false, null, SourceTier.ORG));
        RuleDefinition rule = rule("tenant-A", "rv-old");
        RuleVersion content = version("tenant-A", 4, "rv-v4");
        when(definitions.findByTenantIdAndRuleCode("tenant-A", "RULE.A"))
            .thenReturn(Optional.of(rule));
        when(versions.findByRuleIdAndTenantIdAndVersionNo("rule-a", "tenant-A", 4))
            .thenReturn(Optional.of(content));

        RuleEffectiveVersionResolver.ResolvedRuleVersion resolved =
            resolver.resolve("tenant-A", "RULE.A", "ALL").orElseThrow();

        assertThat(resolved.version().versionId()).isEqualTo("rv-v4");
        assertThat(resolved.resolution()).isNotNull();
    }

    private RuleDefinition rule(String tenantId, String activeVersionId) {
        Instant now = Instant.parse("2026-06-09T00:00:00Z");
        return new RuleDefinition(
            1L, "rule-a", tenantId, "RULE.A", "测试规则", RuleType.ORDER,
            RuleAuthoringMode.DSL, RuleRiskLevel.MEDIUM, 100, null, 0,
            RuleDefinitionStatus.PUBLISHED, activeVersionId, null,
            now, "tester", now, "tester", "trace");
    }

    private RuleVersion version(String tenantId, int versionNo, String versionId) {
        Instant now = Instant.parse("2026-06-09T00:00:00Z");
        return new RuleVersion(
            1L, versionId, tenantId, "rule-a", versionNo, "SRC", "测试",
            "{}", "{}", RuleVersionStatus.PUBLISHED, now, "tester", null,
            now, "tester", now, "tester", "trace");
    }

    private AssetVersion asset(String tenantId, String versionNo, String orgPath) {
        Instant now = Instant.parse("2026-06-09T00:00:00Z");
        return new AssetVersion(
            1L, "av-rule-a-" + versionNo, tenantId, VersionedAssetType.RULE,
            "RULE.A", versionNo, orgPath, "ALL",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.FREE,
            AssetVersionStatus.PUBLISHED, "RULE.A|" + orgPath + "|ALL", "SRC",
            now, null, now, "tester", now, "tester", "trace");
    }
}
