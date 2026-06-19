package com.medkernel.engine.knowledge.production.initialization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** LOW 初始化候选原子批审请求。 */
public record KnowledgeInitializationBatchApproveRequest(
    @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String expectedOverallHash,
    @NotBlank @Size(max = 128) String idempotencyKey,
    @NotBlank @Size(max = 500) String reason
) {
}
