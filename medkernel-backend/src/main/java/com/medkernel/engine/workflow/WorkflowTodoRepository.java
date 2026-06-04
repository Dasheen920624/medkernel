package com.medkernel.engine.workflow;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 统一协同待办仓储。
 */
@Repository
public interface WorkflowTodoRepository extends ListCrudRepository<WorkflowTodo, Long> {

    Optional<WorkflowTodo> findByTenantIdAndTodoId(String tenantId, String todoId);

    Optional<WorkflowTodo> findByTenantIdAndSourceTypeAndSourceId(
        String tenantId,
        WorkflowTodoSourceType sourceType,
        String sourceId);

    @Query("""
        SELECT *
        FROM mk_engine_workflow_todo
        WHERE tenant_id = :tenantId
          AND source_id = :sourceId
          AND source_type IN (
            'RECOMMENDATION_CARD',
            'NURSING_TASK',
            'REPORT_INTERPRETATION',
            'BEDSIDE_KNOWLEDGE'
          )
        ORDER BY created_at ASC, id ASC
        OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY
        """)
    Optional<WorkflowTodo> findRecommendationDerivedByTenantIdAndSourceId(String tenantId, String sourceId);

    @Query("""
        SELECT COUNT(*)
        FROM mk_engine_workflow_todo
        WHERE tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:priority IS NULL OR priority = :priority)
          AND (:sourceType IS NULL OR source_type = :sourceType)
          AND (:assigneeId IS NULL OR assignee_id = :assigneeId)
          AND (:patientId IS NULL OR patient_id = :patientId)
        """)
    long countByFilter(
        String tenantId,
        String status,
        String priority,
        String sourceType,
        String assigneeId,
        String patientId);

    @Query("""
        SELECT COUNT(*)
        FROM mk_engine_workflow_todo
        WHERE tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:priority IS NULL OR priority = :priority)
          AND (:sourceType IS NULL OR source_type = :sourceType)
          AND (:patientId IS NULL OR patient_id = :patientId)
          AND (
            (:assigneeId IS NOT NULL AND assignee_id = :assigneeId)
            OR (
              :assigneeId IS NULL
              AND (
                assignee_id IS NULL
                OR (:currentUserId IS NOT NULL AND assignee_id = :currentUserId)
              )
            )
          )
        """)
    long countByVisibleAssigneeScope(
        String tenantId,
        String status,
        String priority,
        String sourceType,
        String assigneeId,
        String currentUserId,
        String patientId);

    @Query("""
        SELECT *
        FROM mk_engine_workflow_todo
        WHERE tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:priority IS NULL OR priority = :priority)
          AND (:sourceType IS NULL OR source_type = :sourceType)
          AND (:assigneeId IS NULL OR assignee_id = :assigneeId)
          AND (:patientId IS NULL OR patient_id = :patientId)
        ORDER BY
          CASE WHEN source_type = 'SAFETY_REVIEW' THEN 0 ELSE 1 END,
          CASE priority
            WHEN 'CRITICAL' THEN 0
            WHEN 'HIGH' THEN 1
            WHEN 'MEDIUM' THEN 2
            ELSE 3
          END,
          CASE WHEN due_at IS NULL THEN 1 ELSE 0 END,
          due_at ASC,
          created_at ASC,
          id ASC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<WorkflowTodo> pageByFilter(
        String tenantId,
        String status,
        String priority,
        String sourceType,
        String assigneeId,
        String patientId,
        int offset,
        int limit);

    @Query("""
        SELECT *
        FROM mk_engine_workflow_todo
        WHERE tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:priority IS NULL OR priority = :priority)
          AND (:sourceType IS NULL OR source_type = :sourceType)
          AND (:patientId IS NULL OR patient_id = :patientId)
          AND (
            (:assigneeId IS NOT NULL AND assignee_id = :assigneeId)
            OR (
              :assigneeId IS NULL
              AND (
                assignee_id IS NULL
                OR (:currentUserId IS NOT NULL AND assignee_id = :currentUserId)
              )
            )
          )
        ORDER BY
          CASE WHEN source_type = 'SAFETY_REVIEW' THEN 0 ELSE 1 END,
          CASE priority
            WHEN 'CRITICAL' THEN 0
            WHEN 'HIGH' THEN 1
            WHEN 'MEDIUM' THEN 2
            ELSE 3
          END,
          CASE WHEN due_at IS NULL THEN 1 ELSE 0 END,
          due_at ASC,
          created_at ASC,
          id ASC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<WorkflowTodo> pageByVisibleAssigneeScope(
        String tenantId,
        String status,
        String priority,
        String sourceType,
        String assigneeId,
        String currentUserId,
        String patientId,
        int offset,
        int limit);
}
