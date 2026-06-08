package com.medkernel.engine.authoring;

import java.util.List;

/**
 * 条件树或路径守卫的自然语言预览结果。
 */
public record AuthoringPreviewResponse(
    String previewText,
    List<String> lines,
    List<AuthoringPreviewSegment> segments,
    List<String> warnings,
    String traceId
) {
    public AuthoringPreviewResponse {
        lines = lines == null ? List.of() : List.copyOf(lines);
        segments = segments == null ? List.of() : List.copyOf(segments);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
