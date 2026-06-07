package com.medkernel.engine.knowledge;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class KnowledgeIdentityServiceTest {

    private KnowledgeIdentityRepository identityRepo;
    private KnowledgeAssetVersionRepository versionRepo;
    private KnowledgeSupersessionRepository supersessionRepo;
    private SourceDocumentRepository sourceDocRepo;
    private SourceVersionRepository sourceVerRepo;
    private SourceFragmentRepository sourceFragRepo;
    private CitationRepository citationRepo;
    private KnowledgeIdentityService service;

    @BeforeEach
    void setUp() {
        identityRepo = Mockito.mock(KnowledgeIdentityRepository.class);
        versionRepo = Mockito.mock(KnowledgeAssetVersionRepository.class);
        supersessionRepo = Mockito.mock(KnowledgeSupersessionRepository.class);
        sourceDocRepo = Mockito.mock(SourceDocumentRepository.class);
        sourceVerRepo = Mockito.mock(SourceVersionRepository.class);
        sourceFragRepo = Mockito.mock(SourceFragmentRepository.class);
        citationRepo = Mockito.mock(CitationRepository.class);
        service = new KnowledgeIdentityService(
            identityRepo, versionRepo, supersessionRepo, sourceDocRepo, sourceVerRepo, sourceFragRepo, citationRepo
        );
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-1"), "u-99"));
        when(identityRepo.save(any(KnowledgeIdentity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sourceDocRepo.save(any(SourceDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sourceVerRepo.save(any(SourceVersion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(citationRepo.save(any(Citation.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void pageNormalizesKeywordToLowercaseAndWrapsPercent() {
        when(identityRepo.listByFilter(eq("t-1"), any(), any(), any(), eq("%他汀%")))
            .thenReturn(List.of(identityRow(1L), identityRow(2L, "t-1", "DRUG.Y")));

        PageResponse<KnowledgeIdentity> page = service.page(
            new PageRequest(1, 20, null),
            new KnowledgeIdentityFilter(null, null, null, "  他汀  ")
        );
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).hasSize(2);
    }

    @Test
    void pageFilterEmptyKeywordBecomesNull() {
        when(identityRepo.listByFilter(eq("t-1"), any(), any(), any(), eq(null))).thenReturn(List.of());
        PageResponse<KnowledgeIdentity> page = service.page(
            PageRequest.defaults(),
            new KnowledgeIdentityFilter(null, null, null, "   ")
        );
        assertThat(page.items()).isEmpty();
    }

    @Test
    void pageEnumFiltersAreMappedToStringName() {
        when(identityRepo.listByFilter(eq("t-1"), eq("DRUG"), any(), eq("ACTIVE"), any()))
            .thenReturn(List.of(identityRow(1L)));

        service.page(
            PageRequest.defaults(),
            new KnowledgeIdentityFilter(KnowledgeDomain.DRUG, null, KnowledgeIdentityStatus.ACTIVE, null)
        );
        // 校验 enum→String 转换确实发生：count 被调到，且 specialty 为 null
        ArgumentCaptor<String> domainCap = ArgumentCaptor.forClass(String.class);
        Mockito.verify(identityRepo).listByFilter(eq("t-1"), domainCap.capture(), any(), any(), any());
        assertThat(domainCap.getValue()).isEqualTo("DRUG");
    }

    @Test
    void pageMergesCustomerLocalOverridesWithPlatformActiveIdentities() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-hospital"), "doctor"));
        KnowledgeIdentity localOverride = identityRow(200L, "t-hospital", "DRUG.X");
        KnowledgeIdentity platformShadowed = identityRow(100L, "t-1", "DRUG.X");
        KnowledgeIdentity platformOnly = identityRow(101L, "t-1", "DRUG.Y");
        when(identityRepo.listByFilter("t-hospital", null, null, null, null)).thenReturn(List.of(localOverride));
        when(identityRepo.listByFilter("t-1", null, null, "ACTIVE", null))
            .thenReturn(List.of(platformShadowed, platformOnly));

        PageResponse<KnowledgeIdentity> page = service.page(
            PageRequest.defaults(), new KnowledgeIdentityFilter(null, null, null, null));

        assertThat(page.items()).extracting(KnowledgeIdentity::id)
            .containsExactly(200L, 101L);
        assertThat(page.total()).isEqualTo(2);
    }

    @Test
    void getReturnsIdentityWhenExists() {
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identityRow(1L)));
        KnowledgeIdentity result = service.get(1L);
        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void getFallsBackToPlatformIdentityWhenCustomerHasNoLocalOverride() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-hospital"), "doctor"));
        KnowledgeIdentity platform = identityRow(100L, "t-1", "DRUG.X");
        when(identityRepo.findByTenantIdAndId("t-hospital", 100L)).thenReturn(Optional.empty());
        when(identityRepo.findByTenantIdAndId("t-1", 100L)).thenReturn(Optional.of(platform));
        when(identityRepo.findByTenantIdAndIdentityCode("t-hospital", "DRUG.X")).thenReturn(Optional.empty());

        KnowledgeIdentity result = service.get(100L);

        assertThat(result.tenantId()).isEqualTo("t-1");
        assertThat(result.identityCode()).isEqualTo("DRUG.X");
    }

    @Test
    void getPrefersLocalIdentityWithSameCodeOverPlatformIdentity() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-hospital"), "doctor"));
        KnowledgeIdentity platform = identityRow(100L, "t-1", "DRUG.X");
        KnowledgeIdentity local = identityRow(200L, "t-hospital", "DRUG.X");
        when(identityRepo.findByTenantIdAndId("t-hospital", 100L)).thenReturn(Optional.empty());
        when(identityRepo.findByTenantIdAndId("t-1", 100L)).thenReturn(Optional.of(platform));
        when(identityRepo.findByTenantIdAndIdentityCode("t-hospital", "DRUG.X")).thenReturn(Optional.of(local));

        KnowledgeIdentity result = service.get(100L);

        assertThat(result.id()).isEqualTo(200L);
        assertThat(result.tenantId()).isEqualTo("t-hospital");
    }

    @Test
    void getMissingThrowsNotFound() {
        when(identityRepo.findByTenantIdAndId("t-1", 99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(99L))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void getActiveVersionThrowsWhenIdentityExistsButNoActive() {
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identityRow(1L)));

        assertThatThrownBy(() -> service.getActiveVersion(1L))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void getActiveVersionPrefersCurrentVersionPointerForMultiScopeActiveIdentities() {
        when(identityRepo.findByTenantIdAndId("t-1", 1L))
            .thenReturn(Optional.of(identityRow(1L, "t-1", "DRUG.X", 22L)));
        when(versionRepo.findByTenantIdAndId("t-1", 22L))
            .thenReturn(Optional.of(versionRow(22L, 1L, KnowledgeVersionStatus.ACTIVE)));

        KnowledgeAssetVersion active = service.getActiveVersion(1L);

        assertThat(active.id()).isEqualTo(22L);
    }

    @Test
    void lineageBundlesIdentityVersionsAndSupersessions() {
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identityRow(1L)));
        when(versionRepo.listByIdentity("t-1", 1L)).thenReturn(List.of());
        when(supersessionRepo.findByTenantIdAndIdentityIdOrderByTransitionedAtAsc("t-1", 1L)).thenReturn(List.of());

        KnowledgeLineage lineage = service.getLineage(1L);
        assertThat(lineage.identity().id()).isEqualTo(1L);
        assertThat(lineage.versions()).isEmpty();
        assertThat(lineage.supersessions()).isEmpty();
    }

    @Test
    void requiresTenantContext() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.empty(), null));
        assertThatThrownBy(() -> service.get(1L))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.TENANT_CONTEXT_MISSING);
    }

    @Test
    void sourceAuthorityLevelsFollowCanonicalAToETrustOrder() {
        assertThat(SourceAuthorityLevel.A_REGULATION.rank()).isLessThan(SourceAuthorityLevel.B_GUIDELINE.rank());
        assertThat(SourceAuthorityLevel.B_GUIDELINE.rank()).isLessThan(SourceAuthorityLevel.C_CONSENSUS_LITERATURE.rank());
        assertThat(SourceAuthorityLevel.C_CONSENSUS_LITERATURE.rank()).isLessThan(SourceAuthorityLevel.D_HOSPITAL.rank());
        assertThat(SourceAuthorityLevel.D_HOSPITAL.rank()).isLessThan(SourceAuthorityLevel.E_FEEDBACK.rank());
        assertThat(SourceAuthorityLevel.A_REGULATION.label()).contains("A");
        assertThat(SourceAuthorityLevel.A_REGULATION.isHighAuthority()).isTrue();
        assertThat(SourceAuthorityLevel.D_HOSPITAL.isLowAuthority()).isTrue();
    }

    @Test
    void registerSourceRejectsBlankAuthorityBasis() {
        SourceRegisterRequest request = new SourceRegisterRequest(
            "SRC.NHC.2026", SourceType.POLICY, SourceAuthorityLevel.A_REGULATION,
            "国家卫健委政策", "国家卫健委", "公开", "zh-CN", "  "
        );
        when(sourceDocRepo.findByTenantIdAndSourceCode("t-1", "SRC.NHC.2026")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registerSource(request))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);
        Mockito.verify(sourceDocRepo, Mockito.never()).save(any());
    }

    @Test
    void registerSourceStoresAuthorityBasis() {
        SourceRegisterRequest request = new SourceRegisterRequest(
            "SRC.NHC.2026", SourceType.POLICY, SourceAuthorityLevel.A_REGULATION,
            "国家卫健委政策", "国家卫健委", "公开", "zh-CN", "国家卫健委发布文件编号 NHC-2026-01"
        );
        when(sourceDocRepo.findByTenantIdAndSourceCode("t-1", "SRC.NHC.2026")).thenReturn(Optional.empty());

        SourceDocument created = service.registerSource(request);

        assertThat(created.authorityLevel()).isEqualTo(SourceAuthorityLevel.A_REGULATION);
        assertThat(created.authorityBasis()).isEqualTo("国家卫健委发布文件编号 NHC-2026-01");
    }

    @Test
    void registerSourceVersionRejectsBlankContentHashInsteadOfSynthesizingTimestampHash() {
        SourceVersionRegisterRequest request = new SourceVersionRegisterRequest(
            1L, "v1", Instant.now(), " ", "s3://bucket/source.pdf", "zh-CN", null);
        when(sourceDocRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identitySourceDocument()));
        when(sourceVerRepo.findBySourceDocumentIdAndVersionNo(1L, "v1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registerSourceVersion(request))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);
        Mockito.verify(sourceVerRepo, Mockito.never()).save(any());
    }

    @Test
    void registerSourceVersionRejectsNonSha256ContentHash() {
        SourceVersionRegisterRequest request = new SourceVersionRegisterRequest(
            1L, "v1", Instant.now(), "not-a-sha256-source-hash", "s3://bucket/source.pdf", "zh-CN", null);
        when(sourceDocRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identitySourceDocument()));
        when(sourceVerRepo.findBySourceDocumentIdAndVersionNo(1L, "v1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registerSourceVersion(request))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);
        Mockito.verify(sourceVerRepo, Mockito.never()).save(any());
    }

    @Test
    void registerSourceVersionComputesHashFromContentWhenContentHashMissing() {
        SourceVersionRegisterRequest request = new SourceVersionRegisterRequest(
            1L, "v1", Instant.now(), null, "s3://bucket/source.pdf", "zh-CN", "真实指南原文");
        when(sourceDocRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identitySourceDocument()));
        when(sourceVerRepo.findBySourceDocumentIdAndVersionNo(1L, "v1")).thenReturn(Optional.empty());

        SourceVersion created = service.registerSourceVersion(request);

        assertThat(created.contentHash()).isEqualTo(sha256("真实指南原文"));
    }

    @Test
    void registerSourceVersionRejectsMismatchedContentAndHash() {
        SourceVersionRegisterRequest request = new SourceVersionRegisterRequest(
            1L, "v1", Instant.now(), sha256("另一份来源原文"), "s3://bucket/source.pdf", "zh-CN", "真实指南原文");
        when(sourceDocRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identitySourceDocument()));

        assertThatThrownBy(() -> service.registerSourceVersion(request))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);
        Mockito.verify(sourceVerRepo, Mockito.never()).save(any());
    }

    @Test
    void registerSourceVersionNormalizesUppercaseSha256() {
        String upperHash = sha256("真实指南原文").toUpperCase();
        SourceVersionRegisterRequest request = new SourceVersionRegisterRequest(
            1L, "v1", Instant.now(), upperHash, "s3://bucket/source.pdf", "zh-CN", null);
        when(sourceDocRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identitySourceDocument()));
        when(sourceVerRepo.findBySourceDocumentIdAndVersionNo(1L, "v1")).thenReturn(Optional.empty());

        SourceVersion created = service.registerSourceVersion(request);

        assertThat(created.contentHash()).isEqualTo(upperHash.toLowerCase());
    }

    @Test
    void registerSourceVersionReturnsExistingWhenVersionNoAndContentHashMatch() {
        String contentHash = sha256("真实指南原文");
        SourceVersion existing = new SourceVersion(
            8L, "t-1", 1L, "v1", Instant.now(), contentHash, "s3://bucket/source-v1.pdf", "zh-CN",
            Instant.now(), "u-99"
        );
        SourceVersionRegisterRequest request = new SourceVersionRegisterRequest(
            1L, "v1", Instant.now(), contentHash.toUpperCase(), "s3://bucket/source-v1.pdf", "zh-CN", null);
        when(sourceDocRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identitySourceDocument()));
        when(sourceVerRepo.findBySourceDocumentIdAndVersionNo(1L, "v1")).thenReturn(Optional.of(existing));

        SourceVersion created = service.registerSourceVersion(request);

        assertThat(created).isSameAs(existing);
        Mockito.verify(sourceVerRepo, Mockito.never()).save(any());
    }

    @Test
    void registerSourceVersionRejectsSameVersionNoWithDifferentContentHash() {
        SourceVersion existing = new SourceVersion(
            8L, "t-1", 1L, "v1", Instant.now(), sha256("真实指南原文"), "s3://bucket/source-v1.pdf", "zh-CN",
            Instant.now(), "u-99"
        );
        SourceVersionRegisterRequest request = new SourceVersionRegisterRequest(
            1L, "v1", Instant.now(), sha256("另一份来源原文"), "s3://bucket/source-v1.pdf", "zh-CN", null);
        when(sourceDocRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identitySourceDocument()));
        when(sourceVerRepo.findBySourceDocumentIdAndVersionNo(1L, "v1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.registerSourceVersion(request))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
        Mockito.verify(sourceVerRepo, Mockito.never()).save(any());
    }

    @Test
    void registerSourceVersionReturnsExistingWhenContentHashAlreadyRegistered() {
        String contentHash = sha256("真实指南原文");
        SourceVersion existing = new SourceVersion(
            8L, "t-1", 1L, "v1", Instant.now(), contentHash, "s3://bucket/source-v1.pdf", "zh-CN",
            Instant.now(), "u-99"
        );
        SourceVersionRegisterRequest request = new SourceVersionRegisterRequest(
            1L, "v2", Instant.now(), contentHash, "s3://bucket/source-v2.pdf", "zh-CN", null);
        when(sourceDocRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identitySourceDocument()));
        when(sourceVerRepo.findBySourceDocumentIdAndVersionNo(1L, "v2")).thenReturn(Optional.empty());
        when(sourceVerRepo.findBySourceDocumentIdAndContentHash(1L, contentHash)).thenReturn(Optional.of(existing));

        SourceVersion created = service.registerSourceVersion(request);

        assertThat(created).isSameAs(existing);
        Mockito.verify(sourceVerRepo, Mockito.never()).save(any());
    }

    @Test
    void createIdentityRejectsMismatchedTenantContext() {
        KnowledgeIdentityCreateRequest request = identityCreateRequest("t-2", " DRUG.HTN ");

        assertThatThrownBy(() -> service.createIdentity(request))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ORG_SCOPE_DENIED);
        Mockito.verify(identityRepo, Mockito.never()).save(any());
    }

    @Test
    void createIdentityTrimsCodeAndDefaultsActiveWithoutCurrentVersion() {
        KnowledgeIdentityCreateRequest request = identityCreateRequest("t-1", " DRUG.HTN ");
        when(identityRepo.findByTenantIdAndIdentityCode("t-1", "DRUG.HTN")).thenReturn(Optional.empty());

        KnowledgeIdentity created = service.createIdentity(request);

        assertThat(created.identityCode()).isEqualTo("DRUG.HTN");
        assertThat(created.subject()).isEqualTo("高血压用药");
        assertThat(created.status()).isEqualTo(KnowledgeIdentityStatus.ACTIVE);
        assertThat(created.currentVersionId()).isNull();
        assertThat(created.tenantId()).isEqualTo("t-1");
        assertThat(created.createdBy()).isEqualTo("u-99");
        assertThat(created.specialtyId()).isEqualTo("CARD");
    }

    @Test
    void registerSourceVersionWithStandardRequestUsesPathSourceDocumentId() {
        KnowledgeSourceVersionCreateRequest request = new KnowledgeSourceVersionCreateRequest(
            "req-1", "trace-1", "t-1", null, "h-1", null, null, "d-1", "CARD",
            "u-99", List.of("knowledge.write"), "pkg-2026.06",
            "v1", Instant.parse("2026-06-01T00:00:00Z"), sha256("真实指南原文"), "s3://bucket/source.pdf", null,
            null
        );
        when(sourceDocRepo.findByTenantIdAndId("t-1", 42L)).thenReturn(Optional.of(identitySourceDocument()));
        when(sourceVerRepo.findBySourceDocumentIdAndVersionNo(42L, "v1")).thenReturn(Optional.empty());

        SourceVersion created = service.registerSourceVersion(42L, request);

        assertThat(created.sourceDocumentId()).isEqualTo(42L);
        assertThat(created.contentHash()).isEqualTo(sha256("真实指南原文"));
        assertThat(created.language()).isEqualTo("zh-CN");
    }

    @Test
    void createCitationPersistsTenantScopedEvidenceLink() {
        when(versionRepo.findByTenantIdAndId("t-1", 20L))
            .thenReturn(Optional.of(versionRowWithSource(20L, 1L, 1000L)));
        when(sourceFragRepo.findByTenantIdAndId("t-1", 100L))
            .thenReturn(Optional.of(sourceFragment(100L, 1000L)));
        when(citationRepo.findByTenantIdAndAssetVersionIdAndSourceFragmentIdAndRelation(
            "t-1", 20L, 100L, CitationRelation.DERIVED_FROM)).thenReturn(Optional.empty());

        Citation created = service.createCitation(new CitationCreateRequest(
            20L, 100L, CitationRelation.DERIVED_FROM, 90, 0, 8));

        assertThat(created.tenantId()).isEqualTo("t-1");
        assertThat(created.assetVersionId()).isEqualTo(20L);
        assertThat(created.sourceFragmentId()).isEqualTo(100L);
        assertThat(created.weight()).isEqualTo(90);
        assertThat(created.createdBy()).isEqualTo("u-99");
    }

    @Test
    void createCitationRejectsFragmentFromAnotherSourceVersion() {
        when(versionRepo.findByTenantIdAndId("t-1", 20L))
            .thenReturn(Optional.of(versionRowWithSource(20L, 1L, 1000L)));
        when(sourceFragRepo.findByTenantIdAndId("t-1", 100L))
            .thenReturn(Optional.of(sourceFragment(100L, 2000L)));

        assertThatThrownBy(() -> service.createCitation(new CitationCreateRequest(
            20L, 100L, CitationRelation.DERIVED_FROM, 90, null, null)))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).errorCode())
            .isEqualTo(ErrorCode.VALIDATION_FAILED);

        Mockito.verify(citationRepo, Mockito.never()).save(any());
    }

    @Test
    void createCitationRejectsReversedOffsets() {
        when(versionRepo.findByTenantIdAndId("t-1", 20L))
            .thenReturn(Optional.of(versionRowWithSource(20L, 1L, 1000L)));
        when(sourceFragRepo.findByTenantIdAndId("t-1", 100L))
            .thenReturn(Optional.of(sourceFragment(100L, 1000L)));

        assertThatThrownBy(() -> service.createCitation(new CitationCreateRequest(
            20L, 100L, CitationRelation.DERIVED_FROM, 90, 8, 2)))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).errorCode())
            .isEqualTo(ErrorCode.VALIDATION_FAILED);

        Mockito.verify(citationRepo, Mockito.never()).save(any());
    }

    @Test
    void listCitationsReturnsEmptyWhenIdentityHasNoActiveVersion() {
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identityRow(1L)));

        assertThat(service.listCitations(1L)).isEmpty();
        Mockito.verify(citationRepo, Mockito.never())
            .findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc(any(), any());
    }

    @Test
    void listCitationsReadsCurrentActiveVersionOnly() {
        when(identityRepo.findByTenantIdAndId("t-1", 1L))
            .thenReturn(Optional.of(identityRow(1L, "t-1", "DRUG.X", 20L)));
        when(versionRepo.findByTenantIdAndId("t-1", 20L))
            .thenReturn(Optional.of(versionRow(20L, 1L, KnowledgeVersionStatus.ACTIVE)));
        when(citationRepo.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 20L))
            .thenReturn(List.of(citation(8L, 20L, 90)));

        List<Citation> citations = service.listCitations(1L);

        assertThat(citations).hasSize(1);
        assertThat(citations.get(0).assetVersionId()).isEqualTo(20L);
    }

    @Test
    void listCitationsPrefersCurrentVersionPointerForMultiScopeActiveIdentities() {
        when(identityRepo.findByTenantIdAndId("t-1", 1L))
            .thenReturn(Optional.of(identityRow(1L, "t-1", "DRUG.X", 22L)));
        when(versionRepo.findByTenantIdAndId("t-1", 22L))
            .thenReturn(Optional.of(versionRow(22L, 1L, KnowledgeVersionStatus.ACTIVE)));
        when(citationRepo.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 22L))
            .thenReturn(List.of(citation(8L, 22L, 90)));

        List<Citation> citations = service.listCitations(1L);

        assertThat(citations).hasSize(1);
        assertThat(citations.get(0).assetVersionId()).isEqualTo(22L);
    }

    @Test
    void listSourceEvidenceRanksHighAuthorityAsPrimaryAndLabelsLowAuthoritySupplemental() {
        when(identityRepo.findByTenantIdAndId("t-1", 1L))
            .thenReturn(Optional.of(identityRow(1L, "t-1", "DRUG.X", 20L)));
        when(versionRepo.findByTenantIdAndId("t-1", 20L))
            .thenReturn(Optional.of(versionRow(20L, 1L, KnowledgeVersionStatus.ACTIVE)));
        when(citationRepo.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 20L))
            .thenReturn(List.of(
                citation(1L, 20L, 100, 100L),
                citation(2L, 20L, 10, 200L)
            ));
        when(sourceFragRepo.findByTenantIdAndId("t-1", 100L))
            .thenReturn(Optional.of(sourceFragment(100L, 1000L)));
        when(sourceFragRepo.findByTenantIdAndId("t-1", 200L))
            .thenReturn(Optional.of(sourceFragment(200L, 2000L)));
        when(sourceVerRepo.findByTenantIdAndId("t-1", 1000L))
            .thenReturn(Optional.of(sourceVersion(1000L, 10L, Instant.parse("2024-01-01T00:00:00Z"))));
        when(sourceVerRepo.findByTenantIdAndId("t-1", 2000L))
            .thenReturn(Optional.of(sourceVersion(2000L, 20L, Instant.parse("2026-01-01T00:00:00Z"))));
        when(sourceDocRepo.findByTenantIdAndId("t-1", 10L))
            .thenReturn(Optional.of(sourceDocument(10L, SourceAuthorityLevel.D_HOSPITAL, "院内 SOP")));
        when(sourceDocRepo.findByTenantIdAndId("t-1", 20L))
            .thenReturn(Optional.of(sourceDocument(20L, SourceAuthorityLevel.A_REGULATION, "国家法规")));

        List<KnowledgeSourceEvidence> evidence = service.listSourceEvidence(1L);

        assertThat(evidence).hasSize(2);
        assertThat(evidence.get(0))
            .satisfies(item -> {
                assertThat(item.authorityLevel()).isEqualTo(SourceAuthorityLevel.A_REGULATION);
                assertThat(item.displayRole()).isEqualTo(KnowledgeSourceEvidenceRole.PRIMARY);
                assertThat(item.recommendedByDefault()).isTrue();
                assertThat(item.supplementary()).isFalse();
                assertThat(item.displayLabel()).contains("A 法规").contains("主证据");
                assertThat(item.rankingReason()).contains("可信分级").contains("2026-01-01");
            });
        assertThat(evidence.get(1))
            .satisfies(item -> {
                assertThat(item.authorityLevel()).isEqualTo(SourceAuthorityLevel.D_HOSPITAL);
                assertThat(item.displayRole()).isEqualTo(KnowledgeSourceEvidenceRole.SUPPLEMENTARY);
                assertThat(item.recommendedByDefault()).isFalse();
                assertThat(item.supplementary()).isTrue();
                assertThat(item.displayLabel()).contains("D 院内").contains("补充证据");
            });
    }

    @Test
    void provenanceReturnsExactAnchorAndMarksUnresolvedCitationAsPartial() {
        when(identityRepo.findByTenantIdAndId("t-1", 1L))
            .thenReturn(Optional.of(identityRow(1L, "t-1", "DRUG.X", 20L)));
        KnowledgeAssetVersion active = versionRow(20L, 1L, KnowledgeVersionStatus.ACTIVE);
        KnowledgeAssetVersion historical = versionRow(19L, 1L, KnowledgeVersionStatus.SUPERSEDED);
        when(versionRepo.listByIdentity("t-1", 1L)).thenReturn(List.of(active, historical));
        when(versionRepo.findByTenantIdAndId("t-1", 20L)).thenReturn(Optional.of(active));
        Citation resolved = new Citation(
            1L, "t-1", 20L, 100L, CitationRelation.SUPPORTS, 90, 2, 12, Instant.now(), "u");
        Citation unresolved = new Citation(
            2L, "t-1", 20L, 999L, CitationRelation.SUPPORTS, 80, 0, 8, Instant.now(), "u");
        when(citationRepo.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 20L))
            .thenReturn(List.of(resolved, unresolved));
        when(sourceFragRepo.findByTenantIdAndId("t-1", 100L))
            .thenReturn(Optional.of(sourceFragment(100L, 1000L)));
        when(sourceVerRepo.findByTenantIdAndId("t-1", 1000L))
            .thenReturn(Optional.of(sourceVersion(1000L, 10L, Instant.parse("2026-01-01T00:00:00Z"))));
        when(sourceDocRepo.findByTenantIdAndId("t-1", 10L))
            .thenReturn(Optional.of(sourceDocument(10L, SourceAuthorityLevel.A_REGULATION, "国家法规")));

        KnowledgeProvenanceResponse provenance = service.getProvenance(1L);

        assertThat(provenance.currentVersionId()).isEqualTo(20L);
        assertThat(provenance.versions()).extracting(KnowledgeAssetVersion::id)
            .containsExactly(20L, 19L);
        assertThat(provenance.unresolvedCitationCount()).isEqualTo(1);
        assertThat(provenance.partial()).isTrue();
        assertThat(provenance.sourceEvidence()).singleElement().satisfies(item -> {
            assertThat(item.assetVersionId()).isEqualTo(20L);
            assertThat(item.sourceVersionNo()).isEqualTo("v1000");
            assertThat(item.sourceVersionHash()).isNotBlank();
            assertThat(item.anchorPath()).isEqualTo("§100");
            assertThat(item.anchorLabel()).isEqualTo("条款 100");
            assertThat(item.textExcerpt()).isEqualTo("真实来源片段 100");
            assertThat(item.fragmentHash()).isNotBlank();
            assertThat(item.startOffset()).isEqualTo(2);
            assertThat(item.endOffset()).isEqualTo(12);
        });
    }

    private KnowledgeIdentity identityRow(Long id) {
        return identityRow(id, "t-1", "DRUG.X");
    }

    private KnowledgeIdentity identityRow(Long id, String tenantId, String identityCode) {
        return identityRow(id, tenantId, identityCode, null);
    }

    private KnowledgeIdentity identityRow(Long id, String tenantId, String identityCode, Long currentVersionId) {
        Instant now = Instant.now();
        return new KnowledgeIdentity(
            id, tenantId, identityCode, KnowledgeDomain.DRUG, "测试主题", null, null,
            KnowledgeIdentityStatus.ACTIVE, currentVersionId,
            now, "u", now, "u"
        );
    }

    private SourceDocument identitySourceDocument() {
        Instant now = Instant.now();
        return new SourceDocument(
            1L, "t-1", "SRC.X", SourceType.GUIDELINE, SourceAuthorityLevel.D_HOSPITAL,
            "院内制度编号可追溯",
            "来源文件", "发布机构", "LICENSE", "zh-CN", now, "u", now, "u"
        );
    }

    private KnowledgeIdentityCreateRequest identityCreateRequest(String tenantId, String identityCode) {
        return new KnowledgeIdentityCreateRequest(
            "req-1", "trace-1", tenantId, null, "h-1", null, null, "d-1", "CARD",
            "u-99", List.of("knowledge.write"), "pkg-2026.06",
            identityCode, KnowledgeDomain.DRUG, " 高血压用药 ", " CARD ", "指南资产"
        );
    }

    private KnowledgeAssetVersion versionRow(Long id, Long identityId, KnowledgeVersionStatus status) {
        Instant now = Instant.now();
        String organizationScope = "tenant:t-1";
        String applicableScope = KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE;
        String activeScopeKey = status == KnowledgeVersionStatus.ACTIVE
            ? KnowledgeAssetVersion.activeScopeKey(identityId, organizationScope, applicableScope)
            : "version:" + id;
        return new KnowledgeAssetVersion(
            id, "t-1", identityId, "v1", "label",
            null, null, sha256("知识版本夹具内容-" + id), null,
            status, KnowledgeRiskLevel.LOW,
            SourceAuthorityLevel.B_GUIDELINE, null, null, null,
            organizationScope, applicableScope, activeScopeKey,
            null, null, null, null,
            status == KnowledgeVersionStatus.ACTIVE ? now : null, null,
            null, null,
            now, "init", now, "init"
        );
    }

    private KnowledgeAssetVersion versionRowWithSource(Long id, Long identityId, Long sourceVersionId) {
        KnowledgeAssetVersion row = versionRow(
            id, identityId, KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW);
        return new KnowledgeAssetVersion(
            row.id(), row.tenantId(), row.identityId(), row.versionNo(), row.versionLabel(),
            10L, sourceVersionId, row.contentHash(), row.anchors(),
            row.status(), row.riskLevel(), row.authorityLevel(), row.gradeQuality(),
            row.gradeStrength(), row.conflictArbitration(), row.organizationScope(),
            row.applicableScope(), row.activeScopeKey(), row.effectiveFrom(), row.effectiveTo(),
            row.reviewedBy(), row.reviewedAt(), row.activatedAt(), row.supersededAt(),
            row.withdrawnAt(), row.withdrawnReason(), row.createdAt(), row.createdBy(),
            row.updatedAt(), row.updatedBy()
        );
    }

    private Citation citation(Long id, Long assetVersionId, int weight) {
        return citation(id, assetVersionId, weight, 100L);
    }

    private Citation citation(Long id, Long assetVersionId, int weight, Long sourceFragmentId) {
        return new Citation(
            id, "t-1", assetVersionId, sourceFragmentId, CitationRelation.DERIVED_FROM, weight, null, null, Instant.now(), "u"
        );
    }

    private SourceFragment sourceFragment(Long id, Long sourceVersionId) {
        return new SourceFragment(
            id, "t-1", sourceVersionId, "§" + id, "条款 " + id,
            "真实来源片段 " + id, sha256("真实来源片段 " + id), Instant.now()
        );
    }

    private SourceVersion sourceVersion(Long id, Long sourceDocumentId, Instant publishedAt) {
        return new SourceVersion(
            id, "t-1", sourceDocumentId, "v" + id, publishedAt, sha256("来源版本 " + id),
            "s3://source-" + id + ".pdf", "zh-CN", Instant.now(), "u"
        );
    }

    private SourceDocument sourceDocument(Long id, SourceAuthorityLevel authorityLevel, String title) {
        Instant now = Instant.now();
        return new SourceDocument(
            id, "t-1", "SRC." + id, SourceType.GUIDELINE, authorityLevel,
            "分级依据 " + title, title, "发布机构", "LICENSE", "zh-CN", now, "u", now, "u"
        );
    }

    private String sha256(String text) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
