package com.medkernel.engine.datasvc;

import java.time.Instant;

/**
 * 隐私分级策略判定（DATASVC-01 PR2-c，受控工具 {@code validatePrivacyPolicy} 的 D0 输出）。
 *
 * <p>按数据分级 D0–D5 策略（规范 §7 / FR-2）判定某一拟用级别是否准入数据服务/CLI/MCP/模型输入：
 * D0/D1/D2 准入；D3/D4 须字段级加密、当前数据服务尚未实现字段级加密故诚实判 {@code allowed=false}
 * （不以「已支持」伪装未实现的高敏处理，铁律 #1）；D5 重要个人信息禁入。判定结果本身为 D0 策略元数据。
 */
public record PrivacyPolicyDecision(
    String requestedLevel,
    boolean allowed,
    boolean requiresFieldEncryption,
    String reason,
    EngineDataLevel dataLevel,
    Instant generatedAt
) {}
