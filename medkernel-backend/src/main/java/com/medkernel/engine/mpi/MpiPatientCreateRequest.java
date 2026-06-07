package com.medkernel.engine.mpi;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 患者主索引创建请求 DTO。
 */
public record MpiPatientCreateRequest(
    @NotBlank(message = "脱敏姓名不能为空")
    String maskedName,

    @NotBlank(message = "性别不能为空")
    @Pattern(regexp = "(?i)M|F|UNKNOWN", message = "性别仅支持 M、F 或 UNKNOWN")
    String gender,

    @NotNull(message = "年龄不能为空")
    @Min(value = 0, message = "年龄不能小于 0")
    Integer age,

    @NotBlank(message = "身份证后四位不能为空")
    @Pattern(regexp = "\\d{4}", message = "身份证后四位必须为 4 位数字")
    String idLast4
) {}
