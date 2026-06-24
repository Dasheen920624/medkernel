package com.medkernel.engine.versioning;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ContextSnapshot;
import com.medkernel.engine.context.ContextSnapshotRepository;
import com.medkernel.engine.org.OrgHierarchyRepository;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 发布前只读影响评估编排。
 */
@Service
public class ReleaseSimulationService {

    private final AssetVersionRepository assetVersions;
    private final InheritanceOverrideRepository overrides;
    private final AssetDependencyService dependencies;
    private final List<SafetyMonotonicityCheck> safetyChecks;
    private final OrgUnitRepository orgUnits;
    private final OrgHierarchyRepository orgHierarchy;
    private final ContextSnapshotRepository snapshots;
    private final List<ReleaseSimulationReplayEvaluator> replayEvaluators;
    private final ObjectMapper json;
    private final Clock clock;

    @Autowired
    public ReleaseSimulationService(
            AssetVersionRepository assetVersions,
            InheritanceOverrideRepository overrides,
            AssetDependencyService dependencies,
            List<SafetyMonotonicityCheck> safetyChecks,
            OrgUnitRepository orgUnits,
            OrgHierarchyRepository orgHierarchy,
            ContextSnapshotRepository snapshots,
            List<ReleaseSimulationReplayEvaluator> replayEvaluators,
            ObjectMapper json) {
        this(
            assetVersions,
            overrides,
            dependencies,
            safetyChecks,
            orgUnits,
            orgHierarchy,
            snapshots,
            replayEvaluators,
            json,
            Clock.systemUTC()
        );
    }

