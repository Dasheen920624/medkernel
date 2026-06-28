package com.medkernel.engine.versioning;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.stereotype.Repository;

/**
 * 通用资产版本仓库。
 */
@Repository
public interface AssetVersionRepository extends ListCrudRepository<AssetVersion, Long> {

    Optional<AssetVersion> findByVersionIdAndTenantId(String versionId, String tenantId);

    Optional<AssetVersion> findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
        String tenantId,
        VersionedAssetType assetType,
        String assetIdentity,
        String versionNo
    );

    Optional<AssetVersion> findByTenantIdAndAssetTypeAndAssetIdentityAndSourceRef(
        String tenantId,
        VersionedAssetType assetType,
        String assetIdentity,
        String sourceRef
    );

    List<AssetVersion> findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
        String tenantId,
        VersionedAssetType assetType,
        String activeScopeKey,
        AssetVersionStatus status
    );

    List<AssetVersion> findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
        String tenantId,
        VersionedAssetType assetType,
        String assetIdentity,
        AssetVersionStatus status
    );

    List<AssetVersion> findByTenantIdAndAssetTypeAndAssetIdentity(
        String tenantId,
        VersionedAssetType assetType,
        String assetIdentity
    );

    List<AssetVersion> findByTenantIdAndAssetTypeAndAssetIdentityIn(
        String tenantId,
        VersionedAssetType assetType,
        Collection<String> assetIdentities
    );

    List<AssetVersion> findByTenantIdAndAssetIdentityInAndStatusIn(
        String tenantId,
        Collection<String> assetIdentities,
        Collection<AssetVersionStatus> statuses
    );

    @Query("""
        SELECT * FROM mk_version_asset_version
        WHERE tenant_id = :tenantId
          AND asset_type = :assetType
        ORDER BY updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<AssetVersion> pageByTenantIdAndAssetType(
        String tenantId,
        String assetType,
        int offset,
        int limit
    );

    @Query("""
        SELECT COUNT(*) FROM mk_version_asset_version
        WHERE tenant_id = :tenantId
          AND asset_type = :assetType
        """)
    long countByTenantIdAndAssetType(String tenantId, String assetType);

    @Query("""
        SELECT * FROM mk_version_asset_version
        WHERE tenant_id = :tenantId
          AND status = 'DRAFT'
          AND (:assetType IS NULL OR asset_type = :assetType)
          AND (
            :keyword IS NULL
            OR LOWER(asset_identity) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(COALESCE(source_ref, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
        ORDER BY updated_at DESC, asset_type, asset_identity, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<AssetVersion> pagePlatformReleaseCandidates(
        String tenantId,
        VersionedAssetType assetType,
        String keyword,
        int offset,
        int limit
    );

    @Query("""
        SELECT COUNT(*) FROM mk_version_asset_version
        WHERE tenant_id = :tenantId
          AND status = 'DRAFT'
          AND (:assetType IS NULL OR asset_type = :assetType)
          AND (
            :keyword IS NULL
            OR LOWER(asset_identity) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(COALESCE(source_ref, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
        """)
    long countPlatformReleaseCandidates(
        String tenantId,
        VersionedAssetType assetType,
        String keyword
    );

    @Query("""
        SELECT * FROM mk_version_asset_version
        WHERE tenant_id = :tenantId
          AND org_path IN (:organizationScopes)
          AND status IN ('DRAFT', 'PUBLISHED')
          AND (:assetType IS NULL OR asset_type = :assetType)
          AND (
            :keyword IS NULL
            OR LOWER(asset_identity) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(COALESCE(source_ref, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
        ORDER BY updated_at DESC, asset_type, asset_identity, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<AssetVersion> pageHospitalReleaseCandidates(
        String tenantId,
        Collection<String> organizationScopes,
        VersionedAssetType assetType,
        String keyword,
        int offset,
        int limit
    );

    @Query("""
        SELECT COUNT(*) FROM mk_version_asset_version
        WHERE tenant_id = :tenantId
          AND org_path IN (:organizationScopes)
          AND status IN ('DRAFT', 'PUBLISHED')
          AND (:assetType IS NULL OR asset_type = :assetType)
          AND (
            :keyword IS NULL
            OR LOWER(asset_identity) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(COALESCE(source_ref, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
        """)
    long countHospitalReleaseCandidates(
        String tenantId,
        Collection<String> organizationScopes,
        VersionedAssetType assetType,
        String keyword
    );
}
