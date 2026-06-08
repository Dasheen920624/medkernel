package com.medkernel.engine.versioning;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.medkernel.engine.org.OrgHierarchyRepository;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.hash.Sha256ContentHash;

/**
 * 基于组织树闭包解析配置资产继承版本；专病通过适用域参与版本筛选。
 */
@Service
public class InheritanceResolver {

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
        String applicableScope = required(query.applicableScope(), "适用人群或上下文");
        String targetOrgUnitId = required(query.targetOrgUnitId(), "目标组织 ID");

        List<OrgUnit> path = hierarchy.findResolutionAncestorsAndSelf(tenantId, targetOrgUnitId);
        if (path == null || path.isEmpty()) {
            path = hierarchy.findAncestorsAndSelf(tenantId, targetOrgUnitId);
        }
        if (path.isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "组织不存在: " + targetOrgUnitId);
        }
        OrgUnit target = path.get(path.size() - 1);
        List<String> inheritancePath = path.stream().map(OrgUnit::orgPath).toList();

        // 记录沿途被安全护栏忽略的下级覆盖标识，写入解析说明供审核台与审计追溯
        String ignoredOverrideId = null;
        for (int index = path.size() - 1; index >= 0; index--) {
            OrgUnit candidate = path.get(index);
            boolean inherited = !candidate.orgPath().equals(target.orgPath());
            List<AssetVersion> active = assetVersions.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
                tenantId,
                assetType,
                activeScopeKey(assetIdentity, candidate.orgPath(), applicableScope),
                AssetVersionStatus.ACTIVE
            );
            if (active.isEmpty()) {
                // 本级无替换版本：消费本级停用(DISABLE)覆盖（其 override_version_id 为空，按组织生效域直查）
                Optional<InheritanceOverride> disable = findApplicableDisable(
                    tenantId, assetType, assetIdentity, applicableScope, candidate.orgPath());
                if (disable.isPresent()) {
                    InheritanceOverride value = disable.get();
                    // 传播判定：祖先节点的 EXCLUSIVE 停用仅本级生效、不向下沉
                    if (inherited && value.propagation() == InheritancePropagation.EXCLUSIVE) {
                        continue;
                    }
                    // 安全护栏：锁定/红线基线禁止被下级关闭，忽略该停用、回退继承锁定版本
                    if (!permitsDisable(tenantId, value)) {
                        ignoredOverrideId = value.overrideId();
                        continue;
                    }
                    return new ResolvedAssetVersion(
                        null,
                        candidate.orgPath(),
                        inherited,
                        true,
                        true,
                        disabledExplanation(value, inheritancePath, ignoredOverrideId),
                        SourceTier.ORG
                    );
                }
                continue;
            }
            AssetVersion selected = active.get(0);
            Optional<InheritanceOverride> override = overrides.findByTenantIdAndOverrideVersionId(
                tenantId, selected.versionId());
            if (override.isPresent()) {
                InheritanceOverride value = override.get();
                if (value.lifecycleStatus() != InheritanceOverrideStatus.PUBLISHED) {
                    continue;
                }
                // 传播判定：祖先节点的 EXCLUSIVE 覆盖仅本节点生效、不向下沉；下级跳过它，回退到上一层适用版本
                if (inherited && value.propagation() == InheritancePropagation.EXCLUSIVE) {
                    continue;
                }
                // 安全护栏：被继承的锁定/红线基线禁止被放宽性 REPLACE 覆盖，解析期忽略该覆盖、回退继承锁定版本
                if (value.overrideMode() == InheritanceOverrideMode.REPLACE
                        && !permitsLockedBaselineReplace(tenantId, value, selected)) {
                    ignoredOverrideId = value.overrideId();
                    continue;
                }
            }
            boolean overridden = override.isPresent();
            return new ResolvedAssetVersion(
                selected,
                candidate.orgPath(),
                inherited,
                overridden,
                false,
                explanation(selected, inheritancePath, inherited, override, ignoredOverrideId),
                SourceTier.ORG
            );
        }

        // 租户组织闭包内无任何适用版本/覆盖：前置回退平台权威基线（设计附录 G·D1）
        ResolvedAssetVersion platformBaseline = resolvePlatformBaseline(
            assetType, assetIdentity, applicableScope, inheritancePath, ignoredOverrideId);
        if (platformBaseline != null) {
            return platformBaseline;
        }

        throw new ApiException(ErrorCode.NOT_FOUND, "未找到可继承的 ACTIVE 资产版本");
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
                rootQuery.targetOrgUnitId()
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
     * 平台主租户顶层组织路径下该身份的 ACTIVE 版本，标注 {@link SourceTier#PLATFORM}。
     *
     * <p>平台版本是继承链最一般的根：未被任何租户覆盖遮蔽的身份恒解析到平台 ACTIVE 版本，平台升级后
     * 未定制方下次解析自动跟随，无需任何租户级复制（设计 platform-authority 规格）。平台亦无基线时返回
     * {@code null}，由调用方按 {@code NOT_FOUND} 诚实降级，不伪造。
     *
     * @return 命中平台基线时的解析结果；平台无 ACTIVE 基线时为 {@code null}
     */
    private ResolvedAssetVersion resolvePlatformBaseline(
            VersionedAssetType assetType,
            String assetIdentity,
            String applicableScope,
            List<String> inheritancePath,
            String ignoredOverrideId) {
        List<AssetVersion> platformActive = assetVersions.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
            PlatformAuthority.PLATFORM_TENANT_ID,
            assetType,
            activeScopeKey(assetIdentity, PlatformAuthority.PLATFORM_ORG_PATH, applicableScope),
            AssetVersionStatus.ACTIVE
        );
        if (platformActive.isEmpty()) {
            return null;
        }
        AssetVersion baseline = platformActive.get(0);
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
            : "命中本级 ACTIVE 版本 " + version.versionNo();
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
            String tenantId, InheritanceOverride override, AssetVersion candidate) {
        AssetVersion baseline = findInheritedBaseline(tenantId, override.inheritedVersionId()).orElse(null);
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
            String orgPath) {
        return overrides
            .findByTenantIdAndAssetTypeAndAssetIdentityAndOrgPathAndApplicableScopeAndOverrideModeAndLifecycleStatus(
                tenantId,
                assetType,
                assetIdentity,
                orgPath,
                applicableScope,
                InheritanceOverrideMode.DISABLE,
                InheritanceOverrideStatus.PUBLISHED)
            .stream()
            .findFirst();
    }

    /**
     * 判定下级 DISABLE 能否关闭被继承的基线（安全下限，见设计附录 S1/S3）：锁定
     * （{@code override_policy=LOCKED}）或红线（{@code safety_policy=SAFETY_REDLINE}）基线不可被关闭。
     * 基线缺失（如已退役）时按非锁定处理放行，主权威仍由登记期把关。
     */
    private boolean permitsDisable(String tenantId, InheritanceOverride disable) {
        AssetVersion baseline = findInheritedBaseline(tenantId, disable.inheritedVersionId()).orElse(null);
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
}
