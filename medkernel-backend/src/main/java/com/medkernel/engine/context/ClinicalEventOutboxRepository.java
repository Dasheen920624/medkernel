package com.medkernel.engine.context;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 临床事件外部收件箱（Outbox）数据库仓储接口。
 *
 * <p>处理包含临床事件异步派发、失败重试、锁定状态标记及生命周期的持久化操作，
 * 支撑 GA-ENG-API-02 临床事件链路的异步投递与死信链路。
 */
@Repository
public interface ClinicalEventOutboxRepository extends ListCrudRepository<ClinicalEventOutbox, Long> {

    Optional<ClinicalEventOutbox> findByEventIdAndTenantId(String eventId, String tenantId);

    @Query("""
        SELECT * FROM clinical_event_outbox
        WHERE tenant_id = :tenantId AND claim_status = 'DEAD'
        ORDER BY created_at DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<ClinicalEventOutbox> pageDeadByTenantId(String tenantId, int offset, int limit);

    @Query("""
        SELECT COUNT(*) FROM clinical_event_outbox
        WHERE tenant_id = :tenantId AND claim_status = 'DEAD'
        """)
    long countDeadByTenantId(String tenantId);

    @Query("""
        SELECT * FROM clinical_event_outbox
        WHERE claim_status = 'PENDING' AND next_attempt_at <= :now
        ORDER BY created_at
        OFFSET 0 ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<ClinicalEventOutbox> findReadyToClaim(Instant now, int limit);

    @Modifying
    @Query("""
        UPDATE clinical_event_outbox
        SET claim_status = :claimStatus,
            claimed_by = :claimedBy,
            claimed_at = :claimedAt
        WHERE id = :id AND claim_status = 'PENDING'
        """)
    int claim(Long id, String claimStatus, String claimedBy, Instant claimedAt);

    @Modifying
    @Query("""
        UPDATE clinical_event_outbox
        SET claim_status = :claimStatus,
            retry_count = :retryCount,
            last_error_code = :lastErrorCode,
            next_attempt_at = :nextAttemptAt
        WHERE id = :id
        """)
    int markFailed(Long id, String claimStatus, Integer retryCount, String lastErrorCode, Instant nextAttemptAt);

    @Modifying
    @Query("""
        UPDATE clinical_event_outbox
        SET claim_status = 'PROCESSED',
            processed_at = :processedAt
        WHERE id = :id
        """)
    int markProcessed(Long id, Instant processedAt);
}
