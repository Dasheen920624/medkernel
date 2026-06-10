package com.medkernel.engine.terminology;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 院内本地术语字典持久化仓库。
 *
 * <p>所有查询均按 tenant_id 隔离，由 Service 层从 {@link com.medkernel.shared.context.RequestContext}
 * 取当前租户上下文后下发。
 */
@Repository
public interface LocalTermRepository extends ListCrudRepository<LocalTerm, Long> {

    Optional<LocalTerm> findByTenantIdAndId(String tenantId, Long id);

    Optional<LocalTerm> findByTenantIdAndSourceSystemAndLocalCodeAndCategory(
        String tenantId, String sourceSystem, String localCode, TermCategory category);

    /**
     * 按租户 + 可选过滤条件（来源系统 / 分类 / 状态 / 关键词）统计本地术语数量。
     */
    @Query("""
        SELECT COUNT(*) FROM local_term
        WHERE tenant_id = :tenantId
          AND (:sourceSystem IS NULL OR source_system = :sourceSystem)
          AND (:category IS NULL OR category = :category)
          AND (:status IS NULL OR status = :status)
          AND (:keyword IS NULL OR LOWER(local_name) LIKE :keyword OR LOWER(local_code) LIKE :keyword)
        """)
    long countByFilter(String tenantId, String sourceSystem, String category, String status, String keyword);

    /**
     * 按租户 + 可选过滤条件分页查询本地术语（更新时间倒序），用于管理后台分页列表。
     */
    @Query("""
        SELECT * FROM local_term
        WHERE tenant_id = :tenantId
          AND (:sourceSystem IS NULL OR source_system = :sourceSystem)
          AND (:category IS NULL OR category = :category)
          AND (:status IS NULL OR status = :status)
          AND (:keyword IS NULL OR LOWER(local_name) LIKE :keyword OR LOWER(local_code) LIKE :keyword)
        ORDER BY updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<LocalTerm> pageByFilter(String tenantId, String sourceSystem, String category, String status,
                                 String keyword, int offset, int limit);

    List<LocalTerm> findByTenantIdAndSourceSystemAndStatus(String tenantId, String sourceSystem, LocalTermStatus status);
}
