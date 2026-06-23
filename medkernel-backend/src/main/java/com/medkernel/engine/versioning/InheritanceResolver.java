package com.medkernel.engine.versioning;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.medkernel.engine.org.OrgHierarchyRepository;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.hash.Sha256ContentHash;

/**
 * 基于组织树闭包解析配置资产继承版本；专病通过适用域参与版本筛选。
 */
@Service
public class InheritanceResolver {

    private static final List<AssetVersionStatus> RESOLVABLE_VERSION_STATUSES =
        List.of(AssetVersionStatus.PUBLISHED, AssetVersionStatus.WITHDRAWN);
    private static final List<InheritanceOverrideStatus> RESOLVABLE_OVERRIDE_STATUSES =
        List.of(InheritanceOverrideStatus.PUBLISHED, InheritanceOverrideStatus.RETIRED);

    private final OrgHierarchyRepository hierarchy;
    private final AssetVersionRepository assetVersions;
    private final InheritanceOverrideRepository overrides;
    private final List<SafetyMonotonicityCheck> safetyChecks;
    private final AssetDependencyRepository assetDependencies;

    @Autowired
    public InheritanceResolver(
            OrgHierarchyRepository hierarchy,
            AssetVersionRepository assetVersions,
            InheritanceOverrideRepository overrides,
            List<SafetyMonotonicityCheck> safetyChecks,
            AssetDependencyRepository assetDependencies) {
        this.hierarchy = hierarchy;
        this.assetVersions = assetVersions;
        this.overrides = overrides;
        this.safetyChecks = safetyChecks;
        this.assetDependencies = assetDependencies;
    }

    public InheritanceResolver(
            OrgHierarchyRepository hierarchy,
            AssetVersionRepository assetVersions,
            InheritanceOverrideRepository overrides,
            List<SafetyMonotonicityCheck> safetyChecks) {
        this(hierarchy, assetVersions, overrides, safetyChecks, null);
    }

    public ResolvedAssetVersion resolve(InheritanceResolveQuery query) {
        String tenantId = required(query.tenantId(), "租户 ID");
        VersionedAssetType assetType = required(query.assetType(), "资产类型");
        String assetIdentity = required(query.assetIdentity(), "资产身份");
        String applicableScope = ApplicableScopeMatcher.validateDeclaration(
            required(query.applicableScope(), "适用人群或上下文"));
        String targetOrgUnitId = required(query.targetOrgUnitId(), "目标组织 ID");
        Instant effectiveAt = query.effectiveAt() == null ? Instant.now() : query.effectiveAt();

        List<OrgUnit> path = resolutionPath(tenantId, targetOrgUnitId);
        return resolveOnPath(
            tenantId, assetType, assetIdentity, applicableScope, effectiveAt, path, repositoryLookup());
    }

    /**
     * 在同一组织闭包和解析时点内批量解析显式声明的资产。
     *
     * <p>查询次数不随资产数量增长：一次组织闭包、一次覆盖集合、当前租户与平台各一次版本集合。
     * 运行修订要上线哪些资产由发布清单显式声明；解析器不再按旧容器编码隐式拉入 ADD 独有资产。
     */
    public List<BatchResolvedAsset> resolveBatch(InheritanceBatchResolveQuery query) {
        String tenantId = required(query.tenantId(), "租户 ID");
        String targetOrgUnitId = required(query.targetOrgUnitId(), "目标组织 ID");
        Instant effectiveAt = query.effectiveAt() == null ? Instant.now() : query.effectiveAt();
        List<String> scopes = normalizeScopes(query.applicableScopes());
        List<VersionedAssetIdentity> declared = normalizeIdentities(query.declaredAssets());
        List<OrgUnit> path = resolutionPath(tenantId, targetOrgUnitId);
        List<String> orgPaths = inheritanceSearchPath(tenantId, path);
        Set<String> orgPathSet = Set.copyOf(orgPaths);

        List<InheritanceOverride> candidateOverrides = safeList(
            overrides.findByTenantIdAndOrgPathInAndLifecycleStatusIn(
                tenantId, orgPaths, RESOLVABLE_OVERRIDE_STATUSES))
            .stream()
            .filter(value -> Objects.equals(value.tenantId(), tenantId))
            .filter(value -> orgPathSet.contains(value.orgPath()))
            .toList();

        LinkedHashSet<VersionedAssetIdentity> allIdentities = new LinkedHashSet<>(declared);
        if (allIdentities.isEmpty()) {
            return List.of();
        }
        Set<String> identityCodes = allIdentities.stream()
            .map(VersionedAssetIdentity::assetIdentity)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<AssetVersion> tenantVersions = filterVersionsForTenant(
            assetVersions.findByTenantIdAndAssetIdentityInAndStatusIn(
                tenantId, identityCodes, RESOLVABLE_VERSION_STATUSES),
            tenantId,
            identityCodes);
        List<AssetVersion> platformVersions = filterVersionsForTenant(
            assetVersions.findByTenantIdAndAssetIdentityInAndStatusIn(
                PlatformAuthority.PLATFORM_TENANT_ID,
                identityCodes,
                RESOLVABLE_VERSION_STATUSES),
            PlatformAuthority.PLATFORM_TENANT_ID,
            identityCodes);
        CandidateLookup lookup = batchLookup(tenantVersions, platformVersions, candidateOverrides);

        List<BatchResolvedAsset> resolved = new ArrayList<>();
        for (VersionedAssetIdentity identity : allIdentities) {
            ResolvedAssetVersion resolution = null;
            for (String scope : scopes) {
                try {
                    resolution = resolveOnPath(
                        tenantId,
                        identity.assetType(),
                        identity.assetIdentity(),
                        scope,
                        effectiveAt,
                        path,
                        lookup);
                    break;
                } catch (ApiException ex) {
                    if (ex.errorCode() != ErrorCode.NOT_FOUND) {
                        throw ex;
                    }
                }
            }
            if (resolution != null) {
                resolved.add(new BatchResolvedAsset(identity, resolution, false));
            }
        }
        return List.copyOf(resolved);
    }

