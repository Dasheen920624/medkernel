package com.medkernel.engine.versioning;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.security.PermissionCode;
import com.medkernel.engine.security.PermissionEvaluator;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.ids.Ulid;

/**
 * SYS-04 通用版本发布流。
 */
@Service
public class VersionReleaseService implements ReleasePort {

    private final AssetVersionRepository assetVersions;
    private final VersionReleasePlanRepository releasePlans;
    private final VersionActivationTransactionRepository activationTransactions;
    private final PermissionEvaluator permissionEvaluator;
    private final AssetDependencyService assetDependencies;
    private final Clock clock;

    @Autowired
    public VersionReleaseService(
            AssetVersionRepository assetVersions,
            VersionReleasePlanRepository releasePlans,
            VersionActivationTransactionRepository activationTransactions,
            PermissionEvaluator permissionEvaluator,
            AssetDependencyService assetDependencies) {
        this(assetVersions, releasePlans, activationTransactions, permissionEvaluator, assetDependencies, Clock.systemUTC());
    }

    VersionReleaseService(
            AssetVersionRepository assetVersions,
            VersionReleasePlanRepository releasePlans,
            VersionActivationTransactionRepository activationTransactions,
            PermissionEvaluator permissionEvaluator,
            Clock clock) {
        this(assetVersions, releasePlans, activationTransactions, permissionEvaluator, null, clock);
    }

    VersionReleaseService(
            AssetVersionRepository assetVersions,
            VersionReleasePlanRepository releasePlans,
            VersionActivationTransactionRepository activationTransactions,
            PermissionEvaluator permissionEvaluator,
            AssetDependencyService assetDependencies,
            Clock clock) {
        this.assetVersions = assetVersions;
        this.releasePlans = releasePlans;
        this.activationTransactions = activationTransactions;
        this.permissionEvaluator = permissionEvaluator;
        this.assetDependencies = assetDependencies;
        this.clock = clock;
    }

    @Override
    @Transactional
    public VersionReleasePlan submitForReview(VersionReleaseCommand command) {
        requireReleasePermission(command.tenantId());
        AssetVersion version = requireVersion(command);
        requireStatus(version, AssetVersionStatus.DRAFT, "只有草稿版本可以提交审核");
        Instant now = clock.instant();
        assetVersions.save(version.withStatus(
            AssetVersionStatus.PENDING_REVIEW,
            inactiveScopeKey(version),
            now,
            required(command.actor(), "操作人")
        ));
        return savePlan(command, version, null, VersionReleaseStatus.PENDING_REVIEW,
            VersionReleaseScopeType.ALL, null, "PENDING_REVIEW 提交审核：" + required(command.impactDigest(), "影响摘要"), now);
    }

    @Override
    @Transactional
    public VersionReleasePlan rejectReview(VersionReleaseCommand command) {
        requireReleasePermission(command.tenantId());
        AssetVersion version = requireVersion(command);
        requireStatus(version, AssetVersionStatus.PENDING_REVIEW, "只有待审核版本可以驳回到草稿");
        Instant now = clock.instant();
        assetVersions.save(version.withStatus(
            AssetVersionStatus.DRAFT,
            inactiveScopeKey(version),
            now,
            required(command.actor(), "操作人")
        ));
        String evidence = "REVIEW_REJECTED 审核拒绝："
            + required(command.reviewConclusion(), "审核结论")
            + "；" + required(command.impactDigest(), "影响摘要");
        return savePlan(command, version, null, VersionReleaseStatus.REVIEW_REJECTED,
            VersionReleaseScopeType.ALL, null, evidence, now);
    }

