package com.medkernel.engine.datasvc;

import java.time.Instant;

/**
 * 引擎信号汇总（DATASVC-01 PR2，{@code summarizeEngineSignals} 工具 D2 输出）。
 *
 * <p>汇总现已建读模型的聚合信号分组数：规则使用、知识使用、临床信号；{@code generatedAt} 生成时刻
 * 不伪装实时。仅汇总真实存在的读模型分组计量，不虚构未建上游（路径/质控）的数字（铁律 #1）。
 */
public record EngineSignalsSummary(
    long ruleGroups,
    long knowledgeGroups,
    long clinicalSignalGroups,
    Instant generatedAt
) {}
