package com.medkernel.engine.pkg;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.InheritanceResolveQuery;
import com.medkernel.engine.versioning.InheritanceResolver;
import com.medkernel.engine.versioning.PlatformAuthority;
import com.medkernel.engine.versioning.ResolvedAssetVersion;
import com.medkernel.engine.versioning.SourceTier;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.PlatformTenant;

/**
 * 计算平台权威版本升级对当前租户继承链的影响。
 */
@Service
public class PackageInheritanceImpactService {

    private static final String BASELINE_NONE = "NONE";

    private final AssetVersionRepository assetVersions;
    private final OrgUnitRepository orgUnits;
    private final InheritanceResolver inheritanceResolver;

    public PackageInheritanceImpactService(
            AssetVersionRepository assetVersions,
            OrgUnitRepository orgUnits,
            InheritanceResolver inheritanceResolver) {
        this.assetVersions = assetVersions;
        this.orgUnits = orgUnits;
        this.inheritanceResolver = inheritanceResolver;
    }

    @Transactional(readOnly = true)
    public PackageInheritanceImpactResponse analyze(
            String tenantId,
            VersionedAssetType assetType,
            String assetIdentity,
            String applicableScope,
            String upstreamVersionId) {
        String normalizedTenantId = requireText(tenantId, "租户 ID");
        VersionedAssetType normalizedAssetType = requireAssetType(assetType);
        String normalizedAssetIdentity = requireText(assetIdentity, "资产身份");
        String normalizedApplicableScope = requireText(applicableScope, "适用范围");
        String normalizedUpstreamVersionId = requireText(upstreamVersionId, "上游版本 ID");

        AssetVersion target = assetVersions.findByVersionIdAndTenantId(normalizedUpstreamVersionId, PlatformTenant.ID)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "平台上游版本不存在: " + normalizedUpstreamVersionId));
        validatePlatformTarget(target, normalizedAssetType, normalizedAssetIdentity, normalizedApplicableScope);

        AssetVersion base = findActivePlatformBaseline(normalizedAssetType, normalizedAssetIdentity, normalizedApplicableScope);
        List<PackageInheritanceImpactTarget> targets = analyzeTargets(
            normalizedTenantId,
            normalizedAssetType,
            normalizedAssetIdentity,
            normalizedApplicableScope,
            target
        );

        PackageDiffResponse upstreamDiff = buildUpstreamDiff(
            normalizedAssetIdentity,
            normalizedAssetType,
            base,
            target,
            targets
        );

        int autoInheritedCount = count(targets, PackageInheritanceImpactType.AUTO_INHERITS_UPSTREAM);
        int rebaseRequiredCount = count(targets, PackageInheritanceImpactType.REBASE_RECOMMENDED)
            + count(targets, PackageInheritanceImpactType.DISABLE_REVIEW_RECOMMENDED);

        return new PackageInheritanceImpactResponse(
            normalizedTenantId,
            normalizedAssetType,
            normalizedAssetIdentity,
            normalizedApplicableScope,
            base == null ? BASELINE_NONE : base.versionNo(),
            target.versionNo(),
            autoInheritedCount,
            rebaseRequiredCount,
            upstreamDiff,
            targets
        );
    }

    private List<PackageInheritanceImpactTarget> analyzeTargets(
            String tenantId,
            VersionedAssetType assetType,
            String assetIdentity,
            String applicableScope,
            AssetVersion target) {
        List<PackageInheritanceImpactTarget> targets = new ArrayList<>();
        for (OrgUnit orgUnit : orgUnits.findByTenantIdOrderByLevelAscCodeAsc(tenantId)) {
            ResolvedAssetVersion resolved = inheritanceResolver.resolve(
                new InheritanceResolveQuery(tenantId, assetType, assetIdentity, applicableScope, orgUnit.id()));
            PackageInheritanceImpactType impactType = classify(resolved, target);
            targets.add(new PackageInheritanceImpactTarget(
                orgUnit.id(),
                orgUnit.orgPath(),
                impactType,
                resolved.version() == null ? null : resolved.version().versionId(),
                resolved.version() == null ? null : resolved.version().versionNo(),
                resolved.sourceTier() == null ? null : resolved.sourceTier().name(),
                diffSummary(resolved),
                rebasePrompt(impactType, resolved)
            ));
        }
        return targets;
    }

    private PackageInheritanceImpactType classify(ResolvedAssetVersion resolved, AssetVersion target) {
        if (resolved.disabled()) {
            return PackageInheritanceImpactType.DISABLE_REVIEW_RECOMMENDED;
        }
        AssetVersion effective = resolved.version();
        if (effective == null) {
            return PackageInheritanceImpactType.AUTO_INHERITS_UPSTREAM;
        }
        if (sameContent(effective, target)) {
            return PackageInheritanceImpactType.UNAFFECTED;
        }
        if (resolved.sourceTier() == SourceTier.PLATFORM) {
            return PackageInheritanceImpactType.AUTO_INHERITS_UPSTREAM;
        }
        if (resolved.overridden() || resolved.sourceTier() == SourceTier.ORG) {
            return PackageInheritanceImpactType.REBASE_RECOMMENDED;
        }
        return PackageInheritanceImpactType.UNAFFECTED;
    }

    private PackageDiffResponse buildUpstreamDiff(
            String assetIdentity,
            VersionedAssetType assetType,
            AssetVersion base,
            AssetVersion target,
            List<PackageInheritanceImpactTarget> targets) {
        List<PackageDiffChange> changes = buildChanges(assetIdentity, assetType, base, target);
        return new PackageDiffResponse(
            assetIdentity,
            base == null ? BASELINE_NONE : base.versionNo(),
            target.versionNo(),
            base == null ? 1 : 0,
            base != null && !changes.isEmpty() ? 1 : 0,
            0,
            affectedOrgUnitIds(targets),
            changes
        );
    }

    private List<PackageDiffChange> buildChanges(
            String assetIdentity,
            VersionedAssetType assetType,
            AssetVersion base,
            AssetVersion target) {
        if (base == null) {
            return List.of(new PackageDiffChange(
                PackageDiffChangeType.ADDED,
                assetType,
                assetIdentity,
                null,
                target.versionNo()
            ));
        }
        if (sameVersion(base, target) || sameContent(base, target)) {
            return List.of();
        }
        return List.of(new PackageDiffChange(
            PackageDiffChangeType.UPDATED,
            assetType,
            assetIdentity,
            base.versionNo(),
            target.versionNo()
        ));
    }

    private List<String> affectedOrgUnitIds(List<PackageInheritanceImpactTarget> targets) {
        return targets.stream()
            .filter(target -> target.impactType() != PackageInheritanceImpactType.UNAFFECTED)
            .map(PackageInheritanceImpactTarget::orgUnitId)
            .toList();
    }

    private AssetVersion findActivePlatformBaseline(
            VersionedAssetType assetType,
            String assetIdentity,
            String applicableScope) {
        List<AssetVersion> activeVersions = assetVersions.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
            PlatformTenant.ID,
            assetType,
            activeScopeKey(assetIdentity, PlatformAuthority.PLATFORM_ORG_PATH, applicableScope),
            AssetVersionStatus.PUBLISHED
        );
        return activeVersions.isEmpty() ? null : activeVersions.get(0);
    }

    private void validatePlatformTarget(
            AssetVersion target,
            VersionedAssetType assetType,
            String assetIdentity,
            String applicableScope) {
        if (target.assetType() != assetType
                || !Objects.equals(target.assetIdentity(), assetIdentity)
                || !Objects.equals(target.applicableScope(), applicableScope)
                || !Objects.equals(target.organizationScope(), PlatformAuthority.PLATFORM_ORG_PATH)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "上游版本与请求的资产域不一致");
        }
    }

    private int count(List<PackageInheritanceImpactTarget> targets, PackageInheritanceImpactType impactType) {
        return (int) targets.stream()
            .map(PackageInheritanceImpactTarget::impactType)
            .filter(impactType::equals)
            .count();
    }

    private String diffSummary(ResolvedAssetVersion resolved) {
        return resolved.explanation() == null ? null : resolved.explanation().diffSummary();
    }

    private String rebasePrompt(PackageInheritanceImpactType impactType, ResolvedAssetVersion resolved) {
        return switch (impactType) {
            case AUTO_INHERITS_UPSTREAM -> "平台新版本激活后自动继承";
            case REBASE_RECOMMENDED -> "上游平台已变更，建议 rebase 当前覆盖后再发布。当前差异："
                + readableDiffSummary(resolved);
            case DISABLE_REVIEW_RECOMMENDED -> "上游平台已变更，当前组织已停用继承，建议复核是否继续停用或 rebase。当前差异："
                + readableDiffSummary(resolved);
            case UNAFFECTED -> "当前有效版本与上游目标一致，无需 rebase";
        };
    }

    private String readableDiffSummary(ResolvedAssetVersion resolved) {
        String diffSummary = diffSummary(resolved);
        if (diffSummary != null && !diffSummary.isBlank()) {
            return diffSummary;
        }
        if (resolved.explanation() != null
                && resolved.explanation().resolutionSummary() != null
                && !resolved.explanation().resolutionSummary().isBlank()) {
            return resolved.explanation().resolutionSummary();
        }
        return "未返回差异摘要";
    }

    private boolean sameVersion(AssetVersion left, AssetVersion right) {
        return Objects.equals(left.versionId(), right.versionId());
    }

    private boolean sameContent(AssetVersion left, AssetVersion right) {
        return Objects.equals(left.contentHash(), right.contentHash());
    }

    private String activeScopeKey(String assetIdentity, String organizationScope, String applicableScope) {
        return assetIdentity + "|" + organizationScope + "|" + applicableScope;
    }

    private VersionedAssetType requireAssetType(VersionedAssetType assetType) {
        if (assetType == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "资产类型不能为空");
        }
        return assetType;
    }

    private String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "不能为空");
        }
        return value.trim();
    }
}
