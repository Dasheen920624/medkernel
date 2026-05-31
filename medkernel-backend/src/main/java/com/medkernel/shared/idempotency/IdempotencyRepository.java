package com.medkernel.shared.idempotency;

import java.time.Instant;
import java.util.Optional;

/**
 * 平台级幂等记录仓储。
 */
public interface IdempotencyRepository {

    Optional<IdempotencyRecord> findActive(String tenantId, String idempotencyKey, Instant now);

    boolean reserve(IdempotencyRecord record);

    void complete(IdempotencyRecord record);

    void delete(String tenantId, String idempotencyKey);

    default void save(IdempotencyRecord record) {
        complete(record);
    }
}
