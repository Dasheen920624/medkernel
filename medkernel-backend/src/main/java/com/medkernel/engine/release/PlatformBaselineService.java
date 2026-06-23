package com.medkernel.engine.release;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.versioning.AssetDependency;
import com.medkernel.engine.versioning.AssetDependencyRepository;
import com.medkernel.engine.versioning.AssetDependencyService;
import com.medkernel.engine.versioning.AssetIdentity;
import com.medkernel.engine.versioning.AssetIdentityRepository;
import com.medkernel.engine.versioning.AssetTechnicalValidationService;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.ids.Ulid;

/**
 * 平台权威基线发布服务。
 *
 * <p>每次发布都复制上一完整清单，应用本次跨类型资产变更，再生成只追加的新修订。
 */
@Service
public class PlatformBaselineService {

    private final PlatformBaselineReleaseRepository releases;
    private final PlatformBaselineItemRepository items;
    private final AssetIdentityRepository identities;
    private final AssetVersionRepository versions;
    private final AssetTechnicalValidationService validation;
    private final AssetDependencyRepository dependencies;
    private final Clock clock;

    @Autowired
    public PlatformBaselineService(
            PlatformBaselineReleaseRepository releases,
            PlatformBaselineItemRepository items,
            AssetIdentityRepository identities,
            AssetVersionRepository versions,
            AssetTechnicalValidationService validation,
            AssetDependencyRepository dependencies) {
        this(
            releases, items, identities, versions, validation,
            dependencies, Clock.systemUTC());
    }

    PlatformBaselineService(
            PlatformBaselineReleaseRepository releases,
            PlatformBaselineItemRepository items,
            AssetIdentityRepository identities,
            AssetVersionRepository versions,
            AssetTechnicalValidationService validation,
            AssetDependencyRepository dependencies,
            Clock clock) {
        this.releases = releases;
        this.items = items;
        this.identities = identities;
        this.versions = versions;
        this.validation = validation;
        this.dependencies = dependencies;
        this.clock = clock;
    }

    /**
     * 发布任意数量、任意类型的草稿变更为一个完整平台基线。
     */
    @Transactional
    public PlatformBaselineRelease publish(PlatformBaselinePublishCommand command) {
        if (command == null) {
            throw validation("发布命令不能为空");
        }
        String actor = required(command.actor(), "操作人");
        if (command.publishVersionIds().isEmpty() && command.disabledAssets().isEmpty()) {
            throw validation("本次平台发布至少包含一个资产变更");
        }

        PlatformBaselineRelease previous = releases.findFirstByOrderByRevisionNoDesc().orElse(null);
        Map<AssetKey, DraftEntry> manifest = previous == null
            ? new LinkedHashMap<>()
            : fromPrevious(previous);
        Map<AssetKey, AssetIdentity> knownIdentities = new LinkedHashMap<>();
        for (AssetIdentity identity :
                identities.findByTenantIdOrderByAssetTypeAscAssetIdentityAsc(PlatformTenant.ID)) {
            requireRuntimeType(identity.assetType());
            AssetKey key = new AssetKey(
                identity.assetType(), required(identity.assetIdentity(), "资产身份"));
            if (knownIdentities.putIfAbsent(key, identity) != null) {
                throw new ApiException(
                    ErrorCode.CONFLICT, "平台稳定资产身份重复: " + key.identity());
            }
            manifest.putIfAbsent(
                key,
                DraftEntry.disabled(identity.assetType(), identity.assetIdentity()));
        }

        Map<AssetKey, AssetVersion> changedVersions = new LinkedHashMap<>();
        for (String versionId : command.publishVersionIds()) {
            AssetVersion version = versions
                .findByVersionIdAndTenantId(required(versionId, "资产版本 ID"), PlatformTenant.ID)
                .orElseThrow(() -> new ApiException(
                    ErrorCode.NOT_FOUND, "平台草稿版本不存在: " + versionId));
            requireRuntimeType(version.assetType());
            if (version.status() != AssetVersionStatus.DRAFT) {
                throw new ApiException(
                        ErrorCode.CONFLICT, "只有草稿版本可以进入新的平台基线: " + version.versionId());
            }
            AssetKey key = new AssetKey(version.assetType(), version.assetIdentity());
            AssetIdentity identity = knownIdentities.get(key);
            if (identity == null) {
                throw new ApiException(
                    ErrorCode.CONFLICT, "平台资产版本缺少稳定身份登记: " + key.identity());
            }
            if (identity.status() != com.medkernel.engine.versioning.AssetIdentityStatus.ACTIVE) {
                throw new ApiException(
                    ErrorCode.CONFLICT, "退役资产身份不能发布新版本: " + key.identity());
            }
            validation.validateForPublish(version, actor, command.traceId());
            if (changedVersions.putIfAbsent(key, version) != null) {
                throw validation("同一稳定资产身份一次只能发布一个版本: " + key.identity());
            }
            manifest.put(key, DraftEntry.active(version));
        }

        Map<AssetKey, Boolean> disabledKeys = new LinkedHashMap<>();
        for (ReleaseAssetRef disabled : command.disabledAssets()) {
            if (disabled == null) {
                throw validation("停用资产不能为空");
            }
            VersionedAssetType assetType = requireRuntimeType(disabled.assetType());
            AssetKey key = new AssetKey(
                assetType, required(disabled.assetIdentity(), "停用资产身份"));
            if (!manifest.containsKey(key)) {
                throw new ApiException(ErrorCode.NOT_FOUND, "停用资产身份不存在: " + key.identity());
            }
            if (disabledKeys.putIfAbsent(key, Boolean.TRUE) != null) {
                throw validation("同一稳定资产身份不能重复停用: " + key.identity());
            }
            if (changedVersions.containsKey(key)) {
                throw validation("同一稳定资产身份不能同时发布和停用: " + key.identity());
            }
            manifest.put(key, DraftEntry.disabled(key.type(), key.identity()));
        }

        assertDependencyClosure(manifest);
        Instant now = clock.instant();
        for (AssetVersion version : changedVersions.values()) {
            versions.save(version.withStatusAndWindow(
                AssetVersionStatus.PUBLISHED,
                "version:" + version.versionId(),
                now,
                null,
                now,
                actor
            ));
        }

        List<DraftEntry> ordered = manifest.values().stream()
            .sorted((left, right) -> {
                int type = left.type().name().compareTo(right.type().name());
                return type != 0 ? type : left.identity().compareTo(right.identity());
            })
            .toList();
        String hash = ReleaseManifestHash.sha256(
            ordered.stream().map(DraftEntry::canonicalLine).toList());
        long revision = previous == null ? 1L : previous.revisionNo() + 1L;
        String baselineId = "baseline-" + Ulid.newUlid();
        PlatformBaselineRelease release = releases.save(new PlatformBaselineRelease(
            null,
            baselineId,
            revision,
            hash,
            now,
            actor,
            now,
            actor,
            blankToNull(command.traceId())
        ));
        for (DraftEntry entry : ordered) {
            items.save(entry.toItem(baselineId, now, actor, command.traceId()));
        }
        return release;
    }

