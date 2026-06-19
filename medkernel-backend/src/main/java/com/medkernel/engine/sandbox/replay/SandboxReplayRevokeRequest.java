package com.medkernel.engine.sandbox.replay;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 撤销历史重放清单的原因。 */
public record SandboxReplayRevokeRequest(
    @NotBlank @Size(max = 512) String reason
) {
}
