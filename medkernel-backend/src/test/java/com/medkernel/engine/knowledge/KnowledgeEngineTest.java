package com.medkernel.engine.knowledge;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetOwnershipScope;
import com.medkernel.engine.versioning.AssetScopeResolver;
import com.medkernel.engine.versioning.ReleasePort;
import com.medkernel.engine.versioning.VersionPublishEvidence;
import com.medkernel.engine.versioning.VersionPublishQualityGate;
import com.medkernel.engine.knowledge.production.gate.PublicationQualityRecordService;
import com.medkernel.engine.release.ReleaseSourceLayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识资产与版本引擎核心逻辑测试。
 *
 * <p>重点覆盖：
 * <ul>
 *   <li>来源文献、版本及锚点片段登记</li>
 *   <li>引用锚点 SHA-256 摘要签名计算与幂等防重</li>
 *   <li>候选进入新旧识别后只进入待替换审核，不直接发布</li>
 *   <li>基于内容哈希 SHA-256 指纹的重复候选去重</li>
 * </ul>
 */
class KnowledgeEngineTest {

    private KnowledgeIdentityRepository identityRepo;
    private KnowledgeAssetVersionRepository versionRepo;
    private KnowledgeSupersessionRepository supersessionRepo;
    private SourceDocumentRepository sourceDocRepo;
    private SourceVersionRepository sourceVerRepo;
    private SourceFragmentRepository sourceFragRepo;
    private CitationRepository citationRepo;
    private KnowledgeProjectionRefreshPort projectionRefreshPort;
    private CandidateClassificationRepository candidateClassificationRepo;
    private ReviewAssignmentRepository reviewAssignmentRepo;
    private KnowledgeInvalidationRepository invalidationRepo;
    private AffectedCaseTaskRepository affectedCaseTaskRepo;
    private KnowledgeVersionedAssetAdapter versionedAssets;
    private AssetVersionRepository assetVersions;
    private ReleasePort releasePort;
    private PublicationQualityRecordService publicationQualityRecords;
    private AssetScopeResolver assetScopes;
    private KnowledgeEffectiveVersionResolver effectiveVersions;

    private KnowledgeIdentityService identityService;
    private KnowledgeVersionService versionService;

