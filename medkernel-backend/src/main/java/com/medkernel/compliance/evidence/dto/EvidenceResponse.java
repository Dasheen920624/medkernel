package com.medkernel.compliance.evidence.dto;

import java.time.Instant;
import com.medkernel.compliance.evidence.domain.EvidenceSnapshot;

/**
 * 医疗合规可信存证证据快照返回响应 DTO Record。
 */
public record EvidenceResponse(
    Long id,
    String evidenceId,
    String tenantId,
    String traceId,
    String evidenceType,
    String action,
    String subjectType,
    String subjectId,
    String evidenceSummary,
    String payloadSnapshot,
    String payloadHash,
    String fileUri,
    String fileDigest,
    String signatureAlgorithm,
    String signatureValue,
    String signerPublicKey,
    boolean isValid,
    Instant createdAt,
    String createdBy
) {
    public static EvidenceResponse fromEntity(EvidenceSnapshot entity) {
        if (entity == null) {
            return null;
        }
        return new EvidenceResponse(
            entity.id(),
            entity.evidenceId(),
            entity.tenantId(),
            entity.traceId(),
            entity.evidenceType(),
            entity.action(),
            entity.subjectType(),
            entity.subjectId(),
            entity.evidenceSummary(),
            entity.payloadSnapshot(),
            entity.payloadHash(),
            entity.fileUri(),
            entity.fileDigest(),
            entity.signatureAlgorithm(),
            entity.signatureValue(),
            entity.signerPublicKey(),
            entity.isValid(),
            entity.createdAt(),
            entity.createdBy()
        );
    }
}
