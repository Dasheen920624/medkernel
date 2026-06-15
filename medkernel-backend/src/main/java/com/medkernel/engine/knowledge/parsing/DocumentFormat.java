package com.medkernel.engine.knowledge.parsing;

/**
 * 文档格式（AIK-STD-02）。PR1 仅 {@link #STRUCTURED_TEXT} 有确定性解析器；
 * PDF/WORD 由后续 PR 的 PDFBox/POI 适配器接入，缺适配器时编排层诚实记 FAILED（不伪装已支持）。
 */
public enum DocumentFormat {
    /** 结构化文本（Markdown 标题或点分编号标题），B0 纯规则确定性解析。 */
    STRUCTURED_TEXT,
    /** PDF（待 PDFBox 适配器接入）。 */
    PDF,
    /** Word（待 POI 适配器接入）。 */
    WORD
}
