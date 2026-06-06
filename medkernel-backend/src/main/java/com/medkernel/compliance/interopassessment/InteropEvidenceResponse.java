package com.medkernel.compliance.interopassessment;

/**
 * OPT-05 测评项证据映射响应。
 */
public record InteropEvidenceResponse(
    String mapId,
    InteropEvidenceSourceType sourceType,
    String sourceId,
    String evidenceRef,
    String evidenceSummary,
    String fileUri,
    String payloadDigest,
    boolean sharedWithEmrLevel,
    String traceId
) {
}
