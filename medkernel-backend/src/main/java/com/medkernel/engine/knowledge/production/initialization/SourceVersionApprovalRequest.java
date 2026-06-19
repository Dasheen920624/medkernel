package com.medkernel.engine.knowledge.production.initialization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 来源版本独立批准请求。 */
public record SourceVersionApprovalRequest(
    @NotBlank @Size(max = 500) String reason
) {
}
