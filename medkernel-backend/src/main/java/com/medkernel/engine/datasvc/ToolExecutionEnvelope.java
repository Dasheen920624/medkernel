package com.medkernel.engine.datasvc;

/**
 * 受控工具执行治理信封（DATASVC-01 PR2，FR-4）。
 *
 * <p>每次受控工具执行必带：{@code traceId}、数据级别、脱敏策略、来源版本、权限结果、降级状态与输出 hash，
 * 结构化结果放 {@code payload}。失败时返回结构化原因，不暴露内部异常/SQL/原始提示词/敏感字段。
 * {@code degraded} 为上游诚实降级标记，不以空数据伪装（铁律 #1）。
 */
public record ToolExecutionEnvelope(
    String toolName,
    EngineDataLevel dataLevel,
    String desensitizationPolicy,
    String sourceVersion,
    boolean permissionGranted,
    boolean degraded,
    String degradeReason,
    String traceId,
    String outputHash,
    Object payload
) {}