    private Map<AssetKey, DraftEntry> fromPrevious(PlatformBaselineRelease previous) {
        Map<AssetKey, DraftEntry> manifest = new LinkedHashMap<>();
        for (PlatformBaselineItem item :
                items.findByBaselineReleaseIdOrderByAssetTypeAscAssetIdentityAsc(
                    previous.baselineReleaseId())) {
            manifest.put(
                new AssetKey(item.assetType(), item.assetIdentity()),
                DraftEntry.from(item)
            );
        }
        return manifest;
    }

    private void assertDependencyClosure(Map<AssetKey, DraftEntry> manifest) {
        for (DraftEntry owner : manifest.values()) {
            if (owner.state() != ReleaseEntryState.ACTIVE) {
                continue;
            }
            List<AssetDependency> edges =
                dependencies
                    .findByTenantIdAndAssetTypeAndAssetIdentityAndVersionIdOrderByDependsOnAssetTypeAscDependsOnIdentityAsc(
                        PlatformTenant.ID, owner.type(), owner.identity(), owner.versionId());
            for (AssetDependency edge : edges) {
                DraftEntry target = manifest.get(
                    new AssetKey(edge.dependsOnAssetType(), edge.dependsOnIdentity()));
                if (target == null || target.state() != ReleaseEntryState.ACTIVE) {
                    throw new ApiException(
                        ErrorCode.CONFLICT,
                        "发布依赖不闭合：" + owner.identity() + " 依赖 "
                            + edge.dependsOnIdentity() + "，但目标未启用");
                }
                if (!AssetDependencyService.isCompatible(target.versionNo(), edge)) {
                    throw new ApiException(
                        ErrorCode.CONFLICT,
                        "发布依赖版本不兼容：" + owner.identity() + " 依赖 "
                            + edge.dependsOnIdentity() + "@" + target.versionNo());
                }
            }
        }
    }

    private static VersionedAssetType requireRuntimeType(VersionedAssetType assetType) {
        if (assetType == null || !assetType.isRuntimeConfiguration()) {
            throw validation("发布清单只允许正式运行配置资产");
        }
        return assetType;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw validation(label + "不能为空");
        }
        return value.trim();
    }

    private static ApiException validation(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record AssetKey(VersionedAssetType type, String identity) {
    }

    private record DraftEntry(
        VersionedAssetType type,
        String identity,
        ReleaseEntryState state,
        String versionId,
        String versionNo,
        String contentHash
    ) {
        private static DraftEntry active(AssetVersion version) {
            return new DraftEntry(
                version.assetType(),
                version.assetIdentity(),
                ReleaseEntryState.ACTIVE,
                version.versionId(),
                version.versionNo(),
                version.contentHash()
            );
        }

        private static DraftEntry disabled(VersionedAssetType type, String identity) {
            return new DraftEntry(type, identity, ReleaseEntryState.DISABLED, null, null, null);
        }

        private static DraftEntry from(PlatformBaselineItem item) {
            return new DraftEntry(
                item.assetType(),
                item.assetIdentity(),
                item.entryState(),
                item.versionId(),
                item.versionNo(),
                item.contentHash()
            );
        }

        private String canonicalLine() {
            return String.join(
                "\u001f",
                type.name(),
                identity,
                state.name(),
                nullToEmpty(versionId),
                nullToEmpty(versionNo),
                nullToEmpty(contentHash)
            );
        }

        private PlatformBaselineItem toItem(
                String releaseId,
                Instant now,
                String actor,
                String traceId) {
            return new PlatformBaselineItem(
                null,
                releaseId,
                PlatformTenant.ID,
                type,
                identity,
                state,
                versionId,
                versionNo,
                contentHash,
                now,
                actor,
                blankToNull(traceId)
            );
        }

        private static String nullToEmpty(String value) {
            return value == null ? "" : value;
        }
    }
}
