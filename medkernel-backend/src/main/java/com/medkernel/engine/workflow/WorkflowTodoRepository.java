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

    @Query("""
        SELECT t.*
        FROM mk_engine_workflow_todo t
        WHERE t.tenant_id = :tenantId
          AND t.todo_id = :todoId
          AND (
            (:currentUserId IS NOT NULL AND t.assignee_id = :currentUserId)
            OR (
              t.assignee_id IS NULL
              AND (
                t.org_unit_id IS NULL
                OR (
                  :currentOrgUnitId IS NOT NULL
                  AND EXISTS (
                    SELECT 1
                    FROM org_closure c
                    WHERE c.tenant_id = :tenantId
                      AND (
                        (c.ancestor_id = :currentOrgUnitId AND c.descendant_id = t.org_unit_id)
                        OR (c.ancestor_id = t.org_unit_id AND c.descendant_id = :currentOrgUnitId)
                      )
                  )
                )
              )
            )
          )
        """)
    Optional<WorkflowTodo> findVisibleByTenantIdAndTodoId(
        String tenantId,
        String todoId,
        String currentUserId,
        String currentOrgUnitId);

    Optional<WorkflowTodo> findByTenantIdAndSourceTypeAndSourceId(
        String tenantId,
        WorkflowTodoSourceType sourceType,
        String sourceId);

    @Query("""
        SELECT *
        FROM mk_engine_workflow_todo t
        WHERE t.tenant_id = :tenantId
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

    default long countByVisibleAssigneeScope(
            String tenantId,
            String status,
            String priority,
            String sourceType,
            String assigneeId,
            String currentUserId,
            String patientId) {
        return countByVisibleAssigneeScope(
            tenantId,
            status,
            priority,
            sourceType,
            assigneeId,
            currentUserId,
            null,
            patientId);
    }

    @Query("""
        SELECT COUNT(*)
        FROM mk_engine_workflow_todo t
        WHERE t.tenant_id = :tenantId
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
        FROM mk_engine_workflow_todo t
        WHERE t.tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:priority IS NULL OR priority = :priority)
          AND (:sourceType IS NULL OR source_type = :sourceType)
          AND (:patientId IS NULL OR patient_id = :patientId)
          AND (
            (:assigneeId IS NOT NULL AND assignee_id = :assigneeId)
            OR (
              :assigneeId IS NULL
              AND (
                (:currentUserId IS NOT NULL AND assignee_id = :currentUserId)
                OR (
                  assignee_id IS NULL
                  AND (
                    t.org_unit_id IS NULL
                    OR (
                      :currentOrgUnitId IS NOT NULL
                      AND EXISTS (
                        SELECT 1
                        FROM org_closure c
                        WHERE c.tenant_id = :tenantId
                          AND (
                            (c.ancestor_id = :currentOrgUnitId AND c.descendant_id = t.org_unit_id)
                            OR (c.ancestor_id = t.org_unit_id AND c.descendant_id = :currentOrgUnitId)
                          )
                      )
                    )
                  )
                )
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
        String currentOrgUnitId,
        String patientId);

    @Query("""
        SELECT COUNT(*)
        FROM mk_engine_workflow_todo t
        WHERE t.tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:priority IS NULL OR priority = :priority)
          AND (:sourceType IS NULL OR source_type = :sourceType)
          AND (:patientId IS NULL OR patient_id = :patientId)
          AND t.org_unit_id IS NOT NULL
          AND EXISTS (
            SELECT 1
            FROM org_closure selected_scope
            WHERE selected_scope.tenant_id = :tenantId
              AND selected_scope.ancestor_id = :selectedOrgUnitId
              AND selected_scope.descendant_id = t.org_unit_id
          )
          AND (
            (:assigneeId IS NOT NULL AND assignee_id = :assigneeId)
            OR (
              :assigneeId IS NULL
              AND (
                (:currentUserId IS NOT NULL AND assignee_id = :currentUserId)
                OR (
                  assignee_id IS NULL
                  AND :currentOrgUnitId IS NOT NULL
                  AND EXISTS (
                    SELECT 1
                    FROM org_closure c
                    WHERE c.tenant_id = :tenantId
                      AND (
                        (c.ancestor_id = :currentOrgUnitId AND c.descendant_id = t.org_unit_id)
                        OR (c.ancestor_id = t.org_unit_id AND c.descendant_id = :currentOrgUnitId)
                      )
                  )
                )
              )
            )
          )
        """)
    long countByVisibleAssigneeScopeAndOrgUnitFilter(
        String tenantId,
        String status,
        String priority,
        String sourceType,
        String assigneeId,
        String currentUserId,
        String currentOrgUnitId,
        String patientId,
        String selectedOrgUnitId);

    default List<WorkflowTodo> pageByVisibleAssigneeScope(
            String tenantId,
            String status,
            String priority,
            String sourceType,
            String assigneeId,
            String currentUserId,
            String patientId,
            int offset,
            int limit) {
        return pageByVisibleAssigneeScope(
            tenantId,
            status,
            priority,
            sourceType,
            assigneeId,
            currentUserId,
            null,
            patientId,
            offset,
            limit);
    }

    @Query("""
        SELECT t.*
        FROM mk_engine_workflow_todo t
        WHERE t.tenant_id = :tenantId
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
        SELECT t.*
        FROM mk_engine_workflow_todo t
        WHERE t.tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:priority IS NULL OR priority = :priority)
          AND (:sourceType IS NULL OR source_type = :sourceType)
          AND (:patientId IS NULL OR patient_id = :patientId)
          AND (
            (:assigneeId IS NOT NULL AND assignee_id = :assigneeId)
            OR (
              :assigneeId IS NULL
              AND (
                (:currentUserId IS NOT NULL AND assignee_id = :currentUserId)
                OR (
                  assignee_id IS NULL
                  AND (
                    t.org_unit_id IS NULL
                    OR (
                      :currentOrgUnitId IS NOT NULL
                      AND EXISTS (
                        SELECT 1
                        FROM org_closure c
                        WHERE c.tenant_id = :tenantId
                          AND (
                            (c.ancestor_id = :currentOrgUnitId AND c.descendant_id = t.org_unit_id)
                            OR (c.ancestor_id = t.org_unit_id AND c.descendant_id = :currentOrgUnitId)
                          )
                      )
                    )
                  )
                )
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
        String currentOrgUnitId,
        String patientId,
        int offset,
        int limit);

    @Query("""
        SELECT t.*
        FROM mk_engine_workflow_todo t
        WHERE t.tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:priority IS NULL OR priority = :priority)
          AND (:sourceType IS NULL OR source_type = :sourceType)
          AND (:patientId IS NULL OR patient_id = :patientId)
          AND t.org_unit_id IS NOT NULL
          AND EXISTS (
            SELECT 1
            FROM org_closure selected_scope
            WHERE selected_scope.tenant_id = :tenantId
              AND selected_scope.ancestor_id = :selectedOrgUnitId
              AND selected_scope.descendant_id = t.org_unit_id
          )
          AND (
            (:assigneeId IS NOT NULL AND assignee_id = :assigneeId)
            OR (
              :assigneeId IS NULL
              AND (
                (:currentUserId IS NOT NULL AND assignee_id = :currentUserId)
                OR (
                  assignee_id IS NULL
                  AND :currentOrgUnitId IS NOT NULL
                  AND EXISTS (
                    SELECT 1
                    FROM org_closure c
                    WHERE c.tenant_id = :tenantId
                      AND (
                        (c.ancestor_id = :currentOrgUnitId AND c.descendant_id = t.org_unit_id)
                        OR (c.ancestor_id = t.org_unit_id AND c.descendant_id = :currentOrgUnitId)
                      )
                  )
                )
              )
            )
          )
        ORDER BY
          CASE WHEN assignee_id IS NULL THEN 0 ELSE 1 END,
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
    List<WorkflowTodo> pageByVisibleAssigneeScopeAndOrgUnitFilter(
        String tenantId,
        String status,
        String priority,
        String sourceType,
        String assigneeId,
        String currentUserId,
        String currentOrgUnitId,
        String patientId,
        String selectedOrgUnitId,
        int offset,
        int limit);
}
