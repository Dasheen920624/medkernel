package com.medkernel.engine.followup;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * 随访异常与回流事件存储库。
 */
public interface FollowupEventRepository extends CrudRepository<FollowupEvent, Long>, PagingAndSortingRepository<FollowupEvent, Long> {
    Optional<FollowupEvent> findByEventId(String eventId);
    List<FollowupEvent> findByTenantIdAndPlanId(String tenantId, String planId);
    Optional<FollowupEvent> findByTenantIdAndEventTypeAndIdempotencyKey(
        String tenantId, FollowupEventType eventType, String idempotencyKey);
}
