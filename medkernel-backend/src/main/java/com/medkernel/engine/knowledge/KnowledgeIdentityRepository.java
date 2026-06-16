package com.medkernel.engine.knowledge;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 知识身份 Repository。
 *
 * <p>所有查询强制带 tenantId；包含按域、专科、关键词搜索的常用形态。
 */
@Repository
public interface KnowledgeIdentityRepository extends ListCrudRepository<KnowledgeIdentity, Long> {

    Optional<KnowledgeIdentity> findByTenantIdAndId(String tenantId, Long id);

    List<KnowledgeIdentity> findByTenantIdAndIdIn(String tenantId, List<Long> ids);

    Optional<KnowledgeIdentity> findByTenantIdAndIdentityCode(String tenantId, String identityCode);

    @Query("SELECT COUNT(*) FROM knowledge_identity WHERE tenant_id = :tenantId")
    long countByTenantId(String tenantId);

    @Query("""
        SELECT COUNT(*) FROM knowledge_identity
        WHERE tenant_id = :tenantId
          AND (:domain IS NULL OR domain = :domain)
          AND (:specialtyId IS NULL OR specialty_id = :specialtyId)
          AND (:status IS NULL OR status = :status)
          AND (:keyword IS NULL OR LOWER(subject) LIKE :keyword OR LOWER(identity_code) LIKE :keyword)
        """)
    long countByFilter(String tenantId, String domain, String specialtyId, String status, String keyword);

