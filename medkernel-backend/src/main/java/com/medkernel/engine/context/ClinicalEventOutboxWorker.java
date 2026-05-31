package com.medkernel.engine.context;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.observability.TraceIdPropagator;

/**
 * DB-Only 临床事件 outbox worker。
 */
@Component
public class ClinicalEventOutboxWorker {

    private final ClinicalEventOutboxRepository outbox;
    private final ClinicalEventProcessor processor;
    private final ClinicalEventProperties properties;
    private final String workerId;

    public ClinicalEventOutboxWorker(ClinicalEventOutboxRepository outbox,
                                     ClinicalEventProcessor processor,
                                     ClinicalEventProperties properties) {
        this.outbox = outbox;
        this.processor = processor;
        this.properties = properties;
        this.workerId = resolveWorkerId();
    }

    public void pollOnce() {
        Instant now = Instant.now();
        for (ClinicalEventOutbox row : outbox.findReadyToClaim(now, properties.workerBatchSize())) {
            if (outbox.claim(row.id(), "CLAIMED", workerId, now) == 0) {
                continue;
            }
            processClaimed(row);
        }
    }

    private void processClaimed(ClinicalEventOutbox row) {
        TraceIdPropagator.restoreFromTrace(row.traceId(), row.tenantId(), row.actorUserId());
        try {
            processor.process(row.eventId(), row.tenantId());
            outbox.markProcessed(row.id(), Instant.now());
        } catch (ApiException exception) {
            markFailed(row, exception.errorCode());
        } catch (RuntimeException exception) {
            markFailed(row, ErrorCode.INTERNAL_ERROR);
        } finally {
            TraceIdPropagator.clear();
        }
    }

    private void markFailed(ClinicalEventOutbox row, ErrorCode errorCode) {
        int nextRetry = safeRetryCount(row) + 1;
        boolean dead = nextRetry >= properties.maxRetries();
        String status = dead ? "DEAD" : "PENDING";
        Instant nextAttemptAt = nextAttemptAt(nextRetry);
        processor.markFailed(row.eventId(), row.tenantId(), errorCode, nextRetry, dead, nextAttemptAt);
        outbox.markFailed(row.id(), status, nextRetry, errorCode.code(), nextAttemptAt);
    }

    private Instant nextAttemptAt(int retryCount) {
        if (retryCount >= properties.maxRetries()) {
            return Instant.now();
        }
        int listSize = properties.backoffSeconds().size();
        if (retryCount <= listSize) {
            return Instant.now().plusSeconds(properties.backoffSeconds().get(retryCount - 1));
        } else {
            // 当重试次数超过预设列表时，开启基于指数退避（Exponential Backoff）算法进行安全退避，
            // 基础时间是配置列表的最后一个值，以 2 的指数次方递增，最大不超过 3600 秒（1小时），防高并发震荡锁死。
            long lastConfiguredBackoff = properties.backoffSeconds().get(listSize - 1);
            long factor = 1L << Math.min(retryCount - listSize, 6); // 限制指数大小防止溢出 (最大 2^6 = 64)
            long exponentialBackoff = lastConfiguredBackoff * factor;
            long finalBackoff = Math.min(exponentialBackoff, 3600L); // 最大上限 1 小时
            return Instant.now().plusSeconds(finalBackoff);
        }
    }

    private int safeRetryCount(ClinicalEventOutbox row) {
        return row.retryCount() == null ? 0 : row.retryCount();
    }

    private String resolveWorkerId() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException exception) {
            return "clinical-event-worker";
        }
    }
}
