package com.medkernel.engine.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.compliance.evidence.dto.EvidenceCreateDto;
import com.medkernel.compliance.evidence.dto.EvidenceResponse;
import com.medkernel.compliance.evidence.dto.EvidenceVerifyResult;
import com.medkernel.compliance.evidence.service.EvidenceService;
import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItem;
import com.medkernel.engine.context.ClinicalRuntimeReleaseRepository;
import com.medkernel.engine.context.ClinicalRuntimeReleaseService;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

class RuntimeReleaseOfflineDeliveryServiceTest {

    private static final String TENANT = "tenant-A";
    private static final String HOSPITAL = "hospital-A";
    private static final String RELEASE_ID = "runtime-H9";
    private static final String RESTORE_MANIFEST =
        "4c81f69ad2574d28e5e8a4b12afe565b3448dbf7bbfc496329bc6af87ffe3414";

    private final RuntimeReleaseQueryService queries = mock(RuntimeReleaseQueryService.class);
    private final EvidenceService evidence = mock(EvidenceService.class);
    private final ClinicalRuntimeReleaseRepository releases =
        mock(ClinicalRuntimeReleaseRepository.class);
    private final ClinicalRuntimeReleaseService runtimes =
        mock(ClinicalRuntimeReleaseService.class);
    private final RuntimeReleaseOfflineDeliveryService service =
        new RuntimeReleaseOfflineDeliveryService(queries, evidence, releases, runtimes);

    @Test
    void exportsCurrentRuntimeReleaseAsSignedOfflineDeliverySnapshot() {
        ClinicalRuntimeReleaseDetailResponse detail = detail();
        when(queries.currentHospitalRuntime(TENANT, HOSPITAL)).thenReturn(Optional.of(detail));
        when(evidence.createSnapshot(any(), any())).thenAnswer(invocation -> {
            EvidenceCreateDto dto = invocation.getArgument(1);
            return evidenceResponse(dto.evidenceId(), dto.payloadSnapshot());
        });

        RuntimeReleaseOfflineDeliveryResponse result = service.exportCurrentRuntimeRelease(
            TENANT, HOSPITAL, "operator-a", "trace-offline");

        assertThat(result.deliveryKind()).isEqualTo("CLINICAL_RUNTIME_RELEASE");
        assertThat(result.evidenceId())
            .startsWith("runtime-offline-")
            .hasSizeLessThanOrEqualTo(64)
            .matches("[A-Za-z0-9._-]+");
        assertThat(result.fileUri())
            .startsWith("/api/v1/compliance/evidence/snapshots/" + result.evidenceId())
            .endsWith("/file");
        assertThat(result.fileDigest()).isEqualTo("sm3:" + "2".repeat(64));
        assertThat(result.signatureAlgorithm()).isEqualTo("SM3_WITH_SM2");
        assertThat(result.runtimeMutation()).isFalse();
        assertThat(result.release().releaseId()).isEqualTo(RELEASE_ID);
        assertThat(result.items()).hasSize(2);

        ArgumentCaptor<EvidenceCreateDto> dto = ArgumentCaptor.forClass(EvidenceCreateDto.class);
        org.mockito.Mockito.verify(evidence).createSnapshot(org.mockito.Mockito.eq(TENANT), dto.capture());
        assertThat(dto.getValue().evidenceType()).isEqualTo("RUNTIME_RELEASE_OFFLINE_DELIVERY");
        assertThat(dto.getValue().action()).isEqualTo("EXPORT");
        assertThat(dto.getValue().subjectType()).isEqualTo("clinical_runtime_release");
        assertThat(dto.getValue().subjectId()).isEqualTo(RELEASE_ID);
        assertThat(dto.getValue().evidenceId()).isEqualTo(result.evidenceId());
        assertThat(dto.getValue().evidenceId()).hasSizeLessThanOrEqualTo(64);
        assertThat(dto.getValue().payloadSnapshot())
            .contains("\"deliveryKind\":\"CLINICAL_RUNTIME_RELEASE\"")
            .contains("\"runtimeMutation\":false")
            .contains("\"releaseId\":\"runtime-H9\"")
            .contains("\"manifestSha256\":\"" + "b".repeat(64) + "\"")
            .contains("\"assetIdentity\":\"RULE.CKD\"")
            .contains("离线交付文件仅用于完整性校验和导入预检，不作为临床运行指针");
    }

