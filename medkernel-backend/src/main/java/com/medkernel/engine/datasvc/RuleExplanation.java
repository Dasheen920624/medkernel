package com.medkernel.engine.datasvc;

import java.time.Instant;

/**
 * 规则解释读模型（DATASVC-01 PR2-b，受控工具 {@code explainRule} 的 D1 输出）。
 *
 * <p>承载单条规则的已发布资产元数据（编码/名称/类型/风险级别/状态/激活版本），均为 D1「已发布资产元数据」，
 * 不含患者字段。{@code degraded} 为上游不可用时的诚实降级标记（字段留空、不以伪造元数据伪装，铁律 #1）。
 */
public record RuleExplanation(
    String ruleId,
    String ruleCode,
    String name,
    String ruleType,
    String riskLevel,
    String status,
    String activeVersionId,
    EngineDataLevel dataLevel,
    Instant generatedAt,
    boolean degraded,
    String degradeReason
) {}
