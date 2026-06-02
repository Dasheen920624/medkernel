package com.medkernel.engine.versioning;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.org.OrgHierarchyRepository;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.ids.Ulid;

/**
 * 继承局部覆盖解释登记服务。
 */
@Service
public class InheritanceOverrideService {

    private final AssetVersionRepository assetVersions;
    private final InheritanceOverrideRepository overrides;
    private final OrgHierarchyRepository hierarchy;
    private final Clock clock;

    @Autowired
    public InheritanceOverrideService(
            AssetVersionRepository assetVersions,
            InheritanceOverrideRepository overrides,
            OrgHierarchyRepository hierarchy) {
        this(assetVersions, overrides, hierarchy, Clock.systemUTC());
    }

    InheritanceOverrideService(
            AssetVersionRepository assetVersions,
            InheritanceOverrideRepository overrides,
            OrgHierarchyRepository hierarchy,
            Clock clock) {
        this.assetVersions = assetVersions;
        this.overrides = overrides;
        this.hierarchy = hierarchy;
        this.clock = clock;
    }

    @Transactional
    public InheritanceOverride registerOverride(InheritanceOverrideRegisterCommand command) {
        String tenantId = required(command.tenantId(), "租户 ID");
        VersionedAssetType assetType = required(command.assetType(), "资产类型");
        String assetIdentity = required(command.assetIdentity(), "资产身份");
        String inheritedVersionId = required(command.inheritedVersionId(), "被继承版本 ID");
        String targetOrgUnitId = required(command.targetOrgUnitId(), "目标组织 ID");
        String applicableScope = required(command.applicableScope(), "适用人群或上下文");
        InheritanceOverrideMode mode = required(command.overrideMode(), "覆盖方式");
        String diffSummary = required(command.diffSummary(), "差异说明");
        String overrideReason = required(command.overrideReason(), "覆盖原因");
        String impactScope = required(command.impactScope(), "影响范围");
        String actor = required(command.createdBy(), "创建人");

        AssetVersion inherited = findOwnedVersion(tenantId, inheritedVersionId);
        assertVersionDomain(inherited, assetType, assetIdentity, applicableScope, "被继承版本");

        List<OrgUnit> path = hierarchy.findAncestorsAndSelf(tenantId, targetOrgUnitId);
        if (path.isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "组织不存在: " + targetOrgUnitId);
        }
        OrgUnit target = path.get(path.size() - 1);
        if (!isSameOrDescendant(target.orgPath(), inherited.organizationScope())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "覆盖组织必须位于被继承版本生效域下");
        }

        AssetVersion overrideVersion = null;
        if (mode == InheritanceOverrideMode.REPLACE) {
            overrideVersion = findOwnedVersion(tenantId, required(command.overrideVersionId(), "覆盖版本 ID"));
            assertVersionDomain(overrideVersion, assetType, assetIdentity, applicableScope, "覆盖版本");
            if (overrideVersion.status() != AssetVersionStatus.ACTIVE) {
                throw new ApiException(ErrorCode.CONFLICT, "覆盖版本必须为 ACTIVE");
            }
            if (!target.orgPath().equals(overrideVersion.organizationScope())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "覆盖版本组织生效域必须等于目标组织");
            }
        }

        denyUnsafeLowerOverride(inherited, overrideVersion, mode, target.orgPath());
        rejectDisableUntilReleaseFlowSupportsEvidence(mode);

        Instant now = Instant.now(clock);
        return overrides.save(new InheritanceOverride(
            null,
            "io-" + Ulid.newUlid(),
            tenantId,
            assetType,
            assetIdentity,
            inherited.versionId(),
            overrideVersion == null ? null : overrideVersion.versionId(),
            mode,
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

    private void denyUnsafeLowerOverride(
            AssetVersion inherited,
            AssetVersion overrideVersion,
            InheritanceOverrideMode mode,
            String targetOrgPath) {
        boolean lowerOrg = !targetOrgPath.equals(inherited.organizationScope());
        if (!lowerOrg || inherited.safetyPolicy() != AssetVersionSafetyPolicy.SAFETY_REDLINE) {
            return;
        }
        boolean disabling = mode == InheritanceOverrideMode.DISABLE;
        boolean downgrading = overrideVersion != null
            && overrideVersion.safetyPolicy() != AssetVersionSafetyPolicy.SAFETY_REDLINE;
        if (disabling || downgrading) {
            throw new ApiException(
                ErrorCode.INHERITANCE_SAFETY_DENIED,
                "INHERITANCE_SAFETY_DENIED：高风险禁忌红线禁止下级组织关闭或降级覆盖"
            );
        }
    }

    private void rejectDisableUntilReleaseFlowSupportsEvidence(InheritanceOverrideMode mode) {
        if (mode == InheritanceOverrideMode.DISABLE) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "关闭继承需要发布流证据链和解析消费能力，SYS-04 PR2 仅支持替换覆盖登记"
            );
        }
    }

    private AssetVersion findOwnedVersion(String tenantId, String versionId) {
        return assetVersions.findByVersionIdAndTenantId(
                required(versionId, "版本 ID"),
                required(tenantId, "租户 ID")
            )
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "资产版本不存在: " + versionId));
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
