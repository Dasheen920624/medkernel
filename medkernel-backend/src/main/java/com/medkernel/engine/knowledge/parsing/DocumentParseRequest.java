package com.medkernel.engine.knowledge.parsing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 文档解析请求（AIK-STD-02）。PR1 经 {@code content}（结构化文本）提交；
 * 后续 PR 的二进制格式将扩展 base64 字段。{@code format} 决定分派的解析适配器。
 */
public record DocumentParseRequest(
    @NotNull Long sourceDocumentId,
    @NotBlank String versionNo,
    @NotBlank String fileName,
    @NotNull DocumentFormat format,
    @NotBlank String content
) {
}
