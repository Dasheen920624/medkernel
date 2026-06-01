package com.medkernel.shared.observability;

import java.time.Instant;
import java.util.List;

import com.medkernel.shared.observability.DiagnoseResponse.PayloadSummary;
import com.medkernel.shared.observability.DiagnoseResponse.StateTransitionEntry;

/**
 * 按 traceId 汇总的一键诊断响应。
 *
 * <p>只返回状态流转和 payload 引用摘要，不返回 payload 明文。
 */
public record TraceDiagnoseResponse(
    String traceId,
    Instant startedAt,
    Instant endedAt,
    Long durationMs,
    List<StateTransitionEntry> stateHistory,
    List<PayloadSummary> payloads
) {}
