package com.medkernel.engine.followup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 随访模板发布请求。
 */
public record FollowupTemplatePublishRequest(
    @NotBlank @Size(max = 128) String impactDigest,
    @NotBlank @Size(max = 1000) String reason
) {
}
