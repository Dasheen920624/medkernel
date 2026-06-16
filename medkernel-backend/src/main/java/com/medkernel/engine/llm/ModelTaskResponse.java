package com.medkernel.engine.llm;

/**
  * 模型网关推理任务响应对象。
  */
public record ModelTaskResponse(
    String taskId,
    String status,
    String outputContent,
    String modelMode,
    String modelVersion,
    String promptVersion,
    String toolVersion,
    String sourceCitations,
    Double confidence,
    String riskLevel,
    Boolean fallbackUsed,
    String fallbackReason,
    Long timeCostMs,
    String traceId
) {}
