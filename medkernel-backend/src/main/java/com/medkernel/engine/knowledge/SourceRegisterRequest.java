package com.medkernel.engine.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 来源文献注册请求。
 *
 * @param sourceCode 来源代码，在租户下唯一
 * @param sourceType 来源类型
 * @param authorityLevel 可信分级
 * @param title 标题
 * @param publisher 发布者
 * @param license 许可证
 * @param language 语言，默认 zh-CN
 * @param authorityBasis 分级依据
 */
public record SourceRegisterRequest(
    @NotBlank
    String sourceCode,
    @NotNull
    SourceType sourceType,
    @NotNull
    SourceAuthorityLevel authorityLevel,
    @NotBlank
    String title,
    String publisher,
    String license,
    String language,
    @NotBlank
    String authorityBasis
) {
}
