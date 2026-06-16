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

    /**
     * 统一创作资产库按标签、收藏和关键字分页查询随访模板，避免全量租户快照。
     */
    @Query("""
        SELECT ft.*
        FROM mk_followup_template ft
        LEFT JOIN mk_engine_authoring_asset_profile p
          ON p.tenant_id = ft.tenant_id
         AND p.asset_type = 'FOLLOWUP'
         AND p.asset_id = ft.template_id
        WHERE ft.tenant_id = :tenantId
          AND (
              :keyword IS NULL
              OR LOWER(ft.template_id) LIKE :keyword
              OR LOWER(ft.template_code) LIKE :keyword
              OR LOWER(ft.name) LIKE :keyword
              OR LOWER(ft.applicable_scope) LIKE :keyword
              OR LOWER(COALESCE(p.category, '')) LIKE :keyword
              OR LOWER(p.tags_json) LIKE :keyword
          )
          AND (:tagPattern IS NULL OR LOWER(p.tags_json) LIKE :tagPattern)
          AND (:favoriteUserId IS NULL OR EXISTS (
              SELECT 1
              FROM mk_engine_authoring_asset_favorite f
              WHERE f.tenant_id = ft.tenant_id
                AND f.user_id = :favoriteUserId
                AND f.asset_type = 'FOLLOWUP'
                AND f.asset_id = ft.template_id
          ))
        ORDER BY ft.updated_at DESC, ft.id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<FollowupTemplate> pageForAuthoringLibrary(
        String tenantId,
        String keyword,
        String tagPattern,
        String favoriteUserId,
        int offset,
        int limit);

    /**
     * 与 {@link #pageForAuthoringLibrary} 同口径统计随访模板数。
     */
    @Query("""
        SELECT COUNT(*)
        FROM mk_followup_template ft
        LEFT JOIN mk_engine_authoring_asset_profile p
          ON p.tenant_id = ft.tenant_id
         AND p.asset_type = 'FOLLOWUP'
         AND p.asset_id = ft.template_id
        WHERE ft.tenant_id = :tenantId
          AND (
              :keyword IS NULL
              OR LOWER(ft.template_id) LIKE :keyword
              OR LOWER(ft.template_code) LIKE :keyword
              OR LOWER(ft.name) LIKE :keyword
              OR LOWER(ft.applicable_scope) LIKE :keyword
              OR LOWER(COALESCE(p.category, '')) LIKE :keyword
              OR LOWER(p.tags_json) LIKE :keyword
          )
          AND (:tagPattern IS NULL OR LOWER(p.tags_json) LIKE :tagPattern)
          AND (:favoriteUserId IS NULL OR EXISTS (
              SELECT 1
              FROM mk_engine_authoring_asset_favorite f
              WHERE f.tenant_id = ft.tenant_id
                AND f.user_id = :favoriteUserId
                AND f.asset_type = 'FOLLOWUP'
                AND f.asset_id = ft.template_id
          ))
        """)
    long countForAuthoringLibrary(
        String tenantId,
        String keyword,
        String tagPattern,
        String favoriteUserId);
}
