package com.medkernel.engine.pkg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.knowledge.GradeEvidenceQuality;
import com.medkernel.engine.knowledge.GradeRecommendationStrength;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.engine.knowledge.KnowledgeDomain;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityRepository;
import com.medkernel.engine.knowledge.KnowledgeIdentityStatus;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.KnowledgeVersionStatus;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

class AikKnowledgePackageServiceTest {

    private KnowledgePackageRepository packageRepository;
    private PackageItemRepository packageItemRepository;
    private AikPackJobRepository packJobRepository;
    private KnowledgeIdentityRepository identityRepository;
    private KnowledgeAssetVersionRepository versionRepository;
    private PackageVersionedAssetAdapter versionedAssets;
    private AuditRecorder auditRecorder;
    private AikKnowledgePackageService service;

    @BeforeEach
    void setUp() {
        packageRepository = mock(KnowledgePackageRepository.class);
        packageItemRepository = mock(PackageItemRepository.class);
        packJobRepository = mock(AikPackJobRepository.class);
        identityRepository = mock(KnowledgeIdentityRepository.class);
        versionRepository = mock(KnowledgeAssetVersionRepository.class);
        versionedAssets = mock(PackageVersionedAssetAdapter.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new AikKnowledgePackageService(
            packageRepository,
            packageItemRepository,
            packJobRepository,
            identityRepository,
            versionRepository,
            versionedAssets,
            auditRecorder,
            new ObjectMapper()
        );
        when(packageRepository.save(any(KnowledgePackage.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(packageItemRepository.save(any(PackageItem.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(packJobRepository.save(any(AikPackJob.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-aik-package",
            OrgScope.tenant("tenant-A"),
            "author-A"
        ));
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void buildPackagesOnlyActiveKnowledgeVersionsAndRecordsManifest() {
        KnowledgeIdentity guideline = identity(101L, "KNOW.COPD.GUIDE", "慢阻肺指南");
        KnowledgeIdentity drug = identity(102L, "KNOW.DRUG.SAFE", "安全用药知识");
        KnowledgeAssetVersion guidelineVersion = version(
            12L, guideline.id(), "2026.06", "a".repeat(64), KnowledgeVersionStatus.ACTIVE);
        KnowledgeAssetVersion drugVersion = version(
            11L, drug.id(), "2026.05", "b".repeat(64), KnowledgeVersionStatus.ACTIVE);
        when(packageRepository.findByTenantIdAndPackageCodeAndPackageVersion(
            "tenant-A", "AIK.KNOWGEN", "2026.06.1")).thenReturn(Optional.empty());
        when(versionRepository.findByTenantIdAndId("tenant-A", 12L))
            .thenReturn(Optional.of(guidelineVersion));
        when(versionRepository.findByTenantIdAndId("tenant-A", 11L))
            .thenReturn(Optional.of(drugVersion));
        when(identityRepository.findByTenantIdAndId("tenant-A", 101L))
            .thenReturn(Optional.of(guideline));
        when(identityRepository.findByTenantIdAndId("tenant-A", 102L))
            .thenReturn(Optional.of(drug));

        AikPackageBuildResponse response = service.build(request(List.of(12L, 11L)));

        assertThat(response.packageResponse().packageCode()).isEqualTo("AIK.KNOWGEN");
        assertThat(response.packageResponse().status()).isEqualTo(KnowledgePackageStatus.DRAFT);
        assertThat(response.itemCount()).isEqualTo(2);
        assertThat(response.manifestSha256()).matches("[a-f0-9]{64}");

        ArgumentCaptor<PackageItem> itemCaptor = ArgumentCaptor.forClass(PackageItem.class);
        verify(packageItemRepository, org.mockito.Mockito.times(2)).save(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues())
            .extracting(PackageItem::assetId)
            .containsExactly("KNOW.DRUG.SAFE", "KNOW.COPD.GUIDE");
        assertThat(itemCaptor.getAllValues()).allSatisfy(item -> {
            assertThat(item.assetType()).isEqualTo(VersionedAssetType.KNOWLEDGE);
            assertThat(item.packageId()).isEqualTo(response.packageResponse().packageId());
        });

        ArgumentCaptor<AikPackJob> jobCaptor = ArgumentCaptor.forClass(AikPackJob.class);
        verify(packJobRepository).save(jobCaptor.capture());
        AikPackJob job = jobCaptor.getValue();
        assertThat(job.status()).isEqualTo(AikPackJobStatus.PACKAGED);
        assertThat(job.packageId()).isEqualTo(response.packageResponse().packageId());
        assertThat(job.manifestSha256()).isEqualTo(response.manifestSha256());
        assertThat(job.assetManifest())
            .contains("\"identityCode\":\"KNOW.DRUG.SAFE\"")
            .contains("\"identityCode\":\"KNOW.COPD.GUIDE\"");

        verify(versionedAssets).registerDraft(argThat((AssetVersionRegisterCommand command) ->
            command.assetType() == VersionedAssetType.PACKAGE
                && command.assetIdentity().equals("AIK.KNOWGEN")
                && command.versionNo().equals("2026.06.1")
                && command.organizationScope().equals("tenant:tenant-A")
                && command.contentHash().equals(response.manifestSha256())
                && command.sourceRef().equals("aik-pack-job:" + response.jobId())
        ));
        verify(auditRecorder).record(
            AuditAction.CREATE,
            "mk_aik_pack_job",
            response.jobId(),
            "装配 AIK 知识包草稿: AI 工厂首发知识包 (2026.06.1)，资产数 2"
        );
    }

    @Test
    void buildRejectsKnowledgeVersionThatIsNotActive() {
        KnowledgeAssetVersion draft = version(
            21L, 101L, "draft-1", "c".repeat(64), KnowledgeVersionStatus.UNDER_REVIEW);
        when(packageRepository.findByTenantIdAndPackageCodeAndPackageVersion(
            "tenant-A", "AIK.KNOWGEN", "2026.06.1")).thenReturn(Optional.empty());
        when(versionRepository.findByTenantIdAndId("tenant-A", 21L))
            .thenReturn(Optional.of(draft));
        when(identityRepository.findByTenantIdAndId("tenant-A", 101L))
            .thenReturn(Optional.of(identity(101L, "KNOW.COPD.GUIDE", "慢阻肺指南")));

        assertThatThrownBy(() -> service.build(request(List.of(21L))))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PACKAGE_002);

        verify(packageRepository, never()).save(any(KnowledgePackage.class));
        verify(packageItemRepository, never()).save(any(PackageItem.class));
        verify(packJobRepository, never()).save(any(AikPackJob.class));
        verify(versionedAssets, never()).registerDraft(any());
    }

    private AikPackageBuildRequest request(List<Long> assetVersionIds) {
        return new AikPackageBuildRequest(
            "req-aik-package",
            "trace-aik-package",
            "tenant-A",
            null,
            null,
            null,
            null,
            null,
            null,
            "author-A",
            List.of("knowledge-governor"),
            "2026.06.1",
            "AIK.KNOWGEN",
            "2026.06.1",
            "AI 工厂首发知识包",
            "已审知识资产首发包",
            assetVersionIds
        );
    }

    private KnowledgeIdentity identity(Long id, String identityCode, String subject) {
        return new KnowledgeIdentity(
            id,
            "tenant-A",
            identityCode,
            KnowledgeDomain.GUIDELINE,
            subject,
            "RESP",
            "已审知识身份",
            KnowledgeIdentityStatus.ACTIVE,
            null,
            Instant.now(),
            "tester",
            Instant.now(),
            "tester"
        );
    }

    private KnowledgeAssetVersion version(
            Long id,
            Long identityId,
            String versionNo,
            String contentHash,
            KnowledgeVersionStatus status) {
        return new KnowledgeAssetVersion(
            id,
            "tenant-A",
            identityId,
            versionNo,
            "知识版本 " + versionNo,
            10L,
            20L,
            contentHash,
            "[]",
            status,
            KnowledgeRiskLevel.MEDIUM,
            SourceAuthorityLevel.B_GUIDELINE,
            GradeEvidenceQuality.MODERATE,
            GradeRecommendationStrength.WEAK,
            "无冲突",
            "tenant:tenant-A",
            KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE,
            status == KnowledgeVersionStatus.ACTIVE
                ? KnowledgeAssetVersion.activeScopeKey(
                    identityId, "tenant:tenant-A", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE)
                : "version:" + id,
            Instant.parse("2026-01-01T00:00:00Z"),
            null,
            "reviewer",
            Instant.parse("2026-05-01T00:00:00Z"),
            status == KnowledgeVersionStatus.ACTIVE ? Instant.parse("2026-06-01T00:00:00Z") : null,
            null,
            null,
            null,
            Instant.now(),
            "tester",
            Instant.now(),
            "tester",
            12,
            Instant.parse("2027-06-01T00:00:00Z")
        );
    }
}
