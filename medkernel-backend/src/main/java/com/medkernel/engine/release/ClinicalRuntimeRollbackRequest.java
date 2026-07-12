package com.medkernel.engine.release;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 机构生效版本回滚请求。
 */
public record ClinicalRuntimeRollbackRequest(
    @NotBlank(message = "目标机构生效版本不能为空")
    @Size(max = 128)
    String targetReleaseId,
    @NotBlank(message = "已确认当前机构生效版本不能为空")
    @Size(max = 128)
    String expectedCurrentReleaseId
) {
}