    @Test
    void exportEvidenceIdFitsEvidenceSnapshotColumnContractForUlidReleaseIds() {
        ClinicalRuntimeReleaseDetailResponse detail = detail(
            "runtime-01KWW56DWV6A1XZDAVJMPZ2TQ9",
            HOSPITAL
        );
        when(queries.currentHospitalRuntime(TENANT, HOSPITAL)).thenReturn(Optional.of(detail));
        when(evidence.createSnapshot(any(), any())).thenAnswer(invocation -> {
            EvidenceCreateDto dto = invocation.getArgument(1);
            return evidenceResponse(dto.evidenceId(), dto.subjectId(), dto.payloadSnapshot());
        });

        RuntimeReleaseOfflineDeliveryResponse result = service.exportCurrentRuntimeRelease(
            TENANT, HOSPITAL, "operator-a", "trace-offline");

        assertThat(result.evidenceId())
            .startsWith("runtime-offline-")
            .hasSizeLessThanOrEqualTo(64)
            .matches("[A-Za-z0-9._-]+");
        ArgumentCaptor<EvidenceCreateDto> dto = ArgumentCaptor.forClass(EvidenceCreateDto.class);
        org.mockito.Mockito.verify(evidence).createSnapshot(org.mockito.Mockito.eq(TENANT), dto.capture());
        assertThat(dto.getValue().evidenceId()).isEqualTo(result.evidenceId());
        assertThat(dto.getValue().evidenceId()).hasSizeLessThanOrEqualTo(64);
        assertThat(dto.getValue().subjectId()).isEqualTo("runtime-01KWW56DWV6A1XZDAVJMPZ2TQ9");
    }

    @Test
    void validateImportPreviewVerifiesSignatureAndKeepsRuntimeImmutable() {
        when(evidence.verifyEvidence(TENANT, "ev-runtime"))
            .thenReturn(new EvidenceVerifyResult(
                "ev-runtime",
                true,
                "sm3:" + "1".repeat(64),
                "sm3:" + "1".repeat(64),
                "SM3_WITH_SM2",
                true,
                "/api/v1/compliance/evidence/snapshots/ev-runtime/file",
                "sm3:" + "2".repeat(64)
            ));
        when(evidence.getEvidenceById(TENANT, "ev-runtime"))
            .thenReturn(evidenceResponse("ev-runtime", runtimePayloadSnapshot()));
        when(releases.findByTenantIdAndReleaseId(TENANT, RELEASE_ID))
            .thenReturn(Optional.of(runtime()));

        RuntimeReleaseOfflineImportPreviewResponse result = service.validateImportPreview(
            TENANT,
            new RuntimeReleaseOfflineImportPreviewRequest("ev-runtime", RELEASE_ID, HOSPITAL)
        );

        assertThat(result.status()).isEqualTo("VALIDATED");
        assertThat(result.runtimeMutation()).isFalse();
        assertThat(result.signatureValid()).isTrue();
        assertThat(result.manifestMatched()).isTrue();
        assertThat(result.releaseId()).isEqualTo(RELEASE_ID);
        assertThat(result.hospitalId()).isEqualTo(HOSPITAL);
        assertThat(result.itemCount()).isEqualTo(2);
        verifyNoInteractions(queries);
    }