    private ResolvedAssetVersion resolveOnPath(
            String tenantId,
            VersionedAssetType assetType,
            String assetIdentity,
            String applicableScope,
            Instant effectiveAt,
            List<OrgUnit> path,
            CandidateLookup lookup) {
        List<String> inheritancePath = path.stream().map(OrgUnit::orgPath).toList();
        List<String> searchPath = inheritanceSearchPath(tenantId, path);
        Set<String> targetOrgPaths = Set.copyOf(orgPathAliases(tenantId, path, path.size() - 1));

        // 记录沿途被安全护栏忽略的下级覆盖标识，写入解析说明供审核台与审计追溯
        String ignoredOverrideId = null;
        for (int index = searchPath.size() - 1; index >= 0; index--) {
            String candidateOrgPath = searchPath.get(index);
            boolean inherited = !targetOrgPaths.contains(candidateOrgPath);
            Optional<AssetVersion> active = lookup.findApplicableVersion(
                tenantId, assetType, assetIdentity, candidateOrgPath, applicableScope, effectiveAt);
            if (active.isEmpty()) {
                // 本级无替换版本：消费本级停用(DISABLE)覆盖（其 override_version_id 为空，按组织生效域直查）
                Optional<InheritanceOverride> disable = lookup.findApplicableDisable(
                    tenantId, assetType, assetIdentity, applicableScope, candidateOrgPath, effectiveAt);
                if (disable.isPresent()) {
                    InheritanceOverride value = disable.get();
                    // 传播判定：祖先节点的 EXCLUSIVE 停用仅本级生效、不向下沉
                    if (inherited && value.propagation() == InheritancePropagation.EXCLUSIVE) {
                        continue;
                    }
                    // 安全护栏：锁定/红线基线禁止被下级关闭，忽略该停用、回退继承锁定版本
                    if (!permitsDisable(tenantId, value, lookup)) {
                        ignoredOverrideId = value.overrideId();
                        continue;
                    }
                    return new ResolvedAssetVersion(
                        null,
                        candidateOrgPath,
                        inherited,
                        true,
                        true,
                        disabledExplanation(value, inheritancePath, ignoredOverrideId),
                        SourceTier.ORG
                    );
                }
                continue;
            }
            AssetVersion selected = active.get();
            Optional<InheritanceOverride> override = lookup.findOverride(tenantId, selected.versionId());
            if (override.isPresent()) {
                InheritanceOverride value = override.get();
                if (!overrideEffectiveAt(value, effectiveAt)) {
                    continue;
                }
                // 传播判定：祖先节点的 EXCLUSIVE 覆盖仅本节点生效、不向下沉；下级跳过它，回退到上一层适用版本
                if (inherited && value.propagation() == InheritancePropagation.EXCLUSIVE) {
                    continue;
                }
                // 安全护栏：被继承的锁定/红线基线禁止被放宽性 REPLACE 覆盖，解析期忽略该覆盖、回退继承锁定版本
                if (value.overrideMode() == InheritanceOverrideMode.REPLACE
                        && !permitsLockedBaselineReplace(tenantId, value, selected, lookup)) {
                    ignoredOverrideId = value.overrideId();
                    continue;
                }
            }
            boolean overridden = override.isPresent();
            return new ResolvedAssetVersion(
                selected,
                candidateOrgPath,
                inherited,
                overridden,
                false,
                explanation(selected, inheritancePath, inherited, override, ignoredOverrideId),
                SourceTier.ORG
            );
        }

        // 租户组织闭包内无任何适用版本/覆盖：前置回退平台权威基线（设计附录 G·D1）
        ResolvedAssetVersion platformBaseline = resolvePlatformBaseline(
            assetType,
            assetIdentity,
            applicableScope,
            effectiveAt,
            inheritancePath,
            ignoredOverrideId,
            lookup);
        if (platformBaseline != null) {
            return platformBaseline;
        }

        throw new ApiException(ErrorCode.NOT_FOUND, "未找到可继承的 PUBLISHED 资产版本");
    }

