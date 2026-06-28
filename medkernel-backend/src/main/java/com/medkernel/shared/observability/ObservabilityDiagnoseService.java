package com.medkernel.shared.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.medkernel.shared.observability.DiagnoseResponse.PayloadSummary;
import com.medkernel.shared.observability.DiagnoseResponse.StateTransitionEntry;

/**
 * 可观测诊断查询服务。
 *
 * <p>按 traceId 聚合状态流转和 payload 摘要，供证据详情或运行诊断定位执行链路。
 */
@Service
public class ObservabilityDiagnoseService {

    private final StateTransitionHistoryRepository historyRepository;
    private final PayloadStoragePort payloadStorage;

    public ObservabilityDiagnoseService(
            StateTransitionHistoryRepository historyRepository,
            PayloadStoragePort payloadStorage) {
        this.historyRepository = historyRepository;
        this.payloadStorage = payloadStorage;
    }

    public TraceDiagnoseResponse findByTraceId(String traceId) {
        List<StateTransitionHistory> histories =
            historyRepository.findByTraceIdOrderByOccurredAtAsc(traceId);
        List<StateTransitionEntry> stateHistory = histories.stream()
            .map(this::toStateEntry)
            .toList();
        List<PayloadSummary> payloads = payloadStorage.findByTraceId(traceId)
            .stream()
            .map(this::toPayloadSummary)
            .toList();
        Instant startedAt = histories.stream()
            .map(StateTransitionHistory::occurredAt)
            .filter(value -> value != null)
            .min(Comparator.naturalOrder())
            .orElse(null);
        Instant endedAt = histories.stream()
            .map(StateTransitionHistory::occurredAt)
            .filter(value -> value != null)
            .max(Comparator.naturalOrder())
            .orElse(null);
        Long durationMs = startedAt == null || endedAt == null
            ? null
            : Duration.between(startedAt, endedAt).toMillis();
        return new TraceDiagnoseResponse(
            traceId, startedAt, endedAt, durationMs, stateHistory, payloads);
    }

    private StateTransitionEntry toStateEntry(StateTransitionHistory h) {
        TransitionError error = h.errorCode() == null ? null : new TransitionError(
            h.errorCode(), h.errorClass(), h.errorMessage(),
            h.retryCount(), h.nextRetryAt()
        );
        return new StateTransitionEntry(
            h.fromStatus(), h.toStatus(), h.reason(),
            h.actor(), h.traceId(), error, h.occurredAt()
        );
    }

    private PayloadSummary toPayloadSummary(PayloadRef ref) {
        return new PayloadSummary(
            ref.digest(),
            ref.sizeBytes(),
            ref.contentType(),
            ref.storageType(),
            ref.uri()
        );
    }
}
