package com.medkernel.engine.followup;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * 随访任务存储库。
 */
public interface FollowupTaskRepository extends CrudRepository<FollowupTask, Long>, PagingAndSortingRepository<FollowupTask, Long> {
    Optional<FollowupTask> findByTaskId(String taskId);
    Optional<FollowupTask> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);
    List<FollowupTask> findByTenantIdAndPlanId(String tenantId, String planId);

    @Query("""
        SELECT COUNT(*)
        FROM followup_task t
        JOIN followup_plan p ON p.plan_id = t.plan_id AND p.tenant_id = t.tenant_id
        WHERE t.tenant_id = :tenantId
          AND (:patientId IS NULL OR p.patient_id = :patientId)
          AND (:planId IS NULL OR t.plan_id = :planId)
          AND (:status IS NULL OR t.status = :status)
        """)
    long countByTenantIdAndFilters(String tenantId, String patientId, String planId, String status);

    @Query("""
        SELECT t.*
        FROM followup_task t
        JOIN followup_plan p ON p.plan_id = t.plan_id AND p.tenant_id = t.tenant_id
        WHERE t.tenant_id = :tenantId
          AND (:patientId IS NULL OR p.patient_id = :patientId)
          AND (:planId IS NULL OR t.plan_id = :planId)
          AND (:status IS NULL OR t.status = :status)
        ORDER BY t.due_date ASC, t.id ASC
        LIMIT :limit OFFSET :offset
        """)
    List<FollowupTask> pageByTenantIdAndFilters(
        String tenantId, String patientId, String planId, String status, int offset, int limit);
}
