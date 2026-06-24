package com.medkernel.engine.knowledge;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
import com.medkernel.engine.security.AuthenticatedRoleGuard;
import com.medkernel.engine.security.EffectivePermissionService;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.knowledge.production.gate.PublicationQualityRecordService;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.AssetScopeResolver;
import com.medkernel.engine.versioning.ReleasePort;
import com.medkernel.engine.versioning.RolloutPolicy;
import com.medkernel.engine.versioning.VersionPublishEvidence;
import com.medkernel.engine.versioning.VersionReleaseCommand;
import com.medkernel.engine.versioning.VersionRollbackCommand;
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
    private final PublicationQualityRecordService publicationQualityRecords;
    private final AssetScopeResolver assetScopes;
    private final EffectivePermissionService effectivePermissions;

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
                                   ReleasePort releasePort,
                                   PublicationQualityRecordService publicationQualityRecords,
                                   AssetScopeResolver assetScopes,
                                   EffectivePermissionService effectivePermissions) {
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
        this.publicationQualityRecords = publicationQualityRecords;
        this.assetScopes = assetScopes;
        this.effectivePermissions = effectivePermissions;
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
    public KnowledgeReplayResponse replayVersion(Long identityId, Long versionId, String snapshotId) {
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
     * <p>{@code assignmentPlan} 非空时按固定运营职责建立 {@link ReviewAssignment}；
     * 为 null 时使用提交人建立单行分派。
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
            null,
            candidate.effectiveApplicableScope(),
            null,
            candidate.contentHash(),
            knowledgeVersionSourceRef(identity.identityCode(), candidate.versionNo()),
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
        KnowledgeReviewFeedbackType feedbackType = feedbackType(request);
        KnowledgeReviewFollowupAction followupAction = followupAction(request, feedbackType);
        CandidateClassification initial = candidateClassificationRepository.findByTenantIdAndId(tenantId, candidateId)
            .orElseThrow(() -> ApiException.notFound("知识候选 id=" + candidateId));
        identityRepository.findByTenantIdAndIdForUpdate(tenantId, initial.identityId())
            .orElseThrow(() -> ApiException.notFound("知识身份 id=" + initial.identityId()));
        CandidateClassification classification = candidateClassificationRepository.findByTenantIdAndId(
            tenantId, candidateId)
            .orElseThrow(() -> ApiException.notFound("知识候选 id=" + candidateId));
        if (classification.reviewStatus() != CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW) {
            throw new ApiException(ErrorCode.CONFLICT, "候选当前状态 " + classification.reviewStatus() + " 不可重复审核");
        }
        if (request.decision() == KnowledgeCandidateReviewDecision.RETURN
                && (request.reason() == null || request.reason().isBlank())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "退修须填写修订意见");
        }
        KnowledgeAssetVersion candidate = versionRepository.findByTenantIdAndId(
            tenantId, classification.candidateVersionId())
            .orElseThrow(() -> ApiException.notFound("知识版本 id=" + classification.candidateVersionId()));
        List<ReviewAssignment> assignments =
            reviewAssignmentRepository.findByTenantIdAndCandidateClassificationIdOrderByCreatedAtAscIdAsc(
                tenantId, classification.id());
        if (assignments.isEmpty()) {
            throw new ApiException(ErrorCode.CONFLICT, "候选缺少审核分派，不得直接作出结论");
        }
        List<ReviewAssignment> matchingPending = assignments.stream()
            .filter(assignment -> assignment.reviewStatus() == CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW)
            .filter(assignment -> matchesAssignment(assignment, actor))
            .toList();
        ReviewAssignment selectedAssignment = matchingPending.stream()
            .findFirst()
            .orElseThrow(() -> new ApiException(
                ErrorCode.CONFLICT, "当前操作者未命中待审核分派，不能代替他人签署"));

        if (request.decision() == KnowledgeCandidateReviewDecision.APPROVE) {
            ReviewAssignment approvedAssignment = decidedAssignment(
                selectedAssignment,
                CandidateReviewStatus.APPROVED,
                KnowledgeCandidateReviewDecision.APPROVE,
                request.reason(),
                feedbackType,
                followupAction,
                actor,
                now);
            reviewAssignmentRepository.save(approvedAssignment);
            List<ReviewAssignment> updatedAssignments = assignments.stream()
                .map(assignment -> assignment.id().equals(approvedAssignment.id())
                    ? approvedAssignment
                    : assignment)
                .toList();
            closeRemainingAssignments(
                updatedAssignments,
                approvedAssignment.id(),
                CandidateReviewStatus.APPROVED,
                "候选已由医疗引擎运营职责确认，其他历史席位关闭",
                actor,
                now);
            KnowledgeAssetVersion activated = activate(
                classification.identityId(),
                classification.candidateVersionId(),
                request.reason(),
                request.qualityGateRecordId());
            CandidateClassification approved = candidateClassificationRepository.save(classificationWithStatus(
                classification,
                CandidateReviewStatus.APPROVED,
                classification.basis(),
                now,
                actor));
            return new KnowledgeCandidateResponse(
                classification.identityId(),
                candidatePage(List.of(activated)),
                List.of(approved),
                true,
                "APPROVED",
                "候选审核通过，已转交权威版本原子替换流程");
        }
        if (request.decision() == KnowledgeCandidateReviewDecision.RETURN) {
            ReviewAssignment returnedAssignment = decidedAssignment(
                selectedAssignment,
                CandidateReviewStatus.RETURNED,
                KnowledgeCandidateReviewDecision.RETURN,
                request.reason(),
                feedbackType,
                followupAction,
                actor,
                now);
            reviewAssignmentRepository.save(returnedAssignment);
            closeRemainingAssignments(
                assignments,
                returnedAssignment.id(),
                CandidateReviewStatus.RETURNED,
                "同一候选已由分派审核人退修，当前席位关闭",
                actor,
                now);
            KnowledgeAssetVersion returning = candidate;
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
            return new KnowledgeCandidateResponse(
                classification.identityId(),
                candidatePage(List.of(savedDraft)),
                List.of(returned),
                true,
                "RETURNED",
                "候选已退修，退回生产者修订重提");
        }
        ReviewAssignment rejectedAssignment = decidedAssignment(
            selectedAssignment,
            CandidateReviewStatus.REJECTED,
            KnowledgeCandidateReviewDecision.REJECT,
            request.reason(),
            feedbackType,
            followupAction,
            actor,
            now);
        reviewAssignmentRepository.save(rejectedAssignment);
        closeRemainingAssignments(
            assignments,
            rejectedAssignment.id(),
            CandidateReviewStatus.REJECTED,
            "同一候选已由分派审核人拒绝，当前席位关闭",
            actor,
            now);
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
            Long qualityGateRecordId) {
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
        String organizationScope = target.effectiveOrganizationScope();
        String applicableScope = target.effectiveApplicableScope();
        Optional<KnowledgeAssetVersion> currentActiveOpt = versionRepository.findActiveByEffectiveScope(
            tenantId, identityId, organizationScope, applicableScope);
        VersionPublishEvidence publishEvidence = publicationQualityRecords.requirePublishEvidence(
            qualityGateRecordId, identityId, target);
        publishUnifiedVersion(
            identity,
            target,
            currentActiveOpt.orElse(null),
            normalizedReason,
            publishEvidence);

        // 3) 同一完整适用域内的当前 ACTIVE 版本（如有）→ SUPERSEDED
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
            KnowledgeAssetVersion currentActive,
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
            case PUBLISHED -> {
                // 已发布版本重复激活保持幂等，领域内容状态随后对齐。
            }
            case WITHDRAWN -> {
                if (target.status() != KnowledgeVersionStatus.SUPERSEDED) {
                    throw new ApiException(ErrorCode.CONFLICT, "统一知识版本已撤回，不能直接激活");
                }
                rollbackUnifiedVersion(identity, target, currentActive, assetVersion, reason);
            }
        }
    }

    private void rollbackUnifiedVersion(
            KnowledgeIdentity identity,
            KnowledgeAssetVersion rollbackTarget,
            KnowledgeAssetVersion currentActive,
            AssetVersion targetAssetVersion,
            String reason) {
        if (currentActive == null) {
            throw new ApiException(ErrorCode.CONFLICT, "知识版本回滚缺少当前权威版本");
        }
        AssetVersion currentAssetVersion = requireUnifiedAssetVersion(identity, currentActive);
        String rollbackReason = reason == null || reason.isBlank()
            ? "知识版本回滚至 " + rollbackTarget.versionNo()
            : reason.trim();
        releasePort.rollback(new VersionRollbackCommand(
            identity.tenantId(),
            VersionedAssetType.KNOWLEDGE,
            identity.identityCode(),
            currentAssetVersion.versionId(),
            targetAssetVersion.versionId(),
            currentAssetVersion.versionNo(),
            targetAssetVersion.versionNo(),
            rollbackReason,
            true,
            currentActor(),
            RequestContext.currentTraceId()
        ));
    }

    private AssetVersion requireUnifiedAssetVersion(
            KnowledgeIdentity identity,
            KnowledgeAssetVersion version) {
        Optional<AssetVersion> direct = assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
                identity.tenantId(),
                VersionedAssetType.KNOWLEDGE,
                identity.identityCode(),
                version.versionNo());
        if (direct.isPresent()) {
            return direct.get();
        }
        String sourceRef = knowledgeVersionSourceRef(identity.identityCode(), version.versionNo());
        AssetVersion linked = assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndSourceRef(
                identity.tenantId(),
                VersionedAssetType.KNOWLEDGE,
                identity.identityCode(),
                sourceRef)
            .orElseThrow(() -> new ApiException(
                ErrorCode.CONFLICT,
                "知识版本缺少统一资产版本登记: "
                    + identity.identityCode() + "@" + version.versionNo()));
        if (!version.contentHash().equals(linked.contentHash())) {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "知识版本统一资产登记内容指纹不一致: "
                    + identity.identityCode() + "@" + version.versionNo());
        }
        return linked;
    }

    private String knowledgeVersionSourceRef(String identityCode, String versionNo) {
        return "knowledge-version:" + identityCode + ":" + versionNo;
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

    private ReviewAssignment decidedAssignment(
            ReviewAssignment assignment,
            CandidateReviewStatus status,
            KnowledgeCandidateReviewDecision decision,
            String reason,
            KnowledgeReviewFeedbackType feedbackType,
            KnowledgeReviewFollowupAction followupAction,
            String actor,
            Instant now) {
        return new ReviewAssignment(
            assignment.id(),
            assignment.tenantId(),
            assignment.orgPath(),
            assignment.candidateClassificationId(),
            assignment.identityId(),
            assignment.candidateVersionId(),
            assignment.assignedTo(),
            status,
            decision,
            reason == null ? null : reason.trim(),
            feedbackType,
            followupAction,
            actor,
            now,
            assignment.createdAt(),
            assignment.createdBy(),
            now,
            actor);
    }

    private boolean matchesAssignment(ReviewAssignment assignment, String actor) {
        return RoleCode.fromCode(assignment.assignedTo())
            .map(role -> AuthenticatedRoleGuard.has(role) || hasEffectiveRole(role, actor))
            .orElseGet(() -> actor.equals(assignment.assignedTo()));
    }

    private boolean hasEffectiveRole(RoleCode role, String actor) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return effectivePermissions.resolve(authentication, RequestContext.currentOrgScope(), actor)
            .roleCodes()
            .contains(role.code());
    }

    private void closeRemainingAssignments(
            List<ReviewAssignment> assignments,
            Long decidedAssignmentId,
            CandidateReviewStatus terminalStatus,
            String reason,
            String actor,
            Instant now) {
        assignments.stream()
            .filter(assignment -> !assignment.id().equals(decidedAssignmentId))
            .filter(assignment -> assignment.reviewStatus() == CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW)
            .map(assignment -> closedAssignment(assignment, terminalStatus, reason, actor, now))
            .forEach(reviewAssignmentRepository::save);
    }

    private ReviewAssignment closedAssignment(
            ReviewAssignment assignment,
            CandidateReviewStatus status,
            String reason,
            String actor,
            Instant now) {
        return new ReviewAssignment(
            assignment.id(),
            assignment.tenantId(),
            assignment.orgPath(),
            assignment.candidateClassificationId(),
            assignment.identityId(),
            assignment.candidateVersionId(),
            assignment.assignedTo(),
            status,
            null,
            reason,
            null,
            null,
            null,
            now,
            assignment.createdAt(),
            assignment.createdBy(),
            now,
            actor);
    }

    private KnowledgeReviewFeedbackType feedbackType(KnowledgeCandidateReviewRequest request) {
        if (request.feedbackType() != null) {
            return request.feedbackType();
        }
        return switch (request.decision()) {
            case APPROVE -> KnowledgeReviewFeedbackType.ACCEPTED;
            case RETURN -> KnowledgeReviewFeedbackType.CONTENT_GAP;
            case REJECT -> KnowledgeReviewFeedbackType.NOT_ADOPTED;
        };
    }

    private KnowledgeReviewFollowupAction followupAction(
            KnowledgeCandidateReviewRequest request,
            KnowledgeReviewFeedbackType feedbackType) {
        if (request.followupAction() != null) {
            return request.followupAction();
        }
        return switch (feedbackType) {
            case ACCEPTED -> KnowledgeReviewFollowupAction.NONE;
            case CONTENT_GAP -> KnowledgeReviewFollowupAction.CREATE_REVISION_CANDIDATE;
            case SOURCE_BLANK -> KnowledgeReviewFollowupAction.REQUEST_SOURCE_EVIDENCE;
            case FALSE_POSITIVE -> KnowledgeReviewFollowupAction.MARK_FALSE_POSITIVE;
            case NOT_ADOPTED -> KnowledgeReviewFollowupAction.ARCHIVE_REJECTED;
        };
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
            AssetVersionStatus.WITHDRAWN,
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
        saveAffectedTask(invalidation, version, AffectedCaseTaskType.ASSET_DEPENDENCY_REVIEW,
            AffectedCaseTargetType.ASSET_DEPENDENCY, "asset-dependency/version:" + version.id(),
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
        OrgScope scope = new OrgScope(
            tenantId,
            context.groupId(),
            context.hospitalId(),
            context.campusId(),
            context.siteId(),
            context.departmentId(),
            null,
            context.specialtyId());
        return assetScopes.resolve(tenantId, scope).organizationPath();
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
