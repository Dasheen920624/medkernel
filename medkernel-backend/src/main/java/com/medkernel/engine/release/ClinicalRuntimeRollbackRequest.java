package com.medkernel.engine.release;

import jakarta.validation.constraints.NotBlank;

/**
 * 机构生效版本回滚请求。
 */
public record ClinicalRuntimeRollbackRequest(
    @NotBlank(message = "目标机构生效版本不能为空")
    String targetReleaseId
) {
}
