package com.medkernel.engine.terminology;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 正式术语映射持久化仓库；按 tenant_id 隔离。
 */
@Repository
public interface TermMappingRepository extends ListCrudRepository<TermMapping, Long> {

    Optional<TermMapping> findByTenantIdAndId(String tenantId, Long id);

    Optional<TermMapping> findByTenantIdAndLocalTermIdAndStandardTermId(String tenantId, Long localTermId,
                                                                        Long standardTermId);

    List<TermMapping> findByTenantIdAndLocalTermIdAndStatus(String tenantId, Long localTermId,
                                                            TermMappingStatus status);

    List<TermMapping> findByTenantIdAndStandardTermIdAndStatus(String tenantId, Long standardTermId,
                                                               TermMappingStatus status);

    /**
     * 按租户 + 可选过滤条件（来源系统 / 分类 / 状态 / 证据关键词）统计映射数量。
     */
    @Query("""
        SELECT COUNT(*) FROM term_mapping tm
        WHERE tm.tenant_id = :tenantId
          AND (:sourceSystem IS NULL OR tm.source_system = :sourceSystem)
          AND (:category IS NULL OR tm.category = :category)
          AND (:status IS NULL OR tm.status = :status)
          AND (:keyword IS NULL OR LOWER(tm.evidence_text) LIKE :keyword)
        """)
    long countByFilter(String tenantId, String sourceSystem, String category, String status, String keyword);

    /**
     * 按租户 + 可选过滤条件分页查询映射（更新时间倒序），用于管理后台映射列表。
     */
    @Query("""
        SELECT tm.* FROM term_mapping tm
        WHERE tm.tenant_id = :tenantId
          AND (:sourceSystem IS NULL OR tm.source_system = :sourceSystem)
          AND (:category IS NULL OR tm.category = :category)
          AND (:status IS NULL OR tm.status = :status)
          AND (:keyword IS NULL OR LOWER(tm.evidence_text) LIKE :keyword)
        ORDER BY tm.updated_at DESC, tm.id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<TermMapping> pageByFilter(String tenantId, String sourceSystem, String category, String status,
                                   String keyword, int offset, int limit);

    /**
     * 查询给定租户和组织作用域下所有 CONFIRMED 状态映射，
     * 供 {@link TerminologyAssetDraftService#createDraft} 生成不可变术语资产草稿。
     *
     * <p>租户范围读取全部映射；其他组织范围通过组织闭包限定到该祖先节点下的科室。
     */
    @Query("""
        SELECT tm.* FROM term_mapping tm
        JOIN local_term lt ON lt.id = tm.local_term_id AND lt.tenant_id = tm.tenant_id
        WHERE tm.tenant_id = :tenantId
          AND tm.status = 'CONFIRMED'
          AND (
              :scopeLevel = 'TENANT'
              OR (:scopeLevel = 'DEPARTMENT' AND lt.department_id = :scopeCode)
              OR EXISTS (
                  SELECT 1
                  FROM org_closure oc
                  WHERE oc.tenant_id = :tenantId
                    AND oc.ancestor_id = :scopeCode
                    AND oc.descendant_id = lt.department_id
              )
          )
        ORDER BY tm.updated_at DESC, tm.id DESC
        """)
    List<TermMapping> findConfirmedByTenantIdAndScope(String tenantId, String scopeLevel, String scopeCode);
}