    ReleaseSimulationService(
            AssetVersionRepository assetVersions,
            InheritanceOverrideRepository overrides,
            AssetDependencyService dependencies,
            List<SafetyMonotonicityCheck> safetyChecks,
            OrgUnitRepository orgUnits,
            OrgHierarchyRepository orgHierarchy,
            ContextSnapshotRepository snapshots,
            List<ReleaseSimulationReplayEvaluator> replayEvaluators,
            ObjectMapper json,
            Clock clock) {
        this.assetVersions = assetVersions;
        this.overrides = overrides;
        this.dependencies = dependencies;
        this.safetyChecks = safetyChecks == null ? List.of() : List.copyOf(safetyChecks);
        this.orgUnits = orgUnits;
        this.orgHierarchy = orgHierarchy;
        this.snapshots = snapshots;
        this.replayEvaluators = replayEvaluators == null ? List.of() : List.copyOf(replayEvaluators);
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReleaseSimulationResult simulate(ReleaseSimulationCommand command) {
        validate(command);
        AssetVersion candidate = assetVersions.findByVersionIdAndTenantId(
            command.candidateVersionId(),
            command.candidateTenantId()
        ).orElseThrow(() -> new ApiException(
            ErrorCode.NOT_FOUND,
            "候选资产版本不存在: " + command.candidateVersionId()
        ));
        requireCandidateMatches(command, candidate);

        AssetVersion current = findCurrent(command, candidate).orElse(null);
        List<ReleaseSimulationResult.AffectedOrganization> affected = affectedOrganizations(command);
        ReleaseSimulationResult.Check safety = safetyCheck(current, candidate);
        ReleaseSimulationResult.Check dependency = dependencyCheck(candidate);
        List<ReleaseSimulationResult.Conflict> conflicts = conflicts(command);
        List<ContextSnapshot> historicalSnapshots = historicalSnapshots(command, affected);
        ReleaseSimulationResult.Replay replay = replay(command, current, candidate, historicalSnapshots);
        ReleaseSimulationResult.Diff diff = diff(current, candidate);
        boolean releasable = safety.passed()
            && dependency.passed()
            && !"UNSUPPORTED".equals(replay.status());
        Instant generatedAt = clock.instant();
        String digest = digest(
            command,
            candidate,
            current,
            affected,
            diff,
            replay,
            safety,
            dependency,
            conflicts,
            releasable
        );
        return new ReleaseSimulationResult(
            digest,
            generatedAt,
            candidate.versionId(),
            current == null ? null : current.versionId(),
            affected,
            List.of(command.applicableScope().trim()),
            diff,
            replay,
            safety,
            dependency,
            conflicts,
            releasable
        );
    }

    private Optional<AssetVersion> findCurrent(
            ReleaseSimulationCommand command,
            AssetVersion candidate) {
        String activeScopeKey = InheritanceResolver.activeScopeKey(
            command.assetIdentity(),
            command.targetOrgPath(),
            command.applicableScope()
        );
        List<AssetVersion> local = assetVersions.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
            command.tenantId(),
            command.assetType(),
            activeScopeKey,
            AssetVersionStatus.PUBLISHED
        );
        if (!local.isEmpty()) {
            return Optional.of(local.get(0));
        }
        return assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
                candidate.tenantId(),
                candidate.assetType(),
                candidate.assetIdentity(),
                AssetVersionStatus.PUBLISHED
            ).stream()
            .filter(version -> Objects.equals(version.applicableScope(), command.applicableScope()))
            .findFirst();
    }

    private List<ReleaseSimulationResult.AffectedOrganization> affectedOrganizations(
            ReleaseSimulationCommand command) {
        RolloutPolicy policy = command.rolloutPolicy() == null
            ? RolloutPolicy.all()
            : command.rolloutPolicy();
        List<OrgUnit> resolved = switch (policy.strategy()) {
            case ALL -> orgUnits.findByTenantIdOrderByLevelAscCodeAsc(command.tenantId());
            case ORG_SUBTREE -> {
                String root = singleTarget(policy.orgUnitIds(), command.targetOrgUnitIds(), "组织子树根节点");
                yield orgHierarchy.findDescendantsAndSelf(command.tenantId(), root);
            }
            case ORG_LIST -> resolveUnits(
                command.tenantId(),
                policy.orgUnitIds().isEmpty() ? command.targetOrgUnitIds() : policy.orgUnitIds()
            );
            case CANARY_BED_PERCENT, STAGED ->
                resolveUnits(command.tenantId(), command.targetOrgUnitIds());
        };
        if (resolved.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "发布模拟未解析到任何目标组织");
        }
        return resolved.stream()
            .filter(OrgUnit::isActive)
            .map(unit -> new ReleaseSimulationResult.AffectedOrganization(
                unit.id(),
                unit.orgPath(),
                unit.name()
            ))
            .toList();
    }

    private List<OrgUnit> resolveUnits(String tenantId, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "发布模拟至少选择一个目标组织");
        }
        List<OrgUnit> resolved = new ArrayList<>();
        for (String id : ids.stream().filter(value -> value != null && !value.isBlank()).distinct().toList()) {
            OrgUnit unit = orgUnits.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "目标组织不存在: " + id));
            resolved.add(unit);
        }
        return resolved;
    }

    private String singleTarget(List<String> policyTargets, List<String> commandTargets, String label) {
        List<String> targets = policyTargets == null || policyTargets.isEmpty()
            ? commandTargets
            : policyTargets;
        if (targets == null || targets.size() != 1 || targets.get(0) == null || targets.get(0).isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "必须且只能有一个");
        }
        return targets.get(0);
    }

    private ReleaseSimulationResult.Check safetyCheck(
            AssetVersion current,
            AssetVersion candidate) {
        if (current == null
                || current.overridePolicy() != AssetVersionOverridePolicy.LOCKED
                || Objects.equals(current.contentHash(), candidate.contentHash())) {
            return new ReleaseSimulationResult.Check(true, List.of());
        }
        Optional<SafetyMonotonicityCheck> checker = safetyChecks.stream()
            .filter(check -> check.supports(candidate.assetType()))
            .findFirst();
        if (checker.isEmpty()) {
            return new ReleaseSimulationResult.Check(
                false,
                List.of("LOCKED 基线发生内容变化，但当前资产类型没有安全单调性判定器")
            );
        }
        if (!checker.get().isAtLeastAsStrict(current, candidate)) {
            return new ReleaseSimulationResult.Check(
                false,
                List.of("LOCKED 基线安全约束被候选版本放宽")
            );
        }
        return new ReleaseSimulationResult.Check(true, List.of());
    }

    private ReleaseSimulationResult.Check dependencyCheck(AssetVersion candidate) {
        try {
            dependencies.assertDependenciesResolvable(candidate);
            return new ReleaseSimulationResult.Check(true, List.of());
        } catch (ApiException exception) {
            return new ReleaseSimulationResult.Check(false, List.of(exception.getMessage()));
        }
    }

    private List<ReleaseSimulationResult.Conflict> conflicts(ReleaseSimulationCommand command) {
        return overrides.findByTenantIdAndAssetTypeAndAssetIdentityAndLifecycleStatus(
                command.tenantId(),
                command.assetType(),
                command.assetIdentity(),
                InheritanceOverrideStatus.ACTIVE
            ).stream()
            .filter(override -> Objects.equals(override.applicableScope(), command.applicableScope()))
            .filter(override -> isAtOrBelow(override.orgPath(), command.targetOrgPath()))
            .map(override -> new ReleaseSimulationResult.Conflict(
                override.overrideId(),
                override.orgPath(),
                override.overrideMode().name(),
                override.overrideMode() == InheritanceOverrideMode.DISABLE
                    ? "DISABLED"
                    : "LOCAL_OVERRIDE:" + override.overrideVersionId()
            ))
            .toList();
    }

    private List<ContextSnapshot> historicalSnapshots(
            ReleaseSimulationCommand command,
            List<ReleaseSimulationResult.AffectedOrganization> affected) {
        Instant since = clock.instant().minus(command.replayDays(), ChronoUnit.DAYS);
        Set<String> orgUnitIds = affected.stream()
            .map(ReleaseSimulationResult.AffectedOrganization::orgUnitId)
            .collect(Collectors.toSet());
        return snapshots.findRecentActiveByTenantId(
                command.tenantId(),
                since,
                command.replayLimit()
            ).stream()
            .filter(snapshot -> orgUnitIds.contains(snapshot.orgUnitId()))
            .toList();
    }

    private ReleaseSimulationResult.Replay replay(
            ReleaseSimulationCommand command,
            AssetVersion current,
            AssetVersion candidate,
            List<ContextSnapshot> historicalSnapshots) {
        if (historicalSnapshots.isEmpty()) {
            return ReleaseSimulationResult.Replay.noData(
                "近 " + command.replayDays() + " 天目标组织没有可用标准上下文快照"
            );
        }
        return replayEvaluators.stream()
            .filter(evaluator -> evaluator.supports(command.assetType()))
            .findFirst()
            .map(evaluator -> evaluator.replay(command, current, candidate, historicalSnapshots))
            .orElseGet(() -> dependencyImpactReplay(command, historicalSnapshots));
    }

    private ReleaseSimulationResult.Replay dependencyImpactReplay(
            ReleaseSimulationCommand command,
            List<ContextSnapshot> historicalSnapshots) {
        List<ReleaseSimulationResult.ImpactedAsset> impactedAssets = dependencies.activeDependentsOf(
                command.tenantId(),
                command.assetType(),
                command.assetIdentity(),
                command.targetOrgPath(),
                command.applicableScope()
            ).stream()
            .map(version -> new ReleaseSimulationResult.ImpactedAsset(
                version.assetType(),
                version.assetIdentity(),
                version.versionId(),
                version.versionNo()
            ))
            .toList();
        String impactSummary = impactedAssets.isEmpty()
            ? "未发现目标范围内在用资产依赖本次变更"
            : impactedAssets.size() + " 个在用资产依赖本次变更";
        String reason = "依赖影响评估：" + impactSummary
            + "；已纳入 " + historicalSnapshots.size() + " 个历史上下文快照作为目标范围样本，不执行病例级重算。";
        return new ReleaseSimulationResult.Replay(
            "SUPPORTED",
            historicalSnapshots.size(),
            0,
            0,
            0,
            0,
            0,
            List.of(),
            impactedAssets,
            reason
        );
    }

    private ReleaseSimulationResult.Diff diff(AssetVersion current, AssetVersion candidate) {
        String changeType;
        if (current == null) {
            changeType = "ADDED";
        } else if (Objects.equals(current.contentHash(), candidate.contentHash())) {
            changeType = "UNCHANGED";
        } else {
            changeType = "MODIFIED";
        }
        return new ReleaseSimulationResult.Diff(
            changeType,
            current == null ? null : current.versionNo(),
            candidate.versionNo(),
            current == null ? null : current.contentHash(),
            candidate.contentHash()
        );
    }

    private String digest(
            ReleaseSimulationCommand command,
            AssetVersion candidate,
            AssetVersion current,
            List<ReleaseSimulationResult.AffectedOrganization> affected,
            ReleaseSimulationResult.Diff diff,
            ReleaseSimulationResult.Replay replay,
            ReleaseSimulationResult.Check safety,
            ReleaseSimulationResult.Check dependency,
            List<ReleaseSimulationResult.Conflict> conflicts,
            boolean releasable) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("tenantId", command.tenantId());
        evidence.put("candidateVersionId", candidate.versionId());
        evidence.put("currentVersionId", current == null ? null : current.versionId());
        evidence.put("affectedOrganizations", affected);
        evidence.put("applicableScope", command.applicableScope());
        evidence.put("diff", diff);
        evidence.put("replay", replay);
        evidence.put("safety", safety);
        evidence.put("dependencies", dependency);
        evidence.put("conflicts", conflicts);
        evidence.put("releasable", releasable);
        try {
            byte[] canonical = json.writeValueAsString(evidence).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256 摘要算法", exception);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("发布模拟证据序列化失败", exception);
        }
    }

    private void validate(ReleaseSimulationCommand command) {
        if (command == null
                || isBlank(command.tenantId())
                || isBlank(command.candidateTenantId())
                || command.assetType() == null
                || isBlank(command.assetIdentity())
                || isBlank(command.candidateVersionId())
                || isBlank(command.targetOrgPath())
                || isBlank(command.applicableScope())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "发布模拟资产、生效域与候选版本不能为空");
        }
        if (!Objects.equals(command.candidateTenantId(), command.tenantId())
                && !PlatformAuthority.PLATFORM_TENANT_ID.equals(command.candidateTenantId())) {
            throw new ApiException(ErrorCode.FORBIDDEN, "发布模拟禁止读取其他租户的候选资产");
        }
        if (command.replayDays() == null || command.replayDays() < 1 || command.replayDays() > 365) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "历史回放天数必须在 1 到 365 之间");
        }
        if (command.replayLimit() == null || command.replayLimit() < 1 || command.replayLimit() > 1000) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "历史回放样本上限必须在 1 到 1000 之间");
        }
    }

    private void requireCandidateMatches(
            ReleaseSimulationCommand command,
            AssetVersion candidate) {
        if (candidate.assetType() != command.assetType()
                || !Objects.equals(candidate.assetIdentity(), command.assetIdentity())
                || !Objects.equals(candidate.applicableScope(), command.applicableScope())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "候选版本与发布模拟资产域不一致");
        }
    }

    private boolean isAtOrBelow(String orgPath, String targetOrgPath) {
        return Objects.equals(orgPath, targetOrgPath)
            || (orgPath != null && orgPath.startsWith(targetOrgPath + "/"));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
