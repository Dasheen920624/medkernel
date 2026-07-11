package com.medkernel.engine.release;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItem;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItemRepository;
import com.medkernel.engine.context.ClinicalRuntimeReleaseRepository;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.versioning.InheritanceOverride;
import com.medkernel.engine.versioning.InheritanceOverrideRepository;
import com.medkernel.engine.versioning.InheritanceOverrideStatus;
import com.medkernel.engine.versioning.ReleaseSimulationResult;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;

/**
 * 平台标准版本和机构生效版本只读查询服务。
 */
@Service
public class RuntimeReleaseQueryService {

    private final PlatformBaselineReleaseRepository baselines;
    private final PlatformBaselineItemRepository baselineItems;
    private final ClinicalRuntimeReleaseRepository runtimes;
    private final ClinicalRuntimeReleaseItemRepository runtimeItems;
    private final OrgUnitRepository organizations;
    private final InheritanceOverrideRepository overrides;

    public RuntimeReleaseQueryService(
            PlatformBaselineReleaseRepository baselines,
            PlatformBaselineItemRepository baselineItems,
            ClinicalRuntimeReleaseRepository runtimes,
            ClinicalRuntimeReleaseItemRepository runtimeItems,
            OrgUnitRepository organizations,
            InheritanceOverrideRepository overrides) {
        this.baselines = baselines;
        this.baselineItems = baselineItems;
        this.runtimes = runtimes;
        this.runtimeItems = runtimeItems;
        this.organizations = organizations;
        this.overrides = overrides;
    }

    /**
     * 返回当前完整平台标准版本。
     */
    @Transactional(readOnly = true)
    public Optional<PlatformBaselineDetailResponse> currentPlatformBaseline() {
        return baselines.findFirstByOrderByRevisionNoDesc().map(release -> new PlatformBaselineDetailResponse(
            release,
            baselineItems.findByBaselineReleaseIdOrderByAssetTypeAscAssetIdentityAsc(
                release.baselineReleaseId())
        ));
    }

    /**
     * 返回指定医院当前完整机构生效版本。
     */
    @Transactional(readOnly = true)
    public Optional<ClinicalRuntimeReleaseDetailResponse> currentHospitalRuntime(
            String tenantId,
            String hospitalId) {
        return runtimes.findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(
                required(tenantId, "租户"),
                required(hospitalId, "医院"))
            .map(release -> new ClinicalRuntimeReleaseDetailResponse(
            release,
            runtimeItems.findByReleaseIdOrderByAssetTypeAscAssetIdentityAsc(
                release.releaseId())
            ));
    }

    /**
     * 分页返回指定医院的不可变机构生效版本历史。
     */
    @Transactional(readOnly = true)
    public PageResponse<ClinicalRuntimeRelease> hospitalRuntimeHistory(
            String tenantId,
            String hospitalId,
            PageRequest pageRequest) {
        String normalizedTenant = required(tenantId, "租户");
        String normalizedHospital = required(hospitalId, "医院");
        PageRequest page = pageRequest == null ? PageRequest.defaults() : pageRequest;
        return PageResponse.of(
            runtimes.pageByTenantIdAndHospitalId(
                normalizedTenant,
                normalizedHospital,
                page.offset(),
                page.safeSize()),
            page,
            runtimes.countByTenantIdAndHospitalId(
                normalizedTenant, normalizedHospital)
        );
    }

