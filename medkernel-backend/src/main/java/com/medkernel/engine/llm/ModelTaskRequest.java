package com.medkernel.engine.llm;

import jakarta.validation.constraints.NotBlank;

/**
  * 模型网关推理任务提交请求对象。
  */
public record ModelTaskRequest(
    @NotBlank(message = "能力代码不能为空")
    String capabilityCode,

    @NotBlank(message = "输入内容数据不能为空")
    String inputData,

    Integer timeoutSeconds
) {}
