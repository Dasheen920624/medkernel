package com.medkernel.engine.knowledge;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;

import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 知识身份业务服务。
 *
 * <p>覆盖详细规范 §1797-1806：
 * <ul>
 *   <li>列表（分页 + 域/专科/状态/关键词筛选）</li>
 *   <li>详情（按 id / identity_code）</li>
 *   <li>活跃版本快捷查询</li>
 *   <li>历史 lineage（身份 + supersession 链 + 所有版本，按时间排序）</li>
 * </ul>
 *
 * <p>所有方法不接受 tenantId 入参，统一从 {@link RequestContext} 抽取。
 */
@Service
public class KnowledgeIdentityService {

    private final KnowledgeIdentityRepository identityRepository;
    private final KnowledgeAssetVersionRepository versionRepository;
    private final KnowledgeSupersessionRepository supersessionRepository;
    private final SourceDocumentRepository sourceDocumentRepository;
    private final SourceVersionRepository sourceVersionRepository;
    private final SourceFragmentRepository sourceFragmentRepository;

    public KnowledgeIdentityService(KnowledgeIdentityRepository identityRepository,
                                    KnowledgeAssetVersionRepository versionRepository,
                                    KnowledgeSupersessionRepository supersessionRepository,
                                    SourceDocumentRepository sourceDocumentRepository,
                                    SourceVersionRepository sourceVersionRepository,
                                    SourceFragmentRepository sourceFragmentRepository) {
        this.identityRepository = identityRepository;
        this.versionRepository = versionRepository;
        this.supersessionRepository = supersessionRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.sourceVersionRepository = sourceVersionRepository;
        this.sourceFragmentRepository = sourceFragmentRepository;
    }

    public PageResponse<KnowledgeIdentity> page(PageRequest request, KnowledgeIdentityFilter filter) {
        String tenantId = requireCurrentTenant();
        int offset = request.offset();
        int size = request.safeSize();

        String domain = filter.domain() == null ? null : filter.domain().name();
        String status = filter.status() == null ? null : filter.status().name();
        String keyword = normalizeKeyword(filter.keyword());

        long total = identityRepository.countByFilter(tenantId, domain, filter.specialtyId(), status, keyword);
        if (total == 0) {
            return PageResponse.empty(request);
        }
        List<KnowledgeIdentity> items = identityRepository.pageByFilter(
            tenantId, domain, filter.specialtyId(), status, keyword, offset, size);
        return PageResponse.of(items, request, total);
    }

