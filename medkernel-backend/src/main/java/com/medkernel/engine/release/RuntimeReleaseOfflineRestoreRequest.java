package com.medkernel.engine.release;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 机构生效版本离线交付文件恢复执行请求。
 *
 * @param evidenceId 已验签的可信存证证据 ID
 * @param expectedSourceReleaseId 预期离线文件来源机构生效版本 ID
 * @param expectedHospitalId 预期目标医院 ID
 * @param expectedCurrentReleaseId 执行恢复前预期的当前机构生效版本 ID
 * @param confirmedFileDigest 人工确认的离线文件 SM3 摘要
 */
public record RuntimeReleaseOfflineRestoreRequest(
    @NotBlank(message = "证据 ID 不能为空")
    @Size(max = 64, message = "证据 ID 长度不能超过 64")
    @Pattern(regexp = "[A-Za-z0-9._-]+", message = "证据 ID 只能包含字母、数字、点、下划线和连字符")
    String evidenceId,

    @NotBlank(message = "预期来源机构生效版本 ID 不能为空")
    @Size(max = 64, message = "预期来源机构生效版本 ID 长度不能超过 64")
    String expectedSourceReleaseId,

    @NotBlank(message = "预期医院 ID 不能为空")
    @Size(max = 64, message = "预期医院 ID 长度不能超过 64")
    String expectedHospitalId,

    @NotBlank(message = "预期当前机构生效版本 ID 不能为空")
    @Size(max = 64, message = "预期当前机构生效版本 ID 长度不能超过 64")
    String expectedCurrentReleaseId,

    @NotBlank(message = "确认文件摘要不能为空")
    @Pattern(regexp = "sm3:[0-9a-f]{64}", message = "确认文件摘要必须是 sm3 摘要")
    String confirmedFileDigest
) {
}
