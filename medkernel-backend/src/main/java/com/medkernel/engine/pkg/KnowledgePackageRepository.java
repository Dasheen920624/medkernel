package com.medkernel.engine.pkg;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 知识包 Repository 接口。
 */
@Repository
public interface KnowledgePackageRepository extends ListCrudRepository<KnowledgePackage, Long> {

    Optional<KnowledgePackage> findByPackageIdAndTenantId(String packageId, String tenantId);

    Optional<KnowledgePackage> findByTenantIdAndPackageCodeAndPackageVersion(
        String tenantId, String packageCode, String packageVersion);

    List<KnowledgePackage> findByTenantIdAndPackageVersion(String tenantId, String packageVersion);

    Optional<KnowledgePackage> findFirstByTenantIdAndStatusOrderByUpdatedAtDesc(
        String tenantId, KnowledgePackageStatus status);

    List<KnowledgePackage> findByTenantIdOrderByUpdatedAtDesc(String tenantId);

    List<KnowledgePackage> findByTenantIdAndPackageIdIn(String tenantId, Set<String> packageIds);

    @Query("""
        SELECT * FROM knowledge_package
        WHERE tenant_id = :tenantId
        ORDER BY updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<KnowledgePackage> pageByTenantId(String tenantId, int offset, int limit);

    @Query("""
        SELECT COUNT(*) FROM knowledge_package
        WHERE tenant_id = :tenantId
        """)
    long countByTenantId(String tenantId);

    @Query("""
        SELECT kp.* FROM knowledge_package kp
        WHERE kp.tenant_id = :tenantId
          AND (:status IS NULL OR kp.status = :status)
          AND (
               :keyword IS NULL
            OR LOWER(kp.package_code) LIKE :keyword
            OR LOWER(kp.package_version) LIKE :keyword
            OR LOWER(kp.name) LIKE :keyword
          )
          AND (
               :assetType IS NULL
            OR EXISTS (
                SELECT 1 FROM package_item item
                WHERE item.tenant_id = kp.tenant_id
                  AND item.package_id = kp.package_id
                  AND item.asset_type = :assetType
            )
          )
        ORDER BY kp.updated_at DESC, kp.id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<KnowledgePackage> pageByFilter(
        String tenantId,
        String keyword,
        String status,
        String assetType,
        int offset,
        int limit
    );

    @Query("""
        SELECT COUNT(*) FROM knowledge_package kp
        WHERE kp.tenant_id = :tenantId
          AND (:status IS NULL OR kp.status = :status)
          AND (
               :keyword IS NULL
            OR LOWER(kp.package_code) LIKE :keyword
            OR LOWER(kp.package_version) LIKE :keyword
            OR LOWER(kp.name) LIKE :keyword
          )
          AND (
               :assetType IS NULL
            OR EXISTS (
                SELECT 1 FROM package_item item
                WHERE item.tenant_id = kp.tenant_id
                  AND item.package_id = kp.package_id
                  AND item.asset_type = :assetType
            )
          )
        """)
    long countByFilter(String tenantId, String keyword, String status, String assetType);
}
