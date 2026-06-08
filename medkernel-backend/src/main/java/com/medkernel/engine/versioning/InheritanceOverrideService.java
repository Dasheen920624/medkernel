package com.medkernel.engine.versioning;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.org.OrgHierarchyRepository;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.security.PermissionCode;
import com.medkernel.engine.security.PermissionEvaluator;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.ids.Ulid;

/**
 * 继承局部覆盖解释登记服务。
 */
@Service
public class InheritanceOverrideService {

    private final AssetVersionRepository assetVersions;
    private final InheritanceOverrideRepository overrides;
    private final OrgHierarchyRepository hierarchy;
    private final PermissionEvaluator permissionEvaluator;
    private final AssetDependencyService assetDependencies;
    private final Clock clock;

    @Autowired
    public InheritanceOverrideService(
            AssetVersionRepository assetVersions,
            InheritanceOverrideRepository overrides,
            OrgHierarchyRepository hierarchy,
            PermissionEvaluator permissionEvaluator,
            AssetDependencyService assetDependencies) {
        this(assetVersions, overrides, hierarchy, permissionEvaluator, assetDependencies, Clock.systemUTC());
    }

    InheritanceOverrideService(
            AssetVersionRepository assetVersions,
            InheritanceOverrideRepository overrides,
            OrgHierarchyRepository hierarchy,
            PermissionEvaluator permissionEvaluator,
            Clock clock) {
        this(assetVersions, overrides, hierarchy, permissionEvaluator, null, clock);
    }

    InheritanceOverrideService(
            AssetVersionRepository assetVersions,
            InheritanceOverrideRepository overrides,
            OrgHierarchyRepository hierarchy,
            PermissionEvaluator permissionEvaluator,
            AssetDependencyService assetDependencies,
            Clock clock) {
        this.assetVersions = assetVersions;
        this.overrides = overrides;
        this.hierarchy = hierarchy;
        this.permissionEvaluator = permissionEvaluator;
        this.assetDependencies = assetDependencies;
        this.clock = clock;
    }

    @Transactional
    public InheritanceOverride registerOverride(InheritanceOverrideRegisterCommand command) {
        String tenantId = required(command.tenantId(), "租户 ID");
        VersionedAssetType assetType = required(command.assetType(), "资产类型");
        String assetIdentity = required(command.assetIdentity(), "资产身份");
        String targetOrgUnitId = required(command.targetOrgUnitId(), "目标组织 ID");
        String applicableScope = required(command.applicableScope(), "适用人群或上下文");
        InheritanceOverrideMode mode = required(command.overrideMode(), "覆盖方式");
        String diffSummary = required(command.diffSummary(), "差异说明");
        String overrideReason = required(command.overrideReason(), "覆盖原因");
        String impactScope = required(command.impactScope(), "影响范围");
        String actor = required(command.createdBy(), "创建人");
        requireTenantOverridePermission(tenantId);

        List<OrgUnit> path = hierarchy.findAncestorsAndSelf(tenantId, targetOrgUnitId);
        if (path.isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "组织不存在: " + targetOrgUnitId);
        }
        OrgUnit target = path.get(path.size() - 1);
        requireWithinActorOrgClosure(tenantId, targetOrgUnitId);