    public ResolvedAssetGraph resolveWithDependencies(InheritanceResolveQuery query) {
        ResolvedAssetVersion root = resolve(query);
        List<ResolvedAssetDependency> resolvedDependencies = new ArrayList<>();
        Map<String, ResolutionEpochBinding> bindings = new LinkedHashMap<>();
        collectBinding(bindings, root);
        collectDependencies(query, root, resolvedDependencies, bindings, new ArrayList<>());
        List<ResolutionEpochBinding> orderedBindings = bindings.values().stream()
            .sorted(Comparator
                .comparing((ResolutionEpochBinding binding) -> binding.assetType().name())
                .thenComparing(ResolutionEpochBinding::assetIdentity)
                .thenComparing(ResolutionEpochBinding::versionId))
            .toList();
        List<String> epochParts = orderedBindings.stream()
            .map(binding -> binding.assetType().name()
                + "|" + binding.assetIdentity()
                + "|" + binding.versionId()
                + "|" + binding.contentHash())
            .toList();
        String epochSource = String.join("\n", epochParts);
        String epoch = Sha256ContentHash.sha256(epochSource, "resolution epoch 不能为空");
        return new ResolvedAssetGraph(root, resolvedDependencies, orderedBindings, epoch);
    }

    private void collectDependencies(
            InheritanceResolveQuery rootQuery,
            ResolvedAssetVersion owner,
            List<ResolvedAssetDependency> resolvedDependencies,
            Map<String, ResolutionEpochBinding> bindings,
            List<String> stack) {
        if (owner.version() == null || assetDependencies == null) {
            return;
        }
        AssetVersion ownerVersion = owner.version();
        String ownerKey = key(ownerVersion.assetType(), ownerVersion.assetIdentity());
        if (stack.contains(ownerKey)) {
            throw new ApiException(ErrorCode.CONFLICT, "引用完整性校验失败：资产依赖图存在循环 " + stack + " -> " + ownerKey);
        }
        List<String> nextStack = new ArrayList<>(stack);
        nextStack.add(ownerKey);
        List<AssetDependency> edges =
            assetDependencies.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionIdOrderByDependsOnAssetTypeAscDependsOnIdentityAsc(
                ownerVersion.tenantId(), ownerVersion.assetType(), ownerVersion.assetIdentity(), ownerVersion.versionId());
        for (AssetDependency edge : edges) {
            ResolvedAssetVersion dependency = resolve(new InheritanceResolveQuery(
                rootQuery.tenantId(),
                edge.dependsOnAssetType(),
                edge.dependsOnIdentity(),
                rootQuery.applicableScope(),
                rootQuery.targetOrgUnitId(),
                rootQuery.effectiveAt()
            ));
            if (dependency.version() == null || dependency.disabled()) {
                throw new ApiException(
                    ErrorCode.CONFLICT,
                    "引用完整性校验失败：依赖资产 " + edge.dependsOnIdentity() + " 在当前上下文被停用"
                );
            }
            if (!AssetDependencyService.isCompatible(dependency.version().versionNo(), edge)) {
                throw new ApiException(
                    ErrorCode.CONFLICT,
                    "引用完整性校验失败：依赖资产 " + edge.dependsOnIdentity()
                        + " 版本 " + dependency.version().versionNo() + " 不满足兼容范围"
                );
            }
            resolvedDependencies.add(new ResolvedAssetDependency(edge, dependency));
            collectBinding(bindings, dependency);
            collectDependencies(rootQuery, dependency, resolvedDependencies, bindings, nextStack);
        }
    }

