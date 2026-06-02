package com.medkernel.engine.terminology;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 术语映射冲突持久化仓库；按 tenant_id 隔离。
 */
@Repository
public interface MappingConflictRepository extends ListCrudRepository<MappingConflict, Long> {

    Optional<MappingConflict> findByTenantIdAndId(String tenantId, Long id);

    /**
     * 查询同一候选范围下尚未处置的精确冲突，避免候选重复生成时制造重复待裁单。
     */
    @Query("""
        SELECT * FROM mapping_conflict
        WHERE tenant_id = :tenantId
          AND conflict_type = :conflictType
          AND local_term_id = :localTermId
          AND standard_term_id = :standardTermId
          AND status = 'OPEN'
        ORDER BY updated_at DESC, id DESC
        FETCH FIRST 1 ROW ONLY
        """)
    Optional<MappingConflict> findOpenByExactScope(String tenantId,
                                                   MappingConflictType conflictType,
                                                   Long localTermId,
                                                   Long standardTermId);

    /**
     * 查询仅绑定院内词的未处置冲突；用于一个院内词命中多个标准候选的候选生成阶段。
     */
    @Query("""
        SELECT * FROM mapping_conflict
        WHERE tenant_id = :tenantId
          AND conflict_type = :conflictType
          AND local_term_id = :localTermId
          AND standard_term_id IS NULL
          AND status = 'OPEN'
        ORDER BY updated_at DESC, id DESC
        FETCH FIRST 1 ROW ONLY
        """)
    Optional<MappingConflict> findOpenLocalOnly(String tenantId,
                                                MappingConflictType conflictType,
                                                Long localTermId);

    /**
     * 按租户 + 可选过滤条件（状态 / 风险等级 / 冲突类型）统计冲突数量。
     */
    @Query("""
        SELECT COUNT(*) FROM mapping_conflict
        WHERE tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:riskLevel IS NULL OR risk_level = :riskLevel)
          AND (:conflictType IS NULL OR conflict_type = :conflictType)
        """)
    long countByFilter(String tenantId, String status, String riskLevel, String conflictType);

    /**
     * 按租户 + 可选过滤条件分页查询冲突（更新时间倒序），用于冲突处置工作台。
     */
    @Query("""
        SELECT * FROM mapping_conflict
        WHERE tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:riskLevel IS NULL OR risk_level = :riskLevel)
          AND (:conflictType IS NULL OR conflict_type = :conflictType)
        ORDER BY updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<MappingConflict> pageByFilter(String tenantId, String status, String riskLevel, String conflictType,
                                       int offset, int limit);
}
