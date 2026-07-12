package com.medkernel.engine.knowledge.delivery;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItem;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItemRepository;
import com.medkernel.engine.context.ClinicalRuntimeReleaseRepository;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.release.PlatformUpgradeDiffItem;
import com.medkernel.engine.release.PlatformUpgradeDiffSummary;
import com.medkernel.engine.release.PlatformUpgradeRuntimeSnapshot;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.versioning.InheritanceOverride;
import com.medkernel.engine.versioning.InheritanceOverrideMode;
import com.medkernel.engine.versioning.InheritanceOverrideRepository;
import com.medkernel.engine.versioning.InheritanceOverrideStatus;
import com.medkernel.engine.versioning.ReleaseSimulationResult;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 复用当前机构生效版本和本地覆盖事实，生成包导入前的只读升级预览。 */
@Service
public class FullPackagePreviewAnalyzer {

    private final ClinicalRuntimeReleaseRepository runtimes;
    private final ClinicalRuntimeReleaseItemRepository runtimeItems;
    private final OrgUnitRepository organizations;
    private final InheritanceOverrideRepository overrides;

    public FullPackagePreviewAnalyzer(
            ClinicalRuntimeReleaseRepository runtimes,
            ClinicalRuntimeReleaseItemRepository runtimeItems,
            OrgUnitRepository organizations,
            InheritanceOverrideRepository overrides) {
        this.runtimes = runtimes;
        this.runtimeItems = runtimeItems;
        this.organizations = organizations;
        this.overrides = overrides;
    }

    /** 只读比较包内完整平台版本与目标医院当前生效清单，不创建任何资产或发布。 */
    @Transactional(readOnly = true)
    public FullPackagePreflightPreview analyze(
            String tenantId,
            String hospitalId,
            String preflightId,
            FullPackageInspection inspection,
            Instant createdAt) {
        OrgUnit hospital = organizations.findByTenantIdAndId(tenantId, hospitalId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "目标医院不存在"));
        ClinicalRuntimeRelease current = runtimes
            .findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(tenantId, hospitalId)
            .orElse(null);
        List<ClinicalRuntimeReleaseItem> currentItems = current == null
            ? List.of()
            : runtimeItems.findByReleaseIdOrderByAssetTypeAscAssetIdentityAsc(
                current.releaseId());
        Map<AssetKey, ClinicalRuntimeReleaseItem> currentByKey = new HashMap<>();
        currentItems.forEach(item -> currentByKey.put(
            new AssetKey(item.assetType(), item.assetIdentity()), item));
        Map<AssetKey, FullPackageReleaseDocument.Entry> targetByKey = new HashMap<>();
        inspection.releaseDocument().entries().forEach(entry -> targetByKey.put(
            new AssetKey(entry.assetType(), entry.assetIdentity()), entry));

