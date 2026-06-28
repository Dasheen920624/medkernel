package com.medkernel.engine.knowledge.parsing;

/**
 * 解析输入（AIK-STD-02）。稳定端口入参，覆盖全格式：原始字节 + 文件名 + 声明格式。
 * STRUCTURED_TEXT 解析器按 UTF-8 解码 {@code rawBytes}；二进制格式解析器直接读取原始字节。
 */
public record ParseInput(
    Long sourceDocumentId,
    String versionNo,
    String fileName,
    DocumentFormat format,
    byte[] rawBytes,
    String createdBy
) {
}
