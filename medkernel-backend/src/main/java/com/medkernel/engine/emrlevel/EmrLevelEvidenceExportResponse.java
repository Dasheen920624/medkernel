package com.medkernel.engine.emrlevel;

/**
 * 电子病历评级证据导出响应。
 */
public record EmrLevelEvidenceExportResponse(
    String exportId,
    String targetId,
    String hospitalOrgId,
    String standardVersion,
    String status,
    String contentType,
    String fileName,
    String payloadSha256,
    String payload,
    int evidenceLineCount,
    String traceId
) {
}
