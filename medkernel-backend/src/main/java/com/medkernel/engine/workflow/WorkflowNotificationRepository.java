package com.medkernel.engine.workflow;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 统一通知仓储。
 */
@Repository
public interface WorkflowNotificationRepository extends ListCrudRepository<WorkflowNotification, Long> {

    Optional<WorkflowNotification> findByTenantIdAndDedupeKey(String tenantId, String dedupeKey);

    Optional<WorkflowNotification> findByTenantIdAndNotificationId(String tenantId, String notificationId);

    @Query("""
        SELECT COUNT(*)
        FROM mk_engine_notification
        WHERE tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:level IS NULL OR notification_level = :level)
          AND (:recipientId IS NULL OR recipient_id = :recipientId)
        """)
    long countByFilter(String tenantId, String status, String level, String recipientId);

    @Query("""
        SELECT COUNT(*)
        FROM mk_engine_notification
        WHERE tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:level IS NULL OR notification_level = :level)
          AND (
            (:recipientId IS NOT NULL AND recipient_id = :recipientId)
            OR (
              :recipientId IS NULL
              AND (
                recipient_id IS NULL
                OR (:currentUserId IS NOT NULL AND recipient_id = :currentUserId)
              )
            )
          )
        """)
    long countByVisibleRecipientScope(
        String tenantId,
        String status,
        String level,
        String recipientId,
        String currentUserId);

    @Query("""
        SELECT *
        FROM mk_engine_notification
        WHERE tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:level IS NULL OR notification_level = :level)
          AND (:recipientId IS NULL OR recipient_id = :recipientId)
        ORDER BY created_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<WorkflowNotification> pageByFilter(
        String tenantId,
        String status,
        String level,
        String recipientId,
        int offset,
        int limit);

    @Query("""
        SELECT *
        FROM mk_engine_notification
        WHERE tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:level IS NULL OR notification_level = :level)
          AND (
            (:recipientId IS NOT NULL AND recipient_id = :recipientId)
            OR (
              :recipientId IS NULL
              AND (
                recipient_id IS NULL
                OR (:currentUserId IS NOT NULL AND recipient_id = :currentUserId)
              )
            )
          )
        ORDER BY created_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<WorkflowNotification> pageByVisibleRecipientScope(
        String tenantId,
        String status,
        String level,
        String recipientId,
        String currentUserId,
        int offset,
        int limit);
}
