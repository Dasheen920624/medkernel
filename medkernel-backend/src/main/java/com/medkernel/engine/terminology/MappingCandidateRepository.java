package com.medkernel.engine.terminology;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 映射候选项持久化仓库；按 tenant_id 隔离。
 */
@Repository
public interface MappingCandidateRepository extends ListCrudRepository<MappingCandidate, Long> {

    Optional<MappingCandidate> findByTenantIdAndId(String tenantId, Long id);

    /**
     * 按租户 + 可选过滤条件（状态 / 风险等级 / 是否冲突）统计候选数量。
     */
    @Query("""
        SELECT COUNT(*) FROM mapping_candidate
        WHERE tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:riskLevel IS NULL OR risk_level = :riskLevel)
          AND (:conflictFlag IS NULL OR conflict_flag = :conflictFlag)
          AND (:generationJobCode IS NULL OR generation_job_code = :generationJobCode)
        """)
    long countByFilter(String tenantId, String status, String riskLevel, Boolean conflictFlag,
                       String generationJobCode);

    /**
     * 按租户 + 可选过滤条件分页查询候选（更新时间倒序），用于审核工作台。
     */
    @Query("""
        SELECT * FROM mapping_candidate
        WHERE tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:riskLevel IS NULL OR risk_level = :riskLevel)
          AND (:conflictFlag IS NULL OR conflict_flag = :conflictFlag)
          AND (:generationJobCode IS NULL OR generation_job_code = :generationJobCode)
        ORDER BY updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<MappingCandidate> pageByFilter(String tenantId, String status, String riskLevel, Boolean conflictFlag,
                                        String generationJobCode, int offset, int limit);

    Optional<MappingCandidate> findByTenantIdAndLocalTermIdAndStandardTermIdAndStatus(
        String tenantId, Long localTermId, Long standardTermId, MappingCandidateStatus status);
}
