package com.medkernel.compliance.evidence.service;

import org.springframework.stereotype.Component;

import com.medkernel.compliance.evidence.dto.EvidenceCreateDto;
import com.medkernel.compliance.evidence.dto.EvidenceResponse;
import com.medkernel.compliance.evidence.dto.EvidenceVerifyResult;
import com.medkernel.shared.evidence.EvidenceSnapshotCreateCommand;
import com.medkernel.shared.evidence.EvidenceSnapshotPort;
import com.medkernel.shared.evidence.EvidenceSnapshotView;
import com.medkernel.shared.evidence.EvidenceVerificationView;

/**
 * 合规可信存证服务的共享端口适配器。
 */
@Component
public class ComplianceEvidenceSnapshotAdapter implements EvidenceSnapshotPort {

    private final EvidenceService evidence;

    public ComplianceEvidenceSnapshotAdapter(EvidenceService evidence) {
        this.evidence = evidence;
    }

    @Override
    public EvidenceSnapshotView createSnapshot(String tenantId, EvidenceSnapshotCreateCommand command) {
        return toView(evidence.createSnapshot(tenantId, new EvidenceCreateDto(
            command.evidenceId(),
            command.traceId(),
            command.evidenceType(),
            command.action(),
            command.subjectType(),
            command.subjectId(),
            command.evidenceSummary(),
            command.payloadSnapshot()
        )));
    }

    @Override
    public EvidenceVerificationView verifyEvidence(String tenantId, String evidenceId) {
        EvidenceVerifyResult result = evidence.verifyEvidence(tenantId, evidenceId);
        return new EvidenceVerificationView(
            result.evidenceId(),
            result.isValid(),
            result.calculatedHash(),
            result.storedHash(),
            result.signatureAlgorithm(),
            result.signatureValid(),
            result.fileUri(),
            result.fileDigest()
        );
    }

    @Override
    public EvidenceSnapshotView getEvidenceById(String tenantId, String evidenceId) {
        return toView(evidence.getEvidenceById(tenantId, evidenceId));
    }

    private static EvidenceSnapshotView toView(EvidenceResponse response) {
        if (response == null) {
            return null;
        }
        return new EvidenceSnapshotView(
            response.evidenceId(),
            response.tenantId(),
            response.traceId(),
            response.evidenceType(),
            response.action(),
            response.subjectType(),
            response.subjectId(),
            response.evidenceSummary(),
            response.payloadSnapshot(),
            response.fileUri(),
            response.fileDigest(),
            response.signatureAlgorithm(),
            response.isValid(),
            response.createdAt(),
            response.createdBy()
        );
    }
}
