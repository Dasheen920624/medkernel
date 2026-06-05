package com.medkernel.engine.versioning;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.medkernel.engine.org.OrgHierarchyRepository;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 基于七层组织闭包解析配置资产继承版本。
 */
@Service
public class InheritanceResolver {

    private final OrgHierarchyRepository hierarchy;
    private final AssetVersionRepository assetVersions;
    private final InheritanceOverrideRepository overrides;
    private final List<SafetyMonotonicityCheck> safetyChecks;

    public InheritanceResolver(
            OrgHierarchyRepository hierarchy,
            AssetVersionRepository assetVersions,
            InheritanceOverrideRepository overrides,
            List<SafetyMonotonicityCheck> safetyChecks) {
        this.hierarchy = hierarchy;
        this.assetVersions = assetVersions;
        this.overrides = overrides;
        this.safetyChecks = safetyChecks;
    }

    public ResolvedAssetVersion resolve(InheritanceResolveQuery query) {
        String tenantId = required(query.tenantId(), "租户 ID");
        VersionedAssetType assetType = required(query.assetType(), "资产类型");
        String assetIdentity = required(query.assetIdentity(), "资产身份");
        String applicableScope = required(query.applicableScope(), "适用人群或上下文");
        String targetOrgUnitId = required(query.targetOrgUnitId(), "目标组织 ID");

        List<OrgUnit> path = hierarchy.findAncestorsAndSelf(tenantId, targetOrgUnitId);
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
                        disabledExplanation(value, inheritancePath, ignoredOverrideId)
                    );
                }
                continue;
            }
            AssetVersion selected = active.get(0);
            Optional<InheritanceOverride> override = overrides.findByTenantIdAndOverrideVersionId(
                tenantId, selected.versionId());
            if (override.isPresent()) {
                InheritanceOverride value = override.get();
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
                explanation(selected, inheritancePath, inherited, override, ignoredOverrideId)
            );
        }

        throw new ApiException(ErrorCode.NOT_FOUND, "未找到可继承的 ACTIVE 资产版本");
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
        AssetVersion baseline = assetVersions
            .findByVersionIdAndTenantId(override.inheritedVersionId(), tenantId)
            .orElse(null);
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
            .findByTenantIdAndAssetTypeAndAssetIdentityAndOrgPathAndApplicableScopeAndOverrideMode(
                tenantId, assetType, assetIdentity, orgPath, applicableScope, InheritanceOverrideMode.DISABLE)
            .stream()
            .findFirst();
    }

    /**
     * 判定下级 DISABLE 能否关闭被继承的基线（安全下限，见设计附录 S1/S3）：锁定
     * （{@code override_policy=LOCKED}）或红线（{@code safety_policy=SAFETY_REDLINE}）基线不可被关闭。
     * 基线缺失（如已退役）时按非锁定处理放行，主权威仍由登记期把关。
     */
    private boolean permitsDisable(String tenantId, InheritanceOverride disable) {
        AssetVersion baseline = assetVersions
            .findByVersionIdAndTenantId(disable.inheritedVersionId(), tenantId)
            .orElse(null);
        if (baseline == null) {
            return true;
        }
        return baseline.overridePolicy() != AssetVersionOverridePolicy.LOCKED
            && baseline.safetyPolicy() != AssetVersionSafetyPolicy.SAFETY_REDLINE;
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
