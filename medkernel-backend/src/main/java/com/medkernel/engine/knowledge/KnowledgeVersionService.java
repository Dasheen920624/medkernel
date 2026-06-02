package com.medkernel.engine.knowledge;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;

/**
 * 知识版本业务服务，承载详细规范 §1.4 / §1797-1806 的核心状态机：
 *
 * <pre>
 *   {@link #activate}(identity, versionId)
 *     ├─ 悲观锁 knowledge_identity 行
 *     ├─ 当前 ACTIVE 版本（如有）→ SUPERSEDED，写 supersession (REPLACE/ACTIVATE)
 *     ├─ 目标版本（UNDER_REVIEW / CANDIDATE / PENDING_REPLACEMENT_REVIEW）→ ACTIVE
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
    private final SourceDocumentRepository sourceDocumentRepository;
    private final KnowledgeProjectionRefreshPort projectionRefreshPort;
    private final CandidateClassificationRepository candidateClassificationRepository;
    private final ReviewAssignmentRepository reviewAssignmentRepository;

    public KnowledgeVersionService(KnowledgeIdentityRepository identityRepository,
                                   KnowledgeAssetVersionRepository versionRepository,
                                   KnowledgeSupersessionRepository supersessionRepository,
                                   CitationRepository citationRepository,
                                   SourceDocumentRepository sourceDocumentRepository,
                                   KnowledgeProjectionRefreshPort projectionRefreshPort,
                                   CandidateClassificationRepository candidateClassificationRepository,
                                   ReviewAssignmentRepository reviewAssignmentRepository) {
        this.identityRepository = identityRepository;
        this.versionRepository = versionRepository;
        this.supersessionRepository = supersessionRepository;
        this.citationRepository = citationRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.projectionRefreshPort = projectionRefreshPort;
        this.candidateClassificationRepository = candidateClassificationRepository;
        this.reviewAssignmentRepository = reviewAssignmentRepository;
    }

    public List<KnowledgeAssetVersion> listByIdentity(Long identityId) {
        String tenantId = requireCurrentTenant();
        EffectiveKnowledgeIdentity effective = findEffectiveIdentity(identityId, tenantId);
        return versionRepository.findByTenantIdAndIdentityIdOrderByCreatedAtDesc(
            effective.sourceTenantId(), effective.identity().id());
    }

    public KnowledgeAssetVersion getVersion(Long versionId) {
        String tenantId = requireCurrentTenant();
        return versionRepository.findByTenantIdAndId(tenantId, versionId)
            .orElseThrow(() -> ApiException.notFound("知识版本 id=" + versionId));
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
            target.authorityLevel(), target.gradeQuality(), target.gradeStrength(), target.conflictArbitration(),
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
     * 列出指定知识身份下的新旧识别结果与待替换审核候选。
     */
    public KnowledgeCandidateResponse listCandidates(Long identityId) {
        String tenantId = requireCurrentTenant();
        identityRepository.findByTenantIdAndId(tenantId, identityId)
            .orElseThrow(() -> ApiException.notFound("知识身份 id=" + identityId));
        List<CandidateClassification> classifications =
            candidateClassificationRepository.findByTenantIdAndIdentityIdOrderByCreatedAtDescIdDesc(tenantId, identityId);
        List<KnowledgeAssetVersion> candidates = versionRepository.findByTenantIdAndIdentityIdOrderByCreatedAtDesc(
                tenantId,
                identityId)
            .stream()
            .filter(version -> version.status() == KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW)
            .toList();
        return new KnowledgeCandidateResponse(
            identityId,
            candidates,
            classifications,
            true,
            "OK",
            "知识候选审核工作流已可用"
        );
    }

    /**
     * 对新进入的知识版本候选做 B0 新旧识别与审核分流。
     */
    @Transactional
    public KnowledgeCandidateResponse classifyCandidate(Long identityId, KnowledgeVersionCreateRequest request) {
        String tenantId = requireCurrentTenant();
        validateContext(request.context(), tenantId);
        String actor = currentActor();
        String orgPath = currentOrgPath();
        Instant now = Instant.now();
        KnowledgeIdentity identity = identityRepository.findByTenantIdAndId(tenantId, identityId)
            .orElseThrow(() -> ApiException.notFound("知识身份 id=" + identityId));
        SourceDocument sourceDocument = sourceDocumentRepository.findByTenantIdAndId(tenantId, request.sourceDocumentId())
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_KNOW_001, "来源文献不存在 id=" + request.sourceDocumentId()));
        List<KnowledgeAssetVersion> existingVersions =
            versionRepository.findByTenantIdAndIdentityIdOrderByCreatedAtDesc(tenantId, identityId);
        existingVersions.stream()
            .filter(version -> version.versionNo().equalsIgnoreCase(request.versionNo()))
            .findFirst()
            .ifPresent(existing -> {
                throw new ApiException(ErrorCode.CONFLICT,
                    "知识身份 id=" + identityId + " 下的版本号 " + request.versionNo() + " 已存在");
            });

        String contentHash = ContentHash.sha256(request.content());
        Optional<KnowledgeAssetVersion> duplicate = existingVersions.stream()
            .filter(version -> contentHash.equals(version.contentHash()))
            .findFirst();
        Optional<KnowledgeAssetVersion> active = existingVersions.stream()
            .filter(KnowledgeAssetVersion::isAuthoritative)
            .findFirst();
        if (duplicate.isPresent()) {
            KnowledgeAssetVersion duplicateVersion = duplicate.get();
            CandidateClassification classification = candidateClassificationRepository.save(new CandidateClassification(
                null,
                tenantId,
                orgPath,
                identityId,
                null,
                active.map(KnowledgeAssetVersion::id).orElse(duplicateVersion.id()),
                CandidateClassificationType.DUPLICATE,
                CandidateReviewStatus.DUPLICATE_SKIPPED,
                contentHash,
                "content_hash 与既有版本 " + duplicateVersion.versionNo() + " 一致: " + contentHash,
                "重复候选已去重，不新增审核待办",
                now,
                actor,
                now,
                actor));
            return KnowledgeCandidateResponse.classified(
                identityId,
                List.of(),
                List.of(classification),
                CandidateClassificationType.DUPLICATE,
                "候选内容指纹重复，已记录去重依据且未产生审核待办");
        }

        KnowledgeAssetVersion candidate = versionRepository.save(new KnowledgeAssetVersion(
            null,
            tenantId,
            identity.id(),
            request.versionNo(),
            request.versionLabel(),
            request.sourceDocumentId(),
            request.sourceVersionId(),
            contentHash,
            request.anchors(),
            KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW,
            request.riskLevel() == null ? KnowledgeRiskLevel.MEDIUM : request.riskLevel(),
            sourceDocument.authorityLevel(),
            request.gradeQuality(),
            request.gradeStrength(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            now,
            actor,
            now,
            actor));
        CandidateClassificationType classificationType = candidateClassificationType(active, sourceDocument);
        String diffSummary = diffSummary(active.orElse(null), candidate, sourceDocument);
        CandidateClassification classification = candidateClassificationRepository.save(new CandidateClassification(
            null,
            tenantId,
            orgPath,
            identityId,
            candidate.id(),
            active.map(KnowledgeAssetVersion::id).orElse(null),
            classificationType,
            CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW,
            contentHash,
            basis(classificationType, active.orElse(null), sourceDocument, contentHash),
            diffSummary,
            now,
            actor,
            now,
            actor));
        reviewAssignmentRepository.save(new ReviewAssignment(
            null,
            tenantId,
            orgPath,
            classification.id(),
            identityId,
            candidate.id(),
            actor,
            CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW,
            null,
            null,
            null,
            null,
            now,
            actor,
            now,
            actor));
        return KnowledgeCandidateResponse.classified(
            identityId,
            List.of(candidate),
            List.of(classification),
            classificationType,
            "候选已进入替换审核队列，仅供人工对照，不参与临床执行");
    }

    @Transactional
    public KnowledgeCandidateResponse reviewCandidate(Long candidateId, KnowledgeCandidateReviewRequest request) {
        String tenantId = requireCurrentTenant();
        validateContext(request.context(), tenantId);
        String actor = currentActor();
        Instant now = Instant.now();
        CandidateClassification classification = candidateClassificationRepository.findByTenantIdAndId(tenantId, candidateId)
            .orElseThrow(() -> ApiException.notFound("知识候选 id=" + candidateId));
        if (classification.reviewStatus() != CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW) {
            throw new ApiException(ErrorCode.CONFLICT, "候选当前状态 " + classification.reviewStatus() + " 不可重复审核");
        }
        if (request.decision() == KnowledgeCandidateReviewDecision.APPROVE) {
            KnowledgeAssetVersion activated = activate(classification.identityId(), classification.candidateVersionId(), request.reason());
            CandidateClassification approved = candidateClassificationRepository.save(classificationWithStatus(
                classification,
                CandidateReviewStatus.APPROVED,
                classification.basis(),
                now,
                actor));
            reviewAssignmentRepository.save(reviewAssignment(
                approved,
                CandidateReviewStatus.APPROVED,
                KnowledgeCandidateReviewDecision.APPROVE,
                request.reason(),
                actor,
                now));
            return new KnowledgeCandidateResponse(
                classification.identityId(),
                List.of(activated),
                List.of(approved),
                true,
                "APPROVED",
                "候选审核通过，已转交权威版本原子替换流程");
        }
        KnowledgeAssetVersion candidate = versionRepository.findByTenantIdAndId(tenantId, classification.candidateVersionId())
            .orElseThrow(() -> ApiException.notFound("知识版本 id=" + classification.candidateVersionId()));
        KnowledgeAssetVersion rejected = new KnowledgeAssetVersion(
            candidate.id(), candidate.tenantId(), candidate.identityId(),
            candidate.versionNo(), candidate.versionLabel(),
            candidate.sourceDocumentId(), candidate.sourceVersionId(),
            candidate.contentHash(), candidate.anchors(),
            KnowledgeVersionStatus.REJECTED, candidate.riskLevel(),
            candidate.authorityLevel(), candidate.gradeQuality(), candidate.gradeStrength(), candidate.conflictArbitration(),
            candidate.effectiveFrom(), candidate.effectiveTo(),
            candidate.reviewedBy(), candidate.reviewedAt(),
            candidate.activatedAt(), candidate.supersededAt(),
            candidate.withdrawnAt(), candidate.withdrawnReason(),
            candidate.createdAt(), candidate.createdBy(),
            now, actor
        );
        KnowledgeAssetVersion saved = versionRepository.save(rejected);
        CandidateClassification rejectedClassification = candidateClassificationRepository.save(classificationWithStatus(
            classification,
            CandidateReviewStatus.REJECTED,
            appendReason(classification.basis(), request.reason()),
            now,
            actor));
        reviewAssignmentRepository.save(reviewAssignment(
            rejectedClassification,
            CandidateReviewStatus.REJECTED,
            KnowledgeCandidateReviewDecision.REJECT,
            request.reason(),
            actor,
            now));
        return new KnowledgeCandidateResponse(
            classification.identityId(),
            List.of(saved),
            List.of(rejectedClassification),
            true,
            "REJECTED",
            "候选已拒绝并留档，不参与临床执行");
    }

    public KnowledgeCandidateResponse diffCandidate(Long candidateId) {
        String tenantId = requireCurrentTenant();
        CandidateClassification classification = candidateClassificationRepository.findByTenantIdAndId(tenantId, candidateId)
            .orElseThrow(() -> ApiException.notFound("知识候选 id=" + candidateId));
        if (classification.candidateVersionId() == null) {
            return KnowledgeCandidateResponse.classified(
                classification.identityId(),
                List.of(),
                List.of(classification),
                classification.classification(),
                "重复候选未落版本，仅返回去重依据");
        }
        KnowledgeAssetVersion candidate = versionRepository.findByTenantIdAndId(tenantId, classification.candidateVersionId())
            .orElseThrow(() -> ApiException.notFound("知识版本 id=" + classification.candidateVersionId()));
        return KnowledgeCandidateResponse.classified(
            classification.identityId(),
            List.of(candidate),
            List.of(classification),
            classification.classification(),
            "已返回候选与当前权威版本的对照审核视图");
    }

    /**
     * 审核激活：将目标版本原子地推到 ACTIVE，旧版降为 SUPERSEDED。
     *
     * @param identityId 知识身份 id
     * @param versionId  待激活的版本 id（必须属于该身份，状态为 UNDER_REVIEW、CANDIDATE 或 PENDING_REPLACEMENT_REVIEW）
     * @param reason     激活说明（高风险必填，由 Controller 层 / Bean Validation 保证）
     * @return 激活后的新版（status=ACTIVE）
     */
    @Transactional
    public KnowledgeAssetVersion activate(Long identityId, Long versionId, String reason) {
        String tenantId = requireCurrentTenant();
        String actor = currentActor();
        Instant now = Instant.now();
        String normalizedReason = reason == null || reason.isBlank() ? null : reason.trim();

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
                "版本当前状态 " + target.status() + " 不可激活（需 UNDER_REVIEW、CANDIDATE 或 PENDING_REPLACEMENT_REVIEW）");
        }
        if (target.isHighRisk() && normalizedReason == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "高风险版本激活必须填写说明");
        }
        requireCitation(tenantId, target.id());

        // 3) 当前 ACTIVE 版本（如有）→ SUPERSEDED
        Optional<KnowledgeAssetVersion> currentActiveOpt = versionRepository.findActiveByIdentity(tenantId, identityId);
        Long oldVersionId = null;
        SupersessionType transitionType = SupersessionType.ACTIVATE;
        ConflictArbitration arbitration = null;
        if (currentActiveOpt.isPresent()) {
            KnowledgeAssetVersion oldActive = currentActiveOpt.get();
            arbitration = ConflictArbitration.between(oldActive, target);
            if (arbitration.lowAuthorityOverrideHighAuthority() && normalizedReason == null) {
                throw new ApiException(ErrorCode.AUTHORITY_OVERRIDE_DENIED,
                    "低阶来源覆盖高阶来源必须填写理由并由发布审核人确认");
            }
            oldVersionId = oldActive.id();
            transitionType = SupersessionType.REPLACE;
            KnowledgeAssetVersion superseded = new KnowledgeAssetVersion(
                oldActive.id(), oldActive.tenantId(), oldActive.identityId(),
                oldActive.versionNo(), oldActive.versionLabel(),
                oldActive.sourceDocumentId(), oldActive.sourceVersionId(),
                oldActive.contentHash(), oldActive.anchors(),
                KnowledgeVersionStatus.SUPERSEDED, oldActive.riskLevel(),
                oldActive.authorityLevel(), oldActive.gradeQuality(), oldActive.gradeStrength(), oldActive.conflictArbitration(),
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
            target.authorityLevel(), target.gradeQuality(), target.gradeStrength(),
            arbitration != null && arbitration.hasSummary() ? arbitration.summary() : target.conflictArbitration(),
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
            transitionType, transitionReason(normalizedReason, arbitration),
            now, actor
        );
        supersessionRepository.save(transition);
        projectionRefreshPort.refreshPublishedVersion(
            tenantId,
            identityId,
            saved.id(),
            actor,
            RequestContext.currentTraceId());

        return saved;
    }

    private String transitionReason(String reason, ConflictArbitration arbitration) {
        if (arbitration == null || !arbitration.hasSummary()) {
            return reason;
        }
        if (reason == null || reason.isBlank()) {
            return arbitration.summary();
        }
        return reason.trim() + "\n" + arbitration.summary();
    }

    private CandidateClassificationType candidateClassificationType(Optional<KnowledgeAssetVersion> active,
            SourceDocument sourceDocument) {
        if (active.isEmpty()) {
            return CandidateClassificationType.NEW_ASSET;
        }
        SourceAuthorityLevel activeAuthority = active.get().authorityLevel();
        if (activeAuthority != null && activeAuthority != sourceDocument.authorityLevel()) {
            return CandidateClassificationType.CONFLICT;
        }
        return CandidateClassificationType.SAME_IDENTITY_NEW_VERSION;
    }

    private String basis(CandidateClassificationType type, KnowledgeAssetVersion active,
            SourceDocument sourceDocument, String contentHash) {
        if (type == CandidateClassificationType.NEW_ASSET) {
            return "当前知识身份暂无 ACTIVE 版本，按新建候选分流；content_hash=" + contentHash;
        }
        if (type == CandidateClassificationType.CONFLICT) {
            return "同一知识身份已有 ACTIVE 版本，候选来源分级 "
                + sourceDocument.authorityLevel() + " 与当前 "
                + active.authorityLevel() + " 不一致，需对照审核；content_hash=" + contentHash;
        }
        return "同一知识身份已有 ACTIVE 版本，内容指纹不同，按同身份新版待审；content_hash=" + contentHash;
    }

    private String diffSummary(KnowledgeAssetVersion active, KnowledgeAssetVersion candidate,
            SourceDocument sourceDocument) {
        if (active == null) {
            return "当前无 ACTIVE 版本；候选 " + candidate.versionNo() + " 将作为首个待审版本";
        }
        return "当前 ACTIVE=" + active.versionNo()
            + " / " + active.authorityLevel()
            + "；候选=" + candidate.versionNo()
            + " / " + sourceDocument.authorityLevel()
            + "；候选仅供人工对照审核，不参与临床执行";
    }

    private CandidateClassification classificationWithStatus(CandidateClassification classification,
            CandidateReviewStatus status, String basis, Instant now, String actor) {
        return new CandidateClassification(
            classification.id(),
            classification.tenantId(),
            classification.orgPath(),
            classification.identityId(),
            classification.candidateVersionId(),
            classification.activeVersionId(),
            classification.classification(),
            status,
            classification.contentHash(),
            basis,
            classification.diffSummary(),
            classification.createdAt(),
            classification.createdBy(),
            now,
            actor);
    }

    private ReviewAssignment reviewAssignment(CandidateClassification classification, CandidateReviewStatus status,
            KnowledgeCandidateReviewDecision decision, String reason, String actor, Instant now) {
        return new ReviewAssignment(
            null,
            classification.tenantId(),
            classification.orgPath(),
            classification.id(),
            classification.identityId(),
            classification.candidateVersionId(),
            actor,
            status,
            decision,
            reason == null ? null : reason.trim(),
            actor,
            now,
            now,
            actor,
            now,
            actor);
    }

    private String appendReason(String basis, String reason) {
        if (reason == null || reason.isBlank()) {
            return basis;
        }
        return basis + "\n审核拒绝原因：" + reason.trim();
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
            target.authorityLevel(), target.gradeQuality(), target.gradeStrength(), target.conflictArbitration(),
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

    private String currentOrgPath() {
        return AuditEvent.orgPath(RequestContext.currentOrgScope());
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

    private record EffectiveKnowledgeIdentity(KnowledgeIdentity identity, String sourceTenantId) {
    }
}