    private void collectBinding(Map<String, ResolutionEpochBinding> bindings, ResolvedAssetVersion resolved) {
        if (resolved.version() == null) {
            return;
        }
        AssetVersion version = resolved.version();
        bindings.putIfAbsent(
            key(version.assetType(), version.assetIdentity()),
            new ResolutionEpochBinding(
                version.assetType(), version.assetIdentity(), version.versionId(), version.contentHash())
        );
    }

    private String key(VersionedAssetType assetType, String assetIdentity) {
        return assetType.name() + "|" + assetIdentity;
    }

    /**
     * 前置回退平台权威基线：租户组织闭包无适用版本时，按 {@link PlatformAuthority} 约定读取
     * 平台主租户顶层组织路径下该身份的 PUBLISHED 版本，标注 {@link SourceTier#PLATFORM}。
     *
     * <p>平台版本是继承链最一般的根：未被任何租户覆盖遮蔽的身份恒解析到平台 PUBLISHED 版本，平台升级后
     * 未定制方下次解析自动跟随，无需任何租户级复制（设计 platform-authority 规格）。平台亦无基线时返回
     * {@code null}，由调用方按 {@code NOT_FOUND} 诚实降级，不伪造。
     *
     * @return 命中平台基线时的解析结果；平台无 ACTIVE 基线时为 {@code null}
     */
    private ResolvedAssetVersion resolvePlatformBaseline(
            VersionedAssetType assetType,
            String assetIdentity,
            String applicableScope,
            Instant effectiveAt,
            List<String> inheritancePath,
            String ignoredOverrideId,
            CandidateLookup lookup) {
        Optional<AssetVersion> platformActive = lookup.findApplicableVersion(
            PlatformAuthority.PLATFORM_TENANT_ID,
            assetType,
            assetIdentity,
            PlatformAuthority.PLATFORM_ORG_PATH,
            applicableScope,
            effectiveAt);
        if (platformActive.isEmpty()) {
            return null;
        }
        AssetVersion baseline = platformActive.get();
        List<String> chain = new ArrayList<>();
        chain.add(PlatformAuthority.PLATFORM_ORG_PATH);
        chain.addAll(inheritancePath);
        return new ResolvedAssetVersion(
            baseline,
            PlatformAuthority.PLATFORM_ORG_PATH,
            true,
            false,
            false,
            new InheritanceExplanation(
                appendSafetyInterception("继承平台权威基线版本 " + baseline.versionNo(), ignoredOverrideId),
                chain,
                null,
                null,
                null),
            SourceTier.PLATFORM
        );
    }

    static String activeScopeKey(String assetIdentity, String orgPath, String applicableScope) {
        return String.join("|",
            required(assetIdentity, "资产身份"),
            required(orgPath, "组织生效域"),
            required(applicableScope, "适用人群或上下文")
        );
    }

    private InheritanceExplanation explanation(
            AssetVersion version,
            List<String> inheritancePath,
            boolean inherited,
            Optional<InheritanceOverride> override,
            String ignoredOverrideId) {
        if (override.isPresent()) {
            InheritanceOverride value = override.get();
            return new InheritanceExplanation(
                appendSafetyInterception("命中本级局部覆盖版本 " + version.versionNo(), ignoredOverrideId),
                inheritancePath,
                value.diffSummary(),
                value.overrideReason(),
                value.impactScope()
            );
        }
        String summary = inherited
            ? "未找到本级覆盖，继承上级组织版本 " + version.versionNo()
            : "命中本级 PUBLISHED 版本 " + version.versionNo();
        return new InheritanceExplanation(
            appendSafetyInterception(summary, ignoredOverrideId), inheritancePath, null, null, null);
    }

    /**
     * 判定下级 REPLACE 覆盖能否替换被继承的锁定/红线基线（安全单调护栏，见设计附录 S1/S2）。
     *
     * <p>规则：
     * <ul>
     *   <li>红线基线（{@code safety_policy=SAFETY_REDLINE}）被降级为非红线覆盖即为放宽，拒绝——
     *       与登记期前置禁用形成解析期纵深防御；</li>
     *   <li>锁定基线（{@code override_policy=LOCKED}）的内容变更覆盖须经适配的
     *       {@link SafetyMonotonicityCheck} 背书“至少同样严格”方可放行，无谓词可验证时保守拒绝（fail-safe）。</li>
     * </ul>
     * 基线缺失（如已退役）时按非锁定处理放行，主权威仍由登记期把关。
     *
     * @return {@code true} 表示覆盖可被采纳，{@code false} 表示解析期忽略该覆盖
     */
    private boolean permitsLockedBaselineReplace(
            String tenantId,
            InheritanceOverride override,
            AssetVersion candidate,
            CandidateLookup lookup) {
        AssetVersion baseline = lookup.findInheritedBaseline(tenantId, override.inheritedVersionId()).orElse(null);
        if (baseline == null) {
            return true;
        }
        boolean downgradesRedline = baseline.safetyPolicy() == AssetVersionSafetyPolicy.SAFETY_REDLINE
            && candidate.safetyPolicy() != AssetVersionSafetyPolicy.SAFETY_REDLINE;
        if (downgradesRedline) {
            return false;
        }
        boolean lockedContentChange = baseline.overridePolicy() == AssetVersionOverridePolicy.LOCKED
            && !Objects.equals(baseline.contentHash(), candidate.contentHash());
        if (lockedContentChange) {
            // 锁定基线的内容变更覆盖须由领域安全单调谓词背书“至少同样严格”方可放行（附录 S2）
            return vouchedAtLeastAsStrict(baseline, candidate);
        }
        return true;
    }

