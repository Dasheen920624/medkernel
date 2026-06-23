package com.medkernel.engine.release;

import jakarta.validation.constraints.NotBlank;

/**
 * 医院运行修订回滚请求。
 */
public record ClinicalRuntimeRollbackRequest(
    @NotBlank(message = "目标运行修订不能为空")
    String targetReleaseId
) {
}
