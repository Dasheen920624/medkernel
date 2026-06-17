package com.medkernel.engine.knowledge;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditEvent;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.PlatformAuthority;
import com.medkernel.engine.versioning.ReleasePort;
import com.medkernel.engine.versioning.RolloutPolicy;
import com.medkernel.engine.versioning.VersionPublishEvidence;
import com.medkernel.engine.versioning.VersionReleaseCommand;
import com.medkernel.engine.versioning.VersionedAssetType;

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
    private final SourceVersionRepository sourceVersionRepository;
    private final KnowledgeProjectionRefreshPort projectionRefreshPort;
    private final CandidateClassificationRepository candidateClassificationRepository;
    private final ReviewAssignmentRepository reviewAssignmentRepository;
    private final KnowledgeInvalidationRepository invalidationRepository;
    private final AffectedCaseTaskRepository affectedCaseTaskRepository;
    private final KnowledgeVersionedAssetAdapter versionedAssets;
    private final AssetVersionRepository assetVersions;
    private final ReleasePort releasePort;

    public KnowledgeVersionService(KnowledgeIdentityRepository identityRepository,
                                   KnowledgeAssetVersionRepository versionRepository,
                                   KnowledgeSupersessionRepository supersessionRepository,
                                   CitationRepository citationRepository,
                                   SourceDocumentRepository sourceDocumentRepository,
                                   SourceVersionRepository sourceVersionRepository,
                                   KnowledgeProjectionRefreshPort projectionRefreshPort,
                                   CandidateClassificationRepository candidateClassificationRepository,
                                   ReviewAssignmentRepository reviewAssignmentRepository,
                                   KnowledgeInvalidationRepository invalidationRepository,
                                   AffectedCaseTaskRepository affectedCaseTaskRepository,
                                   KnowledgeVersionedAssetAdapter versionedAssets,
                                   AssetVersionRepository assetVersions,
                                   ReleasePort releasePort) {
        this.identityRepository = identityRepository;
        this.versionRepository = versionRepository;
        this.supersessionRepository = supersessionRepository;
        this.citationRepository = citationRepository;
        this.sourceDocumentRepository = sourceDocumentRepository;
        this.sourceVersionRepository = sourceVersionRepository;
        this.projectionRefreshPort = projectionRefreshPort;
        this.candidateClassificationRepository = candidateClassificationRepository;
        this.reviewAssignmentRepository = reviewAssignmentRepository;
        this.invalidationRepository = invalidationRepository;
        this.affectedCaseTaskRepository = affectedCaseTaskRepository;
        this.versionedAssets = versionedAssets;
        this.assetVersions = assetVersions;
        this.releasePort = releasePort;
    }

    public PageResponse<KnowledgeAssetVersion> listByIdentity(Long identityId, PageRequest request) {
        String tenantId = requireCurrentTenant();
        EffectiveKnowledgeIdentity effective = findEffectiveIdentity(identityId, tenantId);
        PageRequest safeRequest = request == null ? PageRequest.defaults() : request;
        long total = versionRepository.countByTenantIdAndIdentityId(
            effective.sourceTenantId(),
            effective.identity().id());
        if (total == 0L) {
            return PageResponse.empty(safeRequest);
        }
        List<KnowledgeAssetVersion> versions = versionRepository.pageByTenantIdAndIdentityId(
            effective.sourceTenantId(),
            effective.identity().id(),
            safeRequest.offset(),
            safeRequest.safeSize());
        return PageResponse.of(versions, safeRequest, total);
    }

    public KnowledgeAssetVersion getVersion(Long versionId) {
        String tenantId = requireCurrentTenant();
        return versionRepository.findByTenantIdAndId(tenantId, versionId)
            .orElseThrow(() -> ApiException.notFound("知识版本 id=" + versionId));
    }

    public PageResponse<KnowledgeReviewQueueItem> listReviewQueue(int withinDays, PageRequest request) {
        if (withinDays < 0 || withinDays > 365) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "复审队列时间窗必须为 0 至 365 天");
        }
        String tenantId = requireCurrentTenant();
        Instant now = Instant.now();
        Instant threshold = now.plus(Duration.ofDays(withinDays));
        long total = versionRepository.countReviewDueByTenantId(tenantId, threshold);
        if (total == 0L) {
            return PageResponse.empty(request);
        }
        List<KnowledgeAssetVersion> dueVersions =
            versionRepository.pageReviewDueByTenantId(
                tenantId,
                threshold,
                request.offset(),
                request.safeSize());
        List<Long> identityIds = dueVersions.stream()
            .map(KnowledgeAssetVersion::identityId)
            .distinct()
            .toList();
        Map<Long, KnowledgeIdentity> identitiesById =
            identityRepository.findByTenantIdAndIdIn(tenantId, identityIds).stream()
                .collect(Collectors.toMap(KnowledgeIdentity::id, Function.identity()));
        List<KnowledgeReviewQueueItem> items = dueVersions.stream()
            .map(version -> {
                KnowledgeIdentity identity = Optional.ofNullable(identitiesById.get(version.identityId()))
                    .orElseThrow(() -> ApiException.notFound("知识身份 id=" + version.identityId()));
                KnowledgeReviewStatus status = !version.nextReviewAt().isAfter(now)
                    ? KnowledgeReviewStatus.OVERDUE
                    : KnowledgeReviewStatus.UPCOMING;
                long daysUntilDue = Duration.between(now, version.nextReviewAt()).toDays();
                return new KnowledgeReviewQueueItem(identity, version, status, daysUntilDue);
            })
            .toList();
        return PageResponse.of(items, request, total);
    }

    /**
     * 提交版本进入审核态。已有 UNDER_REVIEW 版本幂等返回，避免重复点击造成状态冲突。
     */
    @Transactional
    public KnowledgeAssetVersion submit(Long identityId, Long versionId, KnowledgeActionRequest request) {
        String tenantId = requireCurrentTenant();
        validateContext(request.context(), tenantId);
        KnowledgeIdentity identity = identityRepository.findByTenantIdAndId(tenantId, identityId)
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
        AssetVersion assetVersion = requireUnifiedAssetVersion(identity, target);
        releasePort.submitForReview(knowledgeReleaseCommand(
            identity, target, assetVersion, request.reason(), VersionPublishEvidence.empty()));
        Instant now = Instant.now();
        KnowledgeAssetVersion submitted = new KnowledgeAssetVersion(
            target.id(), target.tenantId(), target.identityId(),
            target.versionNo(), target.versionLabel(),
            target.sourceDocumentId(), target.sourceVersionId(),
            target.contentHash(), target.anchors(),
            KnowledgeVersionStatus.UNDER_REVIEW, target.riskLevel(),
            target.authorityLevel(), target.gradeQuality(), target.gradeStrength(), target.conflictArbitration(),
            target.effectiveOrganizationScope(), target.effectiveApplicableScope(),
            target.scopeKeyForStatus(KnowledgeVersionStatus.UNDER_REVIEW),
            target.effectiveFrom(), target.effectiveTo(),
            target.reviewedBy(), target.reviewedAt(),
            target.activatedAt(), target.supersededAt(),
            target.withdrawnAt(), target.withdrawnReason(),
            target.createdAt(), target.createdBy(),
            now, currentActor(),
            target.reviewCycleMonths(), target.nextReviewAt()
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
        return listCandidates(identityId, PageRequest.defaults());
    }

    /**
     * 列出指定知识身份下的新旧识别结果与待替换审核候选；候选队列必须服务端分页。
     */
    public KnowledgeCandidateResponse listCandidates(Long identityId, PageRequest request) {
        String tenantId = requireCurrentTenant();
        identityRepository.findByTenantIdAndId(tenantId, identityId)
            .orElseThrow(() -> ApiException.notFound("知识身份 id=" + identityId));
        PageRequest safeRequest = request == null ? PageRequest.defaults() : request;
        long total = versionRepository.countPendingReplacementCandidatesByTenantIdAndIdentityId(tenantId, identityId);
        List<KnowledgeAssetVersion> candidates = total == 0L
            ? List.of()
            : versionRepository.pagePendingReplacementCandidatesByTenantIdAndIdentityId(
                tenantId,
                identityId,
                safeRequest.offset(),
                safeRequest.safeSize());
        List<Long> candidateVersionIds = candidates.stream()
            .map(KnowledgeAssetVersion::id)
            .toList();
        List<CandidateClassification> classifications = candidateVersionIds.isEmpty()
            ? List.of()
            : candidateClassificationRepository.findByTenantIdAndCandidateVersionIdIn(tenantId, candidateVersionIds);
        return new KnowledgeCandidateResponse(
            identityId,
            total == 0L ? PageResponse.empty(safeRequest) : PageResponse.of(candidates, safeRequest, total),
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
        return classifyCandidate(identityId, request, null);
    }

    /**
     * 对新进入的知识版本候选做 B0 新旧识别与审核分流（AIK-STD-13 PR4）。
     *
     * <p>{@code assignmentPlan} 非空时据 PR3 会签路由建多角色 {@link ReviewAssignment}（归口 ∪ 领域）；
     * 为 null 时沿用既有默认（{@code assignedTo}=提交人，单行），既有调用方零回归。
     */
    @Transactional
    public KnowledgeCandidateResponse classifyCandidate(Long identityId, KnowledgeVersionCreateRequest request,
                                                        ReviewAssignmentPlan assignmentPlan) {
        String tenantId = requireCurrentTenant();
        KnowledgeApiContext context = request.context();
        validateContext(context, tenantId);
        String actor = currentActor();
        String orgPath = currentOrgPath();
        String organizationScope = organizationScope(context, tenantId);
        String applicableScope = applicableScope(context);
        Instant now = Instant.now();
        KnowledgeIdentity identity = identityRepository.findByTenantIdAndId(tenantId, identityId)
            .orElseThrow(() -> ApiException.notFound("知识身份 id=" + identityId));
        SourceDocument sourceDocument = sourceDocumentRepository.findByTenantIdAndId(tenantId, request.sourceDocumentId())
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_KNOW_001, "来源文献不存在 id=" + request.sourceDocumentId()));
        if (versionRepository.existsByTenantIdAndIdentityIdAndVersionNoIgnoreCase(
            tenantId, identityId, request.versionNo())) {
            throw new ApiException(ErrorCode.CONFLICT,
                "知识身份 id=" + identityId + " 下的版本号 " + request.versionNo() + " 已存在");
        }

        String contentHash = ContentHash.sha256(request.content());
        Optional<KnowledgeAssetVersion> duplicate =
            versionRepository.findByTenantIdAndIdentityIdAndContentHash(tenantId, identityId, contentHash);
        Optional<KnowledgeAssetVersion> active =
            versionRepository.findFirstByTenantIdAndIdentityIdAndStatusOrderByCreatedAtDescIdDesc(
                tenantId, identityId, KnowledgeVersionStatus.ACTIVE);
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
            organizationScope,
            applicableScope,
            pendingScopeKey(identity.id(), request.versionNo()),
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
            actor,
            request.reviewCycleMonths(),
            null));
        versionedAssets.registerDraft(new AssetVersionRegisterCommand(
            tenantId,
            VersionedAssetType.KNOWLEDGE,
            identity.identityCode(),
            candidate.versionNo(),
            candidate.effectiveOrganizationScope(),
            candidate.effectiveApplicableScope(),
            null,
            candidate.contentHash(),
            "knowledge-version:" + identity.identityCode() + ":" + candidate.versionNo(),
            actor,
            RequestContext.currentTraceId(),
            AssetVersionSafetyPolicy.NORMAL,
            candidate.isHighRisk() ? AssetVersionOverridePolicy.REVIEW : AssetVersionOverridePolicy.FREE
        ));
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
        if (assignmentPlan != null && !assignmentPlan.isEmpty()) {
            for (String reviewerRole : assignmentPlan.reviewerRoleCodes()) {
                reviewAssignmentRepository.save(new ReviewAssignment(
                    null, tenantId, orgPath, classification.id(), identityId, candidate.id(),
                    reviewerRole, CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW,
                    null, null, null, null, now, actor, now, actor));
            }
        } else {
            reviewAssignmentRepository.save(new ReviewAssignment(
                null, tenantId, orgPath, classification.id(), identityId, candidate.id(),
                actor, CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW,
                null, null, null, null, now, actor, now, actor));
        }
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
            KnowledgeAssetVersion activated = activate(
                classification.identityId(),
                classification.candidateVersionId(),
                request.reason(),
                request.publishEvidence());
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
                candidatePage(List.of(activated)),
                List.of(approved),
                true,
                "APPROVED",
                "候选审核通过，已转交权威版本原子替换流程");
        }
        if (request.decision() == KnowledgeCandidateReviewDecision.RETURN) {
            if (request.reason() == null || request.reason().isBlank()) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "退修须填写修订意见");
            }
            KnowledgeAssetVersion returning = versionRepository.findByTenantIdAndId(tenantId, classification.candidateVersionId())
                .orElseThrow(() -> ApiException.notFound("知识版本 id=" + classification.candidateVersionId()));
            KnowledgeAssetVersion draft = new KnowledgeAssetVersion(
                returning.id(), returning.tenantId(), returning.identityId(),
                returning.versionNo(), returning.versionLabel(),
                returning.sourceDocumentId(), returning.sourceVersionId(),
                returning.contentHash(), returning.anchors(),
                KnowledgeVersionStatus.DRAFT, returning.riskLevel(),
                returning.authorityLevel(), returning.gradeQuality(), returning.gradeStrength(), returning.conflictArbitration(),
                returning.effectiveOrganizationScope(), returning.effectiveApplicableScope(),
                returning.scopeKeyForStatus(KnowledgeVersionStatus.DRAFT),
                returning.effectiveFrom(), returning.effectiveTo(),
                returning.reviewedBy(), returning.reviewedAt(),
                returning.activatedAt(), returning.supersededAt(),
                returning.withdrawnAt(), returning.withdrawnReason(),
                returning.createdAt(), returning.createdBy(),
                now, actor,
                returning.reviewCycleMonths(), returning.nextReviewAt()
            );
            KnowledgeAssetVersion savedDraft = versionRepository.save(draft);
            CandidateClassification returned = candidateClassificationRepository.save(classificationWithStatus(
                classification,
                CandidateReviewStatus.RETURNED,
                appendReason(classification.basis(), request.reason()),
                now,
                actor));
            reviewAssignmentRepository.save(reviewAssignment(
                returned,
                CandidateReviewStatus.RETURNED,
                KnowledgeCandidateReviewDecision.RETURN,
                request.reason(),
                actor,
                now));
            return new KnowledgeCandidateResponse(
                classification.identityId(),
                candidatePage(List.of(savedDraft)),
                List.of(returned),
                true,
                "RETURNED",
                "候选已退修，退回生产者修订重提");
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
            candidate.effectiveOrganizationScope(), candidate.effectiveApplicableScope(),
            candidate.scopeKeyForStatus(KnowledgeVersionStatus.REJECTED),
            candidate.effectiveFrom(), candidate.effectiveTo(),
            candidate.reviewedBy(), candidate.reviewedAt(),
            candidate.activatedAt(), candidate.supersededAt(),
            candidate.withdrawnAt(), candidate.withdrawnReason(),
            candidate.createdAt(), candidate.createdBy(),
            now, actor,
            candidate.reviewCycleMonths(), candidate.nextReviewAt()
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
            candidatePage(List.of(saved)),
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
    public KnowledgeAssetVersion activate(
            Long identityId,
            Long versionId,
            String reason,
            VersionPublishEvidence publishEvidence) {
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
        if (target.status() == KnowledgeVersionStatus.WITHDRAWN && target.isHighRisk()) {
            throw new ApiException(
                ErrorCode.ROLLBACK_SAFETY_DENIED,
                "ROLLBACK_SAFETY_DENIED：被撤回的高风险知识版本禁止一键回滚"
            );
        }
        if (target.status() == null || !target.status().isActivatable()) {
            throw new ApiException(ErrorCode.CONFLICT,
                "版本当前状态 " + target.status()
                    + " 不可激活（需 UNDER_REVIEW、CANDIDATE、PENDING_REPLACEMENT_REVIEW 或 SUPERSEDED）");
        }
        boolean rollbackActivation = target.status() == KnowledgeVersionStatus.SUPERSEDED;
        if (target.isHighRisk() && normalizedReason == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "高风险版本激活必须填写说明");
        }
        requireCitation(tenantId, target.id());
        publishUnifiedVersion(
            identity,
            target,
            normalizedReason,
            VersionPublishEvidence.orEmpty(publishEvidence));

        // 3) 同一完整适用域内的当前 ACTIVE 版本（如有）→ SUPERSEDED
        String organizationScope = target.effectiveOrganizationScope();
        String applicableScope = target.effectiveApplicableScope();
        Optional<KnowledgeAssetVersion> currentActiveOpt = versionRepository.findActiveByEffectiveScope(
            tenantId, identityId, organizationScope, applicableScope);
        Long oldVersionId = null;
        SupersessionType transitionType = SupersessionType.ACTIVATE;
        ConflictArbitration arbitration = null;
        if (currentActiveOpt.isPresent()) {
            KnowledgeAssetVersion oldActive = currentActiveOpt.get();
            arbitration = ConflictArbitration.between(
                oldActive,
                target,
                resolveSourceVersion(tenantId, oldActive.sourceVersionId()),
                resolveSourceVersion(tenantId, target.sourceVersionId())
            );
            if (arbitration.lowAuthorityOverrideHighAuthority() && normalizedReason == null) {
                throw new ApiException(ErrorCode.AUTHORITY_OVERRIDE_DENIED,
                    "低阶来源覆盖高阶来源必须填写理由并由发布审核人确认");
            }
            oldVersionId = oldActive.id();
            transitionType = rollbackActivation ? SupersessionType.ROLLBACK : SupersessionType.REPLACE;
            KnowledgeAssetVersion superseded = new KnowledgeAssetVersion(
                oldActive.id(), oldActive.tenantId(), oldActive.identityId(),
                oldActive.versionNo(), oldActive.versionLabel(),
                oldActive.sourceDocumentId(), oldActive.sourceVersionId(),
                oldActive.contentHash(), oldActive.anchors(),
                KnowledgeVersionStatus.SUPERSEDED, oldActive.riskLevel(),
                oldActive.authorityLevel(), oldActive.gradeQuality(), oldActive.gradeStrength(), oldActive.conflictArbitration(),
                oldActive.effectiveOrganizationScope(), oldActive.effectiveApplicableScope(),
                oldActive.scopeKeyForStatus(KnowledgeVersionStatus.SUPERSEDED),
                oldActive.effectiveFrom(), now /* effective_to = activate 时刻 */,
                oldActive.reviewedBy(), oldActive.reviewedAt(),
                oldActive.activatedAt(), now /* superseded_at */,
                oldActive.withdrawnAt(), oldActive.withdrawnReason(),
                oldActive.createdAt(), oldActive.createdBy(),
                now, actor,
                oldActive.reviewCycleMonths(), oldActive.nextReviewAt()
            );
            versionRepository.save(superseded);
            createReplacementAffectedCaseTasks(superseded, target.id(), normalizedReason, now, actor);
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
            organizationScope, applicableScope, target.activeScopeKeyForActiveStatus(),
            now /* effective_from = 激活时刻 */, null /* effective_to 由后续 supersede 写 */,
            actor, now /* reviewed_at */,
            now /* activated_at */, null, null, null,
            target.createdAt(), target.createdBy(),
            now, actor,
            target.reviewCycleMonths(),
            nextReviewAt(now, target.reviewCycleMonths())
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
            now, actor, null, null, null
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

    private void publishUnifiedVersion(
            KnowledgeIdentity identity,
            KnowledgeAssetVersion target,
            String reason,
            VersionPublishEvidence publishEvidence) {
        AssetVersion assetVersion = requireUnifiedAssetVersion(identity, target);
        VersionReleaseCommand command = knowledgeReleaseCommand(
            identity, target, assetVersion, reason, publishEvidence);
        switch (assetVersion.status()) {
            case DRAFT -> {
                releasePort.submitForReview(command);
                releasePort.approveReview(command);
                releasePort.publish(command);
            }
            case IN_REVIEW -> {
                releasePort.approveReview(command);
                releasePort.publish(command);
            }
            case APPROVED -> releasePort.publish(command);
            case PUBLISHED -> {
                // 已发布版本重复激活保持幂等，领域内容状态随后对齐。
            }
            case DEPRECATED, RETIRED ->
                throw new ApiException(ErrorCode.CONFLICT, "统一知识版本已下线，不能直接激活");
        }
    }

    private AssetVersion requireUnifiedAssetVersion(
            KnowledgeIdentity identity,
            KnowledgeAssetVersion version) {
        return assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
                identity.tenantId(),
                VersionedAssetType.KNOWLEDGE,
                identity.identityCode(),
                version.versionNo())
            .orElseThrow(() -> new ApiException(
                ErrorCode.CONFLICT,
                "知识版本缺少统一资产版本登记: "
                    + identity.identityCode() + "@" + version.versionNo()));
    }

    private VersionReleaseCommand knowledgeReleaseCommand(
            KnowledgeIdentity identity,
            KnowledgeAssetVersion version,
            AssetVersion assetVersion,
            String reason,
            VersionPublishEvidence publishEvidence) {
        String conclusion = reason == null || reason.isBlank()
            ? "知识版本审核通过"
            : reason.trim();
        return new VersionReleaseCommand(
            identity.tenantId(),
            VersionedAssetType.KNOWLEDGE,
            identity.identityCode(),
            assetVersion.versionId(),
            assetVersion.organizationScope(),
            assetVersion.applicableScope(),
            null,
            null,
            RolloutPolicy.all(),
            "知识版本 " + identity.identityCode() + "@" + version.versionNo()
                + "；content_hash=" + version.contentHash(),
            conclusion,
            currentActor(),
            RequestContext.currentTraceId(),
            publishEvidence.electronicSignature(),
            publishEvidence.qualityGate()
        );
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

    private SourceVersion resolveSourceVersion(String tenantId, Long sourceVersionId) {
        if (sourceVersionId == null) {
            return null;
        }
        return sourceVersionRepository.findByTenantIdAndId(tenantId, sourceVersionId).orElse(null);
    }

    private Instant nextReviewAt(Instant reviewedAt, Integer reviewCycleMonths) {
        if (reviewCycleMonths == null || reviewCycleMonths < 1 || reviewCycleMonths > 60) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "复审周期必须为 1 至 60 个月");
        }
        return reviewedAt.atZone(ZoneOffset.UTC).plusMonths(reviewCycleMonths).toInstant();
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

    private PageResponse<KnowledgeAssetVersion> candidatePage(List<KnowledgeAssetVersion> candidates) {
        List<KnowledgeAssetVersion> items = candidates == null ? List.of() : candidates;
        return PageResponse.of(items, PageRequest.defaults(), items.size());
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
        AssetVersion unified = requireUnifiedAssetVersion(identity, target);
        if (unified.status() != AssetVersionStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.CONFLICT, "仅统一底座已发布的知识版本可撤回");
        }

        KnowledgeAssetVersion withdrawn = new KnowledgeAssetVersion(
            target.id(), target.tenantId(), target.identityId(),
            target.versionNo(), target.versionLabel(),
            target.sourceDocumentId(), target.sourceVersionId(),
            target.contentHash(), target.anchors(),
            KnowledgeVersionStatus.WITHDRAWN, target.riskLevel(),
            target.authorityLevel(), target.gradeQuality(), target.gradeStrength(), target.conflictArbitration(),
            target.effectiveOrganizationScope(), target.effectiveApplicableScope(),
            target.scopeKeyForStatus(KnowledgeVersionStatus.WITHDRAWN),
            target.effectiveFrom(), now,
            target.reviewedBy(), target.reviewedAt(),
            target.activatedAt(), target.supersededAt(),
            now /* withdrawn_at */, reason.trim(),
            target.createdAt(), target.createdBy(),
            now, actor,
            target.reviewCycleMonths(), target.nextReviewAt()
        );
        KnowledgeAssetVersion saved = versionRepository.save(withdrawn);
        assetVersions.save(unified.withStatusAndWindow(
            AssetVersionStatus.DEPRECATED,
            "version:" + unified.versionId(),
            unified.effectiveFrom(),
            now,
            now,
            actor));

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
            now, actor, null, null, null
        ));
        KnowledgeInvalidation invalidation = invalidationRepository.save(new KnowledgeInvalidation(
            null,
            tenantId,
            identityId,
            saved.id(),
            KnowledgeInvalidationType.EMERGENCY_WITHDRAW,
            KnowledgeInvalidationStatus.OPEN,
            saved.riskLevel(),
            reason.trim(),
            saved.effectiveOrganizationScope(),
            saved.effectiveApplicableScope(),
            actor,
            now,
            true,
            RequestContext.currentTraceId(),
            now,
            actor,
            now,
            actor
        ));
        createAffectedCaseTasks(invalidation, saved, reason.trim(), now, actor);
        projectionRefreshPort.refreshPublishedVersion(
            tenantId,
            identityId,
            saved.id(),
            actor,
            RequestContext.currentTraceId());

        return saved;
    }

    private void createAffectedCaseTasks(KnowledgeInvalidation invalidation, KnowledgeAssetVersion version,
            String reason, Instant now, String actor) {
        saveAffectedTask(invalidation, version, AffectedCaseTaskType.PHYSICIAN_REVIEW,
            AffectedCaseTargetType.KNOWLEDGE_VERSION, "identity:" + version.identityId() + "/version:" + version.id(),
            reason, now, actor);
        saveAffectedTask(invalidation, version, AffectedCaseTaskType.PACKAGE_RESYNC,
            AffectedCaseTargetType.PACKAGE_DEPENDENCY, "package-dependency/version:" + version.id(),
            reason, now, actor);
        saveAffectedTask(invalidation, version, AffectedCaseTaskType.SYNC_ALERT,
            AffectedCaseTargetType.SYNC_TARGET,
            "sync-target/version:" + version.id() + "/scope:" + version.activeScopeKeyForActiveStatus(),
            reason, now, actor);
    }

    private void createReplacementAffectedCaseTasks(KnowledgeAssetVersion oldVersion, Long newVersionId,
            String reason, Instant now, String actor) {
        String impactReason = replacementImpactReason(oldVersion.id(), newVersionId, reason);
        KnowledgeInvalidation invalidation = invalidationRepository.save(new KnowledgeInvalidation(
            null,
            oldVersion.tenantId(),
            oldVersion.identityId(),
            oldVersion.id(),
            KnowledgeInvalidationType.SUPERSEDED_REPLACEMENT,
            KnowledgeInvalidationStatus.OPEN,
            oldVersion.riskLevel(),
            impactReason,
            oldVersion.effectiveOrganizationScope(),
            oldVersion.effectiveApplicableScope(),
            actor,
            now,
            false,
            RequestContext.currentTraceId(),
            now,
            actor,
            now,
            actor
        ));
        createAffectedCaseTasks(invalidation, oldVersion, impactReason, now, actor);
    }

    private String replacementImpactReason(Long oldVersionId, Long newVersionId, String reason) {
        String base = "知识版本原子替换：oldVersionId=" + oldVersionId + "，newVersionId=" + newVersionId;
        if (reason == null || reason.isBlank()) {
            return base;
        }
        return base + "；原因：" + reason.trim();
    }

    private void saveAffectedTask(KnowledgeInvalidation invalidation, KnowledgeAssetVersion version,
            AffectedCaseTaskType taskType, AffectedCaseTargetType targetType, String targetRef,
            String reason, Instant now, String actor) {
        String taskKey = affectedTaskKey(invalidation, taskType, targetRef);
        affectedCaseTaskRepository.findByTenantIdAndTaskKey(version.tenantId(), taskKey)
            .orElseGet(() -> affectedCaseTaskRepository.save(new AffectedCaseTask(
                null,
                version.tenantId(),
                taskKey,
                invalidation.id(),
                version.identityId(),
                version.id(),
                taskType,
                AffectedCaseTaskStatus.OPEN,
                targetType,
                targetRef,
                reason,
                now.plus(Duration.ofDays(1)),
                actor,
                RequestContext.currentTraceId(),
                now,
                actor,
                now,
                actor
            )));
    }

    private String affectedTaskKey(KnowledgeInvalidation invalidation, AffectedCaseTaskType taskType, String targetRef) {
        String invalidationPart = invalidation.id() == null ? "pending" : String.valueOf(invalidation.id());
        return "knowledge-invalidation:" + invalidationPart + ":" + taskType + ":" + targetRef;
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

    private String organizationScope(KnowledgeApiContext context, String tenantId) {
        if (PlatformTenant.isPlatformTenant(tenantId)) {
            return PlatformAuthority.PLATFORM_ORG_PATH;
        }
        OrgScope scope = new OrgScope(
            tenantId,
            context.groupId(),
            context.hospitalId(),
            context.campusId(),
            context.siteId(),
            context.departmentId(),
            context.specialtyId());
        String orgPath = AuditEvent.orgPath(scope);
        return orgPath == null || orgPath.isBlank() ? "tenant:" + tenantId : orgPath;
    }

    private String applicableScope(KnowledgeApiContext context) {
        return context.specialtyId() == null || context.specialtyId().isBlank()
            ? KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE
            : "specialty:" + context.specialtyId().trim();
    }

    private String pendingScopeKey(Long identityId, String versionNo) {
        String stableVersionNo = versionNo == null || versionNo.isBlank() ? "unknown" : versionNo.trim();
        return "version-pending:" + identityId + ":" + stableVersionNo;
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
