package com.medkernel.engine.versioning;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 继承覆盖解释仓库。
 */
@Repository
public interface InheritanceOverrideRepository extends ListCrudRepository<InheritanceOverride, Long> {

    Optional<InheritanceOverride> findByTenantIdAndOverrideId(String tenantId, String overrideId);

    Optional<InheritanceOverride> findByTenantIdAndOverrideVersionId(String tenantId, String overrideVersionId);

    List<InheritanceOverride> findByAssetTypeAndAssetIdentityAndLifecycleStatus(
        VersionedAssetType assetType,
        String assetIdentity,
        InheritanceOverrideStatus lifecycleStatus
    );

    List<InheritanceOverride> findByTenantIdAndAssetTypeAndAssetIdentityAndLifecycleStatus(
        String tenantId,
        VersionedAssetType assetType,
        String assetIdentity,
        InheritanceOverrideStatus lifecycleStatus
    );

    List<InheritanceOverride> findByTenantIdAndAssetTypeAndAssetIdentity(
        String tenantId,
        VersionedAssetType assetType,
        String assetIdentity
    );

    /**
     * 按组织生效域与适用人群直查指定方式的覆盖（用于解析期消费无替换版本的 DISABLE 停用，
     * 其 {@code override_version_id} 为空、无法经 {@link #findByTenantIdAndOverrideVersionId} 命中）。
     */
    List<InheritanceOverride> findByTenantIdAndAssetTypeAndAssetIdentityAndOrgPathAndApplicableScopeAndOverrideMode(
        String tenantId,
        VersionedAssetType assetType,
        String assetIdentity,
        String orgPath,
        String applicableScope,
        InheritanceOverrideMode overrideMode
    );

    List<InheritanceOverride> findByTenantIdAndAssetTypeAndAssetIdentityAndOrgPathAndApplicableScopeAndLifecycleStatus(
        String tenantId,
        VersionedAssetType assetType,
        String assetIdentity,
        String orgPath,
        String applicableScope,
        InheritanceOverrideStatus lifecycleStatus
    );

    List<InheritanceOverride> findByTenantIdAndOrgPathAndLifecycleStatus(
        String tenantId,
        String orgPath,
        InheritanceOverrideStatus lifecycleStatus
    );

    List<InheritanceOverride> findByTenantIdAndAssetTypeAndAssetIdentityAndOrgPathAndApplicableScopeAndOverrideModeAndLifecycleStatus(
        String tenantId,
        VersionedAssetType assetType,
        String assetIdentity,
        String orgPath,
        String applicableScope,
        InheritanceOverrideMode overrideMode,
        InheritanceOverrideStatus lifecycleStatus
    );

    List<InheritanceOverride> findByTenantIdAndOrgPathInAndLifecycleStatusIn(
        String tenantId,
        Collection<String> orgPaths,
        Collection<InheritanceOverrideStatus> lifecycleStatuses
    );
}
