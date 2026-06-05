package com.medkernel.engine.evaluation;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 整改任务持久化仓库（GA-ENG-API-08）。
 *
 * <p>按质控问题定位当前整改任务，用于提交整改、复核流转和问题详情展示。
 */
@Repository
public interface RectificationTaskRepository extends ListCrudRepository<RectificationTask, Long> {

    /**
     * 按质控问题 ID 与租户 ID 查询对应整改任务。
     */
    Optional<RectificationTask> findByFindingIdAndTenantId(String findingId, String tenantId);

    /**
     * 按整改任务业务 ID 与租户 ID 查询整改任务。
     */
    Optional<RectificationTask> findByTaskIdAndTenantId(String taskId, String tenantId);

    /**
     * 按租户与可选责任科室统计全部整改任务。
     */
    @Query("""
        SELECT COUNT(*)
        FROM rectification_task
        WHERE tenant_id = :tenantId
          AND (:departmentId IS NULL OR responsible_department_id = :departmentId)
        """)
    long countByTenantIdAndDepartmentFilter(String tenantId, String departmentId);

    /**
     * 按租户与可选责任科室统计未闭环整改任务。
     */
    @Query("""
        SELECT COUNT(*)
        FROM rectification_task
        WHERE tenant_id = :tenantId
          AND status IN ('ASSIGNED', 'SUBMITTED', 'RETURNED')
          AND (:departmentId IS NULL OR responsible_department_id = :departmentId)
        """)
    long countOpenByTenantIdAndDepartmentFilter(String tenantId, String departmentId);

    /**
     * 按租户与可选责任科室统计已闭环整改任务。
     */
    @Query("""
        SELECT COUNT(*)
        FROM rectification_task
        WHERE tenant_id = :tenantId
          AND status = 'CLOSED'
          AND (:departmentId IS NULL OR responsible_department_id = :departmentId)
        """)
    long countClosedByTenantIdAndDepartmentFilter(String tenantId, String departmentId);

    /**
     * 按租户与可选责任科室统计已豁免整改任务。
     */
    @Query("""
        SELECT COUNT(*)
        FROM rectification_task
        WHERE tenant_id = :tenantId
          AND status = 'WAIVED'
          AND (:departmentId IS NULL OR responsible_department_id = :departmentId)
        """)
    long countWaivedByTenantIdAndDepartmentFilter(String tenantId, String departmentId);

    /**
     * 按租户与可选责任科室统计已超期且仍开放的整改任务。
     */
    @Query("""
        SELECT COUNT(*)
        FROM rectification_task
        WHERE tenant_id = :tenantId
          AND status IN ('ASSIGNED', 'SUBMITTED', 'RETURNED')
          AND due_at < :now
          AND (:departmentId IS NULL OR responsible_department_id = :departmentId)
        """)
    long countOverdueOpenByTenantIdAndDepartmentFilter(String tenantId, String departmentId, Instant now);

    /**
     * 按租户与可选责任科室统计 P0 安全复核类开放整改任务。
     */
    @Query("""
        SELECT COUNT(*)
        FROM rectification_task t
        JOIN quality_finding f
          ON f.tenant_id = t.tenant_id
         AND f.finding_id = t.finding_id
        WHERE t.tenant_id = :tenantId
          AND t.status IN ('ASSIGNED', 'SUBMITTED', 'RETURNED')
          AND f.severity = 'P0'
          AND (:departmentId IS NULL OR t.responsible_department_id = :departmentId)
        """)
    long countOpenP0ByTenantIdAndDepartmentFilter(String tenantId, String departmentId);
}
