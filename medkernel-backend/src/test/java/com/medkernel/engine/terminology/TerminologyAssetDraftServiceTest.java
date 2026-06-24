package com.medkernel.engine.terminology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.PlatformAuthority;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

class TerminologyAssetDraftServiceTest {

    private TermMappingRepository mappingRepository;
    private LocalTermRepository localTermRepository;
    private StandardTermRepository standardTermRepository;
    private TermMappingSnapshotRepository snapshotRepository;
    private TerminologyVersionedAssetAdapter versionedAssets;
    private AuditRecorder auditRecorder;
    private TerminologyAssetDraftService service;

    @BeforeEach
    void setUp() {
        mappingRepository = mock(TermMappingRepository.class);
        localTermRepository = mock(LocalTermRepository.class);
        standardTermRepository = mock(StandardTermRepository.class);
        snapshotRepository = mock(TermMappingSnapshotRepository.class);
        versionedAssets = mock(TerminologyVersionedAssetAdapter.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new TerminologyAssetDraftService(
            mappingRepository,
            localTermRepository,
            standardTermRepository,
            snapshotRepository,
            versionedAssets,
            auditRecorder
        );
        when(snapshotRepository.save(any(TermMappingSnapshotEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(versionedAssets.registerDraft(any(AssetVersionRegisterCommand.class)))
            .thenReturn(version("av-term-v1", "V1"));
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-term-asset",
            new OrgScope("tenant-A", null, "hospital-A", null, null, "CARD", null, null),
            "author-A"
        ));
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void createDraftFreezesConfirmedMappingsIntoAutomaticallyVersionedTerminologyAsset() {
        TermMapping mapping = mapping();
        when(mappingRepository.findConfirmedByTenantIdAndScope(
            "tenant-A", "DEPARTMENT", "CARD")).thenReturn(List.of(mapping));
        when(localTermRepository.findByTenantIdAndId("tenant-A", 11L))
            .thenReturn(Optional.of(localTerm()));
        when(standardTermRepository.findFirstByTenantIdsAndId(
            List.of(PlatformAuthority.PLATFORM_TENANT_ID, "tenant-A"), "tenant-A", 22L))
            .thenReturn(Optional.of(standardTerm()));

        TerminologyAssetDraftResponse response = service.createDraft(
            request("DEPARTMENT", "CARD"));

        assertThat(response.assetIdentity()).isEqualTo("TERM.LAB");
        assertThat(response.versionId()).isEqualTo("av-term-v1");
        assertThat(response.versionNo()).isEqualTo("V1");
        assertThat(response.status()).isEqualTo(AssetVersionStatus.DRAFT);
        assertThat(response.mappingCount()).isEqualTo(1);

        ArgumentCaptor<AssetVersionRegisterCommand> versionCaptor =
            ArgumentCaptor.forClass(AssetVersionRegisterCommand.class);
        verify(versionedAssets).registerDraft(versionCaptor.capture());
        AssetVersionRegisterCommand command = versionCaptor.getValue();
        assertThat(command.assetType()).isEqualTo(VersionedAssetType.TERMINOLOGY);
        assertThat(command.assetIdentity()).isEqualTo("TERM.LAB");
        assertThat(command.organizationScope()).isEqualTo("department:CARD");
        assertThat(command.content()).contains(
            "\"assetIdentity\":\"TERM.LAB\"",
            "\"mappingId\":401"
        );
        assertThat(command.contentHash()).isNull();

        ArgumentCaptor<TermMappingSnapshotEntity> snapshotCaptor =
            ArgumentCaptor.forClass(TermMappingSnapshotEntity.class);
        verify(snapshotRepository).save(snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue().versionId()).isEqualTo("av-term-v1");
        assertThat(snapshotCaptor.getValue().mappingId()).isEqualTo(401L);
        assertThat(snapshotCaptor.getValue().localCode()).isEqualTo("HB");
        assertThat(snapshotCaptor.getValue().standardCode()).isEqualTo("718-7");
        verify(auditRecorder).record(
            AuditAction.CREATE,
            "terminology_asset_version",
            "av-term-v1",
            "生成术语资产草稿: TERM.LAB@V1，映射 1 条"
        );
    }

    @Test
    void createDraftRejectsScopeOutsideCurrentOrganizationContext() {
        assertThatThrownBy(() -> service.createDraft(request("FACILITY", "CURRENT")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ORG_SCOPE_DENIED);

        verify(mappingRepository, never()).findConfirmedByTenantIdAndScope(any(), any(), any());
        verify(versionedAssets, never()).registerDraft(any());
    }

    @Test
    void createDraftRejectsLegacyOrganizationScopeLevel() {
        assertThatThrownBy(() -> service.createDraft(request("HOSPITAL", "hospital-A")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(mappingRepository, never()).findConfirmedByTenantIdAndScope(any(), any(), any());
        verify(versionedAssets, never()).registerDraft(any());
    }

    private TerminologyAssetDraftRequest request(String scopeLevel, String scopeCode) {
        return new TerminologyAssetDraftRequest(
            "TERM.LAB", "检验术语映射", scopeLevel, scopeCode);
    }

    private AssetVersion version(String versionId, String versionNo) {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        return new AssetVersion(
            null,
            versionId,
            "tenant-A",
            VersionedAssetType.TERMINOLOGY,
            "TERM.LAB",
            versionNo,
            "department:CARD",
            "ALL",
            "a".repeat(64),
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            AssetVersionStatus.DRAFT,
            "version:" + versionId,
            "terminology:TERM.LAB",
            null,
            null,
            now,
            "author-A",
            now,
            "author-A",
            "trace-term-asset"
        );
    }

    private TermMapping mapping() {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        return new TermMapping(
            401L, "tenant-A", 11L, 22L, "LIS", TermCategory.LAB, 0.98D,
            TermRiskLevel.HIGH, TermMappingStatus.CONFIRMED, "人工逐条确认",
            "reviewer-A", now, now, "author-A", now, "author-A"
        );
    }

    private LocalTerm localTerm() {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        return new LocalTerm(
            11L, "tenant-A", "LIS", "HB", TermCategory.LAB,
            "血红蛋白", "血红蛋白", "CARD", LocalTermStatus.MAPPED,
            now, now, now, "system", now, "system"
        );
    }

    private StandardTerm standardTerm() {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        return new StandardTerm(
            22L, PlatformAuthority.PLATFORM_TENANT_ID, "LOINC", "718-7",
            TermCategory.LAB, "Hemoglobin", "hemoglobin", "2.78",
            StandardTermStatus.ACTIVE, 100L, "LOINC 2.78", now, "system", now, "system"
        );
    }
}
