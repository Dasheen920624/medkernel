package com.medkernel.engine.projection;

/**
 * 外部投影执行器命令，只携带同步元数据，不携带业务权威事实。
 */
public record ProjectionExecutionCommand(
    String tenantId,
    String syncId,
    ProjectionTargetType targetType,
    int sourceCount,
    String sourceHash,
    String traceId
) {}