    /**
     * 锁定基线的内容变更覆盖：须由适配该资产类型的领域安全单调谓词背书“至少同样严格”方可放行；
     * 无谓词可验证时保守拒绝（fail-safe，附录 S2）。
     */
    private boolean vouchedAtLeastAsStrict(AssetVersion baseline, AssetVersion candidate) {
        for (SafetyMonotonicityCheck check : safetyChecks) {
            if (check.supports(baseline.assetType())) {
                return check.isAtLeastAsStrict(baseline, candidate);
            }
        }
        return false;
    }

    /**
     * 按组织生效域直查本级是否存在停用(DISABLE)覆盖。DISABLE 无替换版本（{@code override_version_id} 为空），
     * 无法经覆盖版本反查命中，故按 (租户/资产类型/资产身份/组织生效域/适用人群) 直查并取首个。
     */
    private Optional<InheritanceOverride> findApplicableDisable(
            String tenantId,
            VersionedAssetType assetType,
            String assetIdentity,
            String applicableScope,
            String orgPath,
            Instant effectiveAt) {
        List<InheritanceOverride> exact = overrides
            .findByTenantIdAndAssetTypeAndAssetIdentityAndOrgPathAndApplicableScopeAndOverrideModeAndLifecycleStatus(
                tenantId,
                assetType,
                assetIdentity,
                orgPath,
                applicableScope,
                InheritanceOverrideMode.DISABLE,
                InheritanceOverrideStatus.PUBLISHED);
        List<InheritanceOverride> candidates = new ArrayList<>(safeList(exact));
        for (InheritanceOverride value : safeList(
                overrides.findByTenantIdAndAssetTypeAndAssetIdentity(
                    tenantId, assetType, assetIdentity))) {
            if (candidates.stream().noneMatch(existing -> Objects.equals(existing.overrideId(), value.overrideId()))) {
                candidates.add(value);
            }
        }
        return selectApplicableDisable(
            candidates, tenantId, assetType, assetIdentity, applicableScope, orgPath, effectiveAt);
    }

    private Optional<InheritanceOverride> selectApplicableDisable(
            List<InheritanceOverride> candidates,
            String tenantId,
            VersionedAssetType assetType,
            String assetIdentity,
            String applicableScope,
            String orgPath,
            Instant effectiveAt) {
        return safeList(candidates).stream()
            .filter(value -> Objects.equals(value.tenantId(), tenantId))
            .filter(value -> value.assetType() == assetType)
            .filter(value -> Objects.equals(value.assetIdentity(), assetIdentity))
            .filter(value -> value.overrideMode() == InheritanceOverrideMode.DISABLE)
            .filter(value -> Objects.equals(value.orgPath(), orgPath))
            .filter(value -> ApplicableScopeMatcher.matches(value.applicableScope(), applicableScope))
            .filter(value -> overrideEffectiveAt(value, effectiveAt))
            .sorted(Comparator
                .comparingInt((InheritanceOverride value) ->
                    ApplicableScopeMatcher.specificityOf(value.applicableScope()))
                .reversed()
                .thenComparing(InheritanceOverride::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(InheritanceOverride::overrideId, Comparator.nullsFirst(Comparator.reverseOrder())))
            .findFirst();
    }

    private Optional<AssetVersion> findApplicableVersion(
            String tenantId,
            VersionedAssetType assetType,
            String assetIdentity,
            String orgPath,
            String applicableScope,
            Instant effectiveAt) {
        List<AssetVersion> candidates = new ArrayList<>();
        List<AssetVersion> exact = assetVersions.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
            tenantId,
            assetType,
            activeScopeKey(assetIdentity, orgPath, applicableScope),
            AssetVersionStatus.PUBLISHED);
        candidates.addAll(safeList(exact));
        for (AssetVersion value : safeList(
                assetVersions.findByTenantIdAndAssetTypeAndAssetIdentity(tenantId, assetType, assetIdentity))) {
            if (candidates.stream().noneMatch(existing -> Objects.equals(existing.versionId(), value.versionId()))) {
                candidates.add(value);
            }
        }
        return selectApplicableVersion(
            candidates, tenantId, assetType, assetIdentity, orgPath, applicableScope, effectiveAt);
    }