    @Override
    @Transactional
    public VersionReleasePlan approveForSilentObservation(VersionReleaseCommand command) {
        requireReleasePermission(command.tenantId());
        AssetVersion version = requireVersion(command);
        requireStatus(version, AssetVersionStatus.PENDING_REVIEW, "只有待审核版本可以进入静默观察");
        assertDependenciesResolvable(version);
        Instant now = clock.instant();
        assetVersions.save(version.withStatus(
            AssetVersionStatus.PUBLISHED,
            inactiveScopeKey(version),
            now,
            required(command.actor(), "操作人")
        ));
        String evidence = "SILENT_OBSERVATION 静默观察："
            + required(command.reviewConclusion(), "审核结论")
            + "；" + required(command.impactDigest(), "影响摘要");
        return savePlan(command, version, null, VersionReleaseStatus.SILENT_OBSERVATION,
            VersionReleaseScopeType.ALL, null, evidence, now);
    }

    @Override
    @Transactional
    public VersionReleasePlan releaseGray(VersionReleaseCommand command) {
        requireReleasePermission(command.tenantId());
        AssetVersion version = requireVersion(command);
        if (version.status() != AssetVersionStatus.PUBLISHED && version.status() != AssetVersionStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONFLICT, "只有已发布版本可以进入灰度");
        }
        Instant now = clock.instant();
        ReleaseScope scope = normalizeGrayScope(command);
        String evidence = "GRAY 灰度发布：" + required(command.impactDigest(), "影响摘要");
        return savePlan(command, version, null, VersionReleaseStatus.GRAY,
            scope.scopeType(), scope.scopeValue(), evidence, now);
    }

    @Override
    @Transactional
    public VersionReleasePlan releaseFull(VersionReleaseCommand command) {
        requireReleasePermission(command.tenantId());
        AssetVersion target = requireVersion(command);
        if (target.status() != AssetVersionStatus.PUBLISHED && target.status() != AssetVersionStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONFLICT, "只有已发布版本可以全量激活");
        }
        assertDependenciesResolvable(target);
        Instant now = clock.instant();
        String activeScopeKey = activeScopeKey(command);
        List<AssetVersion> activeVersions = assetVersions.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
            command.tenantId(), command.assetType(), activeScopeKey, AssetVersionStatus.ACTIVE
        );
        Optional<VersionActivationTransaction> existingActivation = findExistingActivation(
            command.tenantId(),
            command.assetType(),
            command.assetIdentity(),
            target.versionId(),
            VersionActivationAction.FULL_ACTIVATE,
            activeScopeKey
        );
        if (target.status() == AssetVersionStatus.ACTIVE
                && activeVersions.stream().anyMatch(active -> active.versionId().equals(target.versionId()))
                && existingActivation.isPresent()) {
            VersionActivationTransaction transaction = existingActivation.get();
            return existingPlan(command, target.versionId(), VersionReleaseStatus.FULL)
                .orElseGet(() -> savePlan(command, target, transaction.fromVersionId(), VersionReleaseStatus.FULL,
                    VersionReleaseScopeType.ALL, null, transaction.evidenceSummary(), now));
        }
        for (AssetVersion active : activeVersions) {
            if (!active.versionId().equals(target.versionId())) {
                assetVersions.save(active.withStatusAndWindow(
                    AssetVersionStatus.OFFLINE,
                    inactiveScopeKey(active),
                    active.effectiveFrom(),
                    now,
                    now,
                    required(command.actor(), "操作人")
                ));
            }
        }
        AssetVersion activated = target.withStatusAndWindow(
            AssetVersionStatus.ACTIVE,
            activeScopeKey,
            now,
            null,
            now,
            required(command.actor(), "操作人")
        );
        assetVersions.save(activated);

        String fromVersionId = activeVersions.stream()
            .filter(active -> !active.versionId().equals(target.versionId()))
            .map(AssetVersion::versionId)
            .findFirst()
            .orElse(null);
        String evidence = "FULL 全量激活：" + required(command.impactDigest(), "影响摘要");
        activationTransactions.save(newTransaction(
            command, fromVersionId, target.versionId(), VersionActivationAction.FULL_ACTIVATE,
            activeScopeKey, evidence, now
        ));
        return savePlan(command, target, fromVersionId, VersionReleaseStatus.FULL,
            VersionReleaseScopeType.ALL, null, evidence, now);
    }

    @Override
    @Transactional
    public VersionReleasePlan rollback(VersionRollbackCommand command) {
        requireReleasePermission(command.tenantId());
        AssetVersion current = requireVersion(
            command.tenantId(), command.assetType(), command.assetIdentity(), command.currentVersionId());
        AssetVersion target = requireVersion(
            command.tenantId(), command.assetType(), command.assetIdentity(), command.targetVersionId());
        requireSameEffectiveDomain(current, target);
        requireRollbackConfirmation(command, current, target);
        String activeScopeKey = activeScopeKey(target);
        Optional<VersionActivationTransaction> existingActivation = findExistingActivation(
            command.tenantId(),
            command.assetType(),
            command.assetIdentity(),
            target.versionId(),
            VersionActivationAction.ROLLBACK,
            activeScopeKey
        );
        if (current.status() == AssetVersionStatus.OFFLINE
                && target.status() == AssetVersionStatus.ACTIVE
                && existingActivation.isPresent()) {
            VersionActivationTransaction transaction = existingActivation.get();
            String actor = required(command.actor(), "操作人");
            Instant now = clock.instant();
            return existingPlan(command, target, VersionReleaseStatus.ROLLBACKED)
                .orElseGet(() -> saveRollbackPlan(
                    command, target, current.versionId(), transaction.evidenceSummary(), now, actor));
        }
        requireStatus(current, AssetVersionStatus.ACTIVE, "当前版本必须是 ACTIVE 才能回滚");
        if (target.status() == AssetVersionStatus.WITHDRAWN
                && target.safetyPolicy() == AssetVersionSafetyPolicy.SAFETY_REDLINE) {
            throw new ApiException(
                ErrorCode.ROLLBACK_SAFETY_DENIED,
                "ROLLBACK_SAFETY_DENIED：被撤回的高风险版本禁止一键回滚"
            );
        }
        requireStatus(target, AssetVersionStatus.OFFLINE, "回滚目标必须是已下线历史版本");

        Instant now = clock.instant();
        String actor = required(command.actor(), "操作人");
        assetVersions.save(current.withStatusAndWindow(
            AssetVersionStatus.OFFLINE,
            inactiveScopeKey(current),
            current.effectiveFrom(),
            now,
            now,
            actor
        ));
        assetVersions.save(target.withStatusAndWindow(
            AssetVersionStatus.ACTIVE,
            activeScopeKey,
            now,
            null,
            now,
            actor
        ));
        String evidence = "ROLLBACK 回滚：回滚到 " + target.versionNo()
            + "；原因：" + required(command.reason(), "回滚原因");
        activationTransactions.save(new VersionActivationTransaction(
            null,
            "vat-" + Ulid.newUlid(),
            command.tenantId(),
            command.assetType(),
            command.assetIdentity(),
            current.versionId(),
            target.versionId(),
            VersionActivationAction.ROLLBACK,
            activeScopeKey,
            command.reason(),
            evidence,
            now,
            actor,
            now,
            actor,
            command.traceId()
        ));
        return saveRollbackPlan(command, target, current.versionId(), evidence, now, actor);
    }

    private VersionReleasePlan saveRollbackPlan(
            VersionRollbackCommand command,
            AssetVersion target,
            String fromVersionId,
            String evidence,
            Instant now,
            String actor) {
        return releasePlans.save(new VersionReleasePlan(
            null,
            "vrl-" + Ulid.newUlid(),
            command.tenantId(),
            command.assetType(),
            command.assetIdentity(),
            target.versionId(),
            fromVersionId,
            target.organizationScope(),
            target.applicableScope(),
            VersionReleaseScopeType.ALL,
            null,
            VersionReleaseStatus.ROLLBACKED,
            command.reason(),
            null,
            evidence,
            now,
            actor,
            now,
            actor,
            command.traceId()
        ));
    }

    private void assertDependenciesResolvable(AssetVersion version) {
        if (assetDependencies != null) {
            assetDependencies.assertDependenciesResolvable(version);
        }
    }

    private AssetVersion requireVersion(VersionReleaseCommand command) {
        AssetVersion version = requireVersion(
            command.tenantId(), command.assetType(), command.assetIdentity(), command.versionId());
        if (!Objects.equals(version.organizationScope(), required(command.targetOrgPath(), "目标组织路径"))
                || !Objects.equals(version.applicableScope(), required(command.applicableScope(), "适用范围"))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "发布命令与版本生效域不一致");
        }
        return version;
    }

    private AssetVersion requireVersion(
            String tenantId,
            VersionedAssetType assetType,
            String assetIdentity,
            String versionId) {
        required(tenantId, "租户");
        required(assetIdentity, "资产身份");
        required(versionId, "版本 ID");
        if (assetType == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "资产类型不能为空");
        }
        AssetVersion version = assetVersions.findByVersionIdAndTenantId(versionId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "资产版本不存在: " + versionId));
        if (version.assetType() != assetType || !Objects.equals(version.assetIdentity(), assetIdentity)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "版本与发布命令的资产域不一致");
        }
        return version;
    }

    private void requireRollbackConfirmation(
            VersionRollbackCommand command,
            AssetVersion current,
            AssetVersion target) {
        if (!Objects.equals(command.confirmedCurrentVersion(), current.versionNo())
                || !Objects.equals(command.confirmedTargetVersion(), target.versionNo())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "回滚必须确认当前版本与目标版本号");
        }
        if (!Boolean.TRUE.equals(command.confirmedHighRisk())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "回滚必须完成高风险二次确认");
        }
        required(command.reason(), "回滚原因");
    }

    private void requireSameEffectiveDomain(AssetVersion current, AssetVersion target) {
        if (current.assetType() != target.assetType()
                || !Objects.equals(current.assetIdentity(), target.assetIdentity())
                || !Objects.equals(current.organizationScope(), target.organizationScope())
                || !Objects.equals(current.applicableScope(), target.applicableScope())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "回滚版本必须属于同一资产生效域");
        }
    }

    private void requireStatus(AssetVersion version, AssetVersionStatus expected, String message) {
        if (version.status() != expected) {
            throw new ApiException(ErrorCode.CONFLICT, message);
        }
    }

    private void requireReleasePermission(String tenantId) {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope.hasTenant() && !tenantId.equals(scope.tenantId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "版本发布只能作用于当前请求租户");
        }
        PermissionCode requiredPermission = PlatformAuthority.PLATFORM_TENANT_ID.equals(required(tenantId, "租户"))
            ? PermissionCode.PLATFORM_PUBLISH
            : PermissionCode.TENANT_OVERRIDE;
        if (permissionEvaluator == null || !permissionEvaluator.has(requiredPermission)) {
            throw new ApiException(
                ErrorCode.FORBIDDEN,
                "缺少 " + requiredPermission.code() + "，不能发布或激活该资产版本"
            );
        }
    }

    private ReleaseScope normalizeGrayScope(VersionReleaseCommand command) {
        if (command.scopeType() == null || command.scopeType() == VersionReleaseScopeType.ALL) {
            return defaultCanaryBedPercentScope(command.targetOrgPath());
        }
        return new ReleaseScope(command.scopeType(), required(command.scopeValue(), "灰度范围"));
    }

    private ReleaseScope defaultCanaryBedPercentScope(String targetOrgPath) {
        return new ReleaseScope(
            VersionReleaseScopeType.FACILITY,
            "{\"rolloutStrategy\":\"" + RolloutStrategy.CANARY_BED_PERCENT
                + "\",\"percentage\":10,\"scopeCode\":\"" + required(targetOrgPath, "目标组织路径") + "\"}"
        );
    }

    private VersionReleasePlan savePlan(
            VersionReleaseCommand command,
            AssetVersion version,
            String fromVersionId,
            VersionReleaseStatus status,
            VersionReleaseScopeType scopeType,
            String scopeValue,
            String evidence,
            Instant now) {
        String actor = required(command.actor(), "操作人");
        return releasePlans.save(new VersionReleasePlan(
            null,
            "vrl-" + Ulid.newUlid(),
            command.tenantId(),
            command.assetType(),
            command.assetIdentity(),
            version.versionId(),
            fromVersionId,
            command.targetOrgPath(),
            command.applicableScope(),
            scopeType,
            scopeValue,
            status,
            command.impactDigest(),
            command.reviewConclusion(),
            evidence,
            now,
            actor,
            now,
            actor,
            command.traceId()
        ));
    }

    private VersionActivationTransaction newTransaction(
            VersionReleaseCommand command,
            String fromVersionId,
            String toVersionId,
            VersionActivationAction action,
            String activeScopeKey,
            String evidence,
            Instant now) {
        String actor = required(command.actor(), "操作人");
        return new VersionActivationTransaction(
            null,
            "vat-" + Ulid.newUlid(),
            command.tenantId(),
            command.assetType(),
            command.assetIdentity(),
            fromVersionId,
            toVersionId,
            action,
            activeScopeKey,
            command.impactDigest(),
            evidence,
            now,
            actor,
            now,
            actor,
            command.traceId()
        );
    }

    private Optional<VersionActivationTransaction> findExistingActivation(
            String tenantId,
            VersionedAssetType assetType,
            String assetIdentity,
            String toVersionId,
            VersionActivationAction action,
            String activeScopeKey) {
        return activationTransactions.findByTenantIdAndAssetTypeAndAssetIdentityAndToVersionIdAndActionAndActiveScopeKey(
            tenantId, assetType, assetIdentity, toVersionId, action, activeScopeKey);
    }

    private Optional<VersionReleasePlan> existingPlan(
            VersionReleaseCommand command,
            String versionId,
            VersionReleaseStatus status) {
        return releasePlans.findFirstByTenantIdAndAssetTypeAndAssetIdentityAndVersionIdAndStatusAndTargetOrgPathAndApplicableScopeOrderByCreatedAtDesc(
            command.tenantId(),
            command.assetType(),
            command.assetIdentity(),
            versionId,
            status,
            command.targetOrgPath(),
            command.applicableScope()
        );
    }

    private Optional<VersionReleasePlan> existingPlan(
            VersionRollbackCommand command,
            AssetVersion target,
            VersionReleaseStatus status) {
        return releasePlans.findFirstByTenantIdAndAssetTypeAndAssetIdentityAndVersionIdAndStatusAndTargetOrgPathAndApplicableScopeOrderByCreatedAtDesc(
            command.tenantId(),
            command.assetType(),
            command.assetIdentity(),
            target.versionId(),
            status,
            target.organizationScope(),
            target.applicableScope()
        );
    }

    private String activeScopeKey(VersionReleaseCommand command) {
        return required(command.assetIdentity(), "资产身份")
            + "|" + required(command.targetOrgPath(), "目标组织路径")
            + "|" + required(command.applicableScope(), "适用范围");
    }

    private String activeScopeKey(AssetVersion version) {
        return version.assetIdentity() + "|" + version.organizationScope() + "|" + version.applicableScope();
    }

    private String inactiveScopeKey(AssetVersion version) {
        return "version:" + version.versionId();
    }

    private String required(String value, String label) {
        if (isBlank(value)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "不能为空");
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ReleaseScope(VersionReleaseScopeType scopeType, String scopeValue) {}
}
