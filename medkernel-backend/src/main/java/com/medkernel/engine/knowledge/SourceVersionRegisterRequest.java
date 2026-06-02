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
 * @param contentHash 院方离线文件已计算的 SHA-256 内容哈希；没有时由 content 计算
 * @param fileUri 文件统一资源标识符
 * @param language 语言，默认 zh-CN
 * @param content 可选来源原文；存在时由系统计算 SHA-256
 */
public record SourceVersionRegisterRequest(
    @NotNull
    Long sourceDocumentId,
    @NotBlank
    String versionNo,
    Instant publishedAt,
    String contentHash,
    @NotBlank
    String fileUri,
    String language,
    String content
) {
}