    @Query("""
        SELECT * FROM knowledge_identity
        WHERE tenant_id = :tenantId
          AND (:domain IS NULL OR domain = :domain)
          AND (:specialtyId IS NULL OR specialty_id = :specialtyId)
          AND (:status IS NULL OR status = :status)
          AND (:keyword IS NULL OR LOWER(subject) LIKE :keyword OR LOWER(identity_code) LIKE :keyword)
        ORDER BY updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<KnowledgeIdentity> pageByFilter(String tenantId, String domain, String specialtyId, String status, String keyword,
                                         int offset, int limit);

    /**
     * 与分页查询同口径的完整列表，用于平台主源与当前租户覆盖层的有效身份合并。
     */
    @Query("""
        SELECT * FROM knowledge_identity
        WHERE tenant_id = :tenantId
          AND (:domain IS NULL OR domain = :domain)
          AND (:specialtyId IS NULL OR specialty_id = :specialtyId)
          AND (:status IS NULL OR status = :status)
          AND (:keyword IS NULL OR LOWER(subject) LIKE :keyword OR LOWER(identity_code) LIKE :keyword)
        ORDER BY updated_at DESC, id DESC
        """)
    List<KnowledgeIdentity> listByFilter(String tenantId, String domain, String specialtyId, String status, String keyword);

    @Query("""
        SELECT COUNT(*) FROM (
            SELECT local.identity_code
            FROM knowledge_identity local
            WHERE local.tenant_id = :tenantId
              AND (:domain IS NULL OR local.domain = :domain)
              AND (:specialtyId IS NULL OR local.specialty_id = :specialtyId)
              AND (:tenantStatus IS NULL OR local.status = :tenantStatus)
              AND (:keyword IS NULL OR LOWER(local.subject) LIKE :keyword OR LOWER(local.identity_code) LIKE :keyword)
            UNION ALL
            SELECT platform.identity_code
            FROM knowledge_identity platform
            WHERE platform.tenant_id = :platformTenantId
              AND (:domain IS NULL OR platform.domain = :domain)
              AND (:specialtyId IS NULL OR platform.specialty_id = :specialtyId)
              AND (
                    (:platformStatus IS NULL AND platform.status IN ('ACTIVE', 'DEPRECATED'))
                    OR (:platformStatus IS NOT NULL AND platform.status = :platformStatus)
                  )
              AND (:keyword IS NULL OR LOWER(platform.subject) LIKE :keyword OR LOWER(platform.identity_code) LIKE :keyword)
              AND NOT EXISTS (
                    SELECT 1
                    FROM knowledge_identity local_shadow
                    WHERE local_shadow.tenant_id = :tenantId
                      AND local_shadow.identity_code = platform.identity_code
                      AND (:domain IS NULL OR local_shadow.domain = :domain)
                      AND (:specialtyId IS NULL OR local_shadow.specialty_id = :specialtyId)
                      AND (:tenantStatus IS NULL OR local_shadow.status = :tenantStatus)
                      AND (:keyword IS NULL OR LOWER(local_shadow.subject) LIKE :keyword OR LOWER(local_shadow.identity_code) LIKE :keyword)
                  )
        ) effective_rows
        """)
    long countEffectiveByFilter(String tenantId, String platformTenantId, String domain,
                                String specialtyId, String tenantStatus, String platformStatus,
                                String keyword);

    @Query("""
        SELECT * FROM (
            SELECT local.*
            FROM knowledge_identity local
            WHERE local.tenant_id = :tenantId
              AND (:domain IS NULL OR local.domain = :domain)
              AND (:specialtyId IS NULL OR local.specialty_id = :specialtyId)
              AND (:tenantStatus IS NULL OR local.status = :tenantStatus)
              AND (:keyword IS NULL OR LOWER(local.subject) LIKE :keyword OR LOWER(local.identity_code) LIKE :keyword)
            UNION ALL
            SELECT platform.*
            FROM knowledge_identity platform
            WHERE platform.tenant_id = :platformTenantId
              AND (:domain IS NULL OR platform.domain = :domain)
              AND (:specialtyId IS NULL OR platform.specialty_id = :specialtyId)
              AND (
                    (:platformStatus IS NULL AND platform.status IN ('ACTIVE', 'DEPRECATED'))
                    OR (:platformStatus IS NOT NULL AND platform.status = :platformStatus)
                  )
              AND (:keyword IS NULL OR LOWER(platform.subject) LIKE :keyword OR LOWER(platform.identity_code) LIKE :keyword)
              AND NOT EXISTS (
                    SELECT 1
                    FROM knowledge_identity local_shadow
                    WHERE local_shadow.tenant_id = :tenantId
                      AND local_shadow.identity_code = platform.identity_code
                      AND (:domain IS NULL OR local_shadow.domain = :domain)
                      AND (:specialtyId IS NULL OR local_shadow.specialty_id = :specialtyId)
                      AND (:tenantStatus IS NULL OR local_shadow.status = :tenantStatus)
                      AND (:keyword IS NULL OR LOWER(local_shadow.subject) LIKE :keyword OR LOWER(local_shadow.identity_code) LIKE :keyword)
                  )
        ) effective_rows
        ORDER BY updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<KnowledgeIdentity> pageEffectiveByFilter(String tenantId, String platformTenantId,
                                                  String domain, String specialtyId,
                                                  String tenantStatus, String platformStatus,
                                                  String keyword, int offset, int limit);

    @Query("""
        SELECT * FROM knowledge_identity
        WHERE tenant_id = :tenantId
        ORDER BY updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<KnowledgeIdentity> pageByTenantId(String tenantId, int offset, int limit);

    /**
     * 悲观锁定身份行，用于 activate / withdraw 等状态机变迁。
     *
     * <p>所有 5 方言（PG / Kingbase / Oracle / DM / H2 MODE=PostgreSQL）均支持 {@code SELECT ... FOR UPDATE}。
     * Spring Data JDBC 在 {@code @Transactional} 内开启的连接会持有该锁直到事务结束。
     */
    @Query("SELECT * FROM knowledge_identity WHERE tenant_id = :tenantId AND id = :id FOR UPDATE")
    Optional<KnowledgeIdentity> findByTenantIdAndIdForUpdate(String tenantId, Long id);
}
