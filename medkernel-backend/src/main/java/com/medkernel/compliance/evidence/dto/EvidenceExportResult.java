package com.medkernel.compliance.evidence.dto;

/**
 * 证据导出结果 DTO。
 *
 * @param archiveHash 证据导出内容的 SM3 摘要
 * @param archiveUri  可下载的真实证据导出 URI
 * @param contentType 证据导出内容类型
 * @param itemCount   导出证据条数
 * @param status      导出状态
 */
public record EvidenceExportResult(
    String archiveHash,
    String archiveUri,
    String contentType,
    long itemCount,
    String status
) {}
