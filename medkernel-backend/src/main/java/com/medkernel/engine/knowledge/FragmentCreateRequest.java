package com.medkernel.engine.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 引用锚点片段创建请求。
 *
 * @param sourceVersionId 来源文献版本 ID
 * @param anchorPath 层级路径，同一版本下唯一（如 section-3.2.1）
 * @param anchorLabel 锚点标签/标题
 * @param textExcerpt 来源文本片段
 */
public record FragmentCreateRequest(
    @NotNull
    Long sourceVersionId,
    @NotBlank
    String anchorPath,
    @NotBlank
    String anchorLabel,
    @NotBlank
    String textExcerpt
) {
}
