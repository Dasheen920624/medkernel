package com.medkernel.engine.terminology;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.security.AuthenticatedRoleGuard;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.PlatformAuthority;
import com.medkernel.engine.versioning.ReleasePort;
import com.medkernel.engine.versioning.RolloutStrategy;
import com.medkernel.engine.versioning.VersionReleaseCommand;
import com.medkernel.engine.versioning.VersionReleaseScopeType;
import com.medkernel.engine.versioning.VersionRollbackCommand;
import com.medkernel.engine.versioning.VersionedAssetType;
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

    private static final String DEFAULT_GRAY_SCOPE_JSON = "{\"rolloutStrategy\":\""
        + RolloutStrategy.CANARY_BED_PERCENT + "\",\"percentage\":10}";
    private final StandardTermRepository standardTermRepository;
    private final LocalTermRepository localTermRepository;
    private final TermMappingRepository mappingRepository;
    private final EffectiveTermMappingResolver effectiveMappings;
    private final MappingCandidateRepository candidateRepository;
    private final MappingConflictRepository conflictRepository;
    private final TermMappingPackageRepository packageRepository;
    private final TermMappingPackageItemRepository packageItemRepository;
    private final TermMappingPackageReleaseRepository packageReleaseRepository;
    private final HighRiskRuleRepository highRiskRuleRepository;
    private final TerminologyVersionedAssetAdapter versionedAssets;
    private final AssetVersionRepository assetVersions;
    private final ReleasePort releasePort;

    public TerminologyService(StandardTermRepository standardTermRepository,
                              LocalTermRepository localTermRepository,
                              TermMappingRepository mappingRepository,
                              EffectiveTermMappingResolver effectiveMappings,
                              MappingCandidateRepository candidateRepository,
                              MappingConflictRepository conflictRepository,
                              TermMappingPackageRepository packageRepository,
                              TermMappingPackageItemRepository packageItemRepository,
                              TermMappingPackageReleaseRepository packageReleaseRepository,
                              HighRiskRuleRepository highRiskRuleRepository,
                              TerminologyVersionedAssetAdapter versionedAssets,
                              AssetVersionRepository assetVersions,
                              ReleasePort releasePort) {
        this.standardTermRepository = standardTermRepository;
        this.localTermRepository = localTermRepository;
        this.mappingRepository = mappingRepository;
        this.effectiveMappings = effectiveMappings;
        this.candidateRepository = candidateRepository;
        this.conflictRepository = conflictRepository;
        this.packageRepository = packageRepository;
        this.packageItemRepository = packageItemRepository;
        this.packageReleaseRepository = packageReleaseRepository;
        this.highRiskRuleRepository = highRiskRuleRepository;
        this.versionedAssets = versionedAssets;
        this.assetVersions = assetVersions;
        this.releasePort = releasePort;
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
     * 基于当前租户 + 范围内所有 CONFIRMED 映射构建一个新的 DRAFT 状态术语映射包。
     *
     * <p>范围内若无任何已确认映射则抛冲突错误；包条目逐条以快照形式落 {@code term_mapping_package_item}。
     */
    @Transactional
    public TermMappingPackage buildPackage(BuildTerminologyPackageRequest request) {
        String tenantId = requireValidatedTenant(request.context());
        String userId = currentUserId();
        Instant now = Instant.now();
        PackageScope packageScope = requireCurrentPackageScope(
            tenantId, request.scopeLevel(), request.scopeCode());
        List<TermMapping> mappings = mappingRepository.findConfirmedByTenantIdAndScope(
            tenantId, packageScope.level(), packageScope.code());
        if (mappings.isEmpty()) {
            throw ApiException.conflict("当前范围没有已确认映射，无法构建映射包");
        }
        List<TermMappingSnapshot> snapshots = mappings.stream()
            .map(mapping -> mappingSnapshot(tenantId, mapping))
            .toList();
        String contentHash = hashMappings(request, packageScope, snapshots);
        TermMappingPackage saved = packageRepository.save(new TermMappingPackage(
            null, tenantId, request.packageCode(), request.packageVersion(), request.displayName(),
            packageScope.level(), packageScope.code(), TermMappingPackageStatus.DRAFT,
            mappings.size(), contentHash, null, null, null, null, now, userId, now, userId
        ));
        for (TermMappingSnapshot snapshot : snapshots) {
            String mappingSnapshot = TermMappingSnapshotCodec.write(snapshot);
            packageItemRepository.save(TermMappingPackageItem.fromSnapshot(
                tenantId, saved.id(), snapshot.mappingId(), snapshot, mappingSnapshot, now, userId
            ));
        }
        versionedAssets.registerDraft(new AssetVersionRegisterCommand(
            tenantId,
            VersionedAssetType.TERMINOLOGY,
            saved.packageCode(),
            saved.packageVersion(),
            releaseOrgScope(saved),
            "ALL",
            null,
            saved.contentHash(),
            terminologySourceRef(saved),
            userId,
            RequestContext.currentTraceId()
        ));
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
        TerminologyApiContext context = request.context();
        String tenantId = requireValidatedTenant(context);
        String userId = currentUserId();
        Instant now = Instant.now();
        TermMappingPackage pkg = packageRepository.findByTenantIdAndId(tenantId, packageId)
            .orElseThrow(() -> ApiException.notFound("映射包 id=" + packageId));
        if (pkg.status() == TermMappingPackageStatus.ROLLED_BACK || pkg.status() == TermMappingPackageStatus.ARCHIVED) {
            throw ApiException.conflict("映射包 id=" + packageId + " 已不可发布");
        }
        ensurePublishTransition(pkg, request);
        String grayScopeJson = normalizedGrayScope(request);
        AssetVersion assetVersion = requireAssetVersion(pkg);
        VersionReleaseCommand releaseCommand = releaseCommand(pkg, assetVersion, request, grayScopeJson, userId);
        advanceRelease(assetVersion, releaseCommand, request.releaseMode());
        TermMappingPackage next = pkg.withGrayScope(grayScopeJson)
            .withStatus(request.releaseMode() == PackageReleaseMode.FULL
                ? TermMappingPackageStatus.PUBLISHED
                : TermMappingPackageStatus.GRAY, userId, now);
        if (request.releaseMode() == PackageReleaseMode.FULL) {
            for (TermMappingPackage active : packageRepository.findActiveByTenantIdAndPackageCodeAndScope(
                    tenantId, pkg.packageCode(), pkg.scopeLevel(), pkg.scopeCode())) {
                if (!Objects.equals(active.id(), pkg.id())) {
                    packageRepository.save(active.withStatus(TermMappingPackageStatus.SUPERSEDED, userId, now));
                }
            }
        }
        TermMappingPackage saved = packageRepository.save(next);
        packageReleaseRepository.save(new TermMappingPackageRelease(
            null, tenantId, pkg.id(), null, TermPackageReleaseEventType.PUBLISH,
            request.releaseMode(), request.reason(), grayScopeJson, now, userId
        ));
        return saved;
    }

    /**
     * 把当前 PUBLISHED 的映射包回滚到指定历史版本，同时写一条 ROLLBACK 事件流水。
     *
     * <p>目标包必须与当前包同 (packageCode + scope)，且处于 SUPERSEDED 状态。
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
        if (current.status() != TermMappingPackageStatus.PUBLISHED) {
            throw ApiException.conflict("当前映射包不是全量发布状态，无法执行版本回滚");
        }
        if (target.status() != TermMappingPackageStatus.SUPERSEDED) {
            throw ApiException.conflict("目标映射包不是可回滚发布点");
        }
        AssetVersion currentVersion = requireAssetVersion(current);
        AssetVersion targetVersion = requireAssetVersion(target);
        releasePort.rollback(new VersionRollbackCommand(
            tenantId,
            VersionedAssetType.TERMINOLOGY,
            current.packageCode(),
            currentVersion.versionId(),
            targetVersion.versionId(),
            current.packageVersion(),
            target.packageVersion(),
            request.reason(),
            true,
            userId,
            RequestContext.currentTraceId()
        ));
        packageRepository.save(current.rolledBack(userId, now));
        TermMappingPackage restored = packageRepository.save(target.restoredFromRollback(current.id(), userId, now));
        packageReleaseRepository.save(new TermMappingPackageRelease(
            null, tenantId, current.id(), target.id(), TermPackageReleaseEventType.ROLLBACK,
            PackageReleaseMode.FULL, request.reason(), null, now, userId
        ));
        return restored;
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

    private void ensurePublishTransition(TermMappingPackage pkg,
                                         PublishTerminologyPackageRequest request) {
        if (pkg.status() == TermMappingPackageStatus.DRAFT) {
            if (request.releaseMode() == PackageReleaseMode.FULL
                    && !AuthenticatedRoleGuard.has(RoleCode.HOSPITAL_ADMIN)) {
                throw new ApiException(ErrorCode.FORBIDDEN, "只有医院管理员可跳过灰度直接全量发布");
            }
            return;
        }
        if (pkg.status() == TermMappingPackageStatus.GRAY
                && request.releaseMode() == PackageReleaseMode.FULL) {
            return;
        }
        if (pkg.status() != TermMappingPackageStatus.DRAFT) {
            throw ApiException.conflict("映射包必须处于草稿或灰度状态，才能继续发布");
        }
    }

    private AssetVersion requireAssetVersion(TermMappingPackage pkg) {
        return assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            pkg.tenantId(),
            VersionedAssetType.TERMINOLOGY,
            pkg.packageCode(),
            pkg.packageVersion()
        ).orElseThrow(() -> new ApiException(
            ErrorCode.CONFLICT,
            "术语映射包缺少统一资产版本，禁止发布或回滚"
        ));
    }

    private VersionReleaseCommand releaseCommand(
            TermMappingPackage pkg,
            AssetVersion assetVersion,
            PublishTerminologyPackageRequest request,
            String grayScopeJson,
            String actor) {
        return new VersionReleaseCommand(
            pkg.tenantId(),
            VersionedAssetType.TERMINOLOGY,
            pkg.packageCode(),
            assetVersion.versionId(),
            releaseOrgScope(pkg),
            "ALL",
            request.releaseMode() == PackageReleaseMode.GRAY
                ? releaseScopeType(pkg.scopeLevel())
                : VersionReleaseScopeType.ALL,
            grayScopeJson,
            pkg.contentHash(),
            request.reason(),
            List.of(),
            actor,
            RequestContext.currentTraceId()
        );
    }

    private void advanceRelease(
            AssetVersion assetVersion,
            VersionReleaseCommand command,
            PackageReleaseMode mode) {
        if (assetVersion.status() == AssetVersionStatus.DRAFT) {
            releasePort.submitForReview(command);
            releasePort.approveForSilentObservation(command);
        } else if (assetVersion.status() == AssetVersionStatus.PENDING_REVIEW) {
            releasePort.approveForSilentObservation(command);
        } else if (assetVersion.status() != AssetVersionStatus.PUBLISHED
                && assetVersion.status() != AssetVersionStatus.ACTIVE) {
            throw ApiException.conflict("统一术语资产版本状态不允许发布");
        }
        if (mode == PackageReleaseMode.GRAY) {
            releasePort.releaseGray(command);
        } else {
            releasePort.releaseFull(command);
        }
    }

    private String releaseOrgScope(TermMappingPackage pkg) {
        return pkg.scopeLevel().trim().toUpperCase() + ":" + pkg.scopeCode().trim();
    }

    private VersionReleaseScopeType releaseScopeType(String scopeLevel) {
        if ("TENANT".equalsIgnoreCase(scopeLevel)) {
            return VersionReleaseScopeType.ALL;
        }
        try {
            return VersionReleaseScopeType.valueOf(scopeLevel.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "术语映射包范围层级不受支持: " + scopeLevel);
        }
    }

    private String terminologySourceRef(TermMappingPackage pkg) {
        return "term-mapping-package:" + pkg.packageCode() + ":" + pkg.packageVersion();
    }

    private String normalizedGrayScope(PublishTerminologyPackageRequest request) {
        if (request.releaseMode() != PackageReleaseMode.GRAY) {
            return null;
        }
        if (request.grayScopeJson() == null || request.grayScopeJson().isBlank()) {
            return DEFAULT_GRAY_SCOPE_JSON;
        }
        return request.grayScopeJson().trim();
    }

    private String hashMappings(
            BuildTerminologyPackageRequest request,
            PackageScope packageScope,
            List<TermMappingSnapshot> mappings) {
        StringBuilder payload = new StringBuilder()
            .append(request.packageCode()).append('|')
            .append(request.packageVersion()).append('|')
            .append(packageScope.level()).append('|')
            .append(packageScope.code());
        mappings.forEach(mapping -> payload.append('|')
            .append(TermMappingSnapshotCodec.write(mapping)));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private TermMappingSnapshot mappingSnapshot(String tenantId, TermMapping mapping) {
        LocalTerm localTerm = localTermRepository.findByTenantIdAndId(tenantId, mapping.localTermId())
            .orElseThrow(() -> ApiException.notFound("院内术语 id=" + mapping.localTermId()));
        StandardTerm standardTerm = findEffectiveStandardTermById(tenantId, mapping.standardTermId())
            .orElseThrow(() -> ApiException.notFound("标准术语 id=" + mapping.standardTermId()));
        return TermMappingSnapshot.from(mapping, localTerm, standardTerm);
    }

    private PackageScope requireCurrentPackageScope(
            String tenantId,
            String rawLevel,
            String rawCode) {
        String level = rawLevel == null ? "" : rawLevel.trim().toUpperCase();
        String code = rawCode == null ? "" : rawCode.trim();
        OrgScope current = RequestContext.currentOrgScope();
        String expectedCode = switch (level) {
            case "TENANT" -> tenantId;
            case "GROUP" -> current.groupId();
            case "HOSPITAL" -> current.hospitalId();
            case "CAMPUS" -> current.campusId();
            case "SITE" -> current.siteId();
            case "DEPARTMENT" -> current.departmentId();
            default -> throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "术语映射包范围层级不受支持: " + rawLevel
            );
        };
        if (expectedCode == null || expectedCode.isBlank() || !expectedCode.equals(code)) {
            throw new ApiException(
                ErrorCode.ORG_SCOPE_DENIED,
                "术语映射包范围必须与当前组织上下文一致"
            );
        }
        return new PackageScope(level, code);
    }

    private record PackageScope(String level, String code) {
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
