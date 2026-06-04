package com.medkernel.engine.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 转交统一待办请求。
 */
public record WorkflowTodoTransferRequest(
    @NotBlank(message = "接收人不能为空")
    @Size(max = 64, message = "接收人不能超过 64 字")
    String transferTo,

    @Size(max = 64, message = "接收角色不能超过 64 字")
    String transferRole,

    @NotBlank(message = "转交说明不能为空")
    @Size(max = 1000, message = "转交说明不能超过 1000 字")
    String transferReason
) {
}