    @BeforeEach
    void setUp() {
        identityRepo = Mockito.mock(KnowledgeIdentityRepository.class);
        versionRepo = Mockito.mock(KnowledgeAssetVersionRepository.class);
        supersessionRepo = Mockito.mock(KnowledgeSupersessionRepository.class);
        sourceDocRepo = Mockito.mock(SourceDocumentRepository.class);
        sourceVerRepo = Mockito.mock(SourceVersionRepository.class);
        sourceFragRepo = Mockito.mock(SourceFragmentRepository.class);
        citationRepo = Mockito.mock(CitationRepository.class);
        projectionRefreshPort = Mockito.mock(KnowledgeProjectionRefreshPort.class);
        candidateClassificationRepo = Mockito.mock(CandidateClassificationRepository.class);
        reviewAssignmentRepo = Mockito.mock(ReviewAssignmentRepository.class);
        invalidationRepo = Mockito.mock(KnowledgeInvalidationRepository.class);
        affectedCaseTaskRepo = Mockito.mock(AffectedCaseTaskRepository.class);
        versionedAssets = Mockito.mock(KnowledgeVersionedAssetAdapter.class);
        assetVersions = Mockito.mock(AssetVersionRepository.class);
        releasePort = Mockito.mock(ReleasePort.class);
        publicationQualityRecords = Mockito.mock(PublicationQualityRecordService.class);
        assetScopes = Mockito.mock(AssetScopeResolver.class);
        effectiveVersions = Mockito.mock(KnowledgeEffectiveVersionResolver.class);

        identityService = new KnowledgeIdentityService(
            identityRepo, versionRepo, supersessionRepo, sourceDocRepo, sourceVerRepo, sourceFragRepo, citationRepo,
            new com.medkernel.engine.versioning.AssetIdentityAllocator(), effectiveVersions
        );

        versionService = new KnowledgeVersionService(
            identityRepo, versionRepo, supersessionRepo, citationRepo, sourceDocRepo, sourceVerRepo, projectionRefreshPort,
            candidateClassificationRepo, reviewAssignmentRepo, invalidationRepo, affectedCaseTaskRepo,
            versionedAssets, assetVersions, releasePort, publicationQualityRecords, assetScopes
        );
        when(assetScopes.resolve(any(), any(OrgScope.class)))
            .thenReturn(new AssetOwnershipScope(
                ReleaseSourceLayer.PLATFORM, "/__platform__"));
        when(publicationQualityRecords.requirePublishEvidence(any(), any(), any()))
            .thenReturn(new VersionPublishEvidence(new VersionPublishQualityGate(
                true, true, true, true, true, "服务端质量门测试记录")));

        // 初始化租户与用户上下文环境
        RequestContext.restore(new RequestContext.Snapshot("trace-123", OrgScope.tenant("t-1"), "u-admin"));

        // 设置 Mockito 默认保存行为
        when(sourceDocRepo.save(any(SourceDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sourceVerRepo.save(any(SourceVersion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sourceFragRepo.save(any(SourceFragment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(versionRepo.save(any(KnowledgeAssetVersion.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void registerSourceSavesNewDocumentWhenNotExists() {
        SourceRegisterRequest req = new SourceRegisterRequest(
            "doc-code", SourceType.GUIDELINE, SourceAuthorityLevel.B_GUIDELINE,
            "中华骨科指南", "中华医学会", "MIT", "zh-CN", "国家级指南发布机构与版本号可追溯"
        );

        when(sourceDocRepo.findByTenantIdAndSourceCode("t-1", "doc-code")).thenReturn(Optional.empty());

        SourceDocument saved = identityService.registerSource(req);

        assertThat(saved).isNotNull();
        assertThat(saved.sourceCode()).isEqualTo("doc-code");
        assertThat(saved.title()).isEqualTo("中华骨科指南");
        verify(sourceDocRepo, times(1)).save(any(SourceDocument.class));
    }

    @Test
    void registerSourceReturnsExistingDocumentWhenAlreadyExists() {
        SourceDocument existing = new SourceDocument(
            1L, "t-1", "doc-code", SourceType.GUIDELINE, SourceAuthorityLevel.B_GUIDELINE,
            "国家级指南发布机构与版本号可追溯",
            "中华骨科指南", "中华医学会", "MIT", "zh-CN",
            Instant.now(), "system", Instant.now(), "system"
        );

        SourceRegisterRequest req = new SourceRegisterRequest(
            "doc-code", SourceType.GUIDELINE, SourceAuthorityLevel.B_GUIDELINE,
            "中华骨科指南", "中华医学会", "MIT", "zh-CN", "国家级指南发布机构与版本号可追溯"
        );

        when(sourceDocRepo.findByTenantIdAndSourceCode("t-1", "doc-code")).thenReturn(Optional.of(existing));

        SourceDocument result = identityService.registerSource(req);

        assertThat(result).isEqualTo(existing);
        verify(sourceDocRepo, never()).save(any(SourceDocument.class));
    }

    @Test
    void registerSourceVersionSavesVersionWhenNotExists() {
        SourceVersionRegisterRequest req = new SourceVersionRegisterRequest(
            1L, "v1.0", Instant.now(), sha256("中华骨科指南 2026 版原文"), "http://file", "zh-CN", null
        );

        SourceDocument doc = new SourceDocument(
            1L, "t-1", "doc-code", SourceType.GUIDELINE, SourceAuthorityLevel.B_GUIDELINE,
            "国家级指南发布机构与版本号可追溯",
            "中华骨科指南", "中华医学会", "MIT", "zh-CN",
            Instant.now(), "system", Instant.now(), "system"
        );

        when(sourceDocRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(doc));
        when(sourceVerRepo.findBySourceDocumentIdAndVersionNo(1L, "v1.0")).thenReturn(Optional.empty());

        SourceVersion saved = identityService.registerSourceVersion(req);

        assertThat(saved).isNotNull();
        assertThat(saved.versionNo()).isEqualTo("v1.0");
        assertThat(saved.contentHash()).isEqualTo(sha256("中华骨科指南 2026 版原文"));
        verify(sourceVerRepo, times(1)).save(any(SourceVersion.class));
    }

    @Test
    void registerSourceVersionRejectsIfDocumentDoesNotExist() {
        SourceVersionRegisterRequest req = new SourceVersionRegisterRequest(
            999L, "v1.0", Instant.now(), sha256("不存在来源文献原文"), "http://file", "zh-CN", null
        );

        when(sourceDocRepo.findByTenantIdAndId("t-1", 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> identityService.registerSourceVersion(req))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_KNOW_001);
    }

    @Test
    void createFragmentSavesNewFragment() {
        FragmentCreateRequest req = new FragmentCreateRequest(
            10L, "sec-1", "第一章", "关节置换核心条文"
        );

        SourceVersion version = new SourceVersion(
            10L, "t-1", 1L, "v1.0", Instant.now(), sha256("来源版本原文"), "http", "zh-CN", Instant.now(), "system"
        );

        when(sourceVerRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(version));
        when(sourceFragRepo.findBySourceVersionIdAndAnchorPath(10L, "sec-1")).thenReturn(Optional.empty());

        SourceFragment saved = identityService.createFragment(req);

        assertThat(saved).isNotNull();
        assertThat(saved.anchorPath()).isEqualTo("sec-1");
        assertThat(saved.textExcerpt()).isEqualTo("关节置换核心条文");
        assertThat(saved.contentHash()).isEqualTo(sha256("关节置换核心条文"));
        verify(sourceFragRepo, times(1)).save(any(SourceFragment.class));
    }

    @Test
    void createFragmentReturnsExistingOnIdempotentMatch() {
        SourceFragment existing = new SourceFragment(
            100L, "t-1", 10L, "sec-1", "第一章", "关节置换核心条文", sha256("关节置换核心条文"), Instant.now()
        );

        FragmentCreateRequest req = new FragmentCreateRequest(
            10L, "sec-1", "第一章", "关节置换核心条文"
        );

        SourceVersion version = new SourceVersion(
            10L, "t-1", 1L, "v1.0", Instant.now(), sha256("来源版本原文"), "http", "zh-CN", Instant.now(), "system"
        );

        when(sourceVerRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(version));
        when(sourceFragRepo.findBySourceVersionIdAndAnchorPath(10L, "sec-1")).thenReturn(Optional.of(existing));

        SourceFragment result = identityService.createFragment(req);

        assertThat(result).isEqualTo(existing);
        verify(sourceFragRepo, never()).save(any(SourceFragment.class));
    }

    @Test
    void createFragmentThrowsConflictWhenTextExcerptDoesNotMatch() {
        SourceFragment existing = new SourceFragment(
            100L, "t-1", 10L, "sec-1", "第一章", "不同文本内容", sha256("不同文本内容"), Instant.now()
        );

        FragmentCreateRequest req = new FragmentCreateRequest(
            10L, "sec-1", "第一章", "关节置换核心条文"
        );

        SourceVersion version = new SourceVersion(
            10L, "t-1", 1L, "v1.0", Instant.now(), sha256("来源版本原文"), "http", "zh-CN", Instant.now(), "system"
        );

        when(sourceVerRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(version));
        when(sourceFragRepo.findBySourceVersionIdAndAnchorPath(10L, "sec-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> identityService.createFragment(req))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void classifyCandidateCreatesPendingReviewVersion() {
        KnowledgeIdentity identity = new KnowledgeIdentity(
            5L, "t-1", "DRUG.A", KnowledgeDomain.DRUG, "骨关节炎临床规则", null, null,
            KnowledgeIdentityStatus.ACTIVE, null, Instant.now(), "system", Instant.now(), "system"
        );

        when(identityRepo.findByTenantIdAndId("t-1", 5L)).thenReturn(Optional.of(identity));
        when(sourceDocRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(sourceDocument(1L)));
        when(versionRepo.findByTenantIdAndIdentityIdOrderByCreatedAtDesc("t-1", 5L)).thenReturn(List.of());
        when(candidateClassificationRepo.save(any(CandidateClassification.class))).thenAnswer(inv -> inv.getArgument(0));

        KnowledgeCandidateResponse response = versionService.classifyCandidate(5L,
            versionCreateRequest("v2.0", "这里是全新的医学文献内容"));

        KnowledgeAssetVersion saved = response.candidates().items().get(0);
        assertThat(saved).isNotNull();
        assertThat(saved.versionNo()).isEqualTo("v2.0");
        assertThat(saved.status()).isEqualTo(KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW);
        assertThat(saved.contentHash()).isNotBlank();
        assertThat(response.classifications()).singleElement()
            .satisfies(item -> assertThat(item.classification()).isEqualTo(CandidateClassificationType.NEW_ASSET));
        verify(versionRepo, times(1)).save(any(KnowledgeAssetVersion.class));
        verify(reviewAssignmentRepo, times(1)).save(any(ReviewAssignment.class));
    }

    @Test
    void classifyCandidateDeduplicatesContentHashWithoutReviewAssignment() {
        KnowledgeIdentity identity = new KnowledgeIdentity(
            5L, "t-1", "DRUG.A", KnowledgeDomain.DRUG, "骨关节炎临床规则", null, null,
            KnowledgeIdentityStatus.ACTIVE, null, Instant.now(), "system", Instant.now(), "system"
        );

        // 计算 "这里是完全重复的历史医学内容" 的哈希
        String computedHash = sha256("这里是完全重复的历史医学内容");
        KnowledgeAssetVersion historyVersion = new KnowledgeAssetVersion(
            12L, "t-1", 5L, "v1.0", "旧标签", null, null, computedHash, "anchors",
            KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.MEDIUM,
            SourceAuthorityLevel.B_GUIDELINE, GradeEvidenceQuality.MODERATE, GradeRecommendationStrength.WEAK, null,
            "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE,
            KnowledgeAssetVersion.activeScopeKey(5L, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE),
            null, null, null, null, null, null, null, null,
            Instant.now(), "system", Instant.now(), "system"
        , 12, null);

        when(identityRepo.findByTenantIdAndId("t-1", 5L)).thenReturn(Optional.of(identity));
        when(sourceDocRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(sourceDocument(1L)));
        when(versionRepo.findByTenantIdAndIdentityIdOrderByCreatedAtDesc("t-1", 5L)).thenReturn(List.of(historyVersion));
        when(versionRepo.findByTenantIdAndIdentityIdAndContentHash("t-1", 5L, computedHash))
            .thenReturn(Optional.of(historyVersion));
        when(versionRepo.findFirstByTenantIdAndIdentityIdAndStatusOrderByCreatedAtDescIdDesc(
            "t-1", 5L, KnowledgeVersionStatus.ACTIVE)).thenReturn(Optional.of(historyVersion));
        when(candidateClassificationRepo.save(any(CandidateClassification.class))).thenAnswer(inv -> inv.getArgument(0));

        KnowledgeCandidateResponse response = versionService.classifyCandidate(5L,
            versionCreateRequest("v2.0", "这里是完全重复的历史医学内容"));

        assertThat(response.reasonCode()).isEqualTo("DUPLICATE");
        assertThat(response.candidates().items()).isEmpty();
        assertThat(response.classifications()).singleElement()
            .satisfies(item -> {
                assertThat(item.reviewStatus()).isEqualTo(CandidateReviewStatus.DUPLICATE_SKIPPED);
                assertThat(item.candidateVersionId()).isNull();
            });
        verify(versionRepo, never()).save(any(KnowledgeAssetVersion.class));
        verify(versionRepo, never()).findByTenantIdAndIdentityIdOrderByCreatedAtDesc(any(), any());
        verify(reviewAssignmentRepo, never()).save(any(ReviewAssignment.class));
    }

    private KnowledgeVersionCreateRequest versionCreateRequest(String versionNo, String content) {
        return new KnowledgeVersionCreateRequest(
            "req-1", "trace-1", "t-1", null, "h-1", null, null, "d-1", "CARD",
            "u-admin", List.of("knowledge.write"),
            versionNo, "测试标签", 1L, 2L, content, "anchors", KnowledgeRiskLevel.MEDIUM,
            GradeEvidenceQuality.MODERATE, GradeRecommendationStrength.WEAK, 12
        );
    }

    private SourceDocument sourceDocument(Long id) {
        Instant now = Instant.now();
        return new SourceDocument(
            id, "t-1", "SRC." + id, SourceType.GUIDELINE, SourceAuthorityLevel.B_GUIDELINE,
            "国家级指南发布机构与版本号可追溯",
            "来源文件", "发布机构", "LICENSE", "zh-CN", now, "system", now, "system"
        );
    }

    private String sha256(String text) {
        if (text == null) {
            return "";
        }
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