        List<AssetKey> keys = new ArrayList<>(currentByKey.keySet());
        for (AssetKey key : targetByKey.keySet()) {
            if (!currentByKey.containsKey(key)) {
                keys.add(key);
            }
        }
        keys.sort(Comparator.comparing((AssetKey key) -> key.type().name())
            .thenComparing(AssetKey::identity));
        List<PlatformUpgradeDiffItem> differences = new ArrayList<>();
        Map<AssetKey, String> changes = new LinkedHashMap<>();
        for (AssetKey key : keys) {
            ClinicalRuntimeReleaseItem currentItem = currentByKey.get(key);
            FullPackageReleaseDocument.Entry target = targetByKey.get(key);
            String change = changeType(currentItem, target);
            changes.put(key, change);
            differences.add(new PlatformUpgradeDiffItem(
                key.type(),
                key.identity(),
                change,
                currentItem == null ? null : currentItem.versionId(),
                currentItem == null ? null : currentItem.versionNo(),
                currentItem == null ? null : currentItem.contentHash(),
                target == null ? null : target.versionId(),
                target == null ? null : target.versionNo(),
                target == null ? null : target.sourceContentSha256(),
                conflicts(tenantId, hospital.orgPath(), key)));
        }
        PlatformUpgradeDiffSummary summary = summarize(differences);
        FullPackagePreflightPreview.ImpactSummary impact = impact(
            inspection, currentByKey, changes);
        PlatformUpgradeRuntimeSnapshot currentSnapshot = current == null ? null
            : new PlatformUpgradeRuntimeSnapshot(
                current.releaseId(),
                current.revisionNo(),
                current.platformBaselineReleaseId(),
                current.manifestSha256());
        return new FullPackagePreflightPreview(
            "1.0",
            preflightId,
            FullPackagePreflightStatus.PASSED,
            tenantId,
            hospitalId,
            false,
            inspection.manifest().authorityId(),
            inspection.manifest().deliveryId(),
            inspection.manifest().releaseSequence(),
            inspection.signatureEnvelope().manifestDigest(),
            inspection.manifest().platformReleaseIdentity(),
            inspection.artifact().packageFileDigest(),
            inspection.artifact().packageFileSize(),
            inspection.artifact().quarantineCoordinate(),
            currentSnapshot,
            summary,
            differences,
            impact,
            inspection.releaseDocument().withdrawals(),
            inspection.archiveEntryCount(),
            inspection.expandedBytes(),
            createdAt,
            null);
    }

    private String changeType(
            ClinicalRuntimeReleaseItem current,
            FullPackageReleaseDocument.Entry target) {
        if (target == null || target.state() == ReleaseEntryState.DISABLED) {
            return "DISABLED";
        }
        if (current == null || current.entryState() != ReleaseEntryState.ACTIVE) {
            return "ADDED";
        }
        if (Objects.equals(current.versionId(), target.versionId())
                && Objects.equals(
                    current.contentHash(), plainSha256(target.sourceContentSha256()))) {
            return "UNCHANGED";
        }
        return "MODIFIED";
    }

    private String plainSha256(String value) {
        return value != null && value.startsWith("sha256:")
            ? value.substring("sha256:".length())
            : value;
    }

    private List<ReleaseSimulationResult.Conflict> conflicts(
            String tenantId,
            String hospitalOrgPath,
            AssetKey key) {
        List<InheritanceOverride> active = overrides
            .findByTenantIdAndAssetTypeAndAssetIdentityAndLifecycleStatus(
                tenantId, key.type(), key.identity(), InheritanceOverrideStatus.ACTIVE);
        if (active == null) {
            return List.of();
        }
        return active.stream()
            .filter(override -> isAtOrBelow(override.orgPath(), hospitalOrgPath))
            .map(this::conflict)
            .sorted(Comparator.comparing(
                ReleaseSimulationResult.Conflict::overrideId,
                Comparator.nullsFirst(String::compareTo)))
            .toList();
    }

    private ReleaseSimulationResult.Conflict conflict(InheritanceOverride override) {
        return new ReleaseSimulationResult.Conflict(
            override.overrideId(),
            override.orgPath(),
            override.overrideMode().name(),
            override.overrideMode() == InheritanceOverrideMode.DISABLE
                ? "DISABLED"
                : "LOCAL_OVERRIDE:" + override.overrideVersionId());
    }

    private boolean isAtOrBelow(String orgPath, String hospitalOrgPath) {
        return Objects.equals(orgPath, hospitalOrgPath)
            || (orgPath != null && hospitalOrgPath != null
                && orgPath.startsWith(hospitalOrgPath + "/"));
    }

    private PlatformUpgradeDiffSummary summarize(List<PlatformUpgradeDiffItem> differences) {
        int added = 0;
        int modified = 0;
        int disabled = 0;
        int unchanged = 0;
        int conflictCount = 0;
        for (PlatformUpgradeDiffItem difference : differences) {
            switch (difference.changeType()) {
                case "ADDED" -> added++;
                case "MODIFIED" -> modified++;
                case "DISABLED" -> disabled++;
                case "UNCHANGED" -> unchanged++;
                default -> throw new IllegalStateException(
                    "未知医疗资源包差异类型: " + difference.changeType());
            }
            conflictCount += difference.conflicts().size();
        }
        return new PlatformUpgradeDiffSummary(
            added, modified, disabled, unchanged, conflictCount);
    }

    private FullPackagePreflightPreview.ImpactSummary impact(
            FullPackageInspection inspection,
            Map<AssetKey, ClinicalRuntimeReleaseItem> current,
            Map<AssetKey, String> changes) {
        int dependencyEdges = 0;
        int changedDependencyEdges = 0;
        for (PortableAssetDocument owner : inspection.documents()) {
            for (PortableAssetDocument.Dependency dependency : owner.dependencies()) {
                dependencyEdges++;
                String change = changes.get(
                    new AssetKey(dependency.assetType(), dependency.assetIdentity()));
                if (!"UNCHANGED".equals(change)) {
                    changedDependencyEdges++;
                }
            }
        }
        int activeWithdrawalImpact = 0;
        for (FullPackageReleaseDocument.Withdrawal withdrawal
                : inspection.releaseDocument().withdrawals()) {
            ClinicalRuntimeReleaseItem currentItem = current.get(
                new AssetKey(withdrawal.assetType(), withdrawal.assetIdentity()));
            if (currentItem != null
                    && currentItem.entryState() == ReleaseEntryState.ACTIVE
                    && Objects.equals(
                        currentItem.versionId(), withdrawal.withdrawnVersionId())) {
                activeWithdrawalImpact++;
            }
        }
        return new FullPackagePreflightPreview.ImpactSummary(
            dependencyEdges,
            changedDependencyEdges,
            inspection.releaseDocument().withdrawals().size(),
            activeWithdrawalImpact);
    }

    private record AssetKey(VersionedAssetType type, String identity) {
    }
}
