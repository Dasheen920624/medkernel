package com.medkernel.compliance.evidence.dto;

/**
 * 证据哈希防篡改双向校验结果 DTO Record。
 */
public record EvidenceVerifyResult(
    String evidenceId,
    boolean isValid,
    String calculatedHash,
    String storedHash,
    String signatureAlgorithm,
    boolean signatureValid,
    String fileUri,
    String fileDigest
) {}
