package com.medkernel.engine.context;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.org.OrgFacilityType;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.release.ClinicalRuntimeReleaseItemOfflineSnapshot;
import com.medkernel.engine.release.PlatformBaselineItem;
import com.medkernel.engine.release.PlatformBaselineItemRepository;
import com.medkernel.engine.release.PlatformBaselineRelease;
import com.medkernel.engine.release.PlatformBaselineReleaseRepository;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.release.ReleaseManifestHash;
import com.medkernel.engine.release.ReleaseSourceLayer;
import com.medkernel.engine.versioning.AssetDependency;
import com.medkernel.engine.versioning.AssetDependencyRepository;
import com.medkernel.engine.versioning.AssetDependencyService;
import com.medkernel.engine.versioning.AssetIdentity;
import com.medkernel.engine.versioning.AssetIdentityRepository;
import com.medkernel.engine.versioning.AssetIdentityStatus;
import com.medkernel.engine.versioning.AssetOwnershipScope;
import com.medkernel.engine.versioning.AssetPublicationStatusSynchronizer;
import com.medkernel.engine.versioning.AssetScopeResolver;
import com.medkernel.engine.versioning.AssetTechnicalValidationService;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionNumbers;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.ids.Ulid;

/**
 * 医院不可变机构生效版本服务。
 *
 * <p>机构可选择任意领域、任意类型、任意数量的正式资产；服务端展开必需依赖并物化完整清单。
 */
@Service
public class ClinicalRuntimeReleaseService {

    private final PlatformBaselineReleaseRepository baselines;
    private final PlatformBaselineItemRepository baselineItems;
    private final ClinicalRuntimeReleaseRepository releases;
    private final ClinicalRuntimeReleaseItemRepository runtimeItems;
    private final OrgUnitRepository organizations;
    private final AssetIdentityRepository identities;
    private final AssetVersionRepository versions;
    private final AssetTechnicalValidationService validation;
    private final AssetDependencyRepository dependencies;
    private final AssetScopeResolver assetScopes;
    private final List<AssetPublicationStatusSynchronizer> publicationSynchronizers;
    private final Clock clock;

    @Autowired
    public ClinicalRuntimeReleaseService(
            PlatformBaselineReleaseRepository baselines,
            PlatformBaselineItemRepository baselineItems,
            ClinicalRuntimeReleaseRepository releases,
            ClinicalRuntimeReleaseItemRepository runtimeItems,
            OrgUnitRepository organizations,
            AssetIdentityRepository identities,
            AssetVersionRepository versions,
            AssetTechnicalValidationService validation,
            AssetDependencyRepository dependencies,
            AssetScopeResolver assetScopes,
            List<AssetPublicationStatusSynchronizer> publicationSynchronizers) {
        this(
            baselines, baselineItems, releases, runtimeItems, organizations,
            identities, versions, validation, dependencies, assetScopes,
            publicationSynchronizers, Clock.systemUTC()
        );
    }

    ClinicalRuntimeReleaseService(
            PlatformBaselineReleaseRepository baselines,
            PlatformBaselineItemRepository baselineItems,
            ClinicalRuntimeReleaseRepository releases,
            ClinicalRuntimeReleaseItemRepository runtimeItems,
            OrgUnitRepository organizations,
            AssetIdentityRepository identities,
            AssetVersionRepository versions,
            AssetTechnicalValidationService validation,
            AssetDependencyRepository dependencies,
            AssetScopeResolver assetScopes,
            List<AssetPublicationStatusSynchronizer> publicationSynchronizers,
            Clock clock) {
        this.baselines = baselines;
        this.baselineItems = baselineItems;
        this.releases = releases;
        this.runtimeItems = runtimeItems;
        this.organizations = organizations;
        this.identities = identities;
        this.versions = versions;
        this.validation = validation;
        this.dependencies = dependencies;
        this.assetScopes = assetScopes;
        this.publicationSynchronizers = publicationSynchronizers == null
            ? List.of()
            : List.copyOf(publicationSynchronizers);
        this.clock = clock;
    }