        AssetVersion inherited = null;
        String inheritedVersionId = blankToNull(command.inheritedVersionId());
        if (mode == InheritanceOverrideMode.ADD) {
            if (inheritedVersionId != null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "ADD 独有资产不绑定被继承版本");
            }
            denyAddWhenPlatformBaselineExists(assetType, assetIdentity, applicableScope);
        } else {
            inheritedVersionId = required(inheritedVersionId, "被继承版本 ID");
            inherited = findInheritedVersion(tenantId, inheritedVersionId);
            assertVersionDomain(inherited, assetType, assetIdentity, applicableScope, "被继承版本");
            if (!isSameOrDescendant(target.orgPath(), inherited.organizationScope())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "覆盖组织必须位于被继承版本生效域下");
            }
        }

        // REPLACE/ADD 须绑定本地 ACTIVE 版本；DISABLE 无替换版本，overrideVersion 留空，
        // 仅凭已校验必填的原因/影响/差异/操作者/trace 留作发布证据链（解析期由 InheritanceResolver 消费）
        AssetVersion overrideVersion = null;
        if (mode == InheritanceOverrideMode.REPLACE || mode == InheritanceOverrideMode.ADD) {
            String label = mode == InheritanceOverrideMode.ADD ? "独有版本 ID" : "覆盖版本 ID";
            overrideVersion = findOwnedVersion(tenantId, required(command.overrideVersionId(), label));
            assertVersionDomain(overrideVersion, assetType, assetIdentity, applicableScope, "覆盖版本");
            if (overrideVersion.status() != AssetVersionStatus.ACTIVE) {
                throw new ApiException(ErrorCode.CONFLICT, "覆盖版本必须为 ACTIVE");
            }
            if (!target.orgPath().equals(overrideVersion.organizationScope())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "覆盖版本组织生效域必须等于目标组织");
            }
        }

        denyUnsafeLowerOverride(inherited, overrideVersion, mode, target.orgPath());
        if (mode == InheritanceOverrideMode.DISABLE && assetDependencies != null) {
            assetDependencies.assertDisableAllowed(tenantId, assetType, assetIdentity, target.orgPath(), applicableScope);
        }

        InheritancePropagation propagation = command.propagation() == null
            ? InheritancePropagation.INHERITABLE
            : command.propagation();
        InheritanceOverrideStatus lifecycleStatus = requiresReview(inherited, overrideVersion)
            ? InheritanceOverrideStatus.IN_REVIEW
            : InheritanceOverrideStatus.PUBLISHED;
        Instant now = Instant.now(clock);
        return overrides.save(new InheritanceOverride(
            null,
            "io-" + Ulid.newUlid(),
            tenantId,
            assetType,
            assetIdentity,
            inheritedVersionId,
            overrideVersion == null ? null : overrideVersion.versionId(),
            mode,
            propagation,
            lifecycleStatus,
            target.orgPath(),
            applicableScope,
            diffSummary,
            overrideReason,
            impactScope,
            now,
            actor,
            now,
            actor,
            blankToNull(command.traceId())
        ));
    }

    private void requireTenantOverridePermission(String tenantId) {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope.hasTenant() && !tenantId.equals(scope.tenantId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "tenant.override 只能作用于当前请求租户");
        }
        if (permissionEvaluator == null || !permissionEvaluator.has(PermissionCode.TENANT_OVERRIDE)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "缺少 tenant.override，不能发布租户或机构覆盖");
        }
    }

    private void requireWithinActorOrgClosure(String tenantId, String targetOrgUnitId) {
        String actorOrgUnitId = RequestContext.currentOrgScope().nearestOrgUnitId();
        if (actorOrgUnitId == null || actorOrgUnitId.isBlank()) {
            return;
        }
        if (!hierarchy.isDescendant(tenantId, actorOrgUnitId, targetOrgUnitId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "tenant.override 只能作用于自身组织闭包");
        }
    }

    private boolean requiresReview(AssetVersion inherited) {
        return requiresReview(inherited, null);
    }

    private boolean requiresReview(AssetVersion inherited, AssetVersion overrideVersion) {
        AssetVersion policySource = inherited == null ? overrideVersion : inherited;
        return policySource != null
            && (policySource.overridePolicy() == AssetVersionOverridePolicy.REVIEW
                || policySource.overridePolicy() == AssetVersionOverridePolicy.LOCKED
                || policySource.safetyPolicy() == AssetVersionSafetyPolicy.SAFETY_REDLINE);
    }

    private void denyUnsafeLowerOverride(
            AssetVersion inherited,
            AssetVersion overrideVersion,
            InheritanceOverrideMode mode,
            String targetOrgPath) {
        if (inherited == null) {
            return;
        }
        boolean disabling = mode == InheritanceOverrideMode.DISABLE;
        // 锁定基线禁止被关闭（附录 S1）：编辑期前置禁用，与解析期 permitsDisable 护栏形成纵深防御
        if (disabling && inherited.overridePolicy() == AssetVersionOverridePolicy.LOCKED) {
            throw new ApiException(
                ErrorCode.INHERITANCE_SAFETY_DENIED,
                "INHERITANCE_SAFETY_DENIED：锁定基线(override_policy=LOCKED)禁止下级组织关闭继承"
            );
        }
        boolean lowerOrg = !targetOrgPath.equals(inherited.organizationScope());
        if (!lowerOrg || inherited.safetyPolicy() != AssetVersionSafetyPolicy.SAFETY_REDLINE) {
            return;
        }
        boolean downgrading = overrideVersion != null
            && overrideVersion.safetyPolicy() != AssetVersionSafetyPolicy.SAFETY_REDLINE;
        if (disabling || downgrading) {
            throw new ApiException(
                ErrorCode.INHERITANCE_SAFETY_DENIED,
                "INHERITANCE_SAFETY_DENIED：高风险禁忌红线禁止下级组织关闭或降级覆盖"
            );
        }
    }

    private AssetVersion findInheritedVersion(String tenantId, String versionId) {
        Optional<AssetVersion> tenantVersion = assetVersions.findByVersionIdAndTenantId(
            required(versionId, "版本 ID"),
            required(tenantId, "租户 ID")
        );
        if (tenantVersion.isPresent()) {
            return tenantVersion.get();
        }
        return assetVersions.findByVersionIdAndTenantId(versionId, PlatformTenant.ID)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "资产版本不存在: " + versionId));
    }

    private AssetVersion findOwnedVersion(String tenantId, String versionId) {
        return assetVersions.findByVersionIdAndTenantId(
                required(versionId, "版本 ID"),
                required(tenantId, "租户 ID")
            )
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "资产版本不存在: " + versionId));
    }

    private void denyAddWhenPlatformBaselineExists(
            VersionedAssetType assetType,
            String assetIdentity,
            String applicableScope) {
        List<AssetVersion> platformActive = assetVersions.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
            PlatformAuthority.PLATFORM_TENANT_ID,
            assetType,
            InheritanceResolver.activeScopeKey(assetIdentity, PlatformAuthority.PLATFORM_ORG_PATH, applicableScope),
            AssetVersionStatus.ACTIVE
        );
        if (platformActive != null && !platformActive.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "平台已有该身份基线，请使用 REPLACE 或 DISABLE");
        }
    }

    private void assertVersionDomain(
            AssetVersion version,
            VersionedAssetType assetType,
            String assetIdentity,
            String applicableScope,
            String label) {
        if (version.assetType() != assetType
                || !version.assetIdentity().equals(assetIdentity)
                || !version.applicableScope().equals(applicableScope)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "与覆盖命令的资产域不一致");
        }
    }

    private boolean isSameOrDescendant(String targetOrgPath, String inheritedOrgPath) {
        if (PlatformAuthority.PLATFORM_ORG_PATH.equals(inheritedOrgPath)) {
            return true;
        }
        return targetOrgPath.equals(inheritedOrgPath) || targetOrgPath.startsWith(inheritedOrgPath + "/");
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
