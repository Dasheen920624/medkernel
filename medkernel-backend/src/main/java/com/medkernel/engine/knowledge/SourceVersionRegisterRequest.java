package com.medkernel.engine.knowledge;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 来源文献版本注册请求。
 *
 * @param sourceDocumentId 来源文献 ID
 * @param versionNo 版本号
 * @param publishedAt 发布时间
 * @param contentHash 内容哈希值
 * @param fileUri 文件统一资源标识符
 * @param language 语言，默认 zh-CN
 */
public record SourceVersionRegisterRequest(
    @NotNull
    Long sourceDocumentId,
    @NotBlank
    String versionNo,
    Instant publishedAt,
    @NotBlank
    String contentHash,
    @NotBlank
    String fileUri,
    String language
) {
}
