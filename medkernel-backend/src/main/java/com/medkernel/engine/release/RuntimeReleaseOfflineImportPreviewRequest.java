package com.medkernel.engine.release;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 机构生效版本离线交付文件导入预检请求。
 *
 * @param evidenceId 已导出的可信存证证据 ID
 * @param expectedReleaseId 预期机构生效版本 ID
 * @param expectedHospitalId 预期医院 ID
 */
public record RuntimeReleaseOfflineImportPreviewRequest(
    @NotBlank(message = "证据 ID 不能为空")
    @Size(max = 64, message = "证据 ID 长度不能超过 64")
    @Pattern(regexp = "[A-Za-z0-9._-]+", message = "证据 ID 只能包含字母、数字、点、下划线和连字符")
    String evidenceId,

    @NotBlank(message = "预期机构生效版本 ID 不能为空")
    @Size(max = 64, message = "预期机构生效版本 ID 长度不能超过 64")
    String expectedReleaseId,

    @NotBlank(message = "预期医院 ID 不能为空")
    @Size(max = 64, message = "预期医院 ID 长度不能超过 64")
    String expectedHospitalId
) {
}
