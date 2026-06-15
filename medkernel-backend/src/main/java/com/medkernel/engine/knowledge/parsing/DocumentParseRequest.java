package com.medkernel.engine.knowledge.parsing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 文档解析请求（AIK-STD-02）。{@code content} 按 {@code format} 承载原文：
 * STRUCTURED_TEXT 为原文文本，PDF/WORD 为原文字节的 Base64 编码（统一文本传输，避免多字段）。
 * {@code format} 决定分派的解析适配器；非法 Base64 由编排层结构化 400 拒绝。
 */
public record DocumentParseRequest(
    @NotNull Long sourceDocumentId,
    @NotBlank String versionNo,
    @NotBlank String fileName,
    @NotNull DocumentFormat format,
    @NotBlank String content
) {
}
