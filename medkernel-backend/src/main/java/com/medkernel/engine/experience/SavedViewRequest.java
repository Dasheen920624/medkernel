package com.medkernel.engine.experience;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 页面保存视图写入请求。
 */
public record SavedViewRequest(
    @NotBlank(message = "页面标识不能为空")
    @Size(max = 96, message = "页面标识长度超限")
    String pageKey,

    @NotBlank(message = "视图名称不能为空")
    @Size(max = 96, message = "视图名称长度超限")
    String viewName,

    @NotBlank(message = "视图定义不能为空")
    @Size(max = 12000, message = "视图定义长度超限")
    String definitionJson,

    boolean defaultView
) {
}
