package com.medkernel.engine.pkg;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.medkernel.engine.security.PermissionCode;
import com.medkernel.engine.security.PermissionEvaluator;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PackageAssetPermissionEvaluatorTest {

    private final KnowledgePackageRepository packageRepository = mock(KnowledgePackageRepository.class);
    private final PackageItemRepository itemRepository = mock(PackageItemRepository.class);
    private final PermissionEvaluator permissionEvaluator = mock(PermissionEvaluator.class);
    private final PackageAssetPermissionEvaluator evaluator = new PackageAssetPermissionEvaluator(
        packageRepository, itemRepository, permissionEvaluator);

    @Test
    void genericPackagePublisherCanPublishAnyExistingPackage() {
        when(permissionEvaluator.has(PermissionCode.PACKAGE_PUBLISH)).thenReturn(true);

        assertThat(evaluator.canPublish("pkg-any")).isTrue();
    }

    @Test
    void terminologyPublisherCanPublishOnlyHomogeneousTerminologyPackageInCurrentTenant() throws Exception {
        when(permissionEvaluator.has(PermissionCode.PACKAGE_PUBLISH)).thenReturn(false);
        when(permissionEvaluator.has(PermissionCode.TERM_PUBLISH)).thenReturn(true);
        when(packageRepository.findByPackageIdAndTenantId("pkg-term", "tenant-A"))
            .thenReturn(Optional.of(packageRecord("pkg-term")));
        when(itemRepository.findByTenantIdAndPackageId("tenant-A", "pkg-term"))
            .thenReturn(List.of(item("pkg-term", VersionedAssetType.TERMINOLOGY)));

        boolean allowed = RequestContext.callWith(
            new RequestContext.Snapshot("trace-1", OrgScope.tenant("tenant-A"), "medical-1"),
            () -> evaluator.canPublish("pkg-term"));

        assertThat(allowed).isTrue();
    }

    @Test
    void terminologyPermissionCannotPublishPathwayOrMixedPackages() {
        when(permissionEvaluator.has(PermissionCode.PACKAGE_PUBLISH)).thenReturn(false);
        when(permissionEvaluator.has(PermissionCode.TERM_PUBLISH)).thenReturn(true);
        when(packageRepository.findByPackageIdAndTenantId("pkg-pathway", "tenant-A"))
            .thenReturn(Optional.of(packageRecord("pkg-pathway")));
        when(itemRepository.findByTenantIdAndPackageId("tenant-A", "pkg-pathway"))
            .thenReturn(List.of(item("pkg-pathway", VersionedAssetType.PATHWAY)));
        when(packageRepository.findByPackageIdAndTenantId("pkg-mixed", "tenant-A"))
            .thenReturn(Optional.of(packageRecord("pkg-mixed")));
        when(itemRepository.findByTenantIdAndPackageId("tenant-A", "pkg-mixed"))
            .thenReturn(List.of(
                item("pkg-mixed", VersionedAssetType.TERMINOLOGY),
                item("pkg-mixed", VersionedAssetType.PATHWAY)));

        RequestContext.runWith(
            new RequestContext.Snapshot("trace-1", OrgScope.tenant("tenant-A"), "medical-1"),
            () -> {
                assertThat(evaluator.canPublish("pkg-pathway")).isFalse();
                assertThat(evaluator.canPublish("pkg-mixed")).isFalse();
            });
    }

    @Test
    void domainPermissionDoesNotAuthorizeMissingTenantOrUnknownPackage() throws Exception {
        when(permissionEvaluator.has(PermissionCode.PACKAGE_PUBLISH)).thenReturn(false);
        when(permissionEvaluator.has(PermissionCode.TERM_PUBLISH)).thenReturn(true);
        when(packageRepository.findByPackageIdAndTenantId("pkg-missing", "tenant-A"))
            .thenReturn(Optional.empty());

        assertThat(evaluator.canPublish("pkg-missing")).isFalse();
        boolean allowed = RequestContext.callWith(
            new RequestContext.Snapshot("trace-1", OrgScope.tenant("tenant-A"), "medical-1"),
            () -> evaluator.canPublish("pkg-missing"));
        assertThat(allowed).isFalse();
    }

    private KnowledgePackage packageRecord(String packageId) {
        Instant now = Instant.parse("2026-06-09T00:00:00Z");
        return new KnowledgePackage(
            1L,
            packageId,
            "tenant-A",
            "TERM.TEST",
            "1.0.0",
            "测试包",
            "权限测试",
            KnowledgePackageStatus.DRAFT,
            now,
            "tester",
            now,
            "tester",
            "trace-1");
    }

    private PackageItem item(String packageId, VersionedAssetType assetType) {
        Instant now = Instant.parse("2026-06-09T00:00:00Z");
        return new PackageItem(
            1L,
            packageId + "-" + assetType,
            "tenant-A",
            packageId,
            assetType,
            assetType.name() + "-1",
            "1.0.0",
            now,
            "tester",
            now,
            "tester",
            "trace-1");
    }
}
