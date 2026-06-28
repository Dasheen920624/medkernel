package com.medkernel.engine.datasvc;

/**
 * 受控工具目录条目（DATASVC-01，CLI/MCP 共用受控工具层）。
 *
 * <p>对外登记一个受控工具的对外名、用途、默认数据级别与所需权限码，供 CLI {@code diagnostics}/MCP
 * 工具发现使用。工具只能经受控入口执行，不直连库、不绕权限脱敏审计降级（FR-5）。
 */
public record ControlledToolDescriptor(
    String name,
    String purpose,
    EngineDataLevel dataLevel,
    String requiredPermission
) {}
