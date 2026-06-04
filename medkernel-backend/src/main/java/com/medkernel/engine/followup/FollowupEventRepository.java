package com.medkernel.engine.followup;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

import com.medkernel.engine.workflow.FollowupNotificationRow;

/**
 * 随访异常与回流事件存储库。
 */
public interface FollowupEventRepository extends CrudRepository<FollowupEvent, Long>, PagingAndSortingRepository<FollowupEvent, Long> {
    Optional<FollowupEvent> findByEventId(String eventId);
    List<FollowupEvent> findByTenantIdAndPlanId(String tenantId, String planId);
    Optional<FollowupEvent> findByTenantIdAndEventTypeAndIdempotencyKey(
        String tenantId, FollowupEventType eventType, String idempotencyKey);

    @Query("""
        SELECT e.event_id AS event_id,
               e.plan_id AS plan_id,
               p.patient_id AS patient_id,
               p.encounter_id AS encounter_id,
               t.task_id AS task_id,
               t.executor_id AS executor_id,
               t.executor_type AS executor_type,
               '随访异常回院通知' AS title,
               '患者随访异常，需要安排回院确认' AS message,
               e.trace_id AS trace_id,
               e.created_at AS created_at
        FROM followup_event e
        JOIN followup_plan p ON p.plan_id = e.plan_id AND p.tenant_id = e.tenant_id
        LEFT JOIN followup_task t ON t.plan_id = e.plan_id
             AND t.tenant_id = e.tenant_id
             AND t.task_type = 'RETURN_VISIT'
             AND t.status = 'ABNORMAL_RETURN'
        WHERE e.tenant_id = :tenantId
          AND e.event_type = 'NOTIFICATION_REQUESTED'
        ORDER BY e.created_at DESC, e.id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<FollowupNotificationRow> pageNotificationRows(String tenantId, int offset, int limit);
}
