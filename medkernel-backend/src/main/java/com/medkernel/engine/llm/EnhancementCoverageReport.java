package com.medkernel.engine.llm;

import java.util.List;

/**
 * 模型赋能覆盖矩阵核查报告（LLM-05 FR-4）。
 *
 * <p>{@code total} 全部可增强业务点、{@code active} 已接入（能力码 + B0 就绪）、{@code gaps} 缺口数，
 * {@code gapEntries} 逐条列出待配置业务点——缺口诚实标注，不虚报覆盖（铁律 #1）。
 */
public record EnhancementCoverageReport(
    int total,
    int active,
    int gaps,
    List<ModelEnhancementMatrixResponse> gapEntries
) {}
