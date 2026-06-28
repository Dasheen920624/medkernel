package com.medkernel.engine.knowledge.production.initialization;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.knowledge.CandidateClassification;
import com.medkernel.engine.knowledge.CandidateClassificationRepository;
import com.medkernel.engine.knowledge.CandidateReviewStatus;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.engine.knowledge.KnowledgeCandidateResponse;
import com.medkernel.engine.knowledge.KnowledgeCandidateReviewDecision;
import com.medkernel.engine.knowledge.KnowledgeCandidateReviewRequest;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.KnowledgeVersionService;
import com.medkernel.engine.knowledge.KnowledgeVersionStatus;
import com.medkernel.engine.knowledge.SourceDocument;
import com.medkernel.engine.knowledge.SourceDocumentRepository;
import com.medkernel.engine.knowledge.SourceFragmentRepository;
import com.medkernel.engine.knowledge.SourceVersion;
import com.medkernel.engine.knowledge.SourceVersionRepository;
import com.medkernel.engine.knowledge.production.KnowledgeProducer;
import com.medkernel.engine.knowledge.production.KnowledgeProductionCandidate;
import com.medkernel.engine.knowledge.production.KnowledgeProductionCandidateRepository;
import com.medkernel.engine.knowledge.production.KnowledgeProductionJob;
import com.medkernel.engine.knowledge.production.KnowledgeProductionJobRepository;
import com.medkernel.engine.knowledge.production.gate.PublicationQualityRecord;
import com.medkernel.engine.knowledge.production.gate.PublicationQualityRecordRequest;
import com.medkernel.engine.knowledge.production.gate.PublicationQualityRecordService;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/** 初始化发行批次编排；复用现有候选、审核和发布链，不另建医学状态机。 */
@Service
public class KnowledgeInitializationService {

    private final SourceVersionRepository sourceVersions;
    private final SourceDocumentRepository sourceDocuments;
    private final SourceFragmentRepository sourceFragments;
    private final KnowledgeAssetVersionRepository versions;
    private final CandidateClassificationRepository classifications;
    private final KnowledgeProductionCandidateRepository productionCandidates;
    private final KnowledgeProductionJobRepository productionJobs;
    private final KnowledgeInitializationBatchRepository batches;
    private final KnowledgeInitializationItemRepository items;
    private final KnowledgeVersionService versionService;
    private final PublicationQualityRecordService publicationQualityRecords;
    private final KnowledgeInitializationCatalog catalog;
    private final KnowledgeInitializationManifestValidator validator;
    private final ObjectMapper json;

    public KnowledgeInitializationService(
            SourceVersionRepository sourceVersions,
            SourceDocumentRepository sourceDocuments,
            SourceFragmentRepository sourceFragments,
            KnowledgeAssetVersionRepository versions,
            CandidateClassificationRepository classifications,
            KnowledgeProductionCandidateRepository productionCandidates,
            KnowledgeProductionJobRepository productionJobs,
            KnowledgeInitializationBatchRepository batches,
            KnowledgeInitializationItemRepository items,
            KnowledgeVersionService versionService,
            PublicationQualityRecordService publicationQualityRecords,
            KnowledgeInitializationCatalog catalog,
            KnowledgeInitializationManifestValidator validator,
            ObjectMapper json) {
        this.sourceVersions = sourceVersions;
        this.sourceDocuments = sourceDocuments;
        this.sourceFragments = sourceFragments;
        this.versions = versions;
        this.classifications = classifications;
        this.productionCandidates = productionCandidates;
        this.productionJobs = productionJobs;
        this.batches = batches;
        this.items = items;
        this.versionService = versionService;
        this.publicationQualityRecords = publicationQualityRecords;
        this.catalog = catalog;
        this.validator = validator;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public KnowledgeInitializationBatchPreview preview(KnowledgeInitializationBatchDraftRequest request) {
        ResolvedManifest resolved = resolveManifest(requireCurrentTenant(), request);
        InitializationManifestValidation validation = validator.validate(resolved.draft());
        return new KnowledgeInitializationBatchPreview(
            validation,
            (int) resolved.items().stream()
                .map(item -> item.draftItem().sourceVersionId())
                .distinct()
                .count(),
            resolved.items().size(),
            countRisk(resolved.items(), KnowledgeRiskLevel.LOW),
            countRisk(resolved.items(), KnowledgeRiskLevel.MEDIUM),
            countRisk(resolved.items(), KnowledgeRiskLevel.HIGH));
    }

    @Transactional
    public KnowledgeInitializationBatchView create(KnowledgeInitializationBatchCreateRequest request) {
        String tenantId = requireCurrentTenant();
        KnowledgeInitializationBatchDraftRequest draftRequest = request.draft();
        String idempotencyKey = draftRequest.idempotencyKey().trim();
        return batches.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)
            .map(batch -> {
                if (!batch.sourceManifestHash().equals(request.expectedSourceManifestHash())
                        || !batch.candidateManifestHash().equals(request.expectedCandidateManifestHash())
                        || !batch.overallHash().equals(request.expectedOverallHash())) {
                    throw new ApiException(ErrorCode.CONFLICT, "幂等键已绑定不同初始化清单");
                }
                return view(tenantId, batch);
            })
            .orElseGet(() -> createNew(tenantId, request));
    }

