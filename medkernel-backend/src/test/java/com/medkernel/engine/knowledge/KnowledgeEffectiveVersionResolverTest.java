package com.medkernel.engine.knowledge;

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
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class KnowledgeEffectiveVersionResolverTest {

    private final KnowledgeIdentityRepository identities = mock(KnowledgeIdentityRepository.class);
    private final KnowledgeAssetVersionRepository versions = mock(KnowledgeAssetVersionRepository.class);
    private final AssetVersionRepository assetVersions = mock(AssetVersionRepository.class);
    private final InheritanceResolver inheritanceResolver = mock(InheritanceResolver.class);
    private final KnowledgeEffectiveVersionResolver resolver =
        new KnowledgeEffectiveVersionResolver(identities, versions, assetVersions, inheritanceResolver);

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void unifiedPublishedVersionOverridesLegacyCurrentVersionPointer() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace", OrgScope.tenant("tenant-A"), "clinical-user"));
        KnowledgeIdentity identity = identity("tenant-A", 100L);
        KnowledgeAssetVersion content = version("tenant-A", 2L, "v2");
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
            "tenant-A", VersionedAssetType.KNOWLEDGE, "KNOW.A", AssetVersionStatus.PUBLISHED))
            .thenReturn(List.of(asset("tenant-A", "v2", "tenant:tenant-A")));
        when(identities.findByTenantIdAndIdentityCode("tenant-A", "KNOW.A"))
            .thenReturn(Optional.of(identity));
        when(versions.findByTenantIdAndIdentityIdAndVersionNo("tenant-A", 1L, "v2"))
            .thenReturn(Optional.of(content));

        KnowledgeEffectiveVersionResolver.ResolvedKnowledgeVersion resolved =
            resolver.resolve("tenant-A", "KNOW.A", "ALL").orElseThrow();

        assertThat(resolved.version().id()).isEqualTo(2L);
        assertThat(resolved.version().id()).isNotEqualTo(identity.currentVersionId());
    }

    @Test
    void tenantWithoutLocalVersionUsesPlatformAuthorityPath() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace", OrgScope.tenant("tenant-A"), "clinical-user"));
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
            "tenant-A", VersionedAssetType.KNOWLEDGE, "KNOW.A", AssetVersionStatus.PUBLISHED))
            .thenReturn(List.of());
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
            PlatformAuthority.PLATFORM_TENANT_ID,
            VersionedAssetType.KNOWLEDGE,
            "KNOW.A",
            AssetVersionStatus.PUBLISHED))
            .thenReturn(List.of(asset(
                PlatformAuthority.PLATFORM_TENANT_ID,
                "v3",
                PlatformAuthority.PLATFORM_ORG_PATH)));
        KnowledgeIdentity platform = identity(PlatformAuthority.PLATFORM_TENANT_ID, null);
        KnowledgeAssetVersion content =
            version(PlatformAuthority.PLATFORM_TENANT_ID, 3L, "v3");
        when(identities.findByTenantIdAndIdentityCode(
            PlatformAuthority.PLATFORM_TENANT_ID, "KNOW.A"))
            .thenReturn(Optional.of(platform));
        when(versions.findByTenantIdAndIdentityIdAndVersionNo(
            PlatformAuthority.PLATFORM_TENANT_ID, 1L, "v3"))
            .thenReturn(Optional.of(content));

        KnowledgeEffectiveVersionResolver.ResolvedKnowledgeVersion resolved =
            resolver.resolve("tenant-A", "KNOW.A", "ALL").orElseThrow();

        assertThat(resolved.assetVersion().organizationScope())
            .isEqualTo(PlatformAuthority.PLATFORM_ORG_PATH);
        assertThat(resolved.version().id()).isEqualTo(3L);
    }

    @Test
    void canonicalUnifiedVersionResolvesDomainVersionThroughSourceRef() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace", OrgScope.tenant("tenant-A"), "clinical-user"));
        KnowledgeIdentity identity = identity("tenant-A", 100L);
        KnowledgeAssetVersion content = version("tenant-A", 2L, "ai-draft-task-1");
        String sourceRef = "knowledge-version:KNOW.A:ai-draft-task-1";
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
            "tenant-A", VersionedAssetType.KNOWLEDGE, "KNOW.A", AssetVersionStatus.PUBLISHED))
            .thenReturn(List.of(asset("tenant-A", "V1", "tenant:tenant-A", sourceRef)));
        when(identities.findByTenantIdAndIdentityCode("tenant-A", "KNOW.A"))
            .thenReturn(Optional.of(identity));
        when(versions.findByTenantIdAndIdentityIdAndVersionNo("tenant-A", 1L, "V1"))
            .thenReturn(Optional.empty());
        when(versions.findByTenantIdAndIdentityIdAndVersionNo("tenant-A", 1L, "ai-draft-task-1"))
            .thenReturn(Optional.of(content));

        KnowledgeEffectiveVersionResolver.ResolvedKnowledgeVersion resolved =
            resolver.resolve("tenant-A", "KNOW.A", "ALL").orElseThrow();

        assertThat(resolved.assetVersion().versionNo()).isEqualTo("V1");
        assertThat(resolved.version().versionNo()).isEqualTo("ai-draft-task-1");
        assertThat(resolved.version().id()).isEqualTo(2L);
    }

    @Test
    void missingOrgContextRejectsAmbiguousOrganizationVersions() {
        RequestContext.restore(new RequestContext.Snapshot("trace", null, "clinical-user"));
        KnowledgeIdentity identity = identity("tenant-A", null);
        KnowledgeAssetVersion content = version("tenant-A", 2L, "v2");
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
            "tenant-A", VersionedAssetType.KNOWLEDGE, "KNOW.A", AssetVersionStatus.PUBLISHED))
            .thenReturn(List.of(
                asset("tenant-A", "v1", "tenant:tenant-A/hospital:hospital-1"),
                asset("tenant-A", "v2", "tenant:tenant-A/hospital:hospital-2")
            ));
        when(identities.findByTenantIdAndIdentityCode("tenant-A", "KNOW.A"))
            .thenReturn(Optional.of(identity));
        when(versions.findByTenantIdAndIdentityIdAndVersionNo("tenant-A", 1L, "v2"))
            .thenReturn(Optional.of(content));

        assertThat(resolver.resolve("tenant-A", "KNOW.A", "ALL")).isEmpty();
    }

    private KnowledgeIdentity identity(String tenantId, Long currentVersionId) {
        Instant now = Instant.parse("2026-06-09T00:00:00Z");
        return new KnowledgeIdentity(
            1L, tenantId, "KNOW.A", KnowledgeDomain.GUIDELINE, "测试知识",
            null, null, KnowledgeIdentityStatus.ACTIVE, currentVersionId,
            now, "tester", now, "tester");
    }

    private KnowledgeAssetVersion version(String tenantId, Long id, String versionNo) {
        Instant now = Instant.parse("2026-06-09T00:00:00Z");
        return new KnowledgeAssetVersion(
            id, tenantId, 1L, versionNo, versionNo, null, null, "a".repeat(64), null,
            KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW,
            SourceAuthorityLevel.B_GUIDELINE, GradeEvidenceQuality.HIGH,
            GradeRecommendationStrength.STRONG, null,
            "tenant:" + tenantId, KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE,
            KnowledgeAssetVersion.activeScopeKey(
                1L, "tenant:" + tenantId, KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE),
            now, null, "reviewer", now, now, null, null, null,
            now, "tester", now, "tester", 12, null);
    }

    private AssetVersion asset(String tenantId, String versionNo, String orgPath) {
        return asset(tenantId, versionNo, orgPath, "SRC");
    }

    private AssetVersion asset(String tenantId, String versionNo, String orgPath, String sourceRef) {
        Instant now = Instant.parse("2026-06-09T00:00:00Z");
        return new AssetVersion(
            1L, "av-knowledge-" + versionNo, tenantId, VersionedAssetType.KNOWLEDGE,
            "KNOW.A", versionNo, orgPath, "ALL", "a".repeat(64),
            AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.FREE,
            AssetVersionStatus.PUBLISHED, "KNOW.A|" + orgPath + "|ALL", sourceRef,
            now, null, now, "tester", now, "tester", "trace");
    }
}
