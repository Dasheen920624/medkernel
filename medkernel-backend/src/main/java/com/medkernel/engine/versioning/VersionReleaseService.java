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
        return savePlan(command, version, null, VersionReleaseStatus.IN_REVIEW,
            VersionReleaseScopeType.ALL, null, "提交评审：" + required(command.impactDigest(), "影响摘要"), now);
    }

    @Override
    @Transactional
    public VersionReleasePlan rejectReview(VersionReleaseCommand command) {
        requireReleasePermission(command.tenantId());
        AssetVersion version = requireVersion(command);
        requireStatus(version, AssetVersionStatus.DRAFT, "只有草稿版本可以登记驳回结论");
        Instant now = clock.instant();
        String evidence = "REJECTED 评审拒绝："
            + required(command.reviewConclusion(), "审核结论")
            + "；" + required(command.impactDigest(), "影响摘要");
        return savePlan(command, version, null, VersionReleaseStatus.REJECTED,
            VersionReleaseScopeType.ALL, null, evidence, now);
    }

    @Override
    @Transactional
    public VersionReleasePlan approveReview(VersionReleaseCommand command) {
        requireReleasePermission(command.tenantId());
        AssetVersion version = requireVersion(command);
        requireStatus(version, AssetVersionStatus.DRAFT, "只有草稿版本可以登记批准结论");
        Instant now = clock.instant();
        String evidence = "APPROVED 评审通过："
            + required(command.reviewConclusion(), "审核结论")
            + "；" + required(command.impactDigest(), "影响摘要");
        return savePlan(command, version, null, VersionReleaseStatus.APPROVED,
            VersionReleaseScopeType.ALL, null, evidence, now);
    }

    @Override
    @Transactional
    public VersionReleasePlan releaseGray(VersionReleaseCommand command) {
        requireReleasePermission(command.tenantId());
        AssetVersion version = requireVersion(command);
        if (version.status() != AssetVersionStatus.DRAFT && version.status() != AssetVersionStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.CONFLICT, "只有草稿或已发布版本可以进入灰度计划");
        }
        requireGrayRolloutPolicy(command.rolloutPolicy());
        Instant now = clock.instant();
        ReleaseScope scope = normalizeGrayScope(command);
        String activeScopeKey = activeScopeKey(command);
        String fromVersionId = assetVersions.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
                command.tenantId(),
                command.assetType(),
                activeScopeKey,
                AssetVersionStatus.PUBLISHED
            ).stream()
            .filter(active -> !active.versionId().equals(version.versionId()))
            .map(AssetVersion::versionId)
            .findFirst()
            .orElse(null);
        String evidence = "GRAY 灰度发布：" + required(command.impactDigest(), "影响摘要");
        return savePlan(command, version, fromVersionId, VersionReleaseStatus.GRAY,
            scope.scopeType(), scope.scopeValue(), evidence, now);
    }

    @Override
    @Transactional
    public VersionReleasePlan publish(VersionReleaseCommand command) {
        requireReleasePermission(command.tenantId());
        AssetVersion target = requireVersion(command);
        if (target.status() != AssetVersionStatus.DRAFT && target.status() != AssetVersionStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.CONFLICT, "只有草稿版本可以发布；已发布版本仅用于幂等确认");
        }
        requirePublishGovernance(command, target);
        assertDependenciesResolvable(target);
        Instant now = clock.instant();
        String activeScopeKey = activeScopeKey(command);
        List<AssetVersion> publishedVersions = assetVersions.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
            command.tenantId(), command.assetType(), activeScopeKey, AssetVersionStatus.PUBLISHED
        );
        Optional<VersionActivationTransaction> existingActivation = findExistingActivation(
            command.tenantId(),
            command.assetType(),
            command.assetIdentity(),
            target.versionId(),
            VersionActivationAction.PUBLISH,
            activeScopeKey
        );
        if (target.status() == AssetVersionStatus.PUBLISHED
                && publishedVersions.stream().anyMatch(active -> active.versionId().equals(target.versionId()))
                && existingActivation.isPresent()) {
            VersionActivationTransaction transaction = existingActivation.get();
            return existingPlan(command, target.versionId(), VersionReleaseStatus.PUBLISHED)
                .orElseGet(() -> savePlan(command, target, transaction.fromVersionId(), VersionReleaseStatus.PUBLISHED,
                    VersionReleaseScopeType.ALL, null, transaction.evidenceSummary(), now));
        }
        for (AssetVersion published : publishedVersions) {
            if (!published.versionId().equals(target.versionId())) {
                assetVersions.save(published.withStatusAndWindow(
                    AssetVersionStatus.WITHDRAWN,
                    inactiveScopeKey(published),
                    published.effectiveFrom(),
                    now,
                    now,
                    required(command.actor(), "操作人")
                ));
            }
        }
        AssetVersion published = target.withStatusAndWindow(
            AssetVersionStatus.PUBLISHED,
            activeScopeKey,
            now,
            null,
            now,
            required(command.actor(), "操作人")
        );
        assetVersions.save(published);

        String fromVersionId = publishedVersions.stream()
            .filter(active -> !active.versionId().equals(target.versionId()))
            .map(AssetVersion::versionId)
            .findFirst()
            .orElse(null);
        String evidence = "PUBLISHED 发布：" + required(command.impactDigest(), "影响摘要");
        activationTransactions.save(newTransaction(
            command, fromVersionId, target.versionId(), VersionActivationAction.PUBLISH,
            activeScopeKey, evidence, now
        ));
        return savePlan(command, target, fromVersionId, VersionReleaseStatus.PUBLISHED,
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
        if (current.status() == AssetVersionStatus.WITHDRAWN
                && target.status() == AssetVersionStatus.PUBLISHED
                && existingActivation.isPresent()) {
            VersionActivationTransaction transaction = existingActivation.get();
            String actor = required(command.actor(), "操作人");
            Instant now = clock.instant();
            return existingPlan(command, target, VersionReleaseStatus.ROLLED_BACK)
                .orElseGet(() -> saveRollbackPlan(
                    command, target, current.versionId(), transaction.evidenceSummary(), now, actor));
        }
        requireStatus(current, AssetVersionStatus.PUBLISHED, "当前版本必须是 PUBLISHED 才能回滚");
        if (target.status() == AssetVersionStatus.WITHDRAWN
                && target.safetyPolicy() == AssetVersionSafetyPolicy.SAFETY_REDLINE) {
            throw new ApiException(
                ErrorCode.ROLLBACK_SAFETY_DENIED,
                "ROLLBACK_SAFETY_DENIED：已撤回的高风险版本禁止一键回滚"
            );
        }
        requireStatus(target, AssetVersionStatus.WITHDRAWN, "回滚目标必须是已撤回历史版本");

        Instant now = clock.instant();
        String actor = required(command.actor(), "操作人");
        assetVersions.save(current.withStatusAndWindow(
            AssetVersionStatus.WITHDRAWN,
            inactiveScopeKey(current),
            current.effectiveFrom(),
            now,
            now,
            actor
        ));
        assetVersions.save(target.withStatusAndWindow(
            AssetVersionStatus.PUBLISHED,
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
            VersionReleaseStatus.ROLLED_BACK,
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

    private void requirePublishGovernance(VersionReleaseCommand command, AssetVersion version) {
        boolean platformPublish = PlatformAuthority.PLATFORM_TENANT_ID.equals(command.tenantId());
        if (platformPublish) {
            VersionPublishQualityGate qualityGate = command.qualityGate();
            if (qualityGate == null || !qualityGate.passed()) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "平台发布质量校验未全部通过");
            }
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
        if (!Boolean.TRUE.equals(command.confirmedOperation())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "执行回滚前必须核对版本与影响");
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
            return new ReleaseScope(
                VersionReleaseScopeType.FACILITY,
                required(command.targetOrgPath(), "目标组织路径")
            );
        }
        return new ReleaseScope(command.scopeType(), required(command.scopeValue(), "灰度范围"));
    }

    private void requireGrayRolloutPolicy(RolloutPolicy policy) {
        if (policy == null || policy.strategy() == null || policy.strategy() == RolloutStrategy.ALL) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "灰度发布必须选择非全量放量策略");
        }
        switch (policy.strategy()) {
            case ORG_LIST -> {
                if (policy.orgUnitIds().isEmpty()) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED, "机构清单灰度至少选择一个组织");
                }
            }
            case ORG_SUBTREE -> {
                if (policy.orgUnitIds().size() != 1) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED, "组织子树灰度必须且只能选择一个根组织");
                }
            }
            case CANARY_BED_PERCENT -> {
                if (policy.bedPercent() == null || policy.bedPercent() < 1 || policy.bedPercent() > 99) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED, "床位比例灰度必须在 1 到 99 之间");
                }
            }
            case STAGED -> requireStagedPolicy(policy);
            case ALL -> throw new ApiException(ErrorCode.VALIDATION_FAILED, "灰度发布不能使用全量策略");
        }
        requireThresholdRates(policy.thresholds());
    }

    private void requireStagedPolicy(RolloutPolicy policy) {
        List<Integer> stages = policy.stages();
        if (stages.size() < 2 || stages.get(stages.size() - 1) != 100) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "分批放量至少需要两批且最后一批必须为 100%");
        }
        int previous = 0;
        for (Integer stage : stages) {
            if (stage == null || stage <= previous || stage > 100) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "分批放量比例必须在 1 到 100 之间严格递增");
            }
            previous = stage;
        }
        if (policy.observationMinutes() == null || policy.observationMinutes() < 1) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "分批放量必须配置正数观察窗");
        }
        if (policy.thresholds() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "分批放量必须配置自动暂停阈值");
        }
    }

    private void requireThresholdRates(RolloutThresholds thresholds) {
        if (thresholds == null) {
            return;
        }
        Double[] rates = {
            thresholds.maxHitRate(),
            thresholds.maxBlockRate(),
            thresholds.maxManualRejectionRate(),
            thresholds.maxAnomalyRate()
        };
        for (Double rate : rates) {
            if (rate != null && (rate < 0 || rate > 1)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "灰度暂停阈值必须在 0 到 1 之间");
            }
        }
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
        VersionPublishQualityGate qualityGate = command.qualityGate();
        RolloutPolicy rolloutPolicy = status == VersionReleaseStatus.GRAY
            ? command.rolloutPolicy()
            : RolloutPolicy.all();
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
            rolloutPolicy.strategy(),
            status == VersionReleaseStatus.GRAY ? RolloutPolicyJson.encode(rolloutPolicy) : null,
            0,
            null,
            status,
            command.impactDigest(),
            command.reviewConclusion(),
            evidence,
            qualityGate == null ? null : qualityGate.summaryOrDefault(),
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
