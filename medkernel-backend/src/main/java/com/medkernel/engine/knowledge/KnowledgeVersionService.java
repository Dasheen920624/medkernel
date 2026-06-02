package com.medkernel.engine.knowledge;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 知识版本业务服务，承载详细规范 §1.4 / §1797-1806 的核心状态机：
 *
 * <pre>
 *   {@link #activate}(identity, versionId)
 *     ├─ 悲观锁 knowledge_identity 行
 *     ├─ 当前 ACTIVE 版本（如有）→ SUPERSEDED，写 supersession (REPLACE/ACTIVATE)
 *     ├─ 目标版本（UNDER_REVIEW / CANDIDATE）→ ACTIVE
 *     ├─ knowledge_identity.current_version_id 指向新版
 *     └─ commit
 *
 *   {@link #withdraw}(identity, versionId)
 *     ├─ 悲观锁 knowledge_identity 行
 *     ├─ 目标 ACTIVE 版本 → WITHDRAWN，写 supersession (WITHDRAW)
 *     ├─ knowledge_identity.current_version_id 置 null（或回退到上一个 ACTIVE 后续 RESTORE）
 *     └─ commit
 * </pre>
 *
 * <p>关键不变量：同一 {@code identity_id} 同时刻 {@code status='ACTIVE'} 版本 ≤ 1。
 * 由 {@link KnowledgeIdentityRepository#findByTenantIdAndIdForUpdate} 悲观锁保证（5 方言通用）。
 */
@Service
public class KnowledgeVersionService {

    private final KnowledgeIdentityRepository identityRepository;
    private final KnowledgeAssetVersionRepository versionRepository;
    private final KnowledgeSupersessionRepository supersessionRepository;
    private final CitationRepository citationRepository;

    public KnowledgeVersionService(KnowledgeIdentityRepository identityRepository,
                                   KnowledgeAssetVersionRepository versionRepository,
                                   KnowledgeSupersessionRepository supersessionRepository,
                                   CitationRepository citationRepository) {
        this.identityRepository = identityRepository;
        this.versionRepository = versionRepository;
        this.supersessionRepository = supersessionRepository;
        this.citationRepository = citationRepository;
    }

    public List<KnowledgeAssetVersion> listByIdentity(Long identityId) {
        String tenantId = requireCurrentTenant();
        identityRepository.findByTenantIdAndId(tenantId, identityId)
            .orElseThrow(() -> ApiException.notFound("知识身份 id=" + identityId));
        return versionRepository.findByTenantIdAndIdentityIdOrderByCreatedAtDesc(tenantId, identityId);
    }

    public KnowledgeAssetVersion getVersion(Long versionId) {
        String tenantId = requireCurrentTenant();
        return versionRepository.findByTenantIdAndId(tenantId, versionId)
            .orElseThrow(() -> ApiException.notFound("知识版本 id=" + versionId));
    }

    /**
     * 在指定知识身份下创建待审草稿版本。
     */
    @Transactional
    public KnowledgeAssetVersion createDraftVersion(Long identityId, KnowledgeVersionCreateRequest request) {
        String tenantId = requireCurrentTenant();
        validateContext(request.context(), tenantId);
        return createDraftVersion(request.toDraftVersionCreateRequest(identityId));
    }

    /**
     * 提交版本进入审核态。已有 UNDER_REVIEW 版本幂等返回，避免重复点击造成状态冲突。
     */
    @Transactional
    public KnowledgeAssetVersion submit(Long identityId, Long versionId, KnowledgeActionRequest request) {
        String tenantId = requireCurrentTenant();
        validateContext(request.context(), tenantId);
        identityRepository.findByTenantIdAndId(tenantId, identityId)
            .orElseThrow(() -> ApiException.notFound("知识身份 id=" + identityId));
        KnowledgeAssetVersion target = versionRepository.findByTenantIdAndId(tenantId, versionId)
            .orElseThrow(() -> ApiException.notFound("知识版本 id=" + versionId));
        if (!target.identityId().equals(identityId)) {
            throw new ApiException(ErrorCode.CONFLICT, "版本 " + versionId + " 不属于身份 " + identityId);
        }
        if (target.status() == KnowledgeVersionStatus.UNDER_REVIEW) {
            return target;
        }
        if (target.status() != KnowledgeVersionStatus.DRAFT && target.status() != KnowledgeVersionStatus.CANDIDATE) {
            throw new ApiException(ErrorCode.CONFLICT, "版本当前状态 " + target.status() + " 不可提交审核");
        }
        Instant now = Instant.now();
        KnowledgeAssetVersion submitted = new KnowledgeAssetVersion(
            target.id(), target.tenantId(), target.identityId(),
            target.versionNo(), target.versionLabel(),
            target.sourceDocumentId(), target.sourceVersionId(),
            target.contentHash(), target.anchors(),
            KnowledgeVersionStatus.UNDER_REVIEW, target.riskLevel(),
            target.effectiveFrom(), target.effectiveTo(),
            target.reviewedBy(), target.reviewedAt(),
            target.activatedAt(), target.supersededAt(),
            target.withdrawnAt(), target.withdrawnReason(),
            target.createdAt(), target.createdBy(),
            now, currentActor()
        );
        return versionRepository.save(submitted);
    }

    /**
     * 历史版本重放。返回指定版本本身，显式标记 historicalVersion，绝不混入当前 ACTIVE。
     */
    public KnowledgeReplayResponse replayVersion(Long identityId, Long versionId, String packageVersion, String snapshotId) {
        String tenantId = requireCurrentTenant();
        identityRepository.findByTenantIdAndId(tenantId, identityId)
            .orElseThrow(() -> ApiException.notFound("知识身份 id=" + identityId));
        KnowledgeAssetVersion version = versionRepository.findByTenantIdAndId(tenantId, versionId)
            .orElseThrow(() -> ApiException.notFound("知识版本 id=" + versionId));
        if (!version.identityId().equals(identityId)) {
            throw new ApiException(ErrorCode.CONFLICT, "版本 " + versionId + " 不属于身份 " + identityId);
        }
        return new KnowledgeReplayResponse(
            identityId,
            version.id(),
            version.versionNo(),
            version.status(),
            true,
            packageVersion,
            snapshotId,
            version.contentHash(),
            version.anchors(),
            version.effectiveFrom(),
            version.effectiveTo()
        );
    }

    /**
     * KNOW-02 未实施候选表前，API-03 返回诚实空态。
     */
    public KnowledgeCandidateResponse listCandidates(Long identityId) {
        String tenantId = requireCurrentTenant();
        identityRepository.findByTenantIdAndId(tenantId, identityId)
            .orElseThrow(() -> ApiException.notFound("知识身份 id=" + identityId));
        return KnowledgeCandidateResponse.know02Pending(identityId);
    }

    public KnowledgeCandidateResponse reviewCandidate(Long candidateId, KnowledgeCandidateReviewRequest request) {
        String tenantId = requireCurrentTenant();
        validateContext(request.context(), tenantId);
        throw ApiException.notFound("知识候选尚未接入 KNOW-02 candidate_classification，candidateId=" + candidateId);
    }

    public KnowledgeCandidateResponse diffCandidate(Long candidateId) {
        requireCurrentTenant();
        throw ApiException.notFound("知识候选尚未接入 KNOW-02 candidate_classification，candidateId=" + candidateId);
    }

    /**
     * 审核激活：将目标版本原子地推到 ACTIVE，旧版降为 SUPERSEDED。
     *
     * @param identityId 知识身份 id
     * @param versionId  待激活的版本 id（必须属于该身份，状态为 UNDER_REVIEW 或 CANDIDATE）
     * @param reason     激活说明（高风险必填，由 Controller 层 / Bean Validation 保证）
     * @return 激活后的新版（status=ACTIVE）
     */
    @Transactional
    public KnowledgeAssetVersion activate(Long identityId, Long versionId, String reason) {
        String tenantId = requireCurrentTenant();
        String actor = currentActor();
        Instant now = Instant.now();

        // 1) 悲观锁定身份行 — 序列化同一 identity 的所有 activate / withdraw
        KnowledgeIdentity identity = identityRepository.findByTenantIdAndIdForUpdate(tenantId, identityId)
            .orElseThrow(() -> ApiException.notFound("知识身份 id=" + identityId));
        if (identity.status() != KnowledgeIdentityStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONFLICT, "知识身份已 " + identity.status() + "，不能激活新版");
        }

        // 2) 取目标版本，校验状态
        KnowledgeAssetVersion target = versionRepository.findByTenantIdAndId(tenantId, versionId)
            .orElseThrow(() -> ApiException.notFound("知识版本 id=" + versionId));
        if (!target.identityId().equals(identityId)) {
            throw new ApiException(ErrorCode.CONFLICT, "版本 " + versionId + " 不属于身份 " + identityId);
        }
        if (target.status() == null || !target.status().isActivatable()) {
            throw new ApiException(ErrorCode.CONFLICT,
                "版本当前状态 " + target.status() + " 不可激活（需 UNDER_REVIEW 或 CANDIDATE）");
        }
        if (target.isHighRisk() && (reason == null || reason.isBlank())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "高风险版本激活必须填写说明");
        }
        requireCitation(tenantId, target.id());

        // 3) 当前 ACTIVE 版本（如有）→ SUPERSEDED
        Optional<KnowledgeAssetVersion> currentActiveOpt = versionRepository.findActiveByIdentity(tenantId, identityId);
        Long oldVersionId = null;
        SupersessionType transitionType = SupersessionType.ACTIVATE;
        if (currentActiveOpt.isPresent()) {
            KnowledgeAssetVersion oldActive = currentActiveOpt.get();
            oldVersionId = oldActive.id();
            transitionType = SupersessionType.REPLACE;
            KnowledgeAssetVersion superseded = new KnowledgeAssetVersion(
                oldActive.id(), oldActive.tenantId(), oldActive.identityId(),
                oldActive.versionNo(), oldActive.versionLabel(),
                oldActive.sourceDocumentId(), oldActive.sourceVersionId(),
                oldActive.contentHash(), oldActive.anchors(),
                KnowledgeVersionStatus.SUPERSEDED, oldActive.riskLevel(),
                oldActive.effectiveFrom(), now /* effective_to = activate 时刻 */,
                oldActive.reviewedBy(), oldActive.reviewedAt(),
                oldActive.activatedAt(), now /* superseded_at */,
                oldActive.withdrawnAt(), oldActive.withdrawnReason(),
                oldActive.createdAt(), oldActive.createdBy(),
                now, actor
            );
            versionRepository.save(superseded);
        }

        // 4) 目标版本 → ACTIVE
        KnowledgeAssetVersion activated = new KnowledgeAssetVersion(
            target.id(), target.tenantId(), target.identityId(),
            target.versionNo(), target.versionLabel(),
            target.sourceDocumentId(), target.sourceVersionId(),
            target.contentHash(), target.anchors(),
            KnowledgeVersionStatus.ACTIVE, target.riskLevel(),
            now /* effective_from = 激活时刻 */, null /* effective_to 由后续 supersede 写 */,
            actor, now /* reviewed_at */,
            now /* activated_at */, null, null, null,
            target.createdAt(), target.createdBy(),
            now, actor
        );
        KnowledgeAssetVersion saved = versionRepository.save(activated);

        // 5) 身份 current_version_id 指向新版
        KnowledgeIdentity updatedIdentity = new KnowledgeIdentity(
            identity.id(), identity.tenantId(), identity.identityCode(),
            identity.domain(), identity.subject(), identity.specialtyId(), identity.description(),
            identity.status(), saved.id(),
            identity.createdAt(), identity.createdBy(),
            now, actor
        );
        identityRepository.save(updatedIdentity);

        // 6) supersession 历史链
        KnowledgeSupersession transition = new KnowledgeSupersession(
            null, tenantId, identityId, oldVersionId, saved.id(),
            transitionType, reason == null ? null : reason.trim(),
            now, actor
        );
        supersessionRepository.save(transition);

        return saved;
    }

    /**
     * 紧急撤回：将当前 ACTIVE 版本降为 WITHDRAWN。
     *
     * @param identityId 知识身份 id
     * @param versionId  待撤回的版本 id（必须为该身份当前 ACTIVE）
     * @param reason     撤回原因（必填）
     */
    @Transactional
    public KnowledgeAssetVersion withdraw(Long identityId, Long versionId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "撤回必须填写原因");
        }
        String tenantId = requireCurrentTenant();
        String actor = currentActor();
        Instant now = Instant.now();

        KnowledgeIdentity identity = identityRepository.findByTenantIdAndIdForUpdate(tenantId, identityId)
            .orElseThrow(() -> ApiException.notFound("知识身份 id=" + identityId));

        KnowledgeAssetVersion target = versionRepository.findByTenantIdAndId(tenantId, versionId)
            .orElseThrow(() -> ApiException.notFound("知识版本 id=" + versionId));
        if (!target.identityId().equals(identityId)) {
            throw new ApiException(ErrorCode.CONFLICT, "版本 " + versionId + " 不属于身份 " + identityId);
        }
        if (target.status() != KnowledgeVersionStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONFLICT,
                "仅 ACTIVE 版本可撤回，当前状态 " + target.status());
        }

        KnowledgeAssetVersion withdrawn = new KnowledgeAssetVersion(
            target.id(), target.tenantId(), target.identityId(),
            target.versionNo(), target.versionLabel(),
            target.sourceDocumentId(), target.sourceVersionId(),
            target.contentHash(), target.anchors(),
            KnowledgeVersionStatus.WITHDRAWN, target.riskLevel(),
            target.effectiveFrom(), now,
            target.reviewedBy(), target.reviewedAt(),
            target.activatedAt(), target.supersededAt(),
            now /* withdrawn_at */, reason.trim(),
            target.createdAt(), target.createdBy(),
            now, actor
        );
        KnowledgeAssetVersion saved = versionRepository.save(withdrawn);

        // 身份 current_version_id 置 null（视为暂无权威版本）
        KnowledgeIdentity updatedIdentity = new KnowledgeIdentity(
            identity.id(), identity.tenantId(), identity.identityCode(),
            identity.domain(), identity.subject(), identity.specialtyId(), identity.description(),
            identity.status(), null,
            identity.createdAt(), identity.createdBy(),
            now, actor
        );
        identityRepository.save(updatedIdentity);

        supersessionRepository.save(new KnowledgeSupersession(
            null, tenantId, identityId, saved.id(), null,
            SupersessionType.WITHDRAW, reason.trim(),
            now, actor
        ));

        return saved;
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
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

    private void requireCitation(String tenantId, Long versionId) {
        if (citationRepository.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc(tenantId, versionId).isEmpty()) {
            throw new ApiException(ErrorCode.KNOWLEDGE_CITATION_REQUIRED,
                "知识版本 id=" + versionId + " 缺少来源引用，禁止激活");
        }
    }

    /**
     * 创建待审版本草稿。
     *
     * @param request 创建请求
     * @return 创建的版本草稿实体
     */
    @Transactional
    public KnowledgeAssetVersion createDraftVersion(DraftVersionCreateRequest request) {
        String tenantId = requireCurrentTenant();
        String actor = currentActor();
        Instant now = Instant.now();

        // 1) 校验知识身份是否存在
        KnowledgeIdentity identity = identityRepository.findByTenantIdAndId(tenantId, request.identityId())
            .orElseThrow(() -> ApiException.notFound("知识身份 id=" + request.identityId()));

        // 2) 校验版本号唯一性，避免 uk_knowledge_asset_version (identity_id, version_no) 碰撞
        Optional<KnowledgeAssetVersion> existingVerOpt = versionRepository.findByTenantIdAndIdentityIdOrderByCreatedAtDesc(tenantId, request.identityId()).stream()
            .filter(v -> v.versionNo().equalsIgnoreCase(request.versionNo()))
            .findFirst();
        if (existingVerOpt.isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "知识身份 id=" + request.identityId() + " 下的版本号 " + request.versionNo() + " 已存在");
        }

        // 3) 计算内容哈希 SHA-256
        String content = request.content();
        if (content == null) {
            content = "";
        }
        String contentHash = sha256(content);

        // 4) 扫描同 Identity 下的所有既有版本，若 contentHash 与之匹配则抛出 ENG_KNOW_002 冲突异常
        List<KnowledgeAssetVersion> existingVersions = versionRepository.findByTenantIdAndIdentityIdOrderByCreatedAtDesc(tenantId, request.identityId());
        for (KnowledgeAssetVersion v : existingVersions) {
            if (contentHash.equals(v.contentHash())) {
                throw new ApiException(ErrorCode.ENG_KNOW_002, "知识版本内容指纹冲突已存在，历史冲突版本号: " + v.versionNo());
            }
        }

        // 5) 创建待审版本，初始状态为 UNDER_REVIEW
        KnowledgeAssetVersion draft = new KnowledgeAssetVersion(
            null,
            tenantId,
            request.identityId(),
            request.versionNo(),
            request.versionLabel(),
            request.sourceDocumentId(),
            request.sourceVersionId(),
            contentHash,
            request.anchors(),
            KnowledgeVersionStatus.UNDER_REVIEW, // 初始状态为 UNDER_REVIEW
            request.riskLevel() == null ? KnowledgeRiskLevel.MEDIUM : request.riskLevel(),
            null, null, null, null, null, null, null, null,
            now, actor, now, actor
        );

        return versionRepository.save(draft);
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
