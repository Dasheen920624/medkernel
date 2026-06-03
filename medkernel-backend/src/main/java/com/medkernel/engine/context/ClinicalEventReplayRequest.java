package com.medkernel.engine.context;

import jakarta.validation.constraints.NotBlank;

/**
 * 临床事件重放请求。
 */
public record ClinicalEventReplayRequest(
    @NotBlank String sourceEventId
) {
    public ClinicalEventReplayRequest {
        if (sourceEventId != null) {
            sourceEventId = sourceEventId.trim();
        }
    }
}
