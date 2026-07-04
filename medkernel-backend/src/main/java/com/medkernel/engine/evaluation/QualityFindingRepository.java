package com.medkernel.engine.evaluation;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 质量问题持久化仓库（GA-ENG-API-08）。
 *
 * <p>支持按问题 ID 查询详情、按结果回放问题、按租户浏览最新问题，以及按严重度、状态和责任科室分页查询。
 */
@Repository
public interface QualityFindingRepository extends ListCrudRepository<QualityFinding, Long> {

    /**
     * 按问题业务 ID 与租户 ID 查询单个质量问题。
     */
    Optional<QualityFinding> findByFindingIdAndTenantId(String findingId, String tenantId);

    /**
     * 按评估结果 ID 和租户 ID 查询问题，按创建时间升序用于运行诊断。
     */
    List<QualityFinding> findByResultIdAndTenantIdOrderByCreatedAtAsc(String resultId, String tenantId);

    /**
     * 按租户列出质量问题，按创建时间倒序用于管理端浏览。
     */
    List<QualityFinding> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    /**
     * 按严重度、状态和责任科室可选过滤统计问题总数。
     */
    @Query("""
        SELECT COUNT(*)
        FROM quality_finding
        WHERE tenant_id = :tenantId
          AND (:severity IS NULL OR severity = :severity)
          AND (:status IS NULL OR status = :status)
          AND (:departmentId IS NULL OR responsible_department_id = :departmentId)
        """)
    long countByFilter(String tenantId, String severity, String status, String departmentId);

    /**
     * 与 {@link #countByFilter} 同口径分页查询质量问题，按创建时间倒序返回。
     */
    @Query("""
        SELECT *
        FROM quality_finding
        WHERE tenant_id = :tenantId
          AND (:severity IS NULL OR severity = :severity)
          AND (:status IS NULL OR status = :status)
          AND (:departmentId IS NULL OR responsible_department_id = :departmentId)
        ORDER BY created_at DESC, id DESC
        LIMIT :limit OFFSET :offset
        """)
    List<QualityFinding> pageByFilter(
        String tenantId, String severity, String status, String departmentId, int offset, int limit);
}
