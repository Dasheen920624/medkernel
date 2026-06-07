package com.medkernel.engine.llm;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 平台模型能力目录保存请求。
 */
public record ModelCapabilityDefinitionUpsertRequest(
    @NotBlank(message = "能力名称不能为空")
    @Size(max = 120, message = "能力名称不能超过120个字符")
    String displayName,

    @NotBlank(message = "能力说明不能为空")
    @Size(max = 500, message = "能力说明不能超过500个字符")
    String description,

    @NotBlank(message = "能力分类不能为空")
    @Size(max = 64, message = "能力分类不能超过64个字符")
    String category,

    @NotNull(message = "启用状态不能为空")
    Boolean enabled,

    @NotNull(message = "排序不能为空")
    @Min(value = 0, message = "排序不能小于0")
    @Max(value = 9999, message = "排序不能大于9999")
    Integer sortOrder
) {
}