    private Optional<AssetVersion> selectApplicableVersion(
            List<AssetVersion> candidates,
            String tenantId,
            VersionedAssetType assetType,
            String assetIdentity,
            String orgPath,
            String applicableScope,
            Instant effectiveAt) {
        return safeList(candidates).stream()
            .filter(value -> Objects.equals(value.tenantId(), tenantId))
            .filter(value -> value.assetType() == assetType)
            .filter(value -> Objects.equals(value.assetIdentity(), assetIdentity))
            .filter(value -> Objects.equals(value.organizationScope(), orgPath))
            .filter(value -> ApplicableScopeMatcher.matches(value.applicableScope(), applicableScope))
            .filter(value -> versionEffectiveAt(value, effectiveAt))
            .sorted(Comparator
                .comparingInt((AssetVersion value) ->
                    ApplicableScopeMatcher.specificityOf(value.applicableScope()))
                .reversed()
                .thenComparing(AssetVersion::effectiveFrom, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(AssetVersion::versionId, Comparator.nullsFirst(Comparator.reverseOrder())))
            .findFirst();
    }

    private boolean versionEffectiveAt(AssetVersion version, Instant effectiveAt) {
        if (version.status() != AssetVersionStatus.PUBLISHED
                && version.status() != AssetVersionStatus.WITHDRAWN) {
            return false;
        }
        return (version.effectiveFrom() == null || !effectiveAt.isBefore(version.effectiveFrom()))
            && (version.effectiveTo() == null || !effectiveAt.isAfter(version.effectiveTo()));
    }

    private boolean overrideEffectiveAt(InheritanceOverride value, Instant effectiveAt) {
        if (value.lifecycleStatus() == InheritanceOverrideStatus.PUBLISHED) {
            return value.createdAt() == null || !effectiveAt.isBefore(value.createdAt());
        }
        return value.lifecycleStatus() == InheritanceOverrideStatus.RETIRED
            && (value.createdAt() == null || !effectiveAt.isBefore(value.createdAt()))
            && value.updatedAt() != null
            && !effectiveAt.isAfter(value.updatedAt());
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    /**
     * 判定下级 DISABLE 能否关闭被继承的基线（安全下限，见设计附录 S1/S3）：锁定
     * （{@code override_policy=LOCKED}）或红线（{@code safety_policy=SAFETY_REDLINE}）基线不可被关闭。
     * 基线缺失（如已退役）时按非锁定处理放行，主权威仍由登记期把关。
     */
    private boolean permitsDisable(
            String tenantId,
            InheritanceOverride disable,
            CandidateLookup lookup) {
        AssetVersion baseline = lookup.findInheritedBaseline(tenantId, disable.inheritedVersionId()).orElse(null);
        if (baseline == null) {
            return true;
        }
        return baseline.overridePolicy() != AssetVersionOverridePolicy.LOCKED
            && baseline.safetyPolicy() != AssetVersionSafetyPolicy.SAFETY_REDLINE;
    }

    /**
     * 覆盖记录的 inherited_version_id 既可能指向当前租户组织链上的版本，也可能直接指向平台基线。
     * 安全护栏必须同时识别两类来源，避免平台锁定/红线基线被租户覆盖绕过。
     */
    private Optional<AssetVersion> findInheritedBaseline(String tenantId, String inheritedVersionId) {
        if (inheritedVersionId == null || inheritedVersionId.isBlank()) {
            return Optional.empty();
        }
        Optional<AssetVersion> tenantBaseline =
            assetVersions.findByVersionIdAndTenantId(inheritedVersionId, tenantId);
        if (tenantBaseline.isPresent()) {
            return tenantBaseline;
        }
        return assetVersions.findByVersionIdAndTenantId(
            inheritedVersionId, PlatformAuthority.PLATFORM_TENANT_ID);
    }

    private List<OrgUnit> resolutionPath(String tenantId, String targetOrgUnitId) {
        List<OrgUnit> path = hierarchy.findResolutionAncestorsAndSelf(tenantId, targetOrgUnitId);
        if (path == null || path.isEmpty()) {
            path = hierarchy.findAncestorsAndSelf(tenantId, targetOrgUnitId);
        }
        if (path == null || path.isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "组织不存在: " + targetOrgUnitId);
        }
        return path;
    }

    private List<String> inheritanceSearchPath(String tenantId, List<OrgUnit> path) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (int index = 0; index < path.size(); index++) {
            values.addAll(orgPathAliases(tenantId, path, index));
        }
        return List.copyOf(values);
    }

