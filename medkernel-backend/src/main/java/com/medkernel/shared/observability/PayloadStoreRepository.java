package com.medkernel.shared.observability;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 可观测 payload 存储仓储。
 */
@Repository
public interface PayloadStoreRepository extends ListCrudRepository<PayloadStoreRecord, Long> {

    Optional<PayloadStoreRecord> findByPayloadId(String payloadId);

    Optional<PayloadStoreRecord> findByPayloadIdAndDeletedAtIsNull(String payloadId);

    List<PayloadStoreRecord> findByTraceIdAndDeletedAtIsNullOrderByCreatedAtAsc(String traceId);
}
