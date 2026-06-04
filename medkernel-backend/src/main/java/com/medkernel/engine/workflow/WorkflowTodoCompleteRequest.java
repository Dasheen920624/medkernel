package com.medkernel.engine.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 完成统一待办请求。
 */
public record WorkflowTodoCompleteRequest(
    @NotBlank(message = "完成说明不能为空")
    @Size(max = 1000, message = "完成说明不能超过 1000 字")
    String completionReason
) {
}
