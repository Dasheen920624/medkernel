package com.medkernel.engine.authoring;

/**
 * 自然语言预览中的结构化片段，供前端并排高亮 L2/L3 对应位置。
 */
public record AuthoringPreviewSegment(
    String kind,
    String path,
    String text
) {
}