    private List<String> orgPathAliases(String tenantId, List<OrgUnit> path, int index) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        OrgUnit unit = path.get(index);
        if (unit.orgPath() != null && !unit.orgPath().isBlank()) {
            values.add(unit.orgPath());
        }
        String semantic = semanticOrgPath(tenantId, path, index);
        if (semantic != null && !semantic.isBlank()) {
            values.add(semantic);
        }
        return List.copyOf(values);
    }

    private String semanticOrgPath(String tenantId, List<OrgUnit> path, int index) {
        String effectiveTenant = tenantId;
        String group = null;
        String hospital = null;
        String campus = null;
        String department = null;
        for (int cursor = 0; cursor <= index; cursor++) {
            OrgUnit unit = path.get(cursor);
            if (unit == null || unit.level() == null || unit.code() == null || unit.code().isBlank()) {
                continue;
            }
            OrgLevel level = unit.level();
            if (level == OrgLevel.TENANT) {
                effectiveTenant = unit.code().trim();
            } else if (level == OrgLevel.REGION) {
                group = unit.code().trim();
            } else if (level == OrgLevel.FACILITY) {
                hospital = unit.code().trim();
            } else if (level == OrgLevel.CAMPUS) {
                campus = unit.code().trim();
            } else if (level == OrgLevel.DEPARTMENT) {
                department = unit.code().trim();
            }
        }
        List<String> segments = new ArrayList<>();
        addScopeSegment(segments, "tenant", effectiveTenant);
        addScopeSegment(segments, "group", group);
        addScopeSegment(segments, "hospital", hospital);
        addScopeSegment(segments, "campus", campus);
        addScopeSegment(segments, "department", department);
        return segments.isEmpty() ? null : String.join("/", segments);
    }

    private void addScopeSegment(List<String> segments, String name, String value) {
        if (value != null && !value.isBlank()) {
            segments.add(name + ":" + value.trim());
        }
    }

    private List<String> normalizeScopes(List<String> applicableScopes) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String scope : safeList(applicableScopes)) {
            normalized.add(ApplicableScopeMatcher.validateDeclaration(
                required(scope, "适用人群或上下文")));
        }
        if (normalized.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "适用人群或上下文不能为空");
        }
        return List.copyOf(normalized);
    }

    private List<VersionedAssetIdentity> normalizeIdentities(List<VersionedAssetIdentity> identities) {
        LinkedHashSet<VersionedAssetIdentity> normalized = new LinkedHashSet<>();
        for (VersionedAssetIdentity identity : safeList(identities)) {
            VersionedAssetIdentity value = required(identity, "资产身份");
            normalized.add(new VersionedAssetIdentity(
                required(value.assetType(), "资产类型"),
                required(value.assetIdentity(), "资产身份")));
        }
        return List.copyOf(normalized);
    }

    private List<AssetVersion> filterVersionsForTenant(
            List<AssetVersion> candidates,
            String tenantId,
            Set<String> identityCodes) {
        return safeList(candidates).stream()
            .filter(value -> Objects.equals(value.tenantId(), tenantId))
            .filter(value -> identityCodes.contains(value.assetIdentity()))
            .toList();
    }

    private CandidateLookup repositoryLookup() {
        return new CandidateLookup() {
            @Override
            public Optional<AssetVersion> findApplicableVersion(
                    String tenantId,
                    VersionedAssetType assetType,
                    String assetIdentity,
                    String orgPath,
                    String applicableScope,
                    Instant effectiveAt) {
                return InheritanceResolver.this.findApplicableVersion(
                    tenantId, assetType, assetIdentity, orgPath, applicableScope, effectiveAt);
            }

            @Override
            public Optional<InheritanceOverride> findApplicableDisable(
                    String tenantId,
                    VersionedAssetType assetType,
                    String assetIdentity,
                    String applicableScope,
                    String orgPath,
                    Instant effectiveAt) {
                return InheritanceResolver.this.findApplicableDisable(
                    tenantId, assetType, assetIdentity, applicableScope, orgPath, effectiveAt);
            }

            @Override
            public Optional<InheritanceOverride> findOverride(String tenantId, String overrideVersionId) {
                return overrides.findByTenantIdAndOverrideVersionId(tenantId, overrideVersionId);
            }

            @Override
            public Optional<AssetVersion> findInheritedBaseline(String tenantId, String inheritedVersionId) {
                return InheritanceResolver.this.findInheritedBaseline(tenantId, inheritedVersionId);
            }
        };
    }

    private CandidateLookup batchLookup(
            List<AssetVersion> tenantVersions,
            List<AssetVersion> platformVersions,
            List<InheritanceOverride> candidateOverrides) {
        List<AssetVersion> allVersions = new ArrayList<>(tenantVersions);
        allVersions.addAll(platformVersions);
        return new CandidateLookup() {
            @Override
            public Optional<AssetVersion> findApplicableVersion(
                    String tenantId,
                    VersionedAssetType assetType,
                    String assetIdentity,
                    String orgPath,
                    String applicableScope,
                    Instant effectiveAt) {
                return selectApplicableVersion(
                    allVersions, tenantId, assetType, assetIdentity, orgPath, applicableScope, effectiveAt);
            }

            @Override
            public Optional<InheritanceOverride> findApplicableDisable(
                    String tenantId,
                    VersionedAssetType assetType,
                    String assetIdentity,
                    String applicableScope,
                    String orgPath,
                    Instant effectiveAt) {
                return selectApplicableDisable(
                    candidateOverrides,
                    tenantId,
                    assetType,
                    assetIdentity,
                    applicableScope,
                    orgPath,
                    effectiveAt);
            }

            @Override
            public Optional<InheritanceOverride> findOverride(String tenantId, String overrideVersionId) {
                return candidateOverrides.stream()
                    .filter(value -> Objects.equals(value.tenantId(), tenantId))
                    .filter(value -> Objects.equals(value.overrideVersionId(), overrideVersionId))
                    .sorted(Comparator
                        .comparing(InheritanceOverride::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(
                            InheritanceOverride::overrideId,
                            Comparator.nullsFirst(Comparator.reverseOrder())))
                    .findFirst();
            }

            @Override
            public Optional<AssetVersion> findInheritedBaseline(String tenantId, String inheritedVersionId) {
                if (inheritedVersionId == null || inheritedVersionId.isBlank()) {
                    return Optional.empty();
                }
                Optional<AssetVersion> tenantBaseline = allVersions.stream()
                    .filter(value -> Objects.equals(value.tenantId(), tenantId))
                    .filter(value -> Objects.equals(value.versionId(), inheritedVersionId))
                    .findFirst();
                if (tenantBaseline.isPresent()) {
                    return tenantBaseline;
                }
                return allVersions.stream()
                    .filter(value -> Objects.equals(
                        value.tenantId(), PlatformAuthority.PLATFORM_TENANT_ID))
                    .filter(value -> Objects.equals(value.versionId(), inheritedVersionId))
                    .findFirst();
            }
        };
    }

    private InheritanceExplanation disabledExplanation(
            InheritanceOverride disable, List<String> inheritancePath, String ignoredOverrideId) {
        return new InheritanceExplanation(
            appendSafetyInterception(
                "资产已被机构 " + disable.orgPath() + " 停用（DISABLE 覆盖 " + disable.overrideId() + "）",
                ignoredOverrideId),
            inheritancePath,
            disable.diffSummary(),
            disable.overrideReason(),
            disable.impactScope()
        );
    }

    private static String appendSafetyInterception(String summary, String ignoredOverrideId) {
        if (ignoredOverrideId == null) {
            return summary;
        }
        return summary + "；已按平台安全锁定忽略下级覆盖 " + ignoredOverrideId + "，回退继承锁定版本";
    }

    private static <T> T required(T value, String label) {
        if (value == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "不能为空");
        }
        return value;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "不能为空");
        }
        return value.trim();
    }

    private interface CandidateLookup {
        Optional<AssetVersion> findApplicableVersion(
            String tenantId,
            VersionedAssetType assetType,
            String assetIdentity,
            String orgPath,
            String applicableScope,
            Instant effectiveAt
        );

        Optional<InheritanceOverride> findApplicableDisable(
            String tenantId,
            VersionedAssetType assetType,
            String assetIdentity,
            String applicableScope,
            String orgPath,
            Instant effectiveAt
        );

        Optional<InheritanceOverride> findOverride(String tenantId, String overrideVersionId);

        Optional<AssetVersion> findInheritedBaseline(String tenantId, String inheritedVersionId);
    }
}
