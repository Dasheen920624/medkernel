package com.medkernel.compliance.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.compliance.evidence.dto.EvidenceCreateDto;
import com.medkernel.compliance.evidence.dto.EvidenceResponse;
import com.medkernel.compliance.evidence.dto.EvidenceVerifyResult;
import com.medkernel.compliance.evidence.service.ComplianceEvidenceSnapshotAdapter;
import com.medkernel.compliance.evidence.service.EvidenceService;
import com.medkernel.shared.evidence.EvidenceSnapshotCreateCommand;

class ComplianceEvidenceSnapshotAdapterTest {

    private static final String TENANT = "tenant-A";

    private final EvidenceService evidence = mock(EvidenceService.class);
    private final ComplianceEvidenceSnapshotAdapter adapter =
        new ComplianceEvidenceSnapshotAdapter(evidence);

    @Test
    void createSnapshotMapsSharedCommandToComplianceEvidenceService() {
        when(evidence.createSnapshot(any(), any())).thenReturn(response("ev-runtime", "payload-1"));

        var result = adapter.createSnapshot(TENANT, new EvidenceSnapshotCreateCommand(
            "ev-runtime",
            "trace-runtime",
            "RUNTIME_RELEASE_OFFLINE_DELIVERY",
            "EXPORT",
            "clinical_runtime_release",
            "runtime-1",
            "机构生效版本离线交付文件",
            "payload-1"
        ));

        ArgumentCaptor<EvidenceCreateDto> dto = ArgumentCaptor.forClass(EvidenceCreateDto.class);
        verify(evidence).createSnapshot(org.mockito.Mockito.eq(TENANT), dto.capture());
        assertThat(dto.getValue().evidenceId()).isEqualTo("ev-runtime");
        assertThat(dto.getValue().traceId()).isEqualTo("trace-runtime");
        assertThat(dto.getValue().evidenceType()).isEqualTo("RUNTIME_RELEASE_OFFLINE_DELIVERY");
        assertThat(dto.getValue().action()).isEqualTo("EXPORT");
        assertThat(dto.getValue().subjectType()).isEqualTo("clinical_runtime_release");
        assertThat(dto.getValue().subjectId()).isEqualTo("runtime-1");
        assertThat(dto.getValue().payloadSnapshot()).isEqualTo("payload-1");
        assertThat(result.evidenceId()).isEqualTo("ev-runtime");
        assertThat(result.payloadSnapshot()).isEqualTo("payload-1");
        assertThat(result.fileDigest()).isEqualTo("sm3:" + "2".repeat(64));
    }

    @Test
    void verifyEvidenceMapsComplianceVerificationToSharedView() {
        when(evidence.verifyEvidence(TENANT, "ev-runtime")).thenReturn(new EvidenceVerifyResult(
            "ev-runtime",
            true,
            "sm3:" + "1".repeat(64),
            "sm3:" + "1".repeat(64),
            "SM3_WITH_SM2",
            true,
            "/api/v1/compliance/evidence/snapshots/ev-runtime/file",
            "sm3:" + "2".repeat(64)
        ));

        var result = adapter.verifyEvidence(TENANT, "ev-runtime");

        assertThat(result.evidenceId()).isEqualTo("ev-runtime");
        assertThat(result.valid()).isTrue();
        assertThat(result.signatureValid()).isTrue();
        assertThat(result.fileDigest()).isEqualTo("sm3:" + "2".repeat(64));
    }

    @Test
    void getEvidenceByIdMapsStoredPayloadToSharedView() {
        when(evidence.getEvidenceById(TENANT, "ev-runtime"))
            .thenReturn(response("ev-runtime", "{\"deliveryKind\":\"CLINICAL_RUNTIME_RELEASE\"}"));

        var result = adapter.getEvidenceById(TENANT, "ev-runtime");

        assertThat(result.evidenceId()).isEqualTo("ev-runtime");
        assertThat(result.tenantId()).isEqualTo(TENANT);
        assertThat(result.payloadSnapshot()).contains("CLINICAL_RUNTIME_RELEASE");
        assertThat(result.signatureAlgorithm()).isEqualTo("SM3_WITH_SM2");
    }

    private static EvidenceResponse response(String evidenceId, String payloadSnapshot) {
        return new EvidenceResponse(
            1L,
            evidenceId,
            TENANT,
            "trace-runtime",
            "RUNTIME_RELEASE_OFFLINE_DELIVERY",
            "EXPORT",
            "clinical_runtime_release",
            "runtime-1",
            "机构生效版本离线交付文件",
            payloadSnapshot,
            "sm3:" + "1".repeat(64),
            "/api/v1/compliance/evidence/snapshots/" + evidenceId + "/file",
            "sm3:" + "2".repeat(64),
            "SM3_WITH_SM2",
            "signature",
            "public-key",
            true,
            Instant.EPOCH,
            "system"
        );
    }
}
