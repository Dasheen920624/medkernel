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
     * 根据上下文 / FHIR 编码锚点查询已确认的本地术语到目标标准字典映射。
     */
    @Query("""
        SELECT tm.* FROM term_mapping tm
        JOIN local_term lt ON lt.id = tm.local_term_id AND lt.tenant_id = tm.tenant_id
        JOIN standard_term st ON st.id = tm.standard_term_id AND st.tenant_id = tm.tenant_id
        WHERE tm.tenant_id = :tenantId
          AND tm.status = 'CONFIRMED'
          AND st.status = 'ACTIVE'
          AND lt.local_code = :localCode
          AND (:sourceSystem IS NULL OR lt.source_system = :sourceSystem)
          AND (:targetDictionaryKey IS NULL OR st.standard_system = :targetDictionaryKey)
          AND (:category IS NULL OR tm.category = :category)
        ORDER BY tm.confirmed_at DESC, tm.id DESC
        """)
    List<TermMapping> findConfirmedByTenantIdAndAnchor(String tenantId,
                                                       String sourceSystem,
                                                       String localCode,
                                                       String targetDictionaryKey,
                                                       String category);

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
     * 查询给定租户和组织作用域下所有 CONFIRMED 状态映射，供 {@link TerminologyService#buildPackage} 构建术语包。
     *
     * <p>当 scopeLevel = DEPARTMENT 时按 local_term.department_id 过滤；其他作用域（如 HOSPITAL）暂不区分子范围。
     */
    @Query("""
        SELECT tm.* FROM term_mapping tm
        JOIN local_term lt ON lt.id = tm.local_term_id AND lt.tenant_id = tm.tenant_id
        WHERE tm.tenant_id = :tenantId
          AND tm.status = 'CONFIRMED'
          AND (:scopeLevel IS NULL
               OR (:scopeLevel = 'DEPARTMENT' AND lt.department_id = :scopeCode)
               OR (:scopeLevel <> 'DEPARTMENT'))
        ORDER BY tm.updated_at DESC, tm.id DESC
        """)
    List<TermMapping> findConfirmedByTenantIdAndScope(String tenantId, String scopeLevel, String scopeCode);
}