    /**
     * 原子生成一个新的机构生效版本。
     */
    @Transactional
    public ClinicalRuntimeRelease activate(ClinicalRuntimeReleaseCommand command) {
        if (command == null) {
            throw validation("机构生效版本命令不能为空");
        }
        String tenantId = required(command.tenantId(), "租户");
        String hospitalId = required(command.hospitalId(), "医院");
        String actor = required(command.actor(), "操作人");
        OrgUnit hospital = requireHospital(tenantId, hospitalId);
        PlatformBaselineRelease baseline = baselines
            .findByBaselineReleaseId(required(
                command.platformBaselineReleaseId(), "平台标准版本发布"))
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "平台标准版本不存在"));
        ClinicalRuntimeRelease current = releases
            .findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(tenantId, hospitalId)
            .orElse(null);
        assertExpectedCurrent(current, command.expectedCurrentReleaseId());
        assertBaselineDoesNotDowngrade(current, baseline);
        if (command.activeAssets().isEmpty()) {
            throw validation("机构生效版本启用资产不能为空");
        }

        Map<AssetKey, RuntimeEntry> platform = loadPlatformBaseline(baseline);
        Map<AssetKey, RuntimeEntry> manifest = disabledCopy(platform);
        Map<AssetKey, List<AssetVersion>> localCandidates =
            loadLocalCandidates(tenantId, hospital, current, manifest);
        Map<AssetKey, AssetVersion> draftsToPublish = new LinkedHashMap<>();
        Map<AssetKey, RuntimeEntry> requested = resolveRequested(
            command.activeAssets(),
            tenantId,
            hospital,
            platform,
            actor,
            command.traceId(),
            draftsToPublish);
        manifest.putAll(requested);
        closeRequiredDependencies(manifest, platform, localCandidates);

        Instant now = clock.instant();
        for (AssetVersion draft : draftsToPublish.values()) {
            AssetVersion published = versions.save(draft.withStatusAndWindow(
                AssetVersionStatus.PUBLISHED,
                "version:" + draft.versionId(),
                now,
                null,
                now,
                actor
            ));
            notifyPublished(published, now, actor, command.traceId());
        }
        List<RuntimeEntry> ordered = manifest.values().stream()
            .sorted(Comparator
                .comparing((RuntimeEntry entry) -> entry.type().name())
                .thenComparing(RuntimeEntry::identity))
            .toList();
        String manifestHash = ReleaseManifestHash.sha256(
            ordered.stream().map(RuntimeEntry::canonicalLine).toList());
        long revision = current == null ? 1L : current.revisionNo() + 1L;
        String releaseId = "runtime-" + Ulid.newUlid();
        ClinicalRuntimeRelease release = releases.save(new ClinicalRuntimeRelease(
            null,
            releaseId,
            tenantId,
            hospitalId,
            revision,
            baseline.baselineReleaseId(),
            manifestHash,
            null,
            now,
            actor,
            now,
            actor,
            blankToNull(command.traceId())
        ));
        for (RuntimeEntry entry : ordered) {
            runtimeItems.save(entry.toItem(releaseId, now, actor, command.traceId()));
        }
        return release;
    }

    /**
     * 回滚不会修改旧记录，而是把目标历史清单复制为新的当前修订。
     */
    @Transactional
    public ClinicalRuntimeRelease rollback(
            String tenantId,
            String hospitalId,
            String targetReleaseId,
            String actor,
            String traceId) {
        String normalizedTenant = required(tenantId, "租户");
        String normalizedHospital = required(hospitalId, "医院");
        String normalizedActor = required(actor, "操作人");
        requireHospital(normalizedTenant, normalizedHospital);
        ClinicalRuntimeRelease target = releases
            .findByTenantIdAndReleaseId(
                normalizedTenant, required(targetReleaseId, "目标机构生效版本"))
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "目标机构生效版本不存在"));
        if (!normalizedHospital.equals(target.hospitalId())) {
            throw new ApiException(ErrorCode.CONFLICT, "不能回滚到其他医院的机构生效版本");
        }
        long nextRevision = releases
            .findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(
                normalizedTenant, normalizedHospital)
            .map(ClinicalRuntimeRelease::revisionNo)
            .orElse(0L) + 1L;
        Instant now = clock.instant();
        String newReleaseId = "runtime-" + Ulid.newUlid();
        ClinicalRuntimeRelease release = releases.save(new ClinicalRuntimeRelease(
            null,
            newReleaseId,
            normalizedTenant,
            normalizedHospital,
            nextRevision,
            target.platformBaselineReleaseId(),
            target.manifestSha256(),
            target.releaseId(),
            now,
            normalizedActor,
            now,
            normalizedActor,
            blankToNull(traceId)
        ));
        for (ClinicalRuntimeReleaseItem item :
                runtimeItems.findByReleaseIdOrderByAssetTypeAscAssetIdentityAsc(
                    target.releaseId())) {
            runtimeItems.save(new ClinicalRuntimeReleaseItem(
                null,
                newReleaseId,
                item.sourceTenantId(),
                item.sourceLayer(),
                item.assetType(),
                item.assetIdentity(),
                item.entryState(),
                item.versionId(),
                item.versionNo(),
                item.contentHash(),
                now,
                normalizedActor,
                blankToNull(traceId)
            ));
        }
        return release;
    }

    /**
     * 将已验签的离线交付快照恢复为新的不可变机构生效版本。
     */
    @Transactional
    public ClinicalRuntimeRelease restoreOfflineSnapshot(
            ClinicalRuntimeReleaseOfflineRestoreCommand command) {
        if (command == null) {
            throw validation("离线恢复命令不能为空");
        }
        String tenantId = required(command.tenantId(), "租户");
        String hospitalId = required(command.hospitalId(), "医院");
        String actor = required(command.actor(), "操作人");
        String sourceReleaseId = required(command.sourceReleaseId(), "来源机构生效版本");
        String platformBaselineReleaseId = required(
            command.platformBaselineReleaseId(), "平台标准版本发布");
        String manifestSha256 = required(command.manifestSha256(), "离线清单摘要");
        List<ClinicalRuntimeReleaseItemOfflineSnapshot> snapshotItems =
            required(command.items(), "离线物化资产清单");
        if (snapshotItems.isEmpty()) {
            throw validation("离线物化资产清单不能为空");
        }
        requireHospital(tenantId, hospitalId);
        ClinicalRuntimeRelease source = releases
            .findByTenantIdAndReleaseId(tenantId, sourceReleaseId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "来源机构生效版本不存在"));
        assertSourceMatchesOfflineSnapshot(
            source, tenantId, hospitalId, platformBaselineReleaseId, manifestSha256);
        ClinicalRuntimeRelease current = releases
            .findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(tenantId, hospitalId)
            .orElse(null);
        assertExpectedCurrent(current, command.expectedCurrentReleaseId());
        long nextRevision = current == null ? 1L : current.revisionNo() + 1L;
        Instant now = clock.instant();
        String newReleaseId = "runtime-" + Ulid.newUlid();
        ClinicalRuntimeRelease release = releases.save(new ClinicalRuntimeRelease(
            null,
            newReleaseId,
            tenantId,
            hospitalId,
            nextRevision,
            platformBaselineReleaseId,
            manifestSha256,
            sourceReleaseId,
            now,
            actor,
            now,
            actor,
            blankToNull(command.traceId())
        ));
        for (ClinicalRuntimeReleaseItemOfflineSnapshot item : snapshotItems.stream()
                .sorted(Comparator
                    .comparing((ClinicalRuntimeReleaseItemOfflineSnapshot item) -> item.assetType().name())
                    .thenComparing(ClinicalRuntimeReleaseItemOfflineSnapshot::assetIdentity))
                .toList()) {
            runtimeItems.save(new ClinicalRuntimeReleaseItem(
                null,
                newReleaseId,
                required(item.sourceTenantId(), "来源租户"),
                required(item.sourceLayer(), "来源层级"),
                required(item.assetType(), "资产类型"),
                required(item.assetIdentity(), "资产身份"),
                required(item.entryState(), "条目状态"),
                blankToNull(item.versionId()),
                blankToNull(item.versionNo()),
                blankToNull(item.contentHash()),
                now,
                actor,
                blankToNull(command.traceId())
            ));
        }
        return release;
    }

    private void assertSourceMatchesOfflineSnapshot(
            ClinicalRuntimeRelease source,
            String tenantId,
            String hospitalId,
            String platformBaselineReleaseId,
            String manifestSha256) {
        if (!source.tenantId().equals(tenantId)
                || !source.hospitalId().equals(hospitalId)
                || !source.platformBaselineReleaseId().equals(platformBaselineReleaseId)
                || !source.manifestSha256().equals(manifestSha256)) {
            throw new ApiException(ErrorCode.CONFLICT, "来源机构生效版本不一致，不能恢复离线交付文件");
        }
    }

    private Map<AssetKey, RuntimeEntry> loadPlatformBaseline(
            PlatformBaselineRelease baseline) {
        Map<AssetKey, RuntimeEntry> result = new LinkedHashMap<>();
        for (PlatformBaselineItem item :
                baselineItems.findByBaselineReleaseIdOrderByAssetTypeAscAssetIdentityAsc(
                    baseline.baselineReleaseId())) {
            AssetKey key = new AssetKey(item.assetType(), item.assetIdentity());
            result.put(key, RuntimeEntry.fromPlatform(item));
        }
        return result;
    }

    private Map<AssetKey, RuntimeEntry> disabledCopy(
            Map<AssetKey, RuntimeEntry> available) {
        Map<AssetKey, RuntimeEntry> result = new LinkedHashMap<>();
        available.forEach((key, entry) -> result.put(key, entry.disabled()));
        return result;
    }

    private Map<AssetKey, List<AssetVersion>> loadLocalCandidates(
            String tenantId,
            OrgUnit hospital,
            ClinicalRuntimeRelease current,
            Map<AssetKey, RuntimeEntry> manifest) {
        Map<AssetKey, List<AssetVersion>> candidates = new LinkedHashMap<>();
        Map<AssetKey, ClinicalRuntimeReleaseItem> previous = new LinkedHashMap<>();
        if (current != null) {
            for (ClinicalRuntimeReleaseItem item :
                    runtimeItems.findByReleaseIdOrderByAssetTypeAscAssetIdentityAsc(
                        current.releaseId())) {
                previous.put(new AssetKey(item.assetType(), item.assetIdentity()), item);
            }
        }
        for (AssetIdentity identity :
                identities.findByTenantIdOrderByAssetTypeAscAssetIdentityAsc(tenantId)) {
            if (!identity.assetType().isRuntimeConfiguration()
                    || identity.status() == AssetIdentityStatus.RETIRED) {
                continue;
            }
            AssetKey key = new AssetKey(identity.assetType(), identity.assetIdentity());
            List<AssetVersion> applicable = versions
                .findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
                    tenantId, identity.assetType(), identity.assetIdentity(),
                    AssetVersionStatus.PUBLISHED)
                .stream()
                .filter(version -> appliesToHospital(version.organizationScope(), hospital.orgPath()))
                .sorted(Comparator
                    .comparingInt(this::sourcePriority)
                    .thenComparing(
                        Comparator.<AssetVersion>comparingLong(
                            version -> versionSequence(version.versionNo()))
                            .reversed()))
                .toList();
            if (!applicable.isEmpty()) {
                candidates.put(key, applicable);
                ReleaseSourceLayer sourceLayer = sourceLayer(applicable.get(0));
                manifest.putIfAbsent(
                    key,
                    RuntimeEntry.disabled(
                        tenantId,
                        sourceLayer,
                        identity.assetType(),
                        identity.assetIdentity())
                );
            }
        }
        previous.forEach((key, item) -> manifest.putIfAbsent(
            key,
            RuntimeEntry.disabled(
                item.sourceTenantId(), item.sourceLayer(), item.assetType(), item.assetIdentity())));
        return candidates;
    }

    private Map<AssetKey, RuntimeEntry> resolveRequested(
            List<ClinicalRuntimeAssetSelection> selections,
            String tenantId,
            OrgUnit hospital,
            Map<AssetKey, RuntimeEntry> platform,
            String actor,
            String traceId,
            Map<AssetKey, AssetVersion> draftsToPublish) {
        Map<AssetKey, RuntimeEntry> requested = new LinkedHashMap<>();
        for (ClinicalRuntimeAssetSelection selection : selections) {
            if (selection == null) {
                throw validation("启用资产选择不能为空");
            }
            VersionedAssetType type = required(selection.assetType(), "资产类型");
            if (!type.isRuntimeConfiguration()) {
                throw validation("只能启用正式运行配置资产");
            }
            AssetKey key = new AssetKey(
                type, required(selection.assetIdentity(), "资产身份"));
            if (requested.containsKey(key)) {
                throw validation("同一稳定资产身份不能重复选择: " + key.identity());
            }
            RuntimeEntry resolved;
            if (blankToNull(selection.versionId()) == null) {
                RuntimeEntry baseline = platform.get(key);
                if (baseline == null || baseline.state() != ReleaseEntryState.ACTIVE) {
                    throw new ApiException(
                        ErrorCode.CONFLICT, "平台标准版本未提供可启用资产: " + key.identity());
                }
                resolved = baseline;
            } else {
                resolved = resolveLocalSelection(
                    selection,
                    tenantId,
                    hospital,
                    actor,
                    traceId,
                    draftsToPublish);
            }
            requested.put(key, resolved);
        }
        return requested;
    }

    private RuntimeEntry resolveLocalSelection(
            ClinicalRuntimeAssetSelection selection,
            String tenantId,
            OrgUnit hospital,
            String actor,
            String traceId,
            Map<AssetKey, AssetVersion> draftsToPublish) {
        AssetIdentity identity = identities
            .findByTenantIdAndAssetTypeAndAssetIdentity(
                tenantId, selection.assetType(), selection.assetIdentity())
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "本地资产身份不存在"));
        if (identity.status() != AssetIdentityStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONFLICT, "本地资产身份已退役");
        }
        AssetVersion version = versions
            .findByVersionIdAndTenantId(
                required(selection.versionId(), "本地资产版本"), tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "本地资产版本不存在"));
        if (version.assetType() != selection.assetType()
                || !version.assetIdentity().equals(selection.assetIdentity())) {
            throw new ApiException(ErrorCode.CONFLICT, "只能启用身份匹配的本地资产版本");
        }
        if (version.status() == AssetVersionStatus.DRAFT) {
            validation.validateForPublish(version, actor, traceId);
            draftsToPublish.put(
                new AssetKey(version.assetType(), version.assetIdentity()),
                version);
        } else if (version.status() != AssetVersionStatus.PUBLISHED) {
            throw new ApiException(
                ErrorCode.CONFLICT, "只能启用草稿或已发布的本地资产版本");
        }
        if (!appliesToHospital(version.organizationScope(), hospital.orgPath())) {
            throw new ApiException(ErrorCode.CONFLICT, "本地资产版本不适用于当前医院");
        }
        AssetOwnershipScope ownership = assetScopes.resolveOrganizationPath(
            tenantId, version.organizationScope());
        if (ownership.sourceLayer() != ReleaseSourceLayer.GROUP
                && ownership.sourceLayer() != ReleaseSourceLayer.HOSPITAL) {
            throw new ApiException(ErrorCode.CONFLICT, "本地资产版本来源层级非法");
        }
        return RuntimeEntry.active(
            tenantId, ownership.sourceLayer(), version);
    }

    private void closeRequiredDependencies(
            Map<AssetKey, RuntimeEntry> manifest,
            Map<AssetKey, RuntimeEntry> platform,
            Map<AssetKey, List<AssetVersion>> localCandidates) {
        Deque<RuntimeEntry> queue = new ArrayDeque<>(
            manifest.values().stream()
                .filter(entry -> entry.state() == ReleaseEntryState.ACTIVE)
                .toList());
        Map<String, Boolean> checked = new LinkedHashMap<>();
        while (!queue.isEmpty()) {
            RuntimeEntry owner = queue.removeFirst();
            String ownerKey = owner.sourceTenantId() + "|" + owner.versionId();
            if (checked.putIfAbsent(ownerKey, Boolean.TRUE) != null) {
                continue;
            }
            List<AssetDependency> edges =
                dependencies
                    .findByTenantIdAndAssetTypeAndAssetIdentityAndVersionIdOrderByDependsOnAssetTypeAscDependsOnIdentityAsc(
                        owner.sourceTenantId(),
                        owner.type(),
                        owner.identity(),
                        owner.versionId());
            for (AssetDependency edge : edges) {
                AssetKey dependencyKey =
                    new AssetKey(edge.dependsOnAssetType(), edge.dependsOnIdentity());
                RuntimeEntry target = manifest.get(dependencyKey);
                if (target != null && target.state() == ReleaseEntryState.ACTIVE) {
                    assertCompatible(owner, target, edge);
                    queue.addLast(target);
                    continue;
                }
                RuntimeEntry resolved = compatibleLocal(
                    localCandidates.get(dependencyKey), edge)
                    .orElseGet(() -> compatiblePlatform(
                        platform.get(dependencyKey), edge).orElse(null));
                if (resolved == null) {
                    throw new ApiException(
                        ErrorCode.CONFLICT,
                        "运行资产依赖不闭合：" + owner.identity()
                            + " 依赖 " + edge.dependsOnIdentity());
                }
                manifest.put(dependencyKey, resolved);
                queue.addLast(resolved);
            }
        }
    }

    private Optional<RuntimeEntry> compatiblePlatform(
            RuntimeEntry candidate,
            AssetDependency edge) {
        if (candidate == null || candidate.state() != ReleaseEntryState.ACTIVE
                || !AssetDependencyService.isCompatible(candidate.versionNo(), edge)) {
            return Optional.empty();
        }
        return Optional.of(candidate);
    }

    private Optional<RuntimeEntry> compatibleLocal(
            List<AssetVersion> candidates,
            AssetDependency edge) {
        if (candidates == null) {
            return Optional.empty();
        }
        return candidates.stream()
            .filter(version -> AssetDependencyService.isCompatible(version.versionNo(), edge))
            .findFirst()
            .map(version -> RuntimeEntry.active(
                version.tenantId(),
                sourceLayer(version),
                version));
    }

    private ReleaseSourceLayer sourceLayer(AssetVersion version) {
        return assetScopes.resolveOrganizationPath(
            version.tenantId(), version.organizationScope()).sourceLayer();
    }

    private int sourcePriority(AssetVersion version) {
        return switch (sourceLayer(version)) {
            case HOSPITAL -> 0;
            case GROUP -> 1;
            case PLATFORM -> 2;
        };
    }

    private void assertCompatible(
            RuntimeEntry owner,
            RuntimeEntry target,
            AssetDependency edge) {
        if (!AssetDependencyService.isCompatible(target.versionNo(), edge)) {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "运行资产依赖版本不兼容：" + owner.identity()
                    + " 依赖 " + target.identity() + "@" + target.versionNo());
        }
    }

    private void assertExpectedCurrent(
            ClinicalRuntimeRelease current,
            String expectedCurrentReleaseId) {
        String expected = blankToNull(expectedCurrentReleaseId);
        if (current == null && expected == null) {
            return;
        }
        if (current == null || expected == null || !current.releaseId().equals(expected)) {
            throw new ApiException(
                ErrorCode.CONFLICT, "当前机构生效版本已变化，请刷新后重新确认");
        }
    }

    private void assertBaselineDoesNotDowngrade(
            ClinicalRuntimeRelease current,
            PlatformBaselineRelease target) {
        if (current == null) {
            return;
        }
        PlatformBaselineRelease currentBaseline = baselines
            .findByBaselineReleaseId(current.platformBaselineReleaseId())
            .orElseThrow(() -> new ApiException(
                ErrorCode.INTERNAL_ERROR, "当前机构生效版本引用的平台标准版本不存在"));
        if (target.revisionNo() < currentBaseline.revisionNo()) {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "普通启用不能切换到旧平台标准版本，请使用机构生效版本回滚");
        }
    }

    private OrgUnit requireHospital(String tenantId, String hospitalId) {
        OrgUnit hospital = organizations.findByTenantIdAndId(tenantId, hospitalId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "医院不存在"));
        if (hospital.level() != OrgLevel.FACILITY
                || hospital.facilityType() != OrgFacilityType.HOSPITAL
                || !hospital.isActive()) {
            throw validation("机构生效版本目标必须是启用的医院");
        }
        return hospital;
    }

    private static boolean appliesToHospital(String organizationScope, String hospitalPath) {
        String scope = blankToNull(organizationScope);
        String path = blankToNull(hospitalPath);
        return scope != null && path != null
            && (scope.equals(path) || path.startsWith(scope + "/"));
    }

    private static long versionSequence(String versionNo) {
        return AssetVersionNumbers.sequence(versionNo, "资产版本号");
    }

    private static <T> T required(T value, String label) {
        if (value == null) {
            throw validation(label + "不能为空");
        }
        return value;
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

    private void notifyPublished(
            AssetVersion published,
            Instant publishedAt,
            String actor,
            String traceId) {
        for (AssetPublicationStatusSynchronizer synchronizer : publicationSynchronizers) {
            synchronizer.afterPublished(published, publishedAt, actor, traceId);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record AssetKey(VersionedAssetType type, String identity) {
    }

    private record RuntimeEntry(
        String sourceTenantId,
        ReleaseSourceLayer sourceLayer,
        VersionedAssetType type,
        String identity,
        ReleaseEntryState state,
        String versionId,
        String versionNo,
        String contentHash
    ) {
        private static RuntimeEntry fromPlatform(PlatformBaselineItem item) {
            return new RuntimeEntry(
                PlatformTenant.ID,
                ReleaseSourceLayer.PLATFORM,
                item.assetType(),
                item.assetIdentity(),
                item.entryState(),
                item.versionId(),
                item.versionNo(),
                item.contentHash()
            );
        }

        private static RuntimeEntry active(
                String tenantId,
                ReleaseSourceLayer sourceLayer,
                AssetVersion version) {
            return new RuntimeEntry(
                tenantId,
                sourceLayer,
                version.assetType(),
                version.assetIdentity(),
                ReleaseEntryState.ACTIVE,
                version.versionId(),
                version.versionNo(),
                version.contentHash()
            );
        }

        private static RuntimeEntry disabled(
                String tenantId,
                ReleaseSourceLayer sourceLayer,
                VersionedAssetType type,
                String identity) {
            return new RuntimeEntry(
                tenantId, sourceLayer, type, identity,
                ReleaseEntryState.DISABLED, null, null, null);
        }

        private RuntimeEntry disabled() {
            return disabled(sourceTenantId, sourceLayer, type, identity);
        }

        private String canonicalLine() {
            return String.join(
                "\u001f",
                sourceTenantId,
                sourceLayer.name(),
                type.name(),
                identity,
                state.name(),
                nullToEmpty(versionId),
                nullToEmpty(versionNo),
                nullToEmpty(contentHash)
            );
        }

        private ClinicalRuntimeReleaseItem toItem(
                String releaseId,
                Instant now,
                String actor,
                String traceId) {
            return new ClinicalRuntimeReleaseItem(
                null,
                releaseId,
                sourceTenantId,
                sourceLayer,
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