    /**
     * 只读分析目标平台标准版本相对当前机构生效版本的资产级差异。
     */
    @Transactional(readOnly = true)
    public PlatformUpgradeAnalysisResponse analyzePlatformUpgrade(
            String tenantId,
            String hospitalId,
            String targetBaselineReleaseId) {
        String normalizedTenant = required(tenantId, "租户");
        String normalizedHospital = required(hospitalId, "医院");
        String normalizedBaseline = required(targetBaselineReleaseId, "目标平台标准版本");
        PlatformBaselineRelease target = baselines
            .findByBaselineReleaseId(normalizedBaseline)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "目标平台标准版本不存在"));
        ClinicalRuntimeRelease current = runtimes
            .findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(
                normalizedTenant, normalizedHospital)
            .orElseThrow(() -> new ApiException(ErrorCode.CONFLICT, "机构尚未建立当前生效版本"));
        OrgUnit hospital = organizations.findByTenantIdAndId(normalizedTenant, normalizedHospital)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "医院不存在"));
        List<PlatformBaselineItem> targetItems =
            baselineItems.findByBaselineReleaseIdOrderByAssetTypeAscAssetIdentityAsc(
                target.baselineReleaseId());
        List<ClinicalRuntimeReleaseItem> currentItems =
            runtimeItems.findByReleaseIdOrderByAssetTypeAscAssetIdentityAsc(
                current.releaseId());
        List<PlatformUpgradeDiffItem> diffs = diffItems(
            normalizedTenant,
            hospital.orgPath(),
            currentItems,
            targetItems
        );
        PlatformUpgradeDiffSummary summary = summarize(diffs);
        PlatformUpgradeBaselineSnapshot baselineSnapshot = new PlatformUpgradeBaselineSnapshot(
            target.baselineReleaseId(),
            target.revisionNo(),
            target.manifestSha256()
        );
        PlatformUpgradeRuntimeSnapshot runtimeSnapshot = new PlatformUpgradeRuntimeSnapshot(
            current.releaseId(),
            current.revisionNo(),
            current.platformBaselineReleaseId(),
            current.manifestSha256()
        );
        String digest = digest(baselineSnapshot, runtimeSnapshot, summary, diffs);
        return new PlatformUpgradeAnalysisResponse(
            digest,
            Instant.now(),
            false,
            baselineSnapshot,
            runtimeSnapshot,
            summary,
            diffs
        );
    }

    private List<PlatformUpgradeDiffItem> diffItems(
            String tenantId,
            String hospitalOrgPath,
            List<ClinicalRuntimeReleaseItem> currentItems,
            List<PlatformBaselineItem> targetItems) {
        Map<AssetKey, ClinicalRuntimeReleaseItem> current = new LinkedHashMap<>();
        for (ClinicalRuntimeReleaseItem item : currentItems) {
            current.put(new AssetKey(item.assetType(), item.assetIdentity()), item);
        }
        Map<AssetKey, PlatformBaselineItem> target = new LinkedHashMap<>();
        for (PlatformBaselineItem item : targetItems) {
            target.put(new AssetKey(item.assetType(), item.assetIdentity()), item);
        }
        List<AssetKey> keys = new ArrayList<>();
        keys.addAll(current.keySet());
        for (AssetKey key : target.keySet()) {
            if (!current.containsKey(key)) {
                keys.add(key);
            }
        }
        keys.sort(Comparator.comparing((AssetKey key) -> key.assetType().name())
            .thenComparing(AssetKey::assetIdentity));
        List<PlatformUpgradeDiffItem> diffs = new ArrayList<>();
        for (AssetKey key : keys) {
            ClinicalRuntimeReleaseItem currentItem = current.get(key);
            PlatformBaselineItem targetItem = target.get(key);
            diffs.add(new PlatformUpgradeDiffItem(
                key.assetType(),
                key.assetIdentity(),
                changeType(currentItem, targetItem),
                currentItem == null ? null : currentItem.versionId(),
                currentItem == null ? null : currentItem.versionNo(),
                currentItem == null ? null : currentItem.contentHash(),
                targetItem == null ? null : targetItem.versionId(),
                targetItem == null ? null : targetItem.versionNo(),
                targetItem == null ? null : targetItem.contentHash(),
                conflicts(tenantId, hospitalOrgPath, key)
            ));
        }
        return diffs;
    }

    private List<ReleaseSimulationResult.Conflict> conflicts(
            String tenantId,
            String hospitalOrgPath,
            AssetKey key) {
        List<InheritanceOverride> activeOverrides =
            overrides.findByTenantIdAndAssetTypeAndAssetIdentityAndLifecycleStatus(
                    tenantId,
                    key.assetType(),
                    key.assetIdentity(),
                    InheritanceOverrideStatus.ACTIVE
                );
        if (activeOverrides == null) {
            return List.of();
        }
        return activeOverrides.stream()
            .filter(override -> isAtOrBelow(override.orgPath(), hospitalOrgPath))
            .map(RuntimeReleaseQueryService::conflict)
            .toList();
    }

    private static ReleaseSimulationResult.Conflict conflict(InheritanceOverride override) {
        return new ReleaseSimulationResult.Conflict(
            override.overrideId(),
            override.orgPath(),
            override.overrideMode().name(),
            override.overrideMode() == com.medkernel.engine.versioning.InheritanceOverrideMode.DISABLE
                ? "DISABLED"
                : "LOCAL_OVERRIDE:" + override.overrideVersionId()
        );
    }

    private static boolean isAtOrBelow(String orgPath, String targetOrgPath) {
        return Objects.equals(orgPath, targetOrgPath)
            || (orgPath != null && targetOrgPath != null && orgPath.startsWith(targetOrgPath + "/"));
    }

    private String changeType(
            ClinicalRuntimeReleaseItem current,
            PlatformBaselineItem target) {
        if (target == null || target.entryState() == ReleaseEntryState.DISABLED) {
            return "DISABLED";
        }
        if (current == null || current.entryState() != ReleaseEntryState.ACTIVE) {
            return "ADDED";
        }
        if (Objects.equals(current.versionId(), target.versionId())
                && Objects.equals(current.contentHash(), target.contentHash())) {
            return "UNCHANGED";
        }
        return "MODIFIED";
    }

    private PlatformUpgradeDiffSummary summarize(List<PlatformUpgradeDiffItem> diffs) {
        int added = 0;
        int modified = 0;
        int disabled = 0;
        int unchanged = 0;
        int conflicts = 0;
        for (PlatformUpgradeDiffItem diff : diffs) {
            switch (diff.changeType()) {
                case "ADDED" -> added++;
                case "MODIFIED" -> modified++;
                case "DISABLED" -> disabled++;
                case "UNCHANGED" -> unchanged++;
                default -> {
                }
            }
            conflicts += diff.conflicts().size();
        }
        return new PlatformUpgradeDiffSummary(added, modified, disabled, unchanged, conflicts);
    }

    private String digest(
            PlatformUpgradeBaselineSnapshot baseline,
            PlatformUpgradeRuntimeSnapshot runtime,
            PlatformUpgradeDiffSummary summary,
            List<PlatformUpgradeDiffItem> diffs) {
        List<String> lines = new ArrayList<>();
        lines.add("baseline:" + baseline.baselineReleaseId()
            + "|" + baseline.revisionNo()
            + "|" + baseline.manifestSha256());
        lines.add("runtime:" + runtime.releaseId()
            + "|" + runtime.revisionNo()
            + "|" + runtime.platformBaselineReleaseId()
            + "|" + runtime.manifestSha256());
        lines.add("summary:" + summary.added()
            + "|" + summary.modified()
            + "|" + summary.disabled()
            + "|" + summary.unchanged()
            + "|" + summary.conflictCount());
        for (PlatformUpgradeDiffItem diff : diffs) {
            lines.add(String.join(
                "|",
                diff.assetType().name(),
                diff.assetIdentity(),
                diff.changeType(),
                nullToEmpty(diff.currentVersionId()),
                nullToEmpty(diff.currentContentHash()),
                nullToEmpty(diff.targetVersionId()),
                nullToEmpty(diff.targetContentHash()),
                String.valueOf(diff.conflicts().size())
            ));
            for (ReleaseSimulationResult.Conflict conflict : diff.conflicts().stream()
                    .sorted(Comparator.comparing(
                            ReleaseSimulationResult.Conflict::overrideId,
                            Comparator.nullsFirst(String::compareTo))
                        .thenComparing(
                            ReleaseSimulationResult.Conflict::orgPath,
                            Comparator.nullsFirst(String::compareTo))
                        .thenComparing(
                            ReleaseSimulationResult.Conflict::overrideMode,
                            Comparator.nullsFirst(String::compareTo))
                        .thenComparing(
                            ReleaseSimulationResult.Conflict::resultingSource,
                            Comparator.nullsFirst(String::compareTo)))
                    .toList()) {
                lines.add(String.join(
                    "|",
                    "conflict",
                    nullToEmpty(conflict.overrideId()),
                    nullToEmpty(conflict.orgPath()),
                    nullToEmpty(conflict.overrideMode()),
                    nullToEmpty(conflict.resultingSource())
                ));
            }
        }
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                sha256.digest(String.join("\n", lines).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256 摘要算法", exception);
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "不能为空");
        }
        return value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record AssetKey(VersionedAssetType assetType, String assetIdentity) {
    }
}
