package com.medkernel.engine.integration.masterdata;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在主批次事务回滚后独立保留失败批次元数据，不保存业务载荷。
 */
@Component
public class MasterDataSyncFailureRecorder {

    private final MasterDataSyncBatchRepository batches;

    public MasterDataSyncFailureRecorder(MasterDataSyncBatchRepository batches) {
        this.batches = batches;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            String tenantId,
            String webhookId,
            MasterDataSyncRequest request,
            String payloadHash,
            String traceId,
            String errorCode) {
        MasterDataSyncBatch current = batches
            .findByTenantIdAndSourceSystemAndBatchId(
                tenantId, normalize(request.sourceSystem()), request.batchId())
            .orElse(null);
        if (current != null && current.status() == MasterDataSyncStatus.SUCCESS) {
            return;
        }
        Instant now = Instant.now();
        batches.save(new MasterDataSyncBatch(
            current == null ? null : current.id(),
            request.batchId(),
            tenantId,
            webhookId,
            request.adapterId(),
            normalize(request.sourceSystem()),
            request.mode(),
            blankToNull(request.previousCursor()),
            request.cursor(),
            payloadHash,
            MasterDataSyncStatus.FAILED,
            request.items().size(),
            0,
            request.items().size(),
            errorCode,
            current == null ? now : current.createdAt(),
            now,
            traceId));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
