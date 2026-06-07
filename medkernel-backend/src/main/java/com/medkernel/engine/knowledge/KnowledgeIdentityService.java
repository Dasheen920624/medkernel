package com.medkernel.engine.knowledge;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;
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
    private final CitationRepository citationRepository;

    public KnowledgeIdentityService(KnowledgeIdentityRepository identityRepository,
                                    KnowledgeAssetVersionRepository versionRepository,
                                    KnowledgeSupersessionRepository supersessionRepository,
                                    SourceDocumentRepository sourceDocumentRepository,
                                    SourceVersionRepository sourceVersionRepository,
                                    SourceFragmentRepository sourceFragmentRepository,
                                    CitationRepository citationRepository) {
        this.identityRepository = identityRepository;
        this.versionRepository = versionRepository;
        this.supersessionRepository = supersessionRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.sourceVersionRepository = sourceVersionRepository;
        this.sourceFragmentRepository = sourceFragmentRepository;
        this.citationRepository = citationRepository;
    }

    public PageResponse<KnowledgeIdentity> page(PageRequest request, KnowledgeIdentityFilter filter) {
        String tenantId = requireCurrentTenant();
        int offset = request.offset();
        int size = request.safeSize();

        String domain = filter.domain() == null ? null : filter.domain().name();
        String status = filter.status() == null ? null : filter.status().name();
        String keyword = normalizeKeyword(filter.keyword());

        List<KnowledgeIdentity> effectiveRows =
            effectiveIdentitiesByFilter(tenantId, domain, filter.specialtyId(), status, keyword);
        long total = effectiveRows.size();
        List<KnowledgeIdentity> items = slice(effectiveRows, offset, size);
        return PageResponse.of(items, request, total);
    }

    public KnowledgeIdentity get(Long id) {
        String tenantId = requireCurrentTenant();
        return findEffectiveIdentity(id, tenantId).identity();
    }

    public KnowledgeIdentity getByCode(String identityCode) {
        String tenantId = requireCurrentTenant();
        return identityRepository.findByTenantIdAndIdentityCode(tenantId, identityCode)
            .or(() -> findPlatformIdentityByCodeForTenant(identityCode, tenantId))
            .orElseThrow(() -> ApiException.notFound("知识身份 code=" + identityCode));
    }

    public KnowledgeAssetVersion getActiveVersion(Long identityId) {
        String tenantId = requireCurrentTenant();
        EffectiveKnowledgeIdentity effective = findEffectiveIdentity(identityId, tenantId);
        return findDefaultActiveVersion(effective)
            .orElseThrow(() -> ApiException.notFound("知识身份 id=" + identityId + " 当前无 ACTIVE 版本"));
    }

    public KnowledgeLineage getLineage(Long identityId) {
        String tenantId = requireCurrentTenant();
        EffectiveKnowledgeIdentity effective = findEffectiveIdentity(identityId, tenantId);
        KnowledgeIdentity identity = effective.identity();
        List<KnowledgeAssetVersion> versions = versionRepository.listByIdentity(effective.sourceTenantId(), identity.id());
        List<KnowledgeSupersession> supersessions =
            supersessionRepository.findByTenantIdAndIdentityIdOrderByTransitionedAtAsc(
                effective.sourceTenantId(), identity.id());
        return new KnowledgeLineage(identity, versions, supersessions);
    }

    /**
     * 聚合知识身份当前权威版本的逐条来源与完整版本历史。
     *
     * <p>来源引用缺少片段、来源版本或来源文件时保留未解析计数，调用方据此展示部分成功。
     */
    public KnowledgeProvenanceResponse getProvenance(Long identityId) {
        String tenantId = requireCurrentTenant();
        EffectiveKnowledgeIdentity effective = findEffectiveIdentity(identityId, tenantId);
        KnowledgeIdentity identity = effective.identity();
        List<KnowledgeAssetVersion> versions =
            versionRepository.listByIdentity(effective.sourceTenantId(), identity.id());
        List<KnowledgeSupersession> supersessions =
            supersessionRepository.findByTenantIdAndIdentityIdOrderByTransitionedAtAsc(
                effective.sourceTenantId(), identity.id());
        SourceEvidenceResolution sourceEvidence = resolveSourceEvidence(effective);
        return new KnowledgeProvenanceResponse(
            identity,
            identity.currentVersionId(),
            versions,
            supersessions,
            sourceEvidence.items(),
            sourceEvidence.unresolvedCitationCount(),
            sourceEvidence.unresolvedCitationCount() > 0
        );
    }

    private EffectiveKnowledgeIdentity findEffectiveIdentity(Long identityId, String tenantId) {
        Optional<KnowledgeIdentity> local = identityRepository.findByTenantIdAndId(tenantId, identityId);
        if (local.isPresent()) {
            return new EffectiveKnowledgeIdentity(local.get(), tenantId);
        }
        if (PlatformTenant.isPlatformTenant(tenantId)) {
            throw ApiException.notFound("知识身份 id=" + identityId);
        }
        KnowledgeIdentity platform = identityRepository.findByTenantIdAndId(PlatformTenant.ID, identityId)
            .orElseThrow(() -> ApiException.notFound("知识身份 id=" + identityId));
        return identityRepository.findByTenantIdAndIdentityCode(tenantId, platform.identityCode())
            .map(override -> new EffectiveKnowledgeIdentity(override, tenantId))
            .orElseGet(() -> new EffectiveKnowledgeIdentity(platform, PlatformTenant.ID));
    }

    private Optional<KnowledgeIdentity> findPlatformIdentityByCodeForTenant(String identityCode, String tenantId) {
        if (PlatformTenant.isPlatformTenant(tenantId)) {
            return Optional.empty();
        }
        return identityRepository.findByTenantIdAndIdentityCode(PlatformTenant.ID, identityCode);
    }

    private List<KnowledgeIdentity> effectiveIdentitiesByFilter(String tenantId, String domain,
                                                                String specialtyId, String status, String keyword) {
        LinkedHashMap<String, KnowledgeIdentity> byCode = new LinkedHashMap<>();
        identityRepository.listByFilter(tenantId, domain, specialtyId, status, keyword)
            .forEach(identity -> byCode.put(identity.identityCode(), identity));
        if (!PlatformTenant.isPlatformTenant(tenantId)) {
            String platformStatus = status == null ? KnowledgeIdentityStatus.ACTIVE.name() : status;
            if (KnowledgeIdentityStatus.ACTIVE.name().equals(platformStatus)) {
                identityRepository.listByFilter(PlatformTenant.ID, domain, specialtyId, platformStatus, keyword)
                    .forEach(identity -> byCode.putIfAbsent(identity.identityCode(), identity));
            }
        }
        return List.copyOf(byCode.values());
    }

    private List<KnowledgeIdentity> slice(List<KnowledgeIdentity> rows, int offset, int limit) {
        if (rows.isEmpty() || offset >= rows.size()) {
            return List.of();
        }
        int end = Math.min(rows.size(), offset + limit);
        return rows.subList(offset, end);
    }

    private record EffectiveKnowledgeIdentity(KnowledgeIdentity identity, String sourceTenantId) {
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
     * 创建知识身份。current_version_id 只能由版本激活流程维护，创建时保持为空。
     *
     * @param request 创建请求
     * @return 已创建的知识身份
     */
    public KnowledgeIdentity createIdentity(KnowledgeIdentityCreateRequest request) {
        String tenantId = requireCurrentTenant();
        validateContext(request.context(), tenantId);
        String identityCode = request.identityCode().trim();
        identityRepository.findByTenantIdAndIdentityCode(tenantId, identityCode)
            .ifPresent(existing -> {
                throw new ApiException(ErrorCode.CONFLICT, "知识身份编码已存在: " + identityCode);
            });
        Instant now = Instant.now();
        KnowledgeIdentity identity = new KnowledgeIdentity(
            null,
            tenantId,
            identityCode,
            request.domain(),
            request.subject().trim(),
            blankToNull(request.assetSpecialtyId()),
            blankToNull(request.description()),
            KnowledgeIdentityStatus.ACTIVE,
            null,
            now,
            currentActor(),
            now,
            currentActor()
        );
        return identityRepository.save(identity);
    }

    /**
     * API-03 标准入口：登记来源文献并校验统一上下文。
     */
    public SourceDocument registerSource(KnowledgeSourceCreateRequest request) {
        String tenantId = requireCurrentTenant();
        validateContext(request.context(), tenantId);
        return registerSource(request.toSourceRegisterRequest());
    }

    /**
     * API-03 标准入口：在路径指定的来源文献下登记版本。
     */
    public SourceVersion registerSourceVersion(Long sourceDocumentId, KnowledgeSourceVersionCreateRequest request) {
        String tenantId = requireCurrentTenant();
        validateContext(request.context(), tenantId);
        return registerSourceVersion(request.toSourceVersionRegisterRequest(sourceDocumentId));
    }

    /**
     * 查询当前权威版本的真实引用锚点。无 ACTIVE 版本时返回空列表，不伪造来源证据。
     */
    public List<Citation> listCitations(Long identityId) {
        String tenantId = requireCurrentTenant();
        EffectiveKnowledgeIdentity effective = findEffectiveIdentity(identityId, tenantId);
        Optional<KnowledgeAssetVersion> active = findDefaultActiveVersion(effective);
        if (active.isEmpty()) {
            return List.of();
        }
        return citationRepository.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc(
            effective.sourceTenantId(), active.get().id());
    }

    /**
     * 查询当前权威版本可展示的来源证据视图。排序规则固定为分级 > 时效 > 适用域 > 引用权重。
     */
    public List<KnowledgeSourceEvidence> listSourceEvidence(Long identityId) {
        String tenantId = requireCurrentTenant();
        EffectiveKnowledgeIdentity effective = findEffectiveIdentity(identityId, tenantId);
        return resolveSourceEvidence(effective).items();
    }

    private SourceEvidenceResolution resolveSourceEvidence(EffectiveKnowledgeIdentity effective) {
        Optional<KnowledgeAssetVersion> activeOpt = findDefaultActiveVersion(effective);
        if (activeOpt.isEmpty()) {
            return new SourceEvidenceResolution(List.of(), 0);
        }
        KnowledgeAssetVersion active = activeOpt.get();
        List<Citation> citations = citationRepository.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc(
            effective.sourceTenantId(), active.id());
        List<SourceEvidenceDraft> drafts = new ArrayList<>();
        int unresolvedCitationCount = 0;
        for (Citation citation : citations) {
            Optional<SourceEvidenceDraft> resolved =
                resolveSourceEvidenceItem(effective.sourceTenantId(), active, citation);
            if (resolved.isPresent()) {
                drafts.add(resolved.get());
            } else {
                unresolvedCitationCount++;
            }
        }
        drafts.sort(this::compareSourceEvidence);
        int primaryIndex = primaryIndex(drafts);
        List<KnowledgeSourceEvidence> evidence = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            evidence.add(toSourceEvidence(drafts.get(i), i == primaryIndex));
        }
        return new SourceEvidenceResolution(List.copyOf(evidence), unresolvedCitationCount);
    }

    private Optional<KnowledgeAssetVersion> findDefaultActiveVersion(EffectiveKnowledgeIdentity effective) {
        KnowledgeIdentity identity = effective.identity();
        if (identity.currentVersionId() == null) {
            return Optional.empty();
        }
        return versionRepository.findByTenantIdAndId(effective.sourceTenantId(), identity.currentVersionId())
            .filter(KnowledgeAssetVersion::isAuthoritative);
    }

    private Optional<SourceEvidenceDraft> resolveSourceEvidenceItem(String tenantId, KnowledgeAssetVersion active,
            Citation citation) {
        Optional<SourceFragment> fragmentOpt =
            sourceFragmentRepository.findByTenantIdAndId(tenantId, citation.sourceFragmentId());
        if (fragmentOpt.isEmpty()) {
            return Optional.empty();
        }
        SourceFragment fragment = fragmentOpt.get();
        Optional<SourceVersion> sourceVersionOpt =
            sourceVersionRepository.findByTenantIdAndId(tenantId, fragment.sourceVersionId());
        if (sourceVersionOpt.isEmpty()) {
            return Optional.empty();
        }
        SourceVersion sourceVersion = sourceVersionOpt.get();
        Optional<SourceDocument> sourceDocumentOpt =
            sourceDocumentRepository.findByTenantIdAndId(tenantId, sourceVersion.sourceDocumentId());
        return sourceDocumentOpt.map(sourceDocument ->
            new SourceEvidenceDraft(active, citation, fragment, sourceVersion, sourceDocument));
    }

    private int compareSourceEvidence(SourceEvidenceDraft left, SourceEvidenceDraft right) {
        int authority = Integer.compare(
            SourceEvidencePriority.authorityRank(left.sourceDocument().authorityLevel()),
            SourceEvidencePriority.authorityRank(right.sourceDocument().authorityLevel()));
        if (authority != 0) {
            return authority;
        }
        int recency = -SourceEvidencePriority.compareRecency(left.sourceVersion().publishedAt(), right.sourceVersion().publishedAt());
        if (recency != 0) {
            return recency;
        }
        int scope = Integer.compare(
            SourceEvidencePriority.scopeSpecificity(right.activeVersion()),
            SourceEvidencePriority.scopeSpecificity(left.activeVersion()));
        if (scope != 0) {
            return scope;
        }
        int weight = Integer.compare(weight(right.citation()), weight(left.citation()));
        if (weight != 0) {
            return weight;
        }
        return Comparator.nullsLast(Long::compareTo).compare(left.citation().id(), right.citation().id());
    }

    private int primaryIndex(List<SourceEvidenceDraft> drafts) {
        for (int i = 0; i < drafts.size(); i++) {
            SourceAuthorityLevel level = drafts.get(i).sourceDocument().authorityLevel();
            if (level != null && !level.isLowAuthority()) {
                return i;
            }
        }
        return -1;
    }

    private KnowledgeSourceEvidence toSourceEvidence(SourceEvidenceDraft draft, boolean primary) {
        SourceDocument source = draft.sourceDocument();
        SourceVersion version = draft.sourceVersion();
        Citation citation = draft.citation();
        KnowledgeAssetVersion active = draft.activeVersion();
        SourceAuthorityLevel authority = source.authorityLevel();
        String authorityLabel = authority == null ? "未知分级" : authority.label();
        KnowledgeSourceEvidenceRole role = primary
            ? KnowledgeSourceEvidenceRole.PRIMARY
            : KnowledgeSourceEvidenceRole.SUPPLEMENTARY;
        return new KnowledgeSourceEvidence(
            active.id(),
            citation.id(),
            draft.fragment().id(),
            source.id(),
            version.id(),
            source.sourceCode(),
            source.title(),
            source.sourceType(),
            authority,
            authorityLabel,
            source.authorityBasis(),
            version.versionNo(),
            version.contentHash(),
            draft.fragment().anchorPath(),
            draft.fragment().anchorLabel(),
            draft.fragment().textExcerpt(),
            draft.fragment().contentHash(),
            citation.startOffset(),
            citation.endOffset(),
            active.gradeQuality(),
            active.gradeStrength(),
            version.publishedAt(),
            citation.relation(),
            citation.weight(),
            active.effectiveOrganizationScope(),
            active.effectiveApplicableScope(),
            role,
            primary,
            !primary,
            authorityLabel + (primary ? " · 主证据" : " · 补充证据"),
            rankingReason(authorityLabel, version.publishedAt(), active, citation),
            active.conflictArbitration()
        );
    }

    private String rankingReason(String authorityLabel, Instant publishedAt, KnowledgeAssetVersion active,
            Citation citation) {
        return "按可信分级 " + authorityLabel
            + "、来源发布时间 " + SourceEvidencePriority.evidenceDate(publishedAt)
            + "、适用域 " + active.effectiveOrganizationScope() + "/" + active.effectiveApplicableScope()
            + "、引用权重 " + weight(citation) + " 排序";
    }

    private int weight(Citation citation) {
        return citation.weight() == null ? 0 : citation.weight();
    }

    private record SourceEvidenceDraft(
        KnowledgeAssetVersion activeVersion,
        Citation citation,
        SourceFragment fragment,
        SourceVersion sourceVersion,
        SourceDocument sourceDocument
    ) {
    }

    private record SourceEvidenceResolution(
        List<KnowledgeSourceEvidence> items,
        int unresolvedCitationCount
    ) {
    }

    /**
     * 注册或返回已存在的来源文献。
     *
     * @param request 来源文献注册请求
     * @return 来源文献实体
     */
    public SourceDocument registerSource(SourceRegisterRequest request) {
        String tenantId = requireCurrentTenant();
        String authorityBasis = requireAuthorityBasis(request.authorityBasis());
        return sourceDocumentRepository.findByTenantIdAndSourceCode(tenantId, request.sourceCode())
            .orElseGet(() -> {
                SourceDocument doc = new SourceDocument(
                    null,
                    tenantId,
                    request.sourceCode(),
                    request.sourceType(),
                    request.authorityLevel(),
                    authorityBasis,
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

        String hash = ContentHash.resolve(request.content(), request.contentHash());
        Optional<SourceVersion> existingOpt = sourceVersionRepository.findBySourceDocumentIdAndVersionNo(request.sourceDocumentId(), request.versionNo());
        if (existingOpt.isPresent()) {
            SourceVersion existing = existingOpt.get();
            if (existing.contentHash() != null && existing.contentHash().equalsIgnoreCase(hash)) {
                return existing;
            }
            throw new ApiException(ErrorCode.CONFLICT, "同来源文献下的版本 " + request.versionNo() + " 已存在且内容指纹不一致");
        }

        Optional<SourceVersion> existingHashOpt =
            sourceVersionRepository.findBySourceDocumentIdAndContentHash(request.sourceDocumentId(), hash);
        if (existingHashOpt.isPresent()) {
            return existingHashOpt.get();
        }

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

        String contentHash = ContentHash.sha256(request.textExcerpt());

        Optional<SourceFragment> existingHashOpt = sourceFragmentRepository.findBySourceVersionIdAndContentHash(request.sourceVersionId(), contentHash);
        if (existingHashOpt.isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "相同的文献片段内容已在当前版本中被注册，触发去重防线阻断");
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

    /**
     * 创建知识版本到来源片段的结构化引用；同一版本、片段和关系重复提交时幂等返回。
     */
    public Citation createCitation(CitationCreateRequest request) {
        String tenantId = requireCurrentTenant();
        KnowledgeAssetVersion version = versionRepository.findByTenantIdAndId(tenantId, request.assetVersionId())
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_KNOW_001, "知识资产版本不存在 id=" + request.assetVersionId()));
        SourceFragment fragment = sourceFragmentRepository.findByTenantIdAndId(
                tenantId, request.sourceFragmentId())
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_KNOW_001, "来源片段不存在 id=" + request.sourceFragmentId()));

        if (version.sourceVersionId() == null
                || !version.sourceVersionId().equals(fragment.sourceVersionId())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                "来源片段不属于知识版本登记的来源版本");
        }
        validateCitationOffsets(request, fragment);

        return citationRepository
            .findByTenantIdAndAssetVersionIdAndSourceFragmentIdAndRelation(
                tenantId, version.id(), fragment.id(), request.relation())
            .orElseGet(() -> citationRepository.save(new Citation(
                null,
                tenantId,
                version.id(),
                fragment.id(),
                request.relation(),
                request.weight(),
                request.startOffset(),
                request.endOffset(),
                Instant.now(),
                currentActor()
            )));
    }

    private void validateCitationOffsets(CitationCreateRequest request, SourceFragment fragment) {
        Integer start = request.startOffset();
        Integer end = request.endOffset();
        if ((start == null) != (end == null)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "引用起止偏移必须同时填写或同时省略");
        }
        if (start == null) {
            return;
        }
        int textLength = fragment.textExcerpt() == null ? 0 : fragment.textExcerpt().length();
        if (start > end || end > textLength) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                "引用偏移无效，必须满足 0 <= startOffset <= endOffset <= 来源片段长度");
        }
    }

    private String requireAuthorityBasis(String authorityBasis) {
        if (authorityBasis == null || authorityBasis.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "来源可信分级必须填写判定依据");
        }
        return authorityBasis.trim();
    }

    private String currentActor() {
        return RequestContext.currentUserId()
            .filter(s -> !s.isBlank())
            .orElse("system");
    }

    private void validateContext(KnowledgeApiContext context, String tenantId) {
        if (context == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "标准知识资产 API 缺少统一入参字段");
        }
        context.validateTenant(tenantId);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