    @Test
    void validateImportPreviewRejectsSignedEvidenceWhoseMetadataIsNotOfflineDeliveryExport() {
        when(evidence.verifyEvidence(TENANT, "ev-runtime"))
            .thenReturn(new EvidenceVerifyResult(
                "ev-runtime",
                true,
                "sm3:" + "1".repeat(64),
                "sm3:" + "1".repeat(64),
                "SM3_WITH_SM2",
                true,
                "/api/v1/compliance/evidence/snapshots/ev-runtime/file",
                "sm3:" + "2".repeat(64)
            ));
        when(evidence.getEvidenceById(TENANT, "ev-runtime"))
            .thenReturn(evidenceResponse(
                "ev-runtime",
                "KNOWLEDGE_SOURCE",
                "EXPORT",
                "guideline",
                RELEASE_ID,
                runtimePayloadSnapshot()
            ));

        assertThatThrownBy(() -> service.validateImportPreview(
            TENANT,
            new RuntimeReleaseOfflineImportPreviewRequest("ev-runtime", RELEASE_ID, HOSPITAL)
        )).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
            assertThat(exception).hasMessageContaining("证据元数据不匹配");
        });
    }

    @Test
    void restoreImportRejectsInvalidSignatureBeforeRuntimeMutation() {
        when(evidence.verifyEvidence(TENANT, "ev-runtime"))
            .thenReturn(new EvidenceVerifyResult(
                "ev-runtime",
                true,
                "sm3:" + "1".repeat(64),
                "sm3:" + "1".repeat(64),
                "SM3_WITH_SM2",
                false,
                "/api/v1/compliance/evidence/snapshots/ev-runtime/file",
                "sm3:" + "2".repeat(64)
            ));

        assertThatThrownBy(() -> service.restoreImport(
            TENANT,
            new RuntimeReleaseOfflineRestoreRequest(
                "ev-runtime",
                RELEASE_ID,
                HOSPITAL,
                "runtime-current",
                "sm3:" + "2".repeat(64)
            ),
            "operator-a",
            "trace-restore"
        )).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
            assertThat(exception).hasMessageContaining("验签失败");
        });

        verifyNoInteractions(runtimes);
    }

    @Test
    void restoreImportRejectsConfirmedFileDigestMismatchBeforeRuntimeMutation() {
        stubValidRestoreEvidence(restorePayloadSnapshot());

        assertThatThrownBy(() -> service.restoreImport(
            TENANT,
            new RuntimeReleaseOfflineRestoreRequest(
                "ev-runtime",
                RELEASE_ID,
                HOSPITAL,
                "runtime-current",
                "sm3:" + "9".repeat(64)
            ),
            "operator-a",
            "trace-restore"
        )).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
            assertThat(exception).hasMessageContaining("文件摘要已变化");
        });

        verifyNoInteractions(runtimes);
    }

    @Test
    void restoreImportRejectsSignedEvidenceWhoseMetadataIsNotOfflineDeliveryExport() {
        stubValidRestoreEvidence(evidenceResponse(
            "ev-runtime",
            "KNOWLEDGE_SOURCE",
            "EXPORT",
            "guideline",
            RELEASE_ID,
            restorePayloadSnapshot()
        ));

        assertThatThrownBy(() -> service.restoreImport(
            TENANT,
            new RuntimeReleaseOfflineRestoreRequest(
                "ev-runtime",
                RELEASE_ID,
                HOSPITAL,
                "runtime-current",
                "sm3:" + "2".repeat(64)
            ),
            "operator-a",
            "trace-restore"
        )).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
            assertThat(exception).hasMessageContaining("证据元数据不匹配");
        });

        verifyNoInteractions(runtimes);
    }

    @Test
    void restoreImportRejectsMissingSourceRuntimeLedgerBeforeRuntimeMutation() {
        stubValidRestoreEvidence(restorePayloadSnapshot());
        when(releases.findByTenantIdAndReleaseId(TENANT, RELEASE_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restoreImport(
            TENANT,
            new RuntimeReleaseOfflineRestoreRequest(
                "ev-runtime",
                RELEASE_ID,
                HOSPITAL,
                "runtime-current",
                "sm3:" + "2".repeat(64)
            ),
            "operator-a",
            "trace-restore"
        )).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOT_FOUND);
            assertThat(exception).hasMessageContaining("来源机构生效版本不存在");
        });

        verifyNoInteractions(runtimes);
    }

    @Test
    void restoreImportRejectsSourceRuntimeLedgerMismatchBeforeRuntimeMutation() {
        stubValidRestoreEvidence(restorePayloadSnapshot());
        when(releases.findByTenantIdAndReleaseId(TENANT, RELEASE_ID))
            .thenReturn(Optional.of(runtime(RELEASE_ID, HOSPITAL, "c".repeat(64))));

        assertThatThrownBy(() -> service.restoreImport(
            TENANT,
            new RuntimeReleaseOfflineRestoreRequest(
                "ev-runtime",
                RELEASE_ID,
                HOSPITAL,
                "runtime-current",
                "sm3:" + "2".repeat(64)
            ),
            "operator-a",
            "trace-restore"
        )).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
            assertThat(exception).hasMessageContaining("来源机构生效版本不一致");
        });

        verifyNoInteractions(runtimes);
    }

    @Test
    void restoreImportRejectsTamperedManifestBeforeRuntimeMutation() {
        stubValidRestoreEvidence(restorePayloadSnapshot()
            .replace("\"manifestSha256\":\"" + RESTORE_MANIFEST + "\"",
                "\"manifestSha256\":\"" + "c".repeat(64) + "\""));

        assertThatThrownBy(() -> service.restoreImport(
            TENANT,
            new RuntimeReleaseOfflineRestoreRequest(
                "ev-runtime",
                RELEASE_ID,
                HOSPITAL,
                "runtime-current",
                "sm3:" + "2".repeat(64)
            ),
            "operator-a",
            "trace-restore"
        )).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
            assertThat(exception).hasMessageContaining("清单摘要与文件内容不一致");
        });

        verifyNoInteractions(runtimes);
    }

    @Test
    void restoreImportCreatesNewRuntimeRevisionFromValidatedOfflineSnapshot() {
        stubValidRestoreEvidence(restorePayloadSnapshot());
        when(releases.findByTenantIdAndReleaseId(TENANT, RELEASE_ID))
            .thenReturn(Optional.of(runtime(RELEASE_ID, HOSPITAL, RESTORE_MANIFEST)));
        ClinicalRuntimeRelease restored = new ClinicalRuntimeRelease(
            10L,
            "runtime-restored",
            TENANT,
            HOSPITAL,
            10L,
            "baseline-A8",
            RESTORE_MANIFEST,
            RELEASE_ID,
            Instant.EPOCH,
            "operator-a",
            Instant.EPOCH,
            "operator-a",
            "trace-restore"
        );
        when(runtimes.restoreOfflineSnapshot(any()))
            .thenReturn(restored);

        RuntimeReleaseOfflineRestoreResponse result = service.restoreImport(
            TENANT,
            new RuntimeReleaseOfflineRestoreRequest(
                "ev-runtime",
                RELEASE_ID,
                HOSPITAL,
                "runtime-current",
                "sm3:" + "2".repeat(64)
            ),
            "operator-a",
            "trace-restore"
        );

        assertThat(result.status()).isEqualTo("RESTORED");
        assertThat(result.runtimeMutation()).isTrue();
        assertThat(result.sourceReleaseId()).isEqualTo(RELEASE_ID);
        assertThat(result.targetHospitalId()).isEqualTo(HOSPITAL);
        assertThat(result.fileDigest()).isEqualTo("sm3:" + "2".repeat(64));
        assertThat(result.manifestSha256()).isEqualTo(RESTORE_MANIFEST);
        assertThat(result.itemCount()).isEqualTo(2);
        assertThat(result.restoredRelease()).isEqualTo(restored);
        ArgumentCaptor<com.medkernel.engine.context.ClinicalRuntimeReleaseOfflineRestoreCommand> command =
            ArgumentCaptor.forClass(com.medkernel.engine.context.ClinicalRuntimeReleaseOfflineRestoreCommand.class);
        verify(runtimes).restoreOfflineSnapshot(command.capture());
        assertThat(command.getValue().expectedCurrentReleaseId()).isEqualTo("runtime-current");
        assertThat(command.getValue().sourceReleaseId()).isEqualTo(RELEASE_ID);
        assertThat(command.getValue().items()).hasSize(2);
    }

    private ClinicalRuntimeReleaseDetailResponse detail() {
        return detail(RELEASE_ID, HOSPITAL);
    }

    private ClinicalRuntimeReleaseDetailResponse detail(String releaseId, String hospitalId) {
        return new ClinicalRuntimeReleaseDetailResponse(runtime(releaseId, hospitalId), List.of(
            new ClinicalRuntimeReleaseItem(
                1L,
                releaseId,
                "platform",
                ReleaseSourceLayer.PLATFORM,
                VersionedAssetType.RULE,
                "RULE.CKD",
                ReleaseEntryState.ACTIVE,
                "rule-v1",
                "V1",
                "1".repeat(64),
                Instant.EPOCH,
                "operator-a",
                "trace-offline"
            ),
            new ClinicalRuntimeReleaseItem(
                2L,
                releaseId,
                TENANT,
                ReleaseSourceLayer.HOSPITAL,
                VersionedAssetType.ACTION_CARD,
                "ACTION_CARD.RUNTIME.RELEASE.TEST",
                ReleaseEntryState.ACTIVE,
                "card-v1",
                "V1",
                "2".repeat(64),
                Instant.EPOCH,
                "operator-a",
                "trace-offline"
            )
        ));
    }

    private ClinicalRuntimeRelease runtime() {
        return runtime(RELEASE_ID, HOSPITAL);
    }

    private ClinicalRuntimeRelease runtime(String releaseId, String hospitalId) {
        return runtime(releaseId, hospitalId, "b".repeat(64));
    }

    private ClinicalRuntimeRelease runtime(String releaseId, String hospitalId, String manifestSha256) {
        return new ClinicalRuntimeRelease(
            9L,
            releaseId,
            TENANT,
            hospitalId,
            9L,
            "baseline-A8",
            manifestSha256,
            null,
            Instant.EPOCH,
            "operator-a",
            Instant.EPOCH,
            "operator-a",
            "trace-offline"
        );
    }

    private EvidenceResponse evidenceResponse(String evidenceId, String payloadSnapshot) {
        return evidenceResponse(evidenceId, RELEASE_ID, payloadSnapshot);
    }

    private EvidenceResponse evidenceResponse(String evidenceId, String subjectId, String payloadSnapshot) {
        return evidenceResponse(
            evidenceId,
            "RUNTIME_RELEASE_OFFLINE_DELIVERY",
            "EXPORT",
            "clinical_runtime_release",
            subjectId,
            payloadSnapshot
        );
    }

    private EvidenceResponse evidenceResponse(
            String evidenceId,
            String evidenceType,
            String action,
            String subjectType,
            String subjectId,
            String payloadSnapshot) {
        return new EvidenceResponse(
            1L,
            evidenceId,
            TENANT,
            "trace-offline",
            evidenceType,
            action,
            subjectType,
            subjectId,
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

    private void stubValidRestoreEvidence(String payloadSnapshot) {
        stubValidRestoreEvidence(evidenceResponse("ev-runtime", payloadSnapshot));
    }

    private void stubValidRestoreEvidence(EvidenceResponse storedEvidence) {
        when(evidence.verifyEvidence(TENANT, "ev-runtime"))
            .thenReturn(new EvidenceVerifyResult(
                "ev-runtime",
                true,
                "sm3:" + "1".repeat(64),
                "sm3:" + "1".repeat(64),
                "SM3_WITH_SM2",
                true,
                "/api/v1/compliance/evidence/snapshots/ev-runtime/file",
                "sm3:" + "2".repeat(64)
            ));
        when(evidence.getEvidenceById(TENANT, "ev-runtime"))
            .thenReturn(storedEvidence);
    }

    private String runtimePayloadSnapshot() {
        return """
            {"schemaVersion":"1.0.0","deliveryKind":"CLINICAL_RUNTIME_RELEASE","runtimeMutation":false,"release":{"releaseId":"runtime-H9","tenantId":"tenant-A","hospitalId":"hospital-A","revisionNo":9,"platformBaselineReleaseId":"baseline-A8","manifestSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","activatedAt":"1970-01-01T00:00:00Z","activatedBy":"operator-a"},"items":[{"assetType":"RULE","assetIdentity":"RULE.CKD","entryState":"ACTIVE","versionId":"rule-v1","versionNo":"V1","contentHash":"1111111111111111111111111111111111111111111111111111111111111111"},{"assetType":"ACTION_CARD","assetIdentity":"ACTION_CARD.RUNTIME.RELEASE.TEST","entryState":"ACTIVE","versionId":"card-v1","versionNo":"V1","contentHash":"2222222222222222222222222222222222222222222222222222222222222222"}],"warning":"离线交付文件仅用于完整性校验和导入预检，不作为临床运行指针"}
            """;
    }

    private String restorePayloadSnapshot() {
        return """
            {"schemaVersion":"1.0.0","deliveryKind":"CLINICAL_RUNTIME_RELEASE","runtimeMutation":false,"release":{"releaseId":"runtime-H9","tenantId":"tenant-A","hospitalId":"hospital-A","revisionNo":9,"platformBaselineReleaseId":"baseline-A8","manifestSha256":"4c81f69ad2574d28e5e8a4b12afe565b3448dbf7bbfc496329bc6af87ffe3414","activatedAt":"1970-01-01T00:00:00Z","activatedBy":"operator-a"},"items":[{"sourceTenantId":"platform","sourceLayer":"PLATFORM","assetType":"RULE","assetIdentity":"RULE.CKD","entryState":"ACTIVE","versionId":"rule-v1","versionNo":"V1","contentHash":"1111111111111111111111111111111111111111111111111111111111111111"},{"sourceTenantId":"tenant-A","sourceLayer":"HOSPITAL","assetType":"ACTION_CARD","assetIdentity":"ACTION_CARD.RUNTIME.RELEASE.TEST","entryState":"ACTIVE","versionId":"card-v1","versionNo":"V1","contentHash":"2222222222222222222222222222222222222222222222222222222222222222"}],"warning":"离线交付文件仅用于完整性校验和导入预检，不作为临床运行指针"}
            """;
    }
}
