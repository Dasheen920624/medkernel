package com.medkernel.engine.pathway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.pkg.KnowledgePackage;
import com.medkernel.engine.pkg.KnowledgePackageRepository;
import com.medkernel.engine.pkg.KnowledgePackageStatus;
import com.medkernel.engine.pkg.PackageItem;
import com.medkernel.engine.pkg.PackageItemRepository;
import com.medkernel.engine.pkg.PackageResponse;
import com.medkernel.engine.pkg.PackageVersionedAssetAdapter;
import com.medkernel.engine.pkg.PathwayPackageBuildRequest;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

class PathwayKnowledgePackageServiceTest {

    private KnowledgePackageRepository packageRepository;
    private PackageItemRepository packageItemRepository;
    private SpecialtyProfileRepository profileRepository;
    private PackageVersionedAssetAdapter versionedAssets;
    private AuditRecorder auditRecorder;
    private PathwayKnowledgePackageService service;

    @BeforeEach
    void setUp() {
        packageRepository = mock(KnowledgePackageRepository.class);
        packageItemRepository = mock(PackageItemRepository.class);
        profileRepository = mock(SpecialtyProfileRepository.class);
        versionedAssets = mock(PackageVersionedAssetAdapter.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new PathwayKnowledgePackageService(
            packageRepository,
            packageItemRepository,
            profileRepository,
            versionedAssets,
            auditRecorder,
            new ObjectMapper()
        );
        when(packageRepository.save(any(KnowledgePackage.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(packageItemRepository.save(any(PackageItem.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(profileRepository.save(any(SpecialtyProfile.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-pathway-package",
            OrgScope.tenant("tenant-A"),
            "author-A"
        ));
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void buildPersistsPathwayContentAsUnifiedKnowledgePackage() {
        when(packageRepository.findByTenantIdAndPackageCodeAndPackageVersion(
            "tenant-A", "PKG.COPD", "1.0.0")).thenReturn(Optional.empty());

        PackageResponse response = service.build(request());

        assertThat(response.packageCode()).isEqualTo("PKG.COPD");
        assertThat(response.packageVersion()).isEqualTo("1.0.0");
        assertThat(response.status()).isEqualTo(KnowledgePackageStatus.DRAFT);

        ArgumentCaptor<PackageItem> itemCaptor = ArgumentCaptor.forClass(PackageItem.class);
        verify(packageItemRepository).save(itemCaptor.capture());
        PackageItem marker = itemCaptor.getValue();
        assertThat(marker.packageId()).isEqualTo(response.packageId());
        assertThat(marker.assetType()).isEqualTo(VersionedAssetType.PATHWAY);
        assertThat(marker.assetId()).isEqualTo("PKG.COPD");
        assertThat(marker.assetVersion()).isEqualTo("1.0.0");

        ArgumentCaptor<SpecialtyProfile> profileCaptor = ArgumentCaptor.forClass(SpecialtyProfile.class);
        verify(profileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().packageId()).isEqualTo(response.packageId());
        assertThat(profileCaptor.getValue().profileCode()).isEqualTo("DEFAULT");

        ArgumentCaptor<AssetVersionRegisterCommand> versionCaptor =
            ArgumentCaptor.forClass(AssetVersionRegisterCommand.class);
        verify(versionedAssets).registerDraft(versionCaptor.capture());
        AssetVersionRegisterCommand version = versionCaptor.getValue();
        assertThat(version.assetType()).isEqualTo(VersionedAssetType.PACKAGE);
        assertThat(version.assetIdentity()).isEqualTo("PKG.COPD");
        assertThat(version.versionNo()).isEqualTo("1.0.0");
        assertThat(version.organizationScope()).isEqualTo("tenant:tenant-A");
        assertThat(version.applicableScope()).isEqualTo("disease:COPD");
        assertThat(version.sourceRef()).isEqualTo("专病路径专家共识 2026");
        assertThat(version.content()).contains("\"diseaseCode\":\"COPD\"");

        verify(auditRecorder).record(
            AuditAction.CREATE,
            "knowledge_package",
            response.packageId(),
            "构建路径知识包草稿: 慢阻肺路径知识包 (1.0.0)"
        );
    }

    private PathwayPackageBuildRequest request() {
        return new PathwayPackageBuildRequest(
            "req-pathway-package",
            "trace-pathway-package",
            "tenant-A",
            null,
            null,
            null,
            null,
            null,
            null,
            "author-A",
            List.of("organization-admin"),
            "1.0.0",
            "PKG.COPD",
            "COPD",
            "慢阻肺路径知识包",
            "1.0.0",
            "专病路径专家共识 2026",
            "稳定期路径",
            List.of(new SpecialtyProfileRequest(
                "DEFAULT",
                "默认画像",
                new ObjectMapper().createObjectNode().put("risk", "medium"),
                null,
                null,
                null
            ))
        );
    }
}
