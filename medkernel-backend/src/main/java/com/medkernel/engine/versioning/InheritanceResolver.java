package com.medkernel.engine.versioning;

import java.util.List;
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

    public InheritanceResolver(
            OrgHierarchyRepository hierarchy,
            AssetVersionRepository assetVersions,
            InheritanceOverrideRepository overrides) {
        this.hierarchy = hierarchy;
        this.assetVersions = assetVersions;
        this.overrides = overrides;
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

        for (int index = path.size() - 1; index >= 0; index--) {
            OrgUnit candidate = path.get(index);
            List<AssetVersion> active = assetVersions.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
                tenantId,
                assetType,
                activeScopeKey(assetIdentity, candidate.orgPath(), applicableScope),
                AssetVersionStatus.ACTIVE
            );
            if (active.isEmpty()) {
                continue;
            }
            AssetVersion selected = active.get(0);
            Optional<InheritanceOverride> override = overrides.findByTenantIdAndOverrideVersionId(
                tenantId, selected.versionId());
            boolean inherited = !candidate.orgPath().equals(target.orgPath());
            boolean overridden = override.isPresent();
            return new ResolvedAssetVersion(
                selected,
                candidate.orgPath(),
                inherited,
                overridden,
                explanation(selected, inheritancePath, inherited, override)
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
            Optional<InheritanceOverride> override) {
        if (override.isPresent()) {
            InheritanceOverride value = override.get();
            return new InheritanceExplanation(
                "命中本级局部覆盖版本 " + version.versionNo(),
                inheritancePath,
                value.diffSummary(),
                value.overrideReason(),
                value.impactScope()
            );
        }
        String summary = inherited
            ? "未找到本级覆盖，继承上级组织版本 " + version.versionNo()
            : "命中本级 ACTIVE 版本 " + version.versionNo();
        return new InheritanceExplanation(summary, inheritancePath, null, null, null);
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
