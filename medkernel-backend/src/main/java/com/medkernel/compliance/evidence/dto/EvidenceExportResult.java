package com.medkernel.compliance.evidence.dto;

/**
 * 证据包导出结果 DTO。
 *
 * @param archiveHash 证据包内容的 SM3 摘要
 * @param archiveUri  可下载的真实证据包 URI
 * @param contentType 证据包内容类型
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
