package com.medkernel.engine.datasvc;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.medkernel.engine.knowledge.production.CandidateSubmissionRequest;

/**
 * Agent 受控回写候选工具载荷（AIK-STD-14）。
 *
 * <p>Agent 不直接调用知识生产 REST，不直连库；只把 job、幂等键和候选信封交给受控工具，由后端统一校验并转入
 * AIK-STD-13 候选流水线。
 */
public record AgentProductionCandidatePayload(
    @NotBlank String jobCode,
    @NotBlank String idempotencyKey,
    String dataLevel,
    @NotNull @Valid CandidateSubmissionRequest submission
) {
}
