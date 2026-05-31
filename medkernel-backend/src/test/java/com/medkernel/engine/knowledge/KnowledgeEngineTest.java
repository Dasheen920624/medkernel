package com.medkernel.engine.knowledge;

import java.time.Instant;
import java.util.Collections;
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
 *   <li>待审草稿版本创建（初始为 UNDER_REVIEW）</li>
 *   <li>基于内容哈希 SHA-256 指纹的历史版本碰撞物理去重（ENG-KNOW-002）</li>
 * </ul>
 */
class KnowledgeEngineTest {

    private KnowledgeIdentityRepository identityRepo;
    private KnowledgeAssetVersionRepository versionRepo;
    private KnowledgeSupersessionRepository supersessionRepo;
    private SourceDocumentRepository sourceDocRepo;
    private SourceVersionRepository sourceVerRepo;
    private SourceFragmentRepository sourceFragRepo;

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

        identityService = new KnowledgeIdentityService(
            identityRepo, versionRepo, supersessionRepo, sourceDocRepo, sourceVerRepo, sourceFragRepo
        );

        versionService = new KnowledgeVersionService(
            identityRepo, versionRepo, supersessionRepo
        );

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
            "doc-code", SourceType.GUIDELINE, SourceAuthorityLevel.CHINA_NATIONAL,
            "中华骨科指南", "中华医学会", "MIT", "zh-CN"
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
            1L, "t-1", "doc-code", SourceType.GUIDELINE, SourceAuthorityLevel.CHINA_NATIONAL,
            "中华骨科指南", "中华医学会", "MIT", "zh-CN",
            Instant.now(), "system", Instant.now(), "system"
        );

        SourceRegisterRequest req = new SourceRegisterRequest(
            "doc-code", SourceType.GUIDELINE, SourceAuthorityLevel.CHINA_NATIONAL,
            "中华骨科指南", "中华医学会", "MIT", "zh-CN"
        );

        when(sourceDocRepo.findByTenantIdAndSourceCode("t-1", "doc-code")).thenReturn(Optional.of(existing));

        SourceDocument result = identityService.registerSource(req);

        assertThat(result).isEqualTo(existing);
        verify(sourceDocRepo, never()).save(any(SourceDocument.class));
    }

    @Test
    void registerSourceVersionSavesVersionWhenNotExists() {
        SourceVersionRegisterRequest req = new SourceVersionRegisterRequest(
            1L, "v1.0", Instant.now(), "my-content-hash", "http://file", "zh-CN"
        );

        SourceDocument doc = new SourceDocument(
            1L, "t-1", "doc-code", SourceType.GUIDELINE, SourceAuthorityLevel.CHINA_NATIONAL,
            "中华骨科指南", "中华医学会", "MIT", "zh-CN",
            Instant.now(), "system", Instant.now(), "system"
        );

        when(sourceDocRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(doc));
        when(sourceVerRepo.findBySourceDocumentIdAndVersionNo(1L, "v1.0")).thenReturn(Optional.empty());

        SourceVersion saved = identityService.registerSourceVersion(req);

        assertThat(saved).isNotNull();
        assertThat(saved.versionNo()).isEqualTo("v1.0");
        assertThat(saved.contentHash()).isEqualTo("my-content-hash");
        verify(sourceVerRepo, times(1)).save(any(SourceVersion.class));
    }

    @Test
    void registerSourceVersionRejectsIfDocumentDoesNotExist() {
        SourceVersionRegisterRequest req = new SourceVersionRegisterRequest(
            999L, "v1.0", Instant.now(), "hash", "http://file", "zh-CN"
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
            10L, "t-1", 1L, "v1.0", Instant.now(), "hash", "http", "zh-CN", Instant.now(), "system"
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
            10L, "t-1", 1L, "v1.0", Instant.now(), "hash", "http", "zh-CN", Instant.now(), "system"
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
            10L, "t-1", 1L, "v1.0", Instant.now(), "hash", "http", "zh-CN", Instant.now(), "system"
        );

        when(sourceVerRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(version));
        when(sourceFragRepo.findBySourceVersionIdAndAnchorPath(10L, "sec-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> identityService.createFragment(req))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void createDraftVersionSavesSuccessfully() {
        DraftVersionCreateRequest req = new DraftVersionCreateRequest(
            5L, "v2.0", "测试标签", null, null, "这里是全新的医学文献内容", "anchors", KnowledgeRiskLevel.MEDIUM
        );

        KnowledgeIdentity identity = new KnowledgeIdentity(
            5L, "t-1", "DRUG.A", KnowledgeDomain.DRUG, "骨关节炎临床规则", null, null,
            KnowledgeIdentityStatus.ACTIVE, null, Instant.now(), "system", Instant.now(), "system"
        );

        when(identityRepo.findByTenantIdAndId("t-1", 5L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndIdentityIdOrderByCreatedAtDesc("t-1", 5L)).thenReturn(Collections.emptyList());

        KnowledgeAssetVersion saved = versionService.createDraftVersion(req);

        assertThat(saved).isNotNull();
        assertThat(saved.versionNo()).isEqualTo("v2.0");
        assertThat(saved.status()).isEqualTo(KnowledgeVersionStatus.UNDER_REVIEW);
        assertThat(saved.contentHash()).isNotBlank();
        verify(versionRepo, times(1)).save(any(KnowledgeAssetVersion.class));
    }

    @Test
    void createDraftVersionRejectsDueToContentHashCollision() {
        DraftVersionCreateRequest req = new DraftVersionCreateRequest(
            5L, "v2.0", "测试标签", null, null, "这里是完全重复的历史医学内容", "anchors", KnowledgeRiskLevel.MEDIUM
        );

        KnowledgeIdentity identity = new KnowledgeIdentity(
            5L, "t-1", "DRUG.A", KnowledgeDomain.DRUG, "骨关节炎临床规则", null, null,
            KnowledgeIdentityStatus.ACTIVE, null, Instant.now(), "system", Instant.now(), "system"
        );

        // 计算 "这里是完全重复的历史医学内容" 的哈希
        String computedHash = sha256("这里是完全重复的历史医学内容");
        KnowledgeAssetVersion historyVersion = new KnowledgeAssetVersion(
            12L, "t-1", 5L, "v1.0", "旧标签", null, null, computedHash, "anchors",
            KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.MEDIUM, null, null, null, null, null, null, null, null,
            Instant.now(), "system", Instant.now(), "system"
        );

        when(identityRepo.findByTenantIdAndId("t-1", 5L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndIdentityIdOrderByCreatedAtDesc("t-1", 5L)).thenReturn(List.of(historyVersion));

        assertThatThrownBy(() -> versionService.createDraftVersion(req))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_KNOW_002);
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
