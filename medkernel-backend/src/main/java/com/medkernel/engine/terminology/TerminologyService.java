package com.medkernel.engine.terminology;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final StandardTermRepository standardTermRepository;
    private final LocalTermRepository localTermRepository;
    private final TermMappingRepository mappingRepository;
    private final EffectiveTermMappingResolver effectiveMappings;
    private final MappingCandidateRepository candidateRepository;
    private final MappingConflictRepository conflictRepository;
    private final HighRiskRuleRepository highRiskRuleRepository;

    public TerminologyService(StandardTermRepository standardTermRepository,
                              LocalTermRepository localTermRepository,
                              TermMappingRepository mappingRepository,
                              EffectiveTermMappingResolver effectiveMappings,
                              MappingCandidateRepository candidateRepository,
                              MappingConflictRepository conflictRepository,
                              HighRiskRuleRepository highRiskRuleRepository) {
        this.standardTermRepository = standardTermRepository;
        this.localTermRepository = localTermRepository;
        this.mappingRepository = mappingRepository;
        this.effectiveMappings = effectiveMappings;
        this.candidateRepository = candidateRepository;
        this.conflictRepository = conflictRepository;
        this.highRiskRuleRepository = highRiskRuleRepository;
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
        long total = candidateRepository.countByFilter(tenantId, status, riskLevel, filter.conflictFlag());
        if (total == 0) {
            return PageResponse.empty(request);
        }
        return PageResponse.of(candidateRepository.pageByFilter(
            tenantId, status, riskLevel, filter.conflictFlag(), request.offset(), request.safeSize()
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

    private String normalizeKeyword(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return "%" + raw.trim().toLowerCase() + "%";
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
     * 确定性语义候选生成。
     *
     * <p>扫描指定来源系统下的所有未映射院内词条，只基于真实字典字段中的精确编码、
     * 同义词/缩写别名和编码族生成候选，并幂等写入 PENDING 候选列表。
     */
    @Transactional
    public TerminologyCandidateGenerationResponse generateCandidates(TerminologyCandidateGenerationRequest request) {
        String tenantId = requireValidatedTenant(request.context());
        String userId = currentUserId();
        Instant now = Instant.now();

        List<LocalTerm> unmapped = localTermRepository.findByTenantIdAndSourceSystemAndStatus(
            tenantId, request.sourceSystem(), LocalTermStatus.UNMAPPED);
        if (unmapped.isEmpty()) {
            return new TerminologyCandidateGenerationResponse(0, List.of());
        }

        List<StandardTerm> standardTerms = standardTermRepository.findByTenantIdsAndStatus(
            standardTermSources(tenantId), tenantId, StandardTermStatus.ACTIVE);
        if (standardTerms.isEmpty()) {
            return new TerminologyCandidateGenerationResponse(0, List.of());
        }

        double threshold = request.minimumScore() == null ? 0.2 : request.minimumScore();
        boolean semanticAssistEnabled = !Boolean.FALSE.equals(request.semanticAssistEnabled());
        List<TerminologyCandidateResponse> generated = new java.util.ArrayList<>();
        Map<TermCategory, List<HighRiskRule>> highRiskRulesByCategory = new HashMap<>();
        for (LocalTerm local : unmapped) {
            List<CandidateMatch> matches = new java.util.ArrayList<>();
            List<HighRiskRule> highRiskRules = semanticAssistEnabled
                ? highRiskRulesByCategory.computeIfAbsent(
                    local.category(),
                    category -> highRiskRuleRepository.findActiveByTenantIdAndCategory(tenantId, category)
                )
                : List.of();
            for (StandardTerm standard : standardTerms) {
                if (local.category() != null && standard.category() != null && local.category() != standard.category()) {
                    continue;
                }

                Optional<HighRiskTermMatch> highRisk = semanticAssistEnabled
                    ? HighRiskTermDetector.detect(local, standard, highRiskRules)
                    : Optional.empty();
                Optional<SemanticTermMatch> match = semanticAssistEnabled
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
                        existing.createdAt(), existing.createdBy(), now, userId
                    ));
                } else {
                    saved = candidateRepository.save(new MappingCandidate(
                        null, tenantId, local.id(), standard.id(), decision.score(), MappingCandidateSource.RULE,
                        decision.riskLevel(), decision.evidence(), conflictFlag, MappingCandidateStatus.PENDING,
                        null, null, null, now, userId, now, userId
                    ));
                }
                generated.add(TerminologyCandidateResponse.from(saved));
            }
        }
        return new TerminologyCandidateGenerationResponse(generated.size(), generated);
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
}
