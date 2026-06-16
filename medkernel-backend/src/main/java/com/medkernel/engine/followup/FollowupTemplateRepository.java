package com.medkernel.engine.followup;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 随访模板仓库。
 */
@Repository
public interface FollowupTemplateRepository extends ListCrudRepository<FollowupTemplate, Long> {

    Optional<FollowupTemplate> findByTemplateIdAndTenantId(String templateId, String tenantId);

    Optional<FollowupTemplate> findByTenantIdAndTemplateCodeAndVersionNo(
        String tenantId,
        String templateCode,
        Integer versionNo
    );

    List<FollowupTemplate> findByTenantIdOrderByUpdatedAtDesc(String tenantId);

    @Query("""
        SELECT ft.*
        FROM mk_followup_template ft
        JOIN mk_version_asset_version av
          ON av.tenant_id = ft.tenant_id
         AND av.version_id = ft.asset_version_id
        WHERE ft.tenant_id = :tenantId
          AND (:assetStatus IS NULL OR av.status = :assetStatus)
          AND (
               :keyword IS NULL
            OR LOWER(ft.template_id) LIKE :keyword
            OR LOWER(ft.template_code) LIKE :keyword
            OR LOWER(ft.name) LIKE :keyword
            OR LOWER(ft.applicable_scope) LIKE :keyword
          )
        ORDER BY ft.updated_at DESC, ft.id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<FollowupTemplate> pageByFilter(
        String tenantId,
        String keyword,
        String assetStatus,
        int offset,
        int limit
    );

    @Query("""
        SELECT COUNT(*)
        FROM mk_followup_template ft
        JOIN mk_version_asset_version av
          ON av.tenant_id = ft.tenant_id
         AND av.version_id = ft.asset_version_id
        WHERE ft.tenant_id = :tenantId
          AND (:assetStatus IS NULL OR av.status = :assetStatus)
          AND (
               :keyword IS NULL
            OR LOWER(ft.template_id) LIKE :keyword
            OR LOWER(ft.template_code) LIKE :keyword
            OR LOWER(ft.name) LIKE :keyword
            OR LOWER(ft.applicable_scope) LIKE :keyword
          )
        """)
    long countByFilter(String tenantId, String keyword, String assetStatus);
}
