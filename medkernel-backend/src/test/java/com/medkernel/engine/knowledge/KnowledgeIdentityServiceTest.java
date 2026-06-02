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
        when(sourceVerRepo.save(any(SourceVersion.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void pageNormalizesKeywordToLowercaseAndWrapsPercent() {
        when(identityRepo.countByFilter(eq("t-1"), any(), any(), any(), eq("%他汀%"))).thenReturn(2L);
        when(identityRepo.pageByFilter(eq("t-1"), any(), any(), any(), eq("%他汀%"), anyInt(), anyInt()))
            .thenReturn(List.of(identityRow(1L)));

        PageResponse<KnowledgeIdentity> page = service.page(
            new PageRequest(1, 20, null),
            new KnowledgeIdentityFilter(null, null, null, "  他汀  ")
        );
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).hasSize(1);
    }

    @Test
    void pageFilterEmptyKeywordBecomesNull() {
        when(identityRepo.countByFilter(eq("t-1"), any(), any(), any(), eq(null))).thenReturn(0L);
        PageResponse<KnowledgeIdentity> page = service.page(
            PageRequest.defaults(),
            new KnowledgeIdentityFilter(null, null, null, "   ")
        );
        assertThat(page.items()).isEmpty();
    }

    @Test
    void pageEnumFiltersAreMappedToStringName() {
        when(identityRepo.countByFilter("t-1", "DRUG", null, "ACTIVE", null)).thenReturn(1L);
        when(identityRepo.pageByFilter(eq("t-1"), eq("DRUG"), any(), eq("ACTIVE"), any(), anyInt(), anyInt()))
            .thenReturn(List.of(identityRow(1L)));

        service.page(
            PageRequest.defaults(),
            new KnowledgeIdentityFilter(KnowledgeDomain.DRUG, null, KnowledgeIdentityStatus.ACTIVE, null)
        );
        // 校验 enum→String 转换确实发生：count 被调到，且 specialty 为 null
        ArgumentCaptor<String> domainCap = ArgumentCaptor.forClass(String.class);
        Mockito.verify(identityRepo).countByFilter(eq("t-1"), domainCap.capture(), any(), any(), any());
        assertThat(domainCap.getValue()).isEqualTo("DRUG");
    }

    @Test
    void getReturnsIdentityWhenExists() {
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identityRow(1L)));
        KnowledgeIdentity result = service.get(1L);
        assertThat(result.id()).isEqualTo(1L);
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
        when(versionRepo.findActiveByIdentity("t-1", 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getActiveVersion(1L))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.NOT_FOUND);
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
    void registerSourceVersionRejectsBlankContentHashInsteadOfSynthesizingTimestampHash() {
        SourceVersionRegisterRequest request = new SourceVersionRegisterRequest(
            1L, "v1", Instant.now(), " ", "s3://bucket/source.pdf", "zh-CN");
        when(sourceDocRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identitySourceDocument()));
        when(sourceVerRepo.findBySourceDocumentIdAndVersionNo(1L, "v1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registerSourceVersion(request))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);
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
            "v1", Instant.parse("2026-06-01T00:00:00Z"), " abc123 ", "s3://bucket/source.pdf", null
        );
        when(sourceDocRepo.findByTenantIdAndId("t-1", 42L)).thenReturn(Optional.of(identitySourceDocument()));
        when(sourceVerRepo.findBySourceDocumentIdAndVersionNo(42L, "v1")).thenReturn(Optional.empty());

        SourceVersion created = service.registerSourceVersion(42L, request);

        assertThat(created.sourceDocumentId()).isEqualTo(42L);
        assertThat(created.contentHash()).isEqualTo("abc123");
        assertThat(created.language()).isEqualTo("zh-CN");
    }

    @Test
    void listCitationsReturnsEmptyWhenIdentityHasNoActiveVersion() {
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identityRow(1L)));
        when(versionRepo.findActiveByIdentity("t-1", 1L)).thenReturn(Optional.empty());

        assertThat(service.listCitations(1L)).isEmpty();
        Mockito.verify(citationRepo, Mockito.never())
            .findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc(any(), any());
    }

    @Test
    void listCitationsReadsCurrentActiveVersionOnly() {
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identityRow(1L)));
        when(versionRepo.findActiveByIdentity("t-1", 1L))
            .thenReturn(Optional.of(versionRow(20L, 1L, KnowledgeVersionStatus.ACTIVE)));
        when(citationRepo.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 20L))
            .thenReturn(List.of(citation(8L, 20L, 90)));

        List<Citation> citations = service.listCitations(1L);

        assertThat(citations).hasSize(1);
        assertThat(citations.get(0).assetVersionId()).isEqualTo(20L);
    }

    private KnowledgeIdentity identityRow(Long id) {
        Instant now = Instant.now();
        return new KnowledgeIdentity(
            id, "t-1", "DRUG.X", KnowledgeDomain.DRUG, "测试主题", null, null,
            KnowledgeIdentityStatus.ACTIVE, null,
            now, "u", now, "u"
        );
    }

    private SourceDocument identitySourceDocument() {
        Instant now = Instant.now();
        return new SourceDocument(
            1L, "t-1", "SRC.X", SourceType.GUIDELINE, SourceAuthorityLevel.HOSPITAL,
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
        return new KnowledgeAssetVersion(
            id, "t-1", identityId, "v1", "label",
            null, null, "deadbeef", null,
            status, KnowledgeRiskLevel.LOW,
            null, null, null, null,
            status == KnowledgeVersionStatus.ACTIVE ? now : null, null,
            null, null,
            now, "init", now, "init"
        );
    }

    private Citation citation(Long id, Long assetVersionId, int weight) {
        return new Citation(
            id, "t-1", assetVersionId, 100L, CitationRelation.DERIVED_FROM, weight, Instant.now(), "u"
        );
    }
}
