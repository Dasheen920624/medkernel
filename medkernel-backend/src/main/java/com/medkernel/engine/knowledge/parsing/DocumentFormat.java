package com.medkernel.engine.knowledge.parsing;

/**
 * 文档格式（AIK-STD-02）。结构化文本、PDF 与 Word 均由确定性解析器处理；解析失败时编排层诚实记
 * FAILED，不回退模型或伪装支持。
 */
public enum DocumentFormat {
    /** 结构化文本（Markdown 标题或点分编号标题），B0 纯规则确定性解析。 */
    STRUCTURED_TEXT,
    /** PDF（PDFBox 确定性解析）。 */
    PDF,
    /** Word（POI 确定性解析）。 */
    WORD
}
