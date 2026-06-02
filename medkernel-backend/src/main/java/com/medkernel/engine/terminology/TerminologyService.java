package com.medkernel.engine.terminology;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * GA-ENG-API-04 字典映射应用服务：分页查询、候选生成、候选确认、冲突处置、映射包构建/发布/回滚。
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
    private final MappingCandidateRepository candidateRepository;
    private final MappingConflictRepository conflictRepository;
    private final TermMappingPackageRepository packageRepository;
    private final TermMappingPackageItemRepository packageItemRepository;
    private final TermMappingPackageReleaseRepository packageReleaseRepository;

    public TerminologyService(StandardTermRepository standardTermRepository,
                              LocalTermRepository localTermRepository,
                              TermMappingRepository mappingRepository,
                              MappingCandidateRepository candidateRepository,
                              MappingConflictRepository conflictRepository,
                              TermMappingPackageRepository packageRepository,
                              TermMappingPackageItemRepository packageItemRepository,
                              TermMappingPackageReleaseRepository packageReleaseRepository) {
        this.standardTermRepository = standardTermRepository;
        this.localTermRepository = localTermRepository;
        this.mappingRepository = mappingRepository;
        this.candidateRepository = candidateRepository;
        this.conflictRepository = conflictRepository;
        this.packageRepository = packageRepository;
        this.packageItemRepository = packageItemRepository;
        this.packageReleaseRepository = packageReleaseRepository;
    }

    /**
     * 按租户 + 过滤条件分页查询标准术语。
     */
    public PageResponse<StandardTerm> pageStandardTerms(PageRequest request, StandardTermFilter filter) {
        String tenantId = requireCurrentTenant();
        String category = name(filter.category());
        String status = name(filter.status());
        String keyword = normalizeKeyword(filter.keyword());
        long total = standardTermRepository.countByFilter(tenantId, filter.standardSystem(), category, status, keyword);
        if (total == 0) {
            return PageResponse.empty(request);
        }
        return PageResponse.of(standardTermRepository.pageByFilter(
            tenantId, filter.standardSystem(), category, status, keyword, request.offset(), request.safeSize()
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
     * 按租户 + 过滤条件分页查询术语映射包。
     */
    public PageResponse<TermMappingPackage> pagePackages(PageRequest request, PackageFilter filter) {
        String tenantId = requireCurrentTenant();
        String status = name(filter.status());
        long total = packageRepository.countByFilter(
            tenantId, filter.packageCode(), status, filter.scopeLevel(), filter.scopeCode());
        if (total == 0) {
            return PageResponse.empty(request);
        }
        return PageResponse.of(packageRepository.pageByFilter(
            tenantId, filter.packageCode(), status, filter.scopeLevel(), filter.scopeCode(),
            request.offset(), request.safeSize()
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
        StandardTerm standardTerm = standardTermRepository.findByTenantIdAndId(tenantId, candidate.standardTermId())
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
        candidateRepository.save(candidate.confirmed(reviewNote, userId, now));
        return saved;
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
     * 基于当前租户 + 范围内所有 CONFIRMED 映射构建一个新的 DRAFT 状态术语映射包。
     *
     * <p>范围内若无任何已确认映射则抛冲突错误；包条目逐条以快照形式落 {@code term_mapping_package_item}。
     */
    @Transactional
    public TermMappingPackage buildPackage(BuildTerminologyPackageRequest request) {
        String tenantId = requireValidatedTenant(request.context());
        String userId = currentUserId();
        Instant now = Instant.now();
        List<TermMapping> mappings = mappingRepository.findConfirmedByTenantIdAndScope(
            tenantId, request.scopeLevel(), request.scopeCode());
        if (mappings.isEmpty()) {
            throw ApiException.conflict("当前范围没有已确认映射，无法构建映射包");
        }
        String contentHash = hashMappings(request, mappings);
        TermMappingPackage saved = packageRepository.save(new TermMappingPackage(
            null, tenantId, request.packageCode(), request.packageVersion(), request.displayName(),
            request.scopeLevel(), request.scopeCode(), TermMappingPackageStatus.DRAFT,
            mappings.size(), contentHash, null, null, null, null, now, userId, now, userId
        ));
        for (TermMapping mapping : mappings) {
            packageItemRepository.save(new TermMappingPackageItem(
                null, tenantId, saved.id(), mapping.id(), snapshot(mapping), now, userId
            ));
        }
        return saved;
    }

    /**
     * 把指定术语映射包升级为 GRAY 或 PUBLISHED；ROLLED_BACK / ARCHIVED 状态拒绝发布。
     *
     * <p>FULL 模式发布时把同 (packageCode + scope) 下旧 PUBLISHED/GRAY 包置为 SUPERSEDED；
     * 同步写入一条 PUBLISH 发布事件流水。
     */
    @Transactional
    public TermMappingPackage publishPackage(Long packageId, PublishTerminologyPackageRequest request) {
        String tenantId = requireValidatedTenant(request.context());
        String userId = currentUserId();
        Instant now = Instant.now();
        TermMappingPackage pkg = packageRepository.findByTenantIdAndId(tenantId, packageId)
            .orElseThrow(() -> ApiException.notFound("映射包 id=" + packageId));
        if (pkg.status() == TermMappingPackageStatus.ROLLED_BACK || pkg.status() == TermMappingPackageStatus.ARCHIVED) {
            throw ApiException.conflict("映射包 id=" + packageId + " 已不可发布");
        }
        TermMappingPackage next = pkg.withGrayScope(request.grayScopeJson())
            .withStatus(request.releaseMode() == PackageReleaseMode.FULL
                ? TermMappingPackageStatus.PUBLISHED
                : TermMappingPackageStatus.GRAY, userId, now);
        if (request.releaseMode() == PackageReleaseMode.FULL) {
            for (TermMappingPackage active : packageRepository.findActiveByTenantIdAndPackageCodeAndScope(
                    tenantId, pkg.packageCode(), pkg.scopeLevel(), pkg.scopeCode())) {
                if (!active.id().equals(pkg.id())) {
                    packageRepository.save(active.withStatus(TermMappingPackageStatus.SUPERSEDED, userId, now));
                }
            }
        }
        TermMappingPackage saved = packageRepository.save(next);
        packageReleaseRepository.save(new TermMappingPackageRelease(
            null, tenantId, pkg.id(), null, TermPackageReleaseEventType.PUBLISH,
            request.releaseMode(), request.reason(), request.grayScopeJson(), now, userId
        ));
        return saved;
    }

    /**
     * 把当前 PUBLISHED/GRAY 的映射包回滚到指定历史版本，同时写一条 ROLLBACK 事件流水。
     *
     * <p>目标包必须与当前包同 (packageCode + scope)，且处于 PUBLISHED 或 SUPERSEDED 状态。
     * 操作后当前包置 ROLLED_BACK，目标包重新置 PUBLISHED。
     */
    @Transactional
    public TermMappingPackage rollbackPackage(Long packageId, RollbackTerminologyPackageRequest request) {
        String tenantId = requireValidatedTenant(request.context());
        String userId = currentUserId();
        Instant now = Instant.now();
        TermMappingPackage current = packageRepository.findByTenantIdAndId(tenantId, packageId)
            .orElseThrow(() -> ApiException.notFound("当前映射包 id=" + packageId));
        TermMappingPackage target = packageRepository.findByTenantIdAndId(tenantId, request.targetPackageId())
            .orElseThrow(() -> ApiException.notFound("目标映射包 id=" + request.targetPackageId()));
        if (!sameScope(current, target)) {
            throw ApiException.conflict("回滚目标必须与当前映射包同编码、同范围");
        }
        if (current.status() != TermMappingPackageStatus.PUBLISHED && current.status() != TermMappingPackageStatus.GRAY) {
            throw ApiException.conflict("当前映射包不是已发布或灰度状态，无法回滚");
        }
        if (target.status() != TermMappingPackageStatus.PUBLISHED && target.status() != TermMappingPackageStatus.SUPERSEDED) {
            throw ApiException.conflict("目标映射包不是可回滚发布点");
        }
        packageRepository.save(current.rolledBack(userId, now));
        TermMappingPackage restored = packageRepository.save(target.withStatus(TermMappingPackageStatus.PUBLISHED, userId, now));
        packageReleaseRepository.save(new TermMappingPackageRelease(
            null, tenantId, current.id(), target.id(), TermPackageReleaseEventType.ROLLBACK,
            PackageReleaseMode.FULL, request.reason(), null, now, userId
        ));
        return restored;
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

    private String currentUserId() {
        return RequestContext.currentUserId().orElse("system");
    }

    private String normalizeKeyword(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return "%" + raw.trim().toLowerCase() + "%";
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

    private boolean sameScope(TermMappingPackage current, TermMappingPackage target) {
        return current.packageCode().equals(target.packageCode())
            && current.scopeLevel().equals(target.scopeLevel())
            && current.scopeCode().equals(target.scopeCode());
    }

    private String hashMappings(BuildTerminologyPackageRequest request, List<TermMapping> mappings) {
        StringBuilder payload = new StringBuilder()
            .append(request.packageCode()).append('|')
            .append(request.packageVersion()).append('|')
            .append(request.scopeLevel()).append('|')
            .append(request.scopeCode());
        mappings.forEach(mapping -> payload.append('|')
            .append(mapping.id()).append(':')
            .append(mapping.localTermId()).append(':')
            .append(mapping.standardTermId()).append(':')
            .append(mapping.status()));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private String snapshot(TermMapping mapping) {
        return "{\"mappingId\":" + mapping.id()
            + ",\"localTermId\":" + mapping.localTermId()
            + ",\"standardTermId\":" + mapping.standardTermId()
            + ",\"status\":\"" + mapping.status() + "\"}";
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

        List<StandardTerm> standardTerms = standardTermRepository.findByTenantIdAndStatus(
            tenantId, StandardTermStatus.ACTIVE);
        if (standardTerms.isEmpty()) {
            return new TerminologyCandidateGenerationResponse(0, List.of());
        }

        double threshold = request.minimumScore() == null ? 0.2 : request.minimumScore();
        List<TerminologyCandidateResponse> generated = new java.util.ArrayList<>();
        for (LocalTerm local : unmapped) {
            for (StandardTerm standard : standardTerms) {
                if (local.category() != null && standard.category() != null && local.category() != standard.category()) {
                    continue;
                }

                Optional<SemanticTermMatch> match = SemanticTermMatcher.match(local, standard);
                if (match.isPresent() && match.get().score() >= threshold) {
                    SemanticTermMatch semantic = match.get();

                    Optional<MappingCandidate> existingOpt = candidateRepository
                        .findByTenantIdAndLocalTermIdAndStandardTermIdAndStatus(
                            tenantId, local.id(), standard.id(), MappingCandidateStatus.PENDING);

                    MappingCandidate saved;
                    if (existingOpt.isPresent()) {
                        MappingCandidate existing = existingOpt.get();
                        saved = candidateRepository.save(new MappingCandidate(
                            existing.id(), tenantId, local.id(), standard.id(), semantic.score(), MappingCandidateSource.RULE,
                            semantic.riskLevel(), semantic.evidence(), false, MappingCandidateStatus.PENDING,
                            existing.reviewNote(), existing.reviewedBy(), existing.reviewedAt(),
                            existing.createdAt(), existing.createdBy(), now, userId
                        ));
                    } else {
                        saved = candidateRepository.save(new MappingCandidate(
                            null, tenantId, local.id(), standard.id(), semantic.score(), MappingCandidateSource.RULE,
                            semantic.riskLevel(), semantic.evidence(), false, MappingCandidateStatus.PENDING,
                            null, null, null, now, userId, now, userId
                        ));
                    }
                    generated.add(TerminologyCandidateResponse.from(saved));
                }
            }
        }
        return new TerminologyCandidateGenerationResponse(generated.size(), generated);
    }
}
