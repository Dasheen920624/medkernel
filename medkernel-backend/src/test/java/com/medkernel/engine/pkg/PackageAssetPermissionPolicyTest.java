package com.medkernel.engine.pkg;

import java.util.List;

import com.medkernel.engine.security.PermissionCode;
import com.medkernel.engine.versioning.VersionedAssetType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PackageAssetPermissionPolicyTest {

    @Test
    void homogeneousDomainPackagesUseTheirDomainPublishPermission() {
        assertThat(PackageAssetPermissionPolicy.publishPermission(
            List.of(VersionedAssetType.TERMINOLOGY)))
            .contains(PermissionCode.TERM_PUBLISH);
        assertThat(PackageAssetPermissionPolicy.publishPermission(
            List.of(VersionedAssetType.PATHWAY, VersionedAssetType.PATHWAY)))
            .contains(PermissionCode.PATHWAY_PUBLISH);
        assertThat(PackageAssetPermissionPolicy.publishPermission(
            List.of(VersionedAssetType.RULE)))
            .contains(PermissionCode.RULE_PUBLISH);
        assertThat(PackageAssetPermissionPolicy.publishPermission(
            List.of(VersionedAssetType.KNOWLEDGE)))
            .contains(PermissionCode.KNOWLEDGE_PUBLISH);
        assertThat(PackageAssetPermissionPolicy.publishPermission(
            List.of(VersionedAssetType.EVALUATION)))
            .contains(PermissionCode.EVALUATION_PUBLISH);
    }

    @Test
    void mixedEmptyAndInfrastructurePackagesRequireGenericPackagePermission() {
        assertThat(PackageAssetPermissionPolicy.publishPermission(List.of())).isEmpty();
        assertThat(PackageAssetPermissionPolicy.publishPermission(
            List.of(VersionedAssetType.TERMINOLOGY, VersionedAssetType.PATHWAY))).isEmpty();
        assertThat(PackageAssetPermissionPolicy.publishPermission(
            List.of(VersionedAssetType.FIELD_CATALOG))).isEmpty();
    }
}
