package com.medkernel.engine.versioning;

import java.util.List;

import org.springframework.stereotype.Service;

import com.medkernel.engine.org.OrgHierarchyRepository;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.release.ReleaseSourceLayer;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;

/**
 * 资产归属范围解析器。
 *
 * <p>稳定资产身份不携带来源层。来源层由版本的真实组织归属决定：平台使用唯一权威路径，
 * 租户或区域节点属于集团层，医疗机构及其下级工作上下文统一归并到所属机构根路径。
 * 科室、病区、专科和人群适用性由资产正文表达，不产生额外发布层级。
 */
@Service
public class AssetScopeResolver {

    private final OrgUnitRepository organizations;
    private final OrgHierarchyRepository hierarchy;

    public AssetScopeResolver(
            OrgUnitRepository organizations,
            OrgHierarchyRepository hierarchy) {
        this.organizations = organizations;
        this.hierarchy = hierarchy;
    }

    /**
     * 根据当前真实组织上下文解析资产归属。
     */
    public AssetOwnershipScope resolve(String tenantId, OrgScope scope) {
        String normalizedTenant = required(tenantId, "租户");
        if (PlatformTenant.isPlatformTenant(normalizedTenant)) {
            return new AssetOwnershipScope(
                ReleaseSourceLayer.PLATFORM,
                PlatformAuthority.PLATFORM_ORG_PATH);
        }
        OrgScope normalizedScope = scope == null ? OrgScope.empty() : scope;
        if (normalizedScope.hasTenant()
                && !normalizedTenant.equals(normalizedScope.tenantId().trim())) {
            throw validation("资产租户与组织上下文租户不一致");
        }
        String targetId = blankToNull(normalizedScope.nearestOrgUnitId());
        if (targetId == null) {
            OrgUnit root = organizations
                .findByTenantIdAndParentIdIsNull(normalizedTenant)
                .orElseThrow(() -> new ApiException(
                    ErrorCode.NOT_FOUND, "租户根组织不存在，不能登记资产版本"));
            return ownership(root);
        }
        List<OrgUnit> path = hierarchy.findAncestorsAndSelf(normalizedTenant, targetId);
        if (path.isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "组织上下文不在当前租户组织树中");
        }
        return ownershipFromHierarchy(path);
    }

    /**
     * 校验持久化版本使用的是规范平台、集团或机构根路径，并解析其来源层。
     */
    public AssetOwnershipScope resolveOrganizationPath(
            String tenantId,
            String organizationPath) {
        String normalizedTenant = required(tenantId, "租户");
        String normalizedPath = required(organizationPath, "组织归属路径");
        if (PlatformTenant.isPlatformTenant(normalizedTenant)) {
            if (!PlatformAuthority.PLATFORM_ORG_PATH.equals(normalizedPath)) {
                throw validation("平台资产必须使用唯一平台权威路径");
            }
            return new AssetOwnershipScope(
                ReleaseSourceLayer.PLATFORM,
                PlatformAuthority.PLATFORM_ORG_PATH);
        }
        OrgUnit owner = organizations
            .findByTenantIdAndOrgPath(normalizedTenant, normalizedPath)
            .orElseThrow(() -> new ApiException(
                ErrorCode.NOT_FOUND, "资产组织归属路径不存在"));
        if (owner.level() == OrgLevel.TENANT
                || owner.level() == OrgLevel.REGION
                || owner.level() == OrgLevel.FACILITY) {
            return ownership(owner);
        }
        List<OrgUnit> path = hierarchy.findAncestorsAndSelf(
            normalizedTenant, owner.id());
        if (path.isEmpty()) {
            throw new ApiException(
                ErrorCode.NOT_FOUND, "资产组织归属路径不在当前租户组织树中");
        }
        return ownershipFromHierarchy(path);
    }

    private AssetOwnershipScope ownershipFromHierarchy(List<OrgUnit> path) {
        OrgUnit target = path.get(path.size() - 1);
        if (!target.isActive()) {
            throw validation("停用或归档组织不能登记资产版本");
        }
        OrgUnit facility = path.stream()
            .filter(unit -> unit.level() == OrgLevel.FACILITY)
            .findFirst()
            .orElse(null);
        if (facility != null) {
            return ownership(facility);
        }
        OrgUnit groupOwner = path.stream()
            .filter(unit -> unit.level() == OrgLevel.TENANT
                || unit.level() == OrgLevel.REGION)
            .reduce((left, right) -> right)
            .orElseThrow(() -> validation("组织路径缺少租户或区域归属"));
        return ownership(groupOwner);
    }

    private AssetOwnershipScope ownership(OrgUnit owner) {
        if (owner == null || !owner.isActive()) {
            throw validation("资产归属组织必须处于启用状态");
        }
        String path = required(owner.orgPath(), "组织归属路径");
        if (owner.level() == OrgLevel.TENANT || owner.level() == OrgLevel.REGION) {
            return new AssetOwnershipScope(ReleaseSourceLayer.GROUP, path);
        }
        if (owner.level() == OrgLevel.FACILITY) {
            return new AssetOwnershipScope(ReleaseSourceLayer.HOSPITAL, path);
        }
        throw validation("资产归属只能是平台、集团或医疗机构根节点");
    }

    private static ApiException validation(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }

    private static String required(String value, String label) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw validation(label + "不能为空");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
