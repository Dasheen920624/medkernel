package com.medkernel.engine.datasvc;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;

/**
 * 受控工具执行请求（DATASVC-01 PR2）。
 *
 * <p>{@code purpose} 调用用途为必填——MCP/CLI 调用须声明用途，进审计（FR-6）；{@code from}/{@code to}/
 * {@code page}/{@code size} 为读模型类工具的时间窗与分页参数（缺省由后端服务取默认窗与分页钳制）。
 */
public record ToolExecutionRequest(
    @NotBlank(message = "受控工具调用必须声明用途") String purpose,
    Instant from,
    Instant to,
    int page,
    int size
) {}