    public KnowledgeIdentity get(Long id) {
        String tenantId = requireCurrentTenant();
        return identityRepository.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> ApiException.notFound("知识身份 id=" + id));
    }

    public KnowledgeIdentity getByCode(String identityCode) {
        String tenantId = requireCurrentTenant();
        return identityRepository.findByTenantIdAndIdentityCode(tenantId, identityCode)
            .orElseThrow(() -> ApiException.notFound("知识身份 code=" + identityCode));
    }

    public KnowledgeAssetVersion getActiveVersion(Long identityId) {
        String tenantId = requireCurrentTenant();
        // 先校验身份存在 + 同租户
        identityRepository.findByTenantIdAndId(tenantId, identityId)
            .orElseThrow(() -> ApiException.notFound("知识身份 id=" + identityId));
        return versionRepository.findActiveByIdentity(tenantId, identityId)
            .orElseThrow(() -> ApiException.notFound("知识身份 id=" + identityId + " 当前无 ACTIVE 版本"));
    }

    public KnowledgeLineage getLineage(Long identityId) {
        String tenantId = requireCurrentTenant();
        KnowledgeIdentity identity = identityRepository.findByTenantIdAndId(tenantId, identityId)
            .orElseThrow(() -> ApiException.notFound("知识身份 id=" + identityId));
        List<KnowledgeAssetVersion> versions = versionRepository.listByIdentity(tenantId, identityId);
        List<KnowledgeSupersession> supersessions =
            supersessionRepository.findByTenantIdAndIdentityIdOrderByTransitionedAtAsc(tenantId, identityId);
        return new KnowledgeLineage(identity, versions, supersessions);
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private String normalizeKeyword(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().toLowerCase();
        if (trimmed.isEmpty()) {
            return null;
        }
        // SQL LIKE：包裹 %
        return "%" + trimmed + "%";
    }

    /**
     * 注册或返回已存在的来源文献。
     *
     * @param request 来源文献注册请求
     * @return 来源文献实体
     */
    public SourceDocument registerSource(SourceRegisterRequest request) {
        String tenantId = requireCurrentTenant();
        return sourceDocumentRepository.findByTenantIdAndSourceCode(tenantId, request.sourceCode())
            .orElseGet(() -> {
                SourceDocument doc = new SourceDocument(
                    null,
                    tenantId,
                    request.sourceCode(),
                    request.sourceType(),
                    request.authorityLevel(),
                    request.title(),
                    request.publisher(),
                    request.license(),
                    request.language() == null || request.language().isBlank() ? "zh-CN" : request.language(),
                    Instant.now(),
                    currentActor(),
                    Instant.now(),
                    currentActor()
                );
                return sourceDocumentRepository.save(doc);
            });
    }

    /**
     * 注册来源文献版本。
     *
     * @param request 来源版本注册请求
     * @return 来源版本实体
     */
    public SourceVersion registerSourceVersion(SourceVersionRegisterRequest request) {
        String tenantId = requireCurrentTenant();
        sourceDocumentRepository.findByTenantIdAndId(tenantId, request.sourceDocumentId())
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_KNOW_001, "来源文献不存在 id=" + request.sourceDocumentId()));

        Optional<SourceVersion> existingOpt = sourceVersionRepository.findBySourceDocumentIdAndVersionNo(request.sourceDocumentId(), request.versionNo());
        if (existingOpt.isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "同来源文献下的版本 " + request.versionNo() + " 已存在");
        }

        String hash = requireSourceContentHash(request.contentHash());

        SourceVersion version = new SourceVersion(
            null,
            tenantId,
            request.sourceDocumentId(),
            request.versionNo(),
            request.publishedAt(),
            hash,
            request.fileUri(),
            request.language() == null || request.language().isBlank() ? "zh-CN" : request.language(),
            Instant.now(),
            currentActor()
        );
        return sourceVersionRepository.save(version);
    }

    /**
     * 注册文献片段，计算片段内 textExcerpt 的 SHA-256 摘要哈希，作为引用锚点摘要保护。
     *
     * @param request 片段创建请求
     * @return 文献片段实体
     */
    public SourceFragment createFragment(FragmentCreateRequest request) {
        String tenantId = requireCurrentTenant();
        sourceVersionRepository.findByTenantIdAndId(tenantId, request.sourceVersionId())
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_KNOW_001, "来源文献版本不存在 id=" + request.sourceVersionId()));

        Optional<SourceFragment> existingOpt = sourceFragmentRepository.findBySourceVersionIdAndAnchorPath(request.sourceVersionId(), request.anchorPath());
        if (existingOpt.isPresent()) {
            SourceFragment existing = existingOpt.get();
            // 如果片段文本完全一致，则幂等返回，否则报错冲突
            if (existing.textExcerpt() != null && existing.textExcerpt().equals(request.textExcerpt())) {
                return existing;
            }
            throw new ApiException(ErrorCode.CONFLICT, "锚点路径 " + request.anchorPath() + " 已在当前版本下被占用");
        }

        String contentHash = sha256(request.textExcerpt());

        Optional<SourceFragment> existingHashOpt = sourceFragmentRepository.findBySourceVersionIdAndContentHash(request.sourceVersionId(), contentHash);
        if (existingHashOpt.isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "相同的文献片段内容已在当前版本中被注册，触发去重防线物理阻断");
        }

        SourceFragment fragment = new SourceFragment(
            null,
            tenantId,
            request.sourceVersionId(),
            request.anchorPath(),
            request.anchorLabel(),
            request.textExcerpt(),
            contentHash,
            Instant.now()
        );
        return sourceFragmentRepository.save(fragment);
    }

    private String currentActor() {
        return RequestContext.currentUserId()
            .filter(s -> !s.isBlank())
            .orElse("system");
    }

    private String requireSourceContentHash(String contentHash) {
        if (contentHash == null || contentHash.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "来源版本必须携带真实内容哈希，禁止用版本号或时间戳合成");
        }
        return contentHash.trim();
    }

    private String sha256(String text) {
        if (text == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
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
