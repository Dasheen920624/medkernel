package com.medkernel.engine.versioning;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 通用版本发布计划仓库。
 */
@Repository
public interface VersionReleasePlanRepository extends ListCrudRepository<VersionReleasePlan, Long> {
    Optional<VersionReleasePlan> findFirstByTenantIdAndAssetTypeAndAssetIdentityAndVersionIdAndStatusAndTargetOrgPathAndApplicableScopeOrderByCreatedAtDesc(
        String tenantId,
        VersionedAssetType assetType,
        String assetIdentity,
        String versionId,
        VersionReleaseStatus status,
        String targetOrgPath,
        String applicableScope
    );
}
