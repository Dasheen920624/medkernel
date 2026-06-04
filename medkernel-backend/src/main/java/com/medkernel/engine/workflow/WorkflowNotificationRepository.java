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
        SELECT n.*
        FROM mk_engine_notification n
        WHERE n.tenant_id = :tenantId
          AND n.notification_id = :notificationId
          AND (
            (:currentUserId IS NOT NULL AND n.recipient_id = :currentUserId)
            OR (
              n.recipient_id IS NULL
              AND (
                n.org_unit_id IS NULL
                OR (
                  :currentOrgUnitId IS NOT NULL
                  AND EXISTS (
                    SELECT 1
                    FROM org_closure c
                    WHERE c.tenant_id = :tenantId
                      AND (
                        (c.ancestor_id = :currentOrgUnitId AND c.descendant_id = n.org_unit_id)
                        OR (c.ancestor_id = n.org_unit_id AND c.descendant_id = :currentOrgUnitId)
                      )
                  )
                )
              )
            )
          )
        """)
    Optional<WorkflowNotification> findVisibleByTenantIdAndNotificationId(
        String tenantId,
        String notificationId,
        String currentUserId,
        String currentOrgUnitId);

    default long countByVisibleRecipientScope(
            String tenantId,
            String status,
            String level,
            String recipientId,
            String currentUserId) {
        return countByVisibleRecipientScope(tenantId, status, level, recipientId, currentUserId, null);
    }

    @Query("""
        SELECT COUNT(*)
        FROM mk_engine_notification n
        WHERE n.tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:level IS NULL OR notification_level = :level)
          AND (:recipientId IS NULL OR recipient_id = :recipientId)
        """)
    long countByFilter(String tenantId, String status, String level, String recipientId);

    @Query("""
        SELECT COUNT(*)
        FROM mk_engine_notification n
        WHERE n.tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:level IS NULL OR notification_level = :level)
          AND (
            (:recipientId IS NOT NULL AND recipient_id = :recipientId)
            OR (
              :recipientId IS NULL
              AND (
                (:currentUserId IS NOT NULL AND recipient_id = :currentUserId)
                OR (
                  recipient_id IS NULL
                  AND (
                    n.org_unit_id IS NULL
                    OR (
                      :currentOrgUnitId IS NOT NULL
                      AND EXISTS (
                        SELECT 1
                        FROM org_closure c
                        WHERE c.tenant_id = :tenantId
                          AND (
                            (c.ancestor_id = :currentOrgUnitId AND c.descendant_id = n.org_unit_id)
                            OR (c.ancestor_id = n.org_unit_id AND c.descendant_id = :currentOrgUnitId)
                          )
                      )
                    )
                  )
                )
              )
            )
          )
        """)
    long countByVisibleRecipientScope(
        String tenantId,
        String status,
        String level,
        String recipientId,
        String currentUserId,
        String currentOrgUnitId);

    default List<WorkflowNotification> pageByVisibleRecipientScope(
            String tenantId,
            String status,
            String level,
            String recipientId,
            String currentUserId,
            int offset,
            int limit) {
        return pageByVisibleRecipientScope(tenantId, status, level, recipientId, currentUserId, null, offset, limit);
    }

    @Query("""
        SELECT n.*
        FROM mk_engine_notification n
        WHERE n.tenant_id = :tenantId
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
        SELECT n.*
        FROM mk_engine_notification n
        WHERE n.tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:level IS NULL OR notification_level = :level)
          AND (
            (:recipientId IS NOT NULL AND recipient_id = :recipientId)
            OR (
              :recipientId IS NULL
              AND (
                (:currentUserId IS NOT NULL AND recipient_id = :currentUserId)
                OR (
                  recipient_id IS NULL
                  AND (
                    n.org_unit_id IS NULL
                    OR (
                      :currentOrgUnitId IS NOT NULL
                      AND EXISTS (
                        SELECT 1
                        FROM org_closure c
                        WHERE c.tenant_id = :tenantId
                          AND (
                            (c.ancestor_id = :currentOrgUnitId AND c.descendant_id = n.org_unit_id)
                            OR (c.ancestor_id = n.org_unit_id AND c.descendant_id = :currentOrgUnitId)
                          )
                      )
                    )
                  )
                )
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
        String currentOrgUnitId,
        int offset,
        int limit);
}
