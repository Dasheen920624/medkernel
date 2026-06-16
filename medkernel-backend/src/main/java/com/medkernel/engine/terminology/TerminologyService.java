package com.medkernel.engine.terminology;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.medkernel.engine.versioning.PlatformAuthority;
import com.medkernel.engine.versioning.RolloutStrategy;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * GA-ENG-API-04 字典映射应用服务：分页查询、候选生成、候选确认、冲突处置与运行时解析。
 *
 * <p>所有写操作都在 {@link Transactional} 事务内推进；
 * 租户上下文从 {@link RequestContext#currentOrgScope()} 获取，缺失时直接抛
 * {@link com.medkernel.shared.api.error.ApiException#tenantMissing}。
 */
@Service
public class TerminologyService {
    private static final Logger log = LoggerFactory.getLogger(TerminologyService.class);
    private static final int CANDIDATE_GENERATION_BATCH_SIZE = 500;
    private static final String CANDIDATE_PAGE_PREFIX = "/api/v1/engine/terminology/mappings/candidates";

    private final StandardTermRepository standardTermRepository;
    private final LocalTermRepository localTermRepository;
    private final TermMappingRepository mappingRepository;
    private final EffectiveTermMappingResolver effectiveMappings;
    private final MappingCandidateRepository candidateRepository;
    private final MappingConflictRepository conflictRepository;
    private final HighRiskRuleRepository highRiskRuleRepository;
    private final TerminologyCandidateGenerationJobRepository generationJobRepository;
    private final Executor terminologyCandidateGenerationExecutor;

    public TerminologyService(StandardTermRepository standardTermRepository,
                              LocalTermRepository localTermRepository,
                              TermMappingRepository mappingRepository,
                              EffectiveTermMappingResolver effectiveMappings,
                              MappingCandidateRepository candidateRepository,
                              MappingConflictRepository conflictRepository,
                              HighRiskRuleRepository highRiskRuleRepository,
                              TerminologyCandidateGenerationJobRepository generationJobRepository,
                              @Qualifier("terminologyCandidateGenerationExecutor")
                              Executor terminologyCandidateGenerationExecutor) {
        this.standardTermRepository = standardTermRepository;
        this.localTermRepository = localTermRepository;
        this.mappingRepository = mappingRepository;
        this.effectiveMappings = effectiveMappings;
        this.candidateRepository = candidateRepository;
        this.conflictRepository = conflictRepository;
        this.highRiskRuleRepository = highRiskRuleRepository;
        this.generationJobRepository = generationJobRepository;
        this.terminologyCandidateGenerationExecutor = terminologyCandidateGenerationExecutor;
    }

    /**
     * 登记标准术语。重复业务键按幂等更新处理，不生成重复标准码。
     */
    @Transactional
    public StandardTerm registerStandardTerm(StandardTermRegistrationRequest request) {
        String tenantId = requireValidatedTenant(request.context());
        String userId = currentUserId();
        Instant now = Instant.now();
        Optional<StandardTerm> existing = standardTermRepository
            .findByTenantIdAndStandardSystemAndTermCodeAndVersionNo(
                tenantId, request.standardSystem(), request.termCode(), request.versionNo());
        StandardTerm current = existing.orElse(null);
        return standardTermRepository.save(new StandardTerm(
            current == null ? null : current.id(),
            tenantId,
            request.standardSystem(),
            request.termCode(),
            request.category(),
            request.displayName(),
            normalizedOrFallback(request.normalizedName(), request.displayName(), request.termCode()),
            request.versionNo(),
            StandardTermStatus.ACTIVE,
            request.sourceVersionId(),
            request.evidenceText(),
            current == null ? now : current.createdAt(),
            current == null ? userId : current.createdBy(),
            now,
            userId
        ));
    }

    /**
     * 登记院内术语。已映射条目重复上报时保留 MAPPED 状态，只刷新名称和最后出现时间。
     */
    @Transactional
    public LocalTerm registerLocalTerm(LocalTermRegistrationRequest request) {
        String tenantId = requireValidatedTenant(request.context());
        String userId = currentUserId();
        Instant now = Instant.now();
        Optional<LocalTerm> existing = localTermRepository
            .findByTenantIdAndSourceSystemAndLocalCodeAndCategory(
                tenantId, request.sourceSystem(), request.localCode(), request.category());
        LocalTerm current = existing.orElse(null);
        return localTermRepository.save(new LocalTerm(
            current == null ? null : current.id(),
            tenantId,
            request.sourceSystem(),
            request.localCode(),
            request.category(),
            request.localName(),
            normalizedOrFallback(request.normalizedName(), request.localName(), request.localCode()),
            request.localDepartmentId(),
            current == null ? LocalTermStatus.UNMAPPED : current.status(),
            current == null ? now : current.firstSeenAt(),
            now,
            current == null ? now : current.createdAt(),
            current == null ? userId : current.createdBy(),
            now,
            userId
        ));
    }

    /**
     * 幂等同步院内业务系统本地术语，禁用时保留既有映射和审计历史。
     *
     * @return 本地术语主键
     */
    @Transactional
    public String syncLocalTerm(LocalTermSyncCommand command) {
        String tenantId = requireCurrentTenant();
        if (command == null || command.sourceSystem() == null || command.sourceSystem().isBlank()
                || command.localCode() == null || command.localCode().isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "院内字典同步缺少来源系统或本地编码");
        }
        String sourceActor = requireExternalSourceActor(command.sourceSystem());
        TermCategory category;
        try {
            category = TermCategory.valueOf(command.category().trim().toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "院内字典分类不受支持");
        }
        Optional<LocalTerm> existing = localTermRepository
            .findByTenantIdAndSourceSystemAndLocalCodeAndCategory(
                tenantId, command.sourceSystem().trim(), command.localCode().trim(), category);
        LocalTerm current = existing.orElse(null);
        if (current != null && !sourceActor.equals(current.createdBy())) {
            throw ApiException.conflict("院内字典编码已由人工或其他来源维护，不能被当前来源接管");
        }
        if (command.disable() && current == null) {
            throw ApiException.notFound("院内字典 " + command.localCode());
        }
        if (!command.disable() && (command.localName() == null || command.localName().isBlank())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "院内字典同步缺少名称");
        }
        Instant now = Instant.now();
        LocalTermStatus status = command.disable()
            ? LocalTermStatus.DISABLED
            : current != null && current.status() == LocalTermStatus.MAPPED
                ? LocalTermStatus.MAPPED
                : LocalTermStatus.UNMAPPED;
        LocalTerm saved = localTermRepository.save(new LocalTerm(
            current == null ? null : current.id(),
            tenantId,
            command.sourceSystem().trim(),
            command.localCode().trim(),
            category,
            command.disable() ? current.localName() : command.localName().trim(),
            command.disable()
                ? current.normalizedName()
                : normalizedOrFallback(command.normalizedName(), command.localName(), command.localCode()),
            command.disable() ? current.departmentId() : blankToNull(command.departmentCode()),
            status,
            current == null ? now : current.firstSeenAt(),
            now,
            current == null ? now : current.createdAt(),
            current == null ? currentUserId() : current.createdBy(),
            now,
            currentUserId()));
        return String.valueOf(saved.id());
    }

    @Transactional
    public void disableLocalTermFromExternal(String internalId) {
        String tenantId = requireCurrentTenant();
        Long id;
        try {
            id = Long.valueOf(internalId);
        } catch (NumberFormatException exception) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "院内字典内部标识非法");
        }
        LocalTerm current = localTermRepository.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> ApiException.notFound("院内字典 id=" + internalId));
        syncLocalTerm(new LocalTermSyncCommand(
            current.sourceSystem(), current.localCode(), current.category().name(),
            current.localName(), current.normalizedName(), current.departmentId(),
            LocalTermStatus.DISABLED.name(), true));
    }

    /**
     * 按租户 + 过滤条件分页查询标准术语。
     */
    public PageResponse<StandardTerm> pageStandardTerms(PageRequest request, StandardTermFilter filter) {
        String tenantId = requireCurrentTenant();
        String category = name(filter.category());
        String status = name(filter.status());
        String keyword = normalizeKeyword(filter.keyword());
        List<String> standardSources = standardTermSources(tenantId);
        long total = standardTermRepository.countByTenantIdsFilter(
            standardSources, filter.standardSystem(), category, status, keyword);
        if (total == 0) {
            return PageResponse.empty(request);
        }
        return PageResponse.of(standardTermRepository.pageByTenantIdsFilter(
            standardSources, tenantId, filter.standardSystem(), category, status, keyword,
            request.offset(), request.safeSize()
        ), request, total);
    }

    /**
     * 按租户 + 过滤条件分页查询本地术语。
     */
    public PageResponse<LocalTerm> pageLocalTerms(PageRequest request, LocalTermFilter filter) {
        String tenantId = requireCurrentTenant();
        String category = name(filter.category());
        String status = name(filter.status());
        String keyword = normalizeKeyword(filter.keyword());
        long total = localTermRepository.countByFilter(tenantId, filter.sourceSystem(), category, status, keyword);
        if (total == 0) {
            return PageResponse.empty(request);
        }
        return PageResponse.of(localTermRepository.pageByFilter(
            tenantId, filter.sourceSystem(), category, status, keyword, request.offset(), request.safeSize()
        ), request, total);
    }

    /**
     * 按租户 + 过滤条件分页查询正式术语映射。
     */
    public PageResponse<TermMapping> pageMappings(PageRequest request, MappingFilter filter) {
        String tenantId = requireCurrentTenant();
        String category = name(filter.category());
        String status = name(filter.status());
        String keyword = normalizeKeyword(filter.keyword());
        long total = mappingRepository.countByFilter(tenantId, filter.sourceSystem(), category, status, keyword);
        if (total == 0) {
            return PageResponse.empty(request);
        }
        return PageResponse.of(mappingRepository.pageByFilter(
            tenantId, filter.sourceSystem(), category, status, keyword, request.offset(), request.safeSize()
        ), request, total);
    }

    /**
     * 按租户 + 过滤条件分页查询候选映射。
     */
    public PageResponse<MappingCandidate> pageCandidates(PageRequest request, CandidateFilter filter) {
        String tenantId = requireCurrentTenant();
        String status = name(filter.status());
        String riskLevel = name(filter.riskLevel());
        String generationJobCode = blankToNull(filter.generationJobCode());
        long total = candidateRepository.countByFilter(
            tenantId, status, riskLevel, filter.conflictFlag(), generationJobCode);
        if (total == 0) {
            return PageResponse.empty(request);
        }
        return PageResponse.of(candidateRepository.pageByFilter(
            tenantId, status, riskLevel, filter.conflictFlag(), generationJobCode,
            request.offset(), request.safeSize()
        ), request, total);
    }

    /**
     * 按租户 + 过滤条件分页查询映射冲突。
     */
    public PageResponse<MappingConflict> pageConflicts(PageRequest request, ConflictFilter filter) {
        String tenantId = requireCurrentTenant();
        String status = name(filter.status());
        String riskLevel = name(filter.riskLevel());
        String conflictType = name(filter.conflictType());
        long total = conflictRepository.countByFilter(tenantId, status, riskLevel, conflictType);
        if (total == 0) {
            return PageResponse.empty(request);
        }
        return PageResponse.of(conflictRepository.pageByFilter(
            tenantId, status, riskLevel, conflictType, request.offset(), request.safeSize()
        ), request, total);
    }

    /**
     * 确认候选映射并升级为 CONFIRMED 状态的正式 {@link TermMapping}。
     *
     * <p>候选必须存在且为 PENDING；本地与标准术语分类不一致拒绝。
     * 同 (localTermId, standardTermId) 已存在映射则原地更新，否则新增；最后把候选标记为 CONFIRMED。
     */
    @Transactional
    public TermMapping confirmCandidate(Long candidateId, TerminologyCandidateConfirmRequest request) {
        String tenantId = requireValidatedTenant(request.context());
        String userId = currentUserId();
        Instant now = Instant.now();
        MappingCandidate candidate = candidateRepository.findByTenantIdAndId(tenantId, candidateId)
            .orElseThrow(() -> ApiException.notFound("映射候选 id=" + candidateId));
        if (candidate.status() != MappingCandidateStatus.PENDING) {
            throw ApiException.conflict("映射候选 id=" + candidateId + " 不是待确认状态");
        }
        ensureHighRiskSecondConfirmation(candidate, request);
        return confirmPendingCandidate(candidate, request.reviewNote(), request.evidenceOverride(), userId, now);
    }

    /**
     * 驳回待确认候选；不创建映射、不改动院内术语状态，只留驳回理由与审计责任人。
     *
     * <p>这是高危错配候选（如钾/钠互斥近似）的安全处置出口；驳回理由必填。
     */
    @Transactional
    public MappingCandidate rejectCandidate(Long candidateId, TerminologyCandidateRejectRequest request) {
        String tenantId = requireValidatedTenant(request.context());
        String userId = currentUserId();
        Instant now = Instant.now();
        MappingCandidate candidate = candidateRepository.findByTenantIdAndId(tenantId, candidateId)
            .orElseThrow(() -> ApiException.notFound("映射候选 id=" + candidateId));
        if (candidate.status() != MappingCandidateStatus.PENDING) {
            throw ApiException.conflict("映射候选 id=" + candidateId + " 不是待确认状态");
        }
        String reviewNote = request.reviewNote() == null ? "" : request.reviewNote().trim();
        if (reviewNote.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "驳回理由不能为空");
        }
        return candidateRepository.save(candidate.rejected(reviewNote, userId, now));
    }

    /**
     * 批量确认普通候选；如果包含高危候选，整批拒绝且不落任何映射。
     */
    @Transactional
    public TerminologyBatchConfirmResponse batchConfirmCandidates(TerminologyCandidateBatchConfirmRequest request) {
        String tenantId = requireValidatedTenant(request.context());
        String userId = currentUserId();
        Instant now = Instant.now();
        List<Long> candidateIds = request.candidateIds().stream().distinct().toList();
        List<MappingCandidate> candidates = candidateIds.stream()
            .map(id -> candidateRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> ApiException.notFound("映射候选 id=" + id)))
            .toList();
        if (candidates.stream().anyMatch(candidate -> candidate.riskLevel() == TermRiskLevel.HIGH)) {
            throw new ApiException(ErrorCode.MAPPING_HIGH_RISK_BATCH_DENIED);
        }
        for (MappingCandidate candidate : candidates) {
            if (candidate.status() != MappingCandidateStatus.PENDING) {
                throw ApiException.conflict("映射候选 id=" + candidate.id() + " 不是待确认状态");
            }
        }
        List<Long> confirmedIds = candidates.stream()
            .map(candidate -> {
                confirmPendingCandidate(candidate, request.reviewNote(), null, userId, now);
                return candidate.id();
            })
            .toList();
        return new TerminologyBatchConfirmResponse(confirmedIds.size(), confirmedIds);
    }

    private TermMapping confirmPendingCandidate(MappingCandidate candidate,
                                                String reviewNote,
                                                String evidenceOverride,
                                                String userId,
                                                Instant now) {
        String tenantId = candidate.tenantId();
        LocalTerm localTerm = localTermRepository.findByTenantIdAndId(tenantId, candidate.localTermId())
            .orElseThrow(() -> ApiException.notFound("院内字典 id=" + candidate.localTermId()));
        StandardTerm standardTerm = findEffectiveStandardTermById(tenantId, candidate.standardTermId())
            .orElseThrow(() -> ApiException.notFound("标准字典 id=" + candidate.standardTermId()));
        if (localTerm.category() != null && standardTerm.category() != null
                && localTerm.category() != standardTerm.category()) {
            throw ApiException.conflict("院内字典与标准字典分类不一致，禁止确认映射");
        }
        TermCategory mappingCategory = localTerm.category() == null ? standardTerm.category() : localTerm.category();

        TermMapping saved = mappingRepository
            .findByTenantIdAndLocalTermIdAndStandardTermId(tenantId, candidate.localTermId(), candidate.standardTermId())
            .map(existing -> mappingRepository.save(existing.confirmed(
                userId, now, evidence(evidenceOverride, candidate), localTerm.sourceSystem(), mappingCategory
            )))
            .orElseGet(() -> mappingRepository.save(new TermMapping(
                null, tenantId, candidate.localTermId(), candidate.standardTermId(), localTerm.sourceSystem(), mappingCategory,
                candidate.confidence(), candidate.riskLevel(), TermMappingStatus.CONFIRMED,
                evidence(evidenceOverride, candidate), userId, now, now, userId, now, userId
            )));
        boolean conflictDetected = registerConfirmedMappingConflicts(candidate, saved, userId, now);
        MappingCandidate reviewedCandidate = conflictDetected
            ? candidate.withConflictFlag(true, userId, now)
            : candidate;
        if (localTerm.status() == LocalTermStatus.UNMAPPED) {
            localTermRepository.save(localTerm.mapped(userId, now));
        }
        candidateRepository.save(reviewedCandidate.confirmed(reviewNote, userId, now));
        return saved;
    }

    private boolean registerConfirmedMappingConflicts(MappingCandidate candidate,
                                                      TermMapping saved,
                                                      String userId,
                                                      Instant now) {
        String tenantId = candidate.tenantId();
        boolean conflictDetected = false;
        for (TermMapping existing : mappingRepository.findByTenantIdAndLocalTermIdAndStatus(
                tenantId, candidate.localTermId(), TermMappingStatus.CONFIRMED)) {
            if (Objects.equals(existing.standardTermId(), candidate.standardTermId())) {
                continue;
            }
            conflictDetected = true;
            ensureOpenConflict(
                tenantId,
                MappingConflictType.ONE_TO_MANY,
                candidate.localTermId(),
                candidate.standardTermId(),
                saved.id(),
                higherRisk(candidate.riskLevel(), existing.riskLevel()),
                "同一院内术语 id=" + candidate.localTermId()
                    + " 已确认映射到标准术语 id=" + existing.standardTermId()
                    + "，当前又确认到标准术语 id=" + candidate.standardTermId()
                    + "，待人工裁决",
                userId,
                now
            );
        }
        for (TermMapping existing : mappingRepository.findByTenantIdAndStandardTermIdAndStatus(
                tenantId, candidate.standardTermId(), TermMappingStatus.CONFIRMED)) {
            if (Objects.equals(existing.localTermId(), candidate.localTermId())) {
                continue;
            }
            conflictDetected = true;
            ensureOpenConflict(
                tenantId,
                MappingConflictType.MANY_TO_ONE,
                candidate.localTermId(),
                candidate.standardTermId(),
                saved.id(),
                higherRisk(candidate.riskLevel(), existing.riskLevel()),
                "标准术语 id=" + candidate.standardTermId()
                    + " 已被院内术语 id=" + existing.localTermId()
                    + " 确认映射，当前院内术语 id=" + candidate.localTermId()
                    + " 也确认到该标准术语，待人工裁决",
                userId,
                now
            );
        }
        return conflictDetected;
    }

    /**
     * 处置指定冲突记录；冲突当前必须处于 OPEN 状态，处置后置为 RESOLVED。
     */
    @Transactional
    public MappingConflict resolveConflict(Long conflictId, ResolveConflictRequest request) {
        String tenantId = requireValidatedTenant(request.context());
        MappingConflict conflict = conflictRepository.findByTenantIdAndId(tenantId, conflictId)
            .orElseThrow(() -> ApiException.notFound("映射冲突 id=" + conflictId));
        if (conflict.status() != MappingConflictStatus.OPEN) {
            throw ApiException.conflict("映射冲突 id=" + conflictId + " 不是打开状态");
        }
        return conflictRepository.save(conflict.resolved(request.resolutionNote(), currentUserId(), Instant.now()));
    }

    /**
     * 评估一组标准编码的院内→标准对照覆盖情况（P5 对照覆盖分析，advisory）。
     *
     * @param standardSystem 标准字典/编码系统（如 ICD-10）
     * @param codes          规则/路径引用的标准编码集合
     * @return 每个编码的覆盖项（去重、保序）
     */
    public java.util.List<MappingCoverageItem> evaluateCoverage(
            String standardSystem, java.util.List<String> codes) {
        String tenantId = requireCurrentTenant();
        java.util.LinkedHashSet<String> distinct = new java.util.LinkedHashSet<>();
        for (String code : codes == null ? java.util.List.<String>of() : codes) {
            if (code != null && !code.isBlank()) {
                distinct.add(code.trim());
            }
        }
        java.util.List<MappingCoverageItem> items = new java.util.ArrayList<>();
        List<String> standardSources = standardTermSources(tenantId);
        for (String code : distinct) {
            var standardTerm = standardTermRepository
                .findFirstByTenantIdsAndStandardSystemAndTermCodeAndStatus(
                    standardSources, tenantId, standardSystem, code, StandardTermStatus.ACTIVE);
            int confirmed = standardTerm
                .map(term -> effectiveMappings.countByStandardCode(
                    tenantId, standardSystem, term.termCode()))
                .orElse(0);
            items.add(new MappingCoverageItem(
                code, MappingCoverageItem.classify(standardTerm.isPresent(), confirmed), confirmed));
        }
        return items;
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private String requireValidatedTenant(TerminologyApiContext context) {
        String tenantId = requireCurrentTenant();
        context.validateTenant(tenantId);
        return tenantId;
    }

    private Optional<StandardTerm> findEffectiveStandardTermById(String tenantId, Long standardTermId) {
        return standardTermRepository
            .findFirstByTenantIdsAndId(standardTermSources(tenantId), tenantId, standardTermId)
            .filter(term -> term.status() == StandardTermStatus.ACTIVE);
    }

    private List<String> standardTermSources(String tenantId) {
        String current = tenantId == null ? "" : tenantId.trim();
        if (current.isBlank()) {
            throw ApiException.tenantMissing();
        }
        if (PlatformAuthority.PLATFORM_TENANT_ID.equals(current)) {
            return List.of(PlatformAuthority.PLATFORM_TENANT_ID);
        }
        return List.of(PlatformAuthority.PLATFORM_TENANT_ID, current);
    }

    private String currentUserId() {
        return RequestContext.currentUserId().orElse("system");
    }

    private String requireExternalSourceActor(String sourceSystem) {
        String actor = currentUserId();
        String expected = "integration:" + sourceSystem.trim().toUpperCase(java.util.Locale.ROOT);
        if (!expected.equalsIgnoreCase(actor)) {
            throw ApiException.forbidden("院内字典同步来源与受信集成上下文不一致");
        }
        return actor;
    }

    private String normalizeKeyword(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return "%" + raw.trim().toLowerCase() + "%";
    }

    private String blankToNull(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    private String normalizedOrFallback(String normalized, String displayName, String code) {
        if (normalized != null && !normalized.isBlank()) {
            return normalized.trim();
        }
        return ((displayName == null ? "" : displayName.trim())
            + "|"
            + (code == null ? "" : code.trim())).replaceAll("^\\|+|\\|+$", "");
    }

    private String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String evidence(String evidenceOverride, MappingCandidate candidate) {
        if (evidenceOverride != null && !evidenceOverride.isBlank()) {
            return evidenceOverride.trim();
        }
        return candidate.evidenceText();
    }

    private void ensureHighRiskSecondConfirmation(MappingCandidate candidate, TerminologyCandidateConfirmRequest request) {
        if (candidate.riskLevel() != TermRiskLevel.HIGH) {
            return;
        }
        if (!Boolean.TRUE.equals(request.highRiskAcknowledged())
                || request.highRiskReason() == null
                || request.highRiskReason().isBlank()) {
            throw new ApiException(ErrorCode.MAPPING_HIGH_RISK_AUTOCONFIRM_DENIED);
        }
    }

    /**
     * 提交确定性 B0 候选生成任务。接口立即返回 PENDING，不同步返回候选明细。
     */
    @Transactional
    public TerminologyCandidateGenerationJob generateCandidates(TerminologyCandidateGenerationRequest request) {
        String tenantId = requireValidatedTenant(request.context());
        String userId = currentUserId();
        Instant now = Instant.now();
        TerminologyCandidateGenerationJob job = new TerminologyCandidateGenerationJob(
            null,
            tenantId,
            UUID.randomUUID().toString(),
            request.sourceSystem().trim(),
            request.minimumScore(),
            !Boolean.FALSE.equals(request.semanticAssistEnabled()),
            request.packageVersion(),
            userId,
            TerminologyCandidateGenerationJobStatus.PENDING,
            0,
            0,
            null,
            null,
            now,
            null,
            null
        );
        TerminologyCandidateGenerationJob saved = generationJobRepository.save(job);
        RequestContext.Snapshot snapshot = RequestContext.snapshot();
        dispatchCandidateGenerationAfterCommit(saved.jobCode(), snapshot);
        return saved;
    }

    public TerminologyCandidateGenerationJob getCandidateGenerationJob(String jobCode) {
        String tenantId = requireCurrentTenant();
        return generationJobRepository.findByTenantIdAndJobCode(tenantId, jobCode)
            .orElseThrow(() -> ApiException.notFound("术语候选生成任务 jobCode=" + jobCode));
    }

    public List<TerminologyCandidateGenerationJob> listCandidateGenerationJobs() {
        return generationJobRepository.findTop100ByTenantIdOrderByCreatedAtDesc(requireCurrentTenant());
    }

    private void dispatchCandidateGenerationAfterCommit(String jobCode, RequestContext.Snapshot snapshot) {
        Runnable worker = () -> RequestContext.runWith(snapshot, () -> {
            try {
                executeCandidateGenerationJob(jobCode);
            } catch (Exception exception) {
                log.error("Terminology candidate generation job {} failed", jobCode, exception);
                markCandidateGenerationFailed(jobCode, exception.getMessage());
            }
        });

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    terminologyCandidateGenerationExecutor.execute(worker);
                }
            });
        } else {
            terminologyCandidateGenerationExecutor.execute(worker);
        }
    }

    void executeCandidateGenerationJob(String jobCode) {
        String tenantId = requireCurrentTenant();
        TerminologyCandidateGenerationJob job = generationJobRepository.findByTenantIdAndJobCode(tenantId, jobCode)
            .orElseThrow(() -> ApiException.notFound("术语候选生成任务 jobCode=" + jobCode));
        if (job.status() != TerminologyCandidateGenerationJobStatus.PENDING) {
            log.warn("Skip terminology candidate generation job {} in status {}", jobCode, job.status());
            return;
        }
        Instant startedAt = Instant.now();
        generationJobRepository.save(rebuildGenerationJob(job, b -> {
            b.status = TerminologyCandidateGenerationJobStatus.RUNNING;
            b.progress = 10;
            b.startedAt = startedAt;
        }));

        int generated = generateCandidateRowsForJob(
            tenantId,
            job.requestedBy(),
            job.sourceSystem(),
            job.minimumScore(),
            job.semanticAssistEnabled(),
            job.jobCode(),
            Instant.now()
        );

        TerminologyCandidateGenerationJob refreshed =
            generationJobRepository.findByTenantIdAndJobCode(tenantId, jobCode).orElse(job);
        generationJobRepository.save(rebuildGenerationJob(refreshed, b -> {
            b.status = TerminologyCandidateGenerationJobStatus.SUCCEEDED;
            b.progress = 100;
            b.generatedCount = generated;
            b.candidatePageUri = candidatePageUri(jobCode);
            b.startedAt = startedAt;
            b.completedAt = Instant.now();
        }));
    }

    void markCandidateGenerationFailed(String jobCode, String errorMessage) {
        String tenantId = requireCurrentTenant();
        generationJobRepository.findByTenantIdAndJobCode(tenantId, jobCode).ifPresent(job ->
            generationJobRepository.save(rebuildGenerationJob(job, b -> {
                b.status = TerminologyCandidateGenerationJobStatus.FAILED;
                b.errorMessage = errorMessage;
                if (b.startedAt == null) {
                    b.startedAt = Instant.now();
                }
                b.completedAt = Instant.now();
            }))
        );
    }

    /**
     * 确定性语义候选生成。
     *
     * <p>扫描指定来源系统下的所有未映射院内词条，只基于真实字典字段中的精确编码、
     * 同义词/缩写别名和编码族生成候选，并幂等写入 PENDING 候选列表。
     */
    int generateCandidateRowsForJob(String tenantId,
                                    String userId,
                                    String sourceSystem,
                                    Double minimumScore,
                                    Boolean semanticAssistEnabled,
                                    String generationJobCode,
                                    Instant now) {

        StandardTermGenerationIndex standardIndex = loadStandardTermGenerationIndex(tenantId);
        if (standardIndex.isEmpty()) {
            return 0;
        }

        double threshold = minimumScore == null ? 0.2 : minimumScore;
        boolean useSemanticAssist = !Boolean.FALSE.equals(semanticAssistEnabled);
        int generated = 0;
        Map<TermCategory, List<HighRiskRule>> highRiskRulesByCategory = new HashMap<>();
        int offset = 0;
        while (true) {
            List<LocalTerm> localBatch = localTermRepository.pageByTenantIdAndSourceSystemAndStatus(
                tenantId, sourceSystem, LocalTermStatus.UNMAPPED,
                offset, CANDIDATE_GENERATION_BATCH_SIZE);
            if (localBatch == null || localBatch.isEmpty()) {
                break;
            }
            for (LocalTerm local : localBatch) {
                List<CandidateMatch> matches = new ArrayList<>();
                List<HighRiskRule> highRiskRules = useSemanticAssist
                    ? highRiskRulesByCategory.computeIfAbsent(
                        local.category(),
                        category -> highRiskRuleRepository.findActiveByTenantIdAndCategory(tenantId, category)
                    )
                    : List.of();
                for (StandardTerm standard : standardIndex.candidatesFor(local, useSemanticAssist, highRiskRules)) {
                    if (!categoriesCompatible(local, standard)) {
                        continue;
                    }

                    Optional<HighRiskTermMatch> highRisk = useSemanticAssist
                        ? HighRiskTermDetector.detect(local, standard, highRiskRules)
                        : Optional.empty();
                    Optional<SemanticTermMatch> match = useSemanticAssist
                        ? SemanticTermMatcher.match(local, standard)
                        : SemanticTermMatcher.matchExactCode(local, standard);
                    if (highRisk.isPresent() || (match.isPresent() && match.get().score() >= threshold)) {
                        CandidateDecision decision = candidateDecision(highRisk, match);
                        matches.add(new CandidateMatch(standard, decision));
                    }
                }

                boolean conflictFlag = matches.size() > 1;
                if (conflictFlag) {
                    ensureOpenConflict(
                        tenantId,
                        MappingConflictType.ONE_TO_MANY,
                        local.id(),
                        null,
                        null,
                        highestRisk(matches),
                        "院内术语 id=" + local.id() + " 命中 " + matches.size()
                            + " 个标准候选 " + candidateStandardIds(matches)
                            + "，待人工裁决",
                        userId,
                        now
                    );
                }

                for (CandidateMatch candidateMatch : matches) {
                    StandardTerm standard = candidateMatch.standard();
                    CandidateDecision decision = candidateMatch.decision();
                    Optional<MappingCandidate> existingOpt = candidateRepository
                        .findByTenantIdAndLocalTermIdAndStandardTermIdAndStatus(
                            tenantId, local.id(), standard.id(), MappingCandidateStatus.PENDING);

                    MappingCandidate saved;
                    if (existingOpt.isPresent()) {
                        MappingCandidate existing = existingOpt.get();
                        saved = candidateRepository.save(new MappingCandidate(
                            existing.id(), tenantId, local.id(), standard.id(), decision.score(), MappingCandidateSource.RULE,
                            decision.riskLevel(), decision.evidence(), conflictFlag, MappingCandidateStatus.PENDING,
                            existing.reviewNote(), existing.reviewedBy(), existing.reviewedAt(),
                            existing.createdAt(), existing.createdBy(), now, userId, generationJobCode
                        ));
                    } else {
                        saved = candidateRepository.save(new MappingCandidate(
                            null, tenantId, local.id(), standard.id(), decision.score(), MappingCandidateSource.RULE,
                            decision.riskLevel(), decision.evidence(), conflictFlag, MappingCandidateStatus.PENDING,
                            null, null, null, now, userId, now, userId, generationJobCode
                        ));
                    }
                    if (saved != null) {
                        generated++;
                    }
                }
            }
            if (localBatch.size() < CANDIDATE_GENERATION_BATCH_SIZE) {
                break;
            }
            offset += CANDIDATE_GENERATION_BATCH_SIZE;
        }
        return generated;
    }

    private String candidatePageUri(String jobCode) {
        return CANDIDATE_PAGE_PREFIX + "?status=PENDING&generationJobCode=" + jobCode;
    }

    private TerminologyCandidateGenerationJob rebuildGenerationJob(
            TerminologyCandidateGenerationJob src,
            java.util.function.Consumer<CandidateGenerationJobBuilder> mutator) {
        CandidateGenerationJobBuilder builder = new CandidateGenerationJobBuilder(src);
        mutator.accept(builder);
        return new TerminologyCandidateGenerationJob(
            src.id(), src.tenantId(), src.jobCode(), src.sourceSystem(), src.minimumScore(),
            src.semanticAssistEnabled(), src.packageVersion(), src.requestedBy(),
            builder.status, builder.progress, builder.generatedCount, builder.candidatePageUri,
            builder.errorMessage, src.createdAt(), builder.startedAt, builder.completedAt
        );
    }

    private static final class CandidateGenerationJobBuilder {
        TerminologyCandidateGenerationJobStatus status;
        Integer progress;
        Integer generatedCount;
        String candidatePageUri;
        String errorMessage;
        Instant startedAt;
        Instant completedAt;

        CandidateGenerationJobBuilder(TerminologyCandidateGenerationJob job) {
            this.status = job.status();
            this.progress = job.progress();
            this.generatedCount = job.generatedCount();
            this.candidatePageUri = job.candidatePageUri();
            this.errorMessage = job.errorMessage();
            this.startedAt = job.startedAt();
            this.completedAt = job.completedAt();
        }
    }

    private StandardTermGenerationIndex loadStandardTermGenerationIndex(String tenantId) {
        StandardTermGenerationIndex index = new StandardTermGenerationIndex();
        List<String> tenantIds = standardTermSources(tenantId);
        int offset = 0;
        while (true) {
            List<StandardTerm> batch = standardTermRepository.pageByTenantIdsAndStatus(
                tenantIds, tenantId, StandardTermStatus.ACTIVE,
                offset, CANDIDATE_GENERATION_BATCH_SIZE);
            if (batch == null || batch.isEmpty()) {
                break;
            }
            for (StandardTerm standard : batch) {
                index.add(standard);
            }
            if (batch.size() < CANDIDATE_GENERATION_BATCH_SIZE) {
                break;
            }
            offset += CANDIDATE_GENERATION_BATCH_SIZE;
        }
        return index;
    }

    private boolean categoriesCompatible(LocalTerm local, StandardTerm standard) {
        return local.category() == null || standard.category() == null || local.category() == standard.category();
    }

    private CandidateDecision candidateDecision(Optional<HighRiskTermMatch> highRisk,
                                                Optional<SemanticTermMatch> semantic) {
        if (highRisk.isEmpty()) {
            SemanticTermMatch match = semantic.orElseThrow();
            return new CandidateDecision(match.score(), match.riskLevel(), match.evidence());
        }
        HighRiskTermMatch risk = highRisk.get();
        double score = semantic.map(SemanticTermMatch::score).orElse(risk.score());
        String evidence = risk.evidence()
            + semantic.map(match -> "；原候选依据：" + match.evidence()).orElse("");
        return new CandidateDecision(score, TermRiskLevel.HIGH, evidence);
    }

    private void ensureOpenConflict(String tenantId,
                                    MappingConflictType conflictType,
                                    Long localTermId,
                                    Long standardTermId,
                                    Long mappingId,
                                    TermRiskLevel riskLevel,
                                    String description,
                                    String userId,
                                    Instant now) {
        Optional<MappingConflict> existing = standardTermId == null
            ? conflictRepository.findOpenLocalOnly(tenantId, conflictType, localTermId)
            : conflictRepository.findOpenByExactScope(tenantId, conflictType, localTermId, standardTermId);
        if (existing.isPresent()) {
            return;
        }
        conflictRepository.save(new MappingConflict(
            null, tenantId, conflictType, localTermId, standardTermId, mappingId,
            riskLevel, description, MappingConflictStatus.OPEN,
            null, null, null, now, userId, now, userId
        ));
    }

    private TermRiskLevel highestRisk(List<CandidateMatch> matches) {
        TermRiskLevel highest = TermRiskLevel.LOW;
        for (CandidateMatch match : matches) {
            highest = higherRisk(highest, match.decision().riskLevel());
        }
        return highest;
    }

    private TermRiskLevel higherRisk(TermRiskLevel first, TermRiskLevel second) {
        TermRiskLevel normalizedFirst = first == null ? TermRiskLevel.LOW : first;
        TermRiskLevel normalizedSecond = second == null ? TermRiskLevel.LOW : second;
        if (normalizedFirst == TermRiskLevel.HIGH || normalizedSecond == TermRiskLevel.HIGH) {
            return TermRiskLevel.HIGH;
        }
        if (normalizedFirst == TermRiskLevel.MEDIUM || normalizedSecond == TermRiskLevel.MEDIUM) {
            return TermRiskLevel.MEDIUM;
        }
        return TermRiskLevel.LOW;
    }

    private String candidateStandardIds(List<CandidateMatch> matches) {
        return matches.stream()
            .map(match -> String.valueOf(match.standard().id()))
            .toList()
            .toString();
    }

    private record CandidateMatch(
        StandardTerm standard,
        CandidateDecision decision
    ) {
    }

    private record CandidateDecision(
        double score,
        TermRiskLevel riskLevel,
        String evidence
    ) {
    }

    private static final class StandardTermGenerationIndex {
        private final Map<String, LinkedHashMap<Long, StandardTerm>> exactCodeIndex = new HashMap<>();
        private final Map<String, LinkedHashMap<Long, StandardTerm>> aliasIndex = new HashMap<>();
        private final Map<String, LinkedHashMap<Long, StandardTerm>> codePrefixIndex = new HashMap<>();
        private final List<IndexedStandardTerm> standards = new ArrayList<>();
        private final Map<String, List<StandardTerm>> highRiskTermCache = new HashMap<>();

        void add(StandardTerm standard) {
            standards.add(new IndexedStandardTerm(standard, HighRiskTermDetector.clinicalText(standard)));
            String code = SemanticTermMatcher.canonical(standard.termCode());
            if (!code.isBlank()) {
                put(exactCodeIndex, code, standard);
                for (int length = 4; length <= code.length(); length++) {
                    put(codePrefixIndex, code.substring(0, length), standard);
                }
            }
            for (String alias : SemanticTermMatcher.aliases(
                    standard.displayName(), standard.normalizedName(), standard.termCode())) {
                put(aliasIndex, alias, standard);
            }
        }

        boolean isEmpty() {
            return standards.isEmpty();
        }

        List<StandardTerm> candidatesFor(LocalTerm local,
                                         boolean semanticAssistEnabled,
                                         List<HighRiskRule> highRiskRules) {
            LinkedHashMap<Long, StandardTerm> candidates = new LinkedHashMap<>();
            if (semanticAssistEnabled) {
                addAll(candidates, semanticCandidates(local));
                addAll(candidates, highRiskCandidates(local, highRiskRules));
            } else {
                addAll(candidates, lookup(exactCodeIndex, SemanticTermMatcher.canonical(local.localCode())));
            }
            return List.copyOf(candidates.values());
        }

        private List<StandardTerm> semanticCandidates(LocalTerm local) {
            LinkedHashMap<Long, StandardTerm> candidates = new LinkedHashMap<>();
            for (String alias : SemanticTermMatcher.aliases(
                    local.localName(), local.normalizedName(), local.localCode())) {
                addAll(candidates, lookup(aliasIndex, alias));
            }
            String localCode = SemanticTermMatcher.canonical(local.localCode());
            if (localCode.length() >= 4) {
                for (int length = 4; length <= localCode.length(); length++) {
                    addAll(candidates, lookup(codePrefixIndex, localCode.substring(0, length)));
                }
            }
            return List.copyOf(candidates.values());
        }

        private List<StandardTerm> highRiskCandidates(LocalTerm local, List<HighRiskRule> rules) {
            if (rules == null || rules.isEmpty()) {
                return List.of();
            }
            LinkedHashMap<Long, StandardTerm> candidates = new LinkedHashMap<>();
            ClinicalText localText = HighRiskTermDetector.clinicalText(local);
            for (HighRiskRule rule : rules) {
                if (rule.status() != HighRiskRuleStatus.ACTIVE) {
                    continue;
                }
                switch (rule.ruleType()) {
                    case MUTUALLY_EXCLUSIVE_TERMS -> {
                        if (HighRiskTermDetector.containsAny(localText, rule.leftTerms())) {
                            addAll(candidates, standardsContaining(rule.rightTerms()));
                        }
                        if (HighRiskTermDetector.containsAny(localText, rule.rightTerms())) {
                            addAll(candidates, standardsContaining(rule.leftTerms()));
                        }
                    }
                    case DOSE_MAGNITUDE -> {
                        if (HighRiskTermDetector.containsAny(localText, rule.unitTerms())) {
                            addAll(candidates, standardsContaining(rule.unitTerms()));
                        }
                    }
                    case UNIT_STRENGTH -> {
                        if (HighRiskTermDetector.containsAny(localText, rule.leftTerms())) {
                            addAll(candidates, standardsContaining(rule.leftTerms()));
                        }
                    }
                }
            }
            return List.copyOf(candidates.values());
        }

        private List<StandardTerm> standardsContaining(String terms) {
            LinkedHashMap<Long, StandardTerm> result = new LinkedHashMap<>();
            for (RuleTerm term : HighRiskTermDetector.splitTerms(terms)) {
                addAll(result, highRiskTermCache.computeIfAbsent(
                    term.normalized() + "|" + term.tokenOnly(),
                    ignored -> standards.stream()
                        .filter(indexed -> contains(indexed.clinicalText(), term))
                        .map(IndexedStandardTerm::standard)
                        .toList()
                ));
            }
            return List.copyOf(result.values());
        }

        private static boolean contains(ClinicalText text, RuleTerm term) {
            if (term.tokenOnly()) {
                return text.tokens().contains(term.normalized());
            }
            return text.tokens().contains(term.normalized()) || text.compact().contains(term.normalized());
        }

        private static void put(Map<String, LinkedHashMap<Long, StandardTerm>> index,
                                String key,
                                StandardTerm standard) {
            if (key == null || key.isBlank()) {
                return;
            }
            index.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).put(standard.id(), standard);
        }

        private static List<StandardTerm> lookup(Map<String, LinkedHashMap<Long, StandardTerm>> index, String key) {
            if (key == null || key.isBlank()) {
                return List.of();
            }
            LinkedHashMap<Long, StandardTerm> values = index.get(key);
            return values == null ? List.of() : List.copyOf(values.values());
        }

        private static void addAll(LinkedHashMap<Long, StandardTerm> target, List<StandardTerm> standards) {
            for (StandardTerm standard : standards) {
                target.put(standard.id(), standard);
            }
        }

        private record IndexedStandardTerm(StandardTerm standard, ClinicalText clinicalText) {
        }
    }
}