    @Transactional(readOnly = true)
    public List<KnowledgeInitializationBatch> list() {
        return batches.findByTenantIdOrderByCreatedAtDescIdDesc(requireCurrentTenant());
    }

    @Transactional(readOnly = true)
    public KnowledgeInitializationBatchView get(String batchCode) {
        String tenantId = requireCurrentTenant();
        KnowledgeInitializationBatch batch = batches.findByTenantIdAndBatchCode(tenantId, batchCode)
            .orElseThrow(() -> ApiException.notFound("初始化批次 " + batchCode));
        return view(tenantId, batch);
    }

    @Transactional
    public KnowledgeInitializationBatchView approveLow(
            String batchCode,
            KnowledgeInitializationBatchApproveRequest request) {
        String tenantId = requireCurrentTenant();
        String actor = currentActor();
        KnowledgeInitializationBatch batch = batches.findByTenantIdAndBatchCode(tenantId, batchCode)
            .orElseThrow(() -> ApiException.notFound("初始化批次 " + batchCode));
        if (!batch.overallHash().equals(request.expectedOverallHash())) {
            throw new ApiException(ErrorCode.CONFLICT, "初始化批次摘要已变化，请重新预览");
        }
        String bulkIdempotencyKey = request.idempotencyKey().trim();
        if (bulkIdempotencyKey.equals(batch.lastBulkIdempotencyKey())) {
            return view(tenantId, batch);
        }
        if (batch.status() != KnowledgeInitializationBatchStatus.IN_REVIEW) {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "初始化批次当前状态不允许 LOW 批审：" + batch.status());
        }
        List<KnowledgeInitializationItem> batchItems =
            items.findByTenantIdAndBatchIdOrderBySequenceNoAscIdAsc(tenantId, batch.id());
        List<KnowledgeInitializationItem> pendingLow = batchItems.stream()
            .filter(item -> item.status() == KnowledgeInitializationItemStatus.PENDING_REVIEW)
            .filter(item -> item.riskLevel() == KnowledgeRiskLevel.LOW)
            .toList();
        if (pendingLow.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "批审仅 LOW 候选可执行，当前无待审 LOW 条目");
        }
        for (KnowledgeInitializationItem item : pendingLow) {
            requireSourceCurrent(tenantId, item);
            ParsedCandidateRef candidateRef = parseCandidateRef(item.candidateRef());
            KnowledgeAssetVersion candidate = versions.findByTenantIdAndIdentityIdAndVersionNo(
                tenantId, candidateRef.identityId(), candidateRef.versionNo())
                .orElseThrow(() -> ApiException.notFound("知识候选引用 " + item.candidateRef()));
            KnowledgeProductionCandidate lineage = productionCandidates.findByTenantIdAndCandidateRefIn(
                tenantId, List.of(item.candidateRef())).stream()
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("候选生产血缘 " + item.candidateRef()));
            PublicationQualityRecord qualityRecord = publicationQualityRecords.create(
                lineage.jobCode(),
                new PublicationQualityRecordRequest(
                    item.candidateRef(), candidateRef.identityId(), candidate.id()));
            KnowledgeCandidateResponse response = versionService.reviewCandidate(
                item.candidateClassificationId(),
                batchReviewRequest(batch, item, request, tenantId, actor, qualityRecord.id()));
            if (!"APPROVED".equals(response.reasonCode())) {
                throw new ApiException(ErrorCode.CONFLICT, "LOW 候选未完成批准：" + item.canonicalId());
            }
            items.save(withItemStatus(item, KnowledgeInitializationItemStatus.APPROVED, actor));
        }
        Instant now = Instant.now();
        Set<Long> newlyApprovedItemIds = new HashSet<>(
            pendingLow.stream().map(KnowledgeInitializationItem::id).toList());
        boolean anyBlocked = batchItems.stream()
            .anyMatch(item -> item.status() == KnowledgeInitializationItemStatus.BLOCKED);
        boolean allApproved = batchItems.stream()
            .allMatch(item -> item.status() == KnowledgeInitializationItemStatus.APPROVED
                || newlyApprovedItemIds.contains(item.id()));
        KnowledgeInitializationBatchStatus nextStatus;
        if (anyBlocked) {
            nextStatus = KnowledgeInitializationBatchStatus.BLOCKED;
        } else if (allApproved) {
            nextStatus = KnowledgeInitializationBatchStatus.COMPLETE;
        } else {
            nextStatus = KnowledgeInitializationBatchStatus.IN_REVIEW;
        }
        KnowledgeInitializationBatch updated = withBatchStatus(
            batch,
            nextStatus,
            bulkIdempotencyKey,
            now,
            actor);
        return view(tenantId, batches.save(updated));
    }

    @Transactional
    public KnowledgeInitializationBatchView refresh(String batchCode) {
        String tenantId = requireCurrentTenant();
        String actor = currentActor();
        KnowledgeInitializationBatch batch = batches.findByTenantIdAndBatchCode(tenantId, batchCode)
            .orElseThrow(() -> ApiException.notFound("初始化批次 " + batchCode));
        List<KnowledgeInitializationItem> reconciled = items
            .findByTenantIdAndBatchIdOrderBySequenceNoAscIdAsc(tenantId, batch.id())
            .stream()
            .map(item -> reconcileItem(tenantId, item, actor))
            .toList();
        KnowledgeInitializationBatchStatus status;
        if (reconciled.stream().anyMatch(item -> item.status() == KnowledgeInitializationItemStatus.BLOCKED)) {
            status = KnowledgeInitializationBatchStatus.BLOCKED;
        } else if (reconciled.stream().allMatch(
                    item -> item.status() == KnowledgeInitializationItemStatus.APPROVED)) {
            status = KnowledgeInitializationBatchStatus.COMPLETE;
        } else {
            status = KnowledgeInitializationBatchStatus.IN_REVIEW;
        }
        KnowledgeInitializationBatch updated = batches.save(withBatchReviewStatus(batch, status, actor));
        return new KnowledgeInitializationBatchView(updated, reconciled);
    }

    private KnowledgeInitializationBatchView createNew(
            String tenantId,
            KnowledgeInitializationBatchCreateRequest request) {
        ResolvedManifest resolved = resolveManifest(tenantId, request.draft());
        InitializationManifestValidation validation = validator.validate(resolved.draft());
        requireExpectedHashes(validation, request);
        KnowledgeInitializationBatchPreview preview = previewFrom(resolved, validation);
        Instant now = Instant.now();
        String actor = currentActor();
        KnowledgeInitializationBatch saved = batches.save(new KnowledgeInitializationBatch(
            null,
            tenantId,
            request.draft().batchCode().trim(),
            request.draft().releaseType(),
            request.draft().releaseVersion(),
            request.draft().foundationReleaseVersion(),
            request.draft().phase(),
            KnowledgeInitializationBatchStatus.IN_REVIEW,
            validation.sourceManifestHash(),
            validation.candidateManifestHash(),
            validation.overallHash(),
            preview.sourceCount(),
            preview.candidateCount(),
            preview.lowCount(),
            preview.mediumCount(),
            preview.highCount(),
            toJson(request.draft().coverage()),
            request.draft().templateVersion(),
            normalize(request.draft().modelVersion()),
            request.draft().summary().trim(),
            request.draft().idempotencyKey().trim(),
            null,
            null,
            now,
            actor,
            now,
            actor));
        if (saved.id() == null) {
            throw new ApiException(ErrorCode.CONFLICT, "初始化批次保存后缺少 id");
        }
        List<KnowledgeInitializationItem> savedItems = new ArrayList<>();
        int sequence = 1;
        for (ResolvedItem resolvedItem : resolved.items()) {
            InitializationManifestDraftItem item = resolvedItem.draftItem();
            savedItems.add(items.save(new KnowledgeInitializationItem(
                null,
                tenantId,
                saved.id(),
                sequence++,
                item.catalogCode(),
                item.assetType(),
                item.canonicalId(),
                item.namespace(),
                item.assetVersion(),
                item.sourceVersionId(),
                item.sourceHash(),
                item.candidateRef(),
                resolvedItem.classificationId(),
                item.candidateContentHash(),
                item.riskLevel(),
                item.generatedByModel() ? "Y" : "N",
                toJson(item.dependencyCanonicalIds()),
                governanceJson(item),
                item.changeType(),
                item.replacementCanonicalId(),
                item.effectiveTo(),
                KnowledgeInitializationItemStatus.PENDING_REVIEW,
                now,
                actor,
                now,
                actor)));
        }
        return new KnowledgeInitializationBatchView(saved, savedItems);
    }

    private ResolvedManifest resolveManifest(
            String tenantId,
            KnowledgeInitializationBatchDraftRequest request) {
        List<ResolvedItem> resolved = new ArrayList<>();
        for (KnowledgeInitializationEntryRequest entry : request.entries()) {
            resolved.add(resolveEntry(tenantId, entry));
        }
        boolean foundationComplete = request.releaseType() == InitializationReleaseType.FOUNDATION
            || batches.findFirstByTenantIdAndReleaseTypeAndReleaseVersionAndPhaseAndStatusOrderByIdDesc(
                tenantId,
                InitializationReleaseType.FOUNDATION,
                request.foundationReleaseVersion(),
                InitializationPhase.F8,
                KnowledgeInitializationBatchStatus.COMPLETE).isPresent();
        InitializationManifestDraft draft = new InitializationManifestDraft(
            request.releaseType(),
            request.releaseVersion(),
            request.foundationReleaseVersion(),
            request.phase(),
            request.templateVersion(),
            request.modelVersion(),
            request.summary(),
            request.declaredSourceFileCount(),
            request.declaredEntryCount(),
            request.coverage(),
            Set.copyOf(items.findCompletedCanonicalIds(tenantId)),
            foundationComplete,
            resolved.stream().map(ResolvedItem::draftItem).toList());
        return new ResolvedManifest(draft, resolved);
    }

    private ResolvedItem resolveEntry(
            String tenantId,
            KnowledgeInitializationEntryRequest entry) {
        ParsedCandidateRef ref = parseCandidateRef(entry.candidateRef());
        KnowledgeAssetVersion candidate = versions.findByTenantIdAndIdentityIdAndVersionNo(
            tenantId, ref.identityId(), ref.versionNo())
            .orElseThrow(() -> ApiException.notFound("知识候选引用 " + entry.candidateRef()));
        if (candidate.status() != KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW) {
            throw new ApiException(ErrorCode.CONFLICT, "初始化批次只能纳入待审核候选");
        }
        CandidateClassification classification = classifications.findByTenantIdAndCandidateVersionId(
            tenantId, candidate.id())
            .orElseThrow(() -> ApiException.notFound("候选分类 versionId=" + candidate.id()));
        if (classification.reviewStatus() != CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW) {
            throw new ApiException(ErrorCode.CONFLICT, "候选分类已不在待审核状态");
        }
        List<KnowledgeProductionCandidate> lineage = productionCandidates.findByTenantIdAndCandidateRefIn(
            tenantId, List.of(entry.candidateRef().trim()));
        if (lineage.size() != 1) {
            throw new ApiException(ErrorCode.CONFLICT, "候选生产血缘缺失或不唯一");
        }
        KnowledgeProductionCandidate productionCandidate = lineage.getFirst();
        String canonicalId = entry.canonicalId().trim();
        if (!canonicalId.equals(productionCandidate.assetIdentity())) {
            throw new ApiException(ErrorCode.CONFLICT, "canonical ID 与生产血缘身份不一致");
        }
        if (!candidate.contentHash().equals(productionCandidate.contentHash())) {
            throw new ApiException(ErrorCode.CONFLICT, "候选正文摘要与生产血缘不一致");
        }
        if (candidate.riskLevel() != productionCandidate.riskLevel()) {
            throw new ApiException(ErrorCode.CONFLICT, "候选风险等级与生产血缘不一致");
        }
        KnowledgeProductionJob job = productionJobs.findByTenantIdAndJobCode(
            tenantId, productionCandidate.jobCode())
            .orElseThrow(() -> ApiException.notFound("知识生产 job=" + productionCandidate.jobCode()));
        if (candidate.sourceVersionId() == null) {
            throw new ApiException(ErrorCode.CONFLICT, "初始化候选未绑定来源版本");
        }
        SourceVersion sourceVersion = sourceVersions.findByTenantIdAndId(tenantId, candidate.sourceVersionId())
            .orElseThrow(() -> ApiException.notFound("来源版本 id=" + candidate.sourceVersionId()));
        requireCompleteSource(tenantId, sourceVersion);
        if (candidate.anchors() == null || candidate.anchors().isBlank()) {
            throw new ApiException(ErrorCode.CONFLICT, "候选缺少来源锚点");
        }
        boolean anchorExists = sourceFragments
            .findByTenantIdAndSourceVersionIdOrderByAnchorPathAsc(tenantId, sourceVersion.id())
            .stream()
            .anyMatch(fragment -> candidate.anchors().equals(fragment.anchorPath()));
        if (!anchorExists) {
            throw new ApiException(ErrorCode.CONFLICT, "候选声明的来源锚点不存在");
        }
        catalog.find(entry.catalogCode())
            .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "未知 KNOWGEN 目录项"));
        requireStableEvolution(tenantId, entry, job.assetType());
        InitializationManifestDraftItem draftItem = new InitializationManifestDraftItem(
            entry.catalogCode(),
            job.assetType(),
            canonicalId,
            entry.namespace().trim(),
            entry.assetVersion().trim(),
            sourceVersion.id(),
            sourceVersion.contentHash(),
            entry.candidateRef().trim(),
            candidate.contentHash(),
            candidate.riskLevel(),
            job.producer() != KnowledgeProducer.MANUAL,
            entry.dependencyCanonicalIds(),
            entry.parentCanonicalId(),
            entry.unitDimension(),
            entry.conversionTargetCanonicalId(),
            entry.sourcePolicy(),
            entry.reviewPolicy(),
            entry.testEvidenceRef(),
            entry.ownerRole(),
            entry.runtimeConsumers(),
            entry.rollbackStrategy(),
            entry.changeType(),
            entry.replacementCanonicalId(),
            entry.effectiveTo());
        return new ResolvedItem(draftItem, classification.id());
    }

    private void requireStableEvolution(
            String tenantId,
            KnowledgeInitializationEntryRequest entry,
            com.medkernel.engine.versioning.VersionedAssetType assetType) {
        String canonicalId = entry.canonicalId().trim();
        List<KnowledgeInitializationItem> history = items.findCompletedHistory(tenantId, canonicalId);
        if (history.isEmpty()) {
            if (entry.changeType() != InitializationChangeType.NEW) {
                throw new ApiException(
                    ErrorCode.CONFLICT,
                    "canonical 首个完成版本必须声明 NEW：" + canonicalId);
            }
            return;
        }
        KnowledgeInitializationItem previous = history.getFirst();
        if (entry.changeType() == InitializationChangeType.NEW) {
            throw new ApiException(ErrorCode.CONFLICT, "canonical 已有完成版本不得声明 NEW：" + canonicalId);
        }
        if (previous.assetType() != assetType) {
            throw new ApiException(ErrorCode.CONFLICT, "canonical 资产类型禁止漂移：" + canonicalId);
        }
        if (!previous.namespace().equals(entry.namespace().trim())) {
            throw new ApiException(ErrorCode.CONFLICT, "canonical 命名空间禁止漂移：" + canonicalId);
        }
        SemanticVersion prior = SemanticVersion.parse(previous.assetVersion(), canonicalId);
        SemanticVersion next = SemanticVersion.parse(entry.assetVersion().trim(), canonicalId);
        if (next.compareTo(prior) <= 0) {
            throw new ApiException(ErrorCode.CONFLICT, "canonical 资产版本必须严格递增：" + canonicalId);
        }
        switch (entry.changeType()) {
            case PATCH_COMPATIBLE -> {
                if (next.major() != prior.major() || next.minor() != prior.minor()) {
                    throw new ApiException(
                        ErrorCode.CONFLICT,
                        "PATCH_COMPATIBLE 必须只递增 patch：" + canonicalId);
                }
            }
            case MINOR_COMPATIBLE -> {
                if (next.major() != prior.major() || next.minor() <= prior.minor()) {
                    throw new ApiException(
                        ErrorCode.CONFLICT,
                        "MINOR_COMPATIBLE 必须在同一 major 递增 minor：" + canonicalId);
                }
            }
            case MAJOR_BREAKING -> {
                if (next.major() <= prior.major()) {
                    throw new ApiException(
                        ErrorCode.CONFLICT,
                        "MAJOR_BREAKING 必须递增 major：" + canonicalId);
                }
            }
            case DEPRECATION -> {
                // 废止仍需产生严格递增的可审计版本，替代关系由清单校验器强制。
            }
            case NEW -> throw new IllegalStateException("已有完成版本不得进入 NEW 分支");
        }
    }

    private int countRisk(List<ResolvedItem> entries, KnowledgeRiskLevel risk) {
        return (int) entries.stream()
            .filter(entry -> entry.draftItem().riskLevel() == risk)
            .count();
    }

    private KnowledgeInitializationBatchPreview previewFrom(
            ResolvedManifest resolved,
            InitializationManifestValidation validation) {
        return new KnowledgeInitializationBatchPreview(
            validation,
            (int) resolved.items().stream().map(item -> item.draftItem().sourceVersionId()).distinct().count(),
            resolved.items().size(),
            countRisk(resolved.items(), KnowledgeRiskLevel.LOW),
            countRisk(resolved.items(), KnowledgeRiskLevel.MEDIUM),
            countRisk(resolved.items(), KnowledgeRiskLevel.HIGH));
    }

    private void requireExpectedHashes(
            InitializationManifestValidation validation,
            KnowledgeInitializationBatchCreateRequest request) {
        if (!validation.sourceManifestHash().equals(request.expectedSourceManifestHash())
                || !validation.candidateManifestHash().equals(request.expectedCandidateManifestHash())
                || !validation.overallHash().equals(request.expectedOverallHash())) {
            throw new ApiException(ErrorCode.CONFLICT, "初始化发行明细校验码与预览不一致");
        }
    }

    private KnowledgeInitializationBatchView view(String tenantId, KnowledgeInitializationBatch batch) {
        return new KnowledgeInitializationBatchView(
            batch,
            items.findByTenantIdAndBatchIdOrderBySequenceNoAscIdAsc(tenantId, batch.id()));
    }

    private void requireSourceCurrent(String tenantId, KnowledgeInitializationItem item) {
        SourceVersion source = sourceVersions.findByTenantIdAndId(tenantId, item.sourceVersionId())
            .orElseThrow(() -> ApiException.notFound("来源版本 id=" + item.sourceVersionId()));
        requireCompleteSource(tenantId, source);
        if (!item.sourceHash().equals(source.contentHash())) {
            throw new ApiException(ErrorCode.CONFLICT, "来源漂移，阻断整批激活");
        }
    }

    private void requireCompleteSource(String tenantId, SourceVersion version) {
        if (blank(version.versionNo()) || !hash(version.contentHash()) || blank(version.fileUri())) {
            throw new ApiException(
                ErrorCode.BAD_REQUEST,
                "来源版本追溯信息不完整：必须包含版本号、64 位文件摘要和受管原件 URI");
        }
        SourceDocument document = sourceDocuments.findByTenantIdAndId(tenantId, version.sourceDocumentId())
            .orElseThrow(() -> ApiException.notFound("来源文档 id=" + version.sourceDocumentId()));
        if (document.authorityLevel() == null || blank(document.authorityBasis())
                || blank(document.title()) || blank(document.publisher()) || blank(document.license())) {
            throw new ApiException(
                ErrorCode.BAD_REQUEST,
                "来源文档元数据不完整：必须包含权威等级、依据、标题、发布机构和许可");
        }
    }

    private KnowledgeInitializationItem reconcileItem(
            String tenantId,
            KnowledgeInitializationItem item,
            String actor) {
        KnowledgeInitializationItemStatus status = reconciliationStatus(tenantId, item);
        if (status == item.status()) {
            return item;
        }
        return items.save(withItemStatus(item, status, actor));
    }

    private KnowledgeInitializationItemStatus reconciliationStatus(
            String tenantId,
            KnowledgeInitializationItem item) {
        try {
            requireSourceCurrent(tenantId, item);
        } catch (ApiException exception) {
            return KnowledgeInitializationItemStatus.BLOCKED;
        }
        CandidateClassification classification = classifications.findByTenantIdAndId(
            tenantId, item.candidateClassificationId()).orElse(null);
        if (classification == null) {
            return KnowledgeInitializationItemStatus.BLOCKED;
        }
        return switch (classification.reviewStatus()) {
            case APPROVED -> KnowledgeInitializationItemStatus.APPROVED;
            case PENDING_REPLACEMENT_REVIEW -> KnowledgeInitializationItemStatus.PENDING_REVIEW;
            case DUPLICATE_SKIPPED, REJECTED, RETURNED -> KnowledgeInitializationItemStatus.BLOCKED;
        };
    }

    private KnowledgeCandidateReviewRequest batchReviewRequest(
            KnowledgeInitializationBatch batch,
            KnowledgeInitializationItem item,
            KnowledgeInitializationBatchApproveRequest request,
            String tenantId,
            String actor,
            Long qualityGateRecordId) {
        return new KnowledgeCandidateReviewRequest(
            request.idempotencyKey().trim() + ":" + item.id(),
            RequestContext.currentTraceId(),
            tenantId,
            null,
            null,
            null,
            null,
            null,
            null,
            actor,
            authenticatedRoleCodes(),
            KnowledgeCandidateReviewDecision.APPROVE,
            request.reason(),
            qualityGateRecordId);
    }

    private List<String> authenticatedRoleCodes() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "初始化批审缺少已认证身份");
        }
        List<String> roleCodes = authentication.getAuthorities().stream()
            .map(authority -> RoleCode.fromAuthority(authority.getAuthority()).orElse(null))
            .filter(role -> role != null)
            .map(RoleCode::code)
            .distinct()
            .toList();
        if (roleCodes.isEmpty()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "初始化批审缺少标准职责角色");
        }
        return roleCodes;
    }

    private KnowledgeInitializationItem withItemStatus(
            KnowledgeInitializationItem item,
            KnowledgeInitializationItemStatus status,
            String actor) {
        return new KnowledgeInitializationItem(
            item.id(), item.tenantId(), item.batchId(), item.sequenceNo(), item.catalogCode(), item.assetType(),
            item.canonicalId(), item.namespace(), item.assetVersion(), item.sourceVersionId(), item.sourceHash(),
            item.candidateRef(), item.candidateClassificationId(), item.candidateContentHash(), item.riskLevel(),
            item.generatedByModelFlag(), item.dependenciesJson(), item.governanceJson(), item.changeType(),
            item.replacementCanonicalId(), item.effectiveTo(), status, item.createdAt(), item.createdBy(),
            Instant.now(), actor);
    }

    private KnowledgeInitializationBatch withBatchStatus(
            KnowledgeInitializationBatch batch,
            KnowledgeInitializationBatchStatus status,
            String bulkIdempotencyKey,
            Instant now,
            String actor) {
        return new KnowledgeInitializationBatch(
            batch.id(), batch.tenantId(), batch.batchCode(), batch.releaseType(), batch.releaseVersion(),
            batch.foundationReleaseVersion(), batch.phase(), status, batch.sourceManifestHash(),
            batch.candidateManifestHash(), batch.overallHash(), batch.sourceCount(), batch.candidateCount(),
            batch.lowCount(), batch.mediumCount(), batch.highCount(), batch.coverageJson(),
            batch.templateVersion(), batch.modelVersion(), batch.summary(), batch.idempotencyKey(),
            bulkIdempotencyKey, now, batch.createdAt(), batch.createdBy(), now, actor);
    }

    private KnowledgeInitializationBatch withBatchReviewStatus(
            KnowledgeInitializationBatch batch,
            KnowledgeInitializationBatchStatus status,
            String actor) {
        Instant now = Instant.now();
        return new KnowledgeInitializationBatch(
            batch.id(), batch.tenantId(), batch.batchCode(), batch.releaseType(), batch.releaseVersion(),
            batch.foundationReleaseVersion(), batch.phase(), status, batch.sourceManifestHash(),
            batch.candidateManifestHash(), batch.overallHash(), batch.sourceCount(), batch.candidateCount(),
            batch.lowCount(), batch.mediumCount(), batch.highCount(), batch.coverageJson(),
            batch.templateVersion(), batch.modelVersion(), batch.summary(), batch.idempotencyKey(),
            batch.lastBulkIdempotencyKey(), batch.lastBulkAt(), batch.createdAt(), batch.createdBy(), now, actor);
    }

    private String governanceJson(InitializationManifestDraftItem item) {
        return toJson(Map.of(
            "sourcePolicy", item.sourcePolicy(),
            "reviewPolicy", item.reviewPolicy(),
            "testEvidenceRef", item.testEvidenceRef(),
            "ownerRole", item.ownerRole(),
            "runtimeConsumers", item.runtimeConsumers(),
            "rollbackStrategy", item.rollbackStrategy()));
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "初始化发行清单序列化失败", exception);
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private boolean hash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private ParsedCandidateRef parseCandidateRef(String candidateRef) {
        if (candidateRef == null || candidateRef.isBlank() || !candidateRef.trim().startsWith("kv:")) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "候选引用格式必须为 kv:{identityId}:{versionNo}");
        }
        String normalized = candidateRef.trim();
        int separator = normalized.indexOf(':', 3);
        if (separator < 0 || separator == normalized.length() - 1) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "候选引用格式必须为 kv:{identityId}:{versionNo}");
        }
        try {
            return new ParsedCandidateRef(
                Long.valueOf(normalized.substring(3, separator)),
                normalized.substring(separator + 1));
        } catch (NumberFormatException exception) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "候选引用身份 id 必须是数字", exception);
        }
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
            .filter(actor -> !actor.isBlank())
            .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "缺少初始化发行操作者"));
    }

    private record SemanticVersion(int major, int minor, int patch)
        implements Comparable<SemanticVersion> {

        private static SemanticVersion parse(String value, String canonicalId) {
            if (value == null || !value.matches("\\d+\\.\\d+\\.\\d+")) {
                throw new ApiException(
                    ErrorCode.BAD_REQUEST,
                    "canonical 资产版本必须使用 major.minor.patch：" + canonicalId);
            }
            String[] parts = value.split("\\.");
            try {
                return new SemanticVersion(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
            } catch (NumberFormatException exception) {
                throw new ApiException(
                    ErrorCode.BAD_REQUEST,
                    "canonical 资产版本超出支持范围：" + canonicalId,
                    exception);
            }
        }

        @Override
        public int compareTo(SemanticVersion other) {
            int majorResult = Integer.compare(major, other.major);
            if (majorResult != 0) {
                return majorResult;
            }
            int minorResult = Integer.compare(minor, other.minor);
            return minorResult != 0 ? minorResult : Integer.compare(patch, other.patch);
        }
    }

    private record ParsedCandidateRef(Long identityId, String versionNo) {
    }

    private record ResolvedItem(InitializationManifestDraftItem draftItem, Long classificationId) {
    }

    private record ResolvedManifest(InitializationManifestDraft draft, List<ResolvedItem> items) {
    }
}
