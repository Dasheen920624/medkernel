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

import com.medkernel.engine.pkg.KnowledgePackage;
import com.medkernel.engine.pkg.KnowledgePackageRepository;
import com.medkernel.engine.pkg.KnowledgePackageStatus;
import com.medkernel.engine.pkg.PackageItem;
import com.medkernel.engine.pkg.PackageItemRepository;
import com.medkernel.engine.pkg.PackageResponse;
import com.medkernel.engine.pkg.PackageVersionedAssetAdapter;
import com.medkernel.engine.pkg.TerminologyPackageBuildRequest;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.PlatformAuthority;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

class TerminologyKnowledgePackageServiceTest {

    private KnowledgePackageRepository packageRepository;
    private PackageItemRepository packageItemRepository;
    private TermMappingRepository mappingRepository;
    private LocalTermRepository localTermRepository;
    private StandardTermRepository standardTermRepository;
    private TermMappingSnapshotRepository snapshotRepository;
    private PackageVersionedAssetAdapter versionedAssets;
    private AuditRecorder auditRecorder;
    private TerminologyKnowledgePackageService service;

    @BeforeEach
    void setUp() {
        packageRepository = mock(KnowledgePackageRepository.class);
        packageItemRepository = mock(PackageItemRepository.class);
        mappingRepository = mock(TermMappingRepository.class);
        localTermRepository = mock(LocalTermRepository.class);
        standardTermRepository = mock(StandardTermRepository.class);
        snapshotRepository = mock(TermMappingSnapshotRepository.class);
        versionedAssets = mock(PackageVersionedAssetAdapter.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new TerminologyKnowledgePackageService(
            packageRepository,
            packageItemRepository,
            mappingRepository,
            localTermRepository,
            standardTermRepository,
            snapshotRepository,
            versionedAssets,
            auditRecorder
        );
        when(packageRepository.save(any(KnowledgePackage.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(packageItemRepository.save(any(PackageItem.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(snapshotRepository.save(any(TermMappingSnapshotEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-term-package",
            new OrgScope("tenant-A", null, "hospital-A", null, null, "CARD", null),
            "author-A"
        ));
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void buildFreezesConfirmedMappingsIntoUnifiedKnowledgePackage() {
        TermMapping mapping = mapping();
        when(packageRepository.findByTenantIdAndPackageCodeAndPackageVersion(
            "tenant-A", "TERM.LAB", "2026.06")).thenReturn(Optional.empty());
        when(mappingRepository.findConfirmedByTenantIdAndScope(
            "tenant-A", "DEPARTMENT", "CARD")).thenReturn(List.of(mapping));
        when(localTermRepository.findByTenantIdAndId("tenant-A", 11L))
            .thenReturn(Optional.of(localTerm()));
        when(standardTermRepository.findFirstByTenantIdsAndId(
            List.of(PlatformAuthority.PLATFORM_TENANT_ID, "tenant-A"), "tenant-A", 22L))
            .thenReturn(Optional.of(standardTerm()));

        PackageResponse response = service.build(request("DEPARTMENT", "CARD"));

        assertThat(response.packageCode()).isEqualTo("TERM.LAB");
        assertThat(response.packageVersion()).isEqualTo("2026.06");
        assertThat(response.status()).isEqualTo(KnowledgePackageStatus.DRAFT);

        ArgumentCaptor<PackageItem> itemCaptor = ArgumentCaptor.forClass(PackageItem.class);
        verify(packageItemRepository).save(itemCaptor.capture());
        PackageItem marker = itemCaptor.getValue();
        assertThat(marker.assetType()).isEqualTo(VersionedAssetType.TERMINOLOGY);
        assertThat(marker.assetId()).isEqualTo("TERM.LAB|DEPARTMENT|CARD");
        assertThat(marker.assetVersion()).isEqualTo("2026.06");

        ArgumentCaptor<TermMappingSnapshotEntity> snapshotCaptor =
            ArgumentCaptor.forClass(TermMappingSnapshotEntity.class);
        verify(snapshotRepository).save(snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue().packageItemId()).isEqualTo(marker.itemId());
        assertThat(snapshotCaptor.getValue().mappingId()).isEqualTo(401L);
        assertThat(snapshotCaptor.getValue().localCode()).isEqualTo("HB");
        assertThat(snapshotCaptor.getValue().standardCode()).isEqualTo("718-7");
        assertThat(snapshotCaptor.getValue().mappingSnapshot()).contains("\"mappingId\":401");

        ArgumentCaptor<AssetVersionRegisterCommand> versionCaptor =
            ArgumentCaptor.forClass(AssetVersionRegisterCommand.class);
        verify(versionedAssets).registerDraft(versionCaptor.capture());
        AssetVersionRegisterCommand version = versionCaptor.getValue();
        assertThat(version.assetType()).isEqualTo(VersionedAssetType.PACKAGE);
        assertThat(version.assetIdentity()).isEqualTo("TERM.LAB");
        assertThat(version.versionNo()).isEqualTo("2026.06");
        assertThat(version.organizationScope()).isEqualTo("department:CARD");
        assertThat(version.contentHash()).matches("[a-f0-9]{64}");
        verify(auditRecorder).record(
            AuditAction.CREATE,
            "knowledge_package",
            response.packageId(),
            "构建术语知识包草稿: 检验术语映射包 (2026.06)"
        );
    }

    @Test
    void buildRejectsScopeOutsideCurrentOrganizationContext() {
        assertThatThrownBy(() -> service.build(request("FACILITY", "CURRENT")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ORG_SCOPE_DENIED);

        verify(mappingRepository, never()).findConfirmedByTenantIdAndScope(any(), any(), any());
        verify(packageRepository, never()).save(any(KnowledgePackage.class));
        verify(versionedAssets, never()).registerDraft(any());
    }

    @Test
    void buildRejectsLegacyOrganizationScopeLevel() {
        assertThatThrownBy(() -> service.build(request("HOSPITAL", "hospital-A")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(mappingRepository, never()).findConfirmedByTenantIdAndScope(any(), any(), any());
        verify(packageRepository, never()).save(any(KnowledgePackage.class));
    }

    private TerminologyPackageBuildRequest request(String scopeLevel, String scopeCode) {
        return new TerminologyPackageBuildRequest(
            "req-term-package", "trace-term-package", "tenant-A",
            null, "hospital-A", null, null, "CARD", null, "author-A",
            List.of("organization-admin"), "2026.06",
            "TERM.LAB", "2026.06", "检验术语映射包", scopeLevel, scopeCode
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
