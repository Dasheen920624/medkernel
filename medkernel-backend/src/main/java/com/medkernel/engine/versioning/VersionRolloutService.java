package com.medkernel.engine.versioning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.ids.Ulid;

/**
 * 灰度观测与分批推进服务。
 */
@Service
public class VersionRolloutService {

    private final VersionReleasePlanRepository releasePlans;
    private final VersionRolloutObservationRepository observations;
    private final RolloutPauseNotifier pauseNotifier;
    private final Clock clock;

    @Autowired
    public VersionRolloutService(
            VersionReleasePlanRepository releasePlans,
            VersionRolloutObservationRepository observations,
            RolloutPauseNotifier pauseNotifier) {
        this(releasePlans, observations, pauseNotifier, Clock.systemUTC());
    }

    VersionRolloutService(
            VersionReleasePlanRepository releasePlans,
            VersionRolloutObservationRepository observations,
            RolloutPauseNotifier pauseNotifier,
            Clock clock) {
        this.releasePlans = releasePlans;
        this.observations = observations;
        this.pauseNotifier = pauseNotifier;
        this.clock = clock;
    }

    @Transactional
    public VersionRolloutObservationResult observe(VersionRolloutObservationCommand command) {
        requireCommand(command);
        VersionReleasePlan plan = releasePlans.findByPlanIdAndTenantId(command.planId(), command.tenantId())
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "灰度发布计划不存在: " + command.planId()));
        if (plan.status() != VersionReleaseStatus.GRAY) {
            throw new ApiException(ErrorCode.CONFLICT, "只有进行中的灰度发布计划可以记录观测");
        }
        if (!command.stageIndex().equals(plan.rolloutStageIndex())) {
            throw new ApiException(ErrorCode.CONFLICT, "观测批次与发布计划当前批次不一致");
        }
        RolloutPolicy policy = requirePolicy(plan);
        Instant observedAt = command.observedAt() == null ? clock.instant() : command.observedAt();
        VersionRolloutObservation observation = observations.save(newObservation(command, observedAt));
        String pausedReason = thresholdBreachReason(observation, policy.thresholds());
        if (pausedReason != null) {
            VersionReleasePlan paused = releasePlans.save(plan.withRolloutState(
                VersionReleaseStatus.PAUSED,
                plan.rolloutStageIndex(),
                pausedReason,
                clock.instant(),
                command.actor().trim(),
                command.traceId()
            ));
            pauseNotifier.notifyPaused(paused, pausedReason);
            return new VersionRolloutObservationResult(
                paused,
                observation,
                true,
                false,
                stagePercent(policy, paused.rolloutStageIndex())
            );
        }

        RolloutProgress progress = progress(plan, policy, observedAt);
        VersionReleasePlan advanced = plan;
        if (progress.stageIndex() != plan.rolloutStageIndex()) {
            advanced = releasePlans.save(plan.withRolloutState(
                VersionReleaseStatus.GRAY,
                progress.stageIndex(),
                null,
                clock.instant(),
                command.actor().trim(),
                command.traceId()
            ));
        }
        return new VersionRolloutObservationResult(
            advanced,
            observation,
            false,
            progress.readyForFullRelease(),
            stagePercent(policy, progress.stageIndex())
        );
    }

    @Transactional
    public VersionReleasePlan rollback(VersionRolloutRollbackCommand command) {
        requireRollbackCommand(command);
        VersionReleasePlan plan = releasePlans.findByPlanIdAndTenantId(command.planId(), command.tenantId())
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "灰度发布计划不存在: " + command.planId()));
        if (plan.status() == VersionReleaseStatus.ROLLED_BACK) {
            return plan;
        }
        if (plan.status() != VersionReleaseStatus.GRAY && plan.status() != VersionReleaseStatus.PAUSED) {
            throw new ApiException(ErrorCode.CONFLICT, "只有进行中或已暂停的灰度发布计划可以回退");
        }
        return releasePlans.save(plan.withRolloutRollback(
            command.reason().trim(),
            clock.instant(),
            command.actor().trim(),
            command.traceId()
        ));
    }

    private VersionRolloutObservation newObservation(
            VersionRolloutObservationCommand command,
            Instant observedAt) {
        return new VersionRolloutObservation(
            null,
            "vro-" + Ulid.newUlid(),
            command.planId(),
            command.tenantId(),
            command.stageIndex(),
            command.sampleCount(),
            command.hitCount(),
            command.blockCount(),
            command.manualRejectionCount(),
            command.anomalyCount(),
            rate(command.hitCount(), command.sampleCount()),
            rate(command.blockCount(), command.sampleCount()),
            rate(command.manualRejectionCount(), command.sampleCount()),
            rate(command.anomalyCount(), command.sampleCount()),
            observedAt,
            command.actor().trim(),
            command.traceId()
        );
    }

    private RolloutProgress progress(
            VersionReleasePlan plan,
            RolloutPolicy policy,
            Instant observedAt) {
        if (policy.strategy() != RolloutStrategy.STAGED) {
            return new RolloutProgress(plan.rolloutStageIndex(), false);
        }
        Instant windowStart = plan.updatedAt() == null ? plan.createdAt() : plan.updatedAt();
        Instant earliestAdvance = windowStart.plus(policy.observationMinutes(), ChronoUnit.MINUTES);
        if (observedAt.isBefore(earliestAdvance)) {
            return new RolloutProgress(plan.rolloutStageIndex(), false);
        }
        int lastStageIndex = policy.stages().size() - 1;
        if (plan.rolloutStageIndex() >= lastStageIndex) {
            return new RolloutProgress(lastStageIndex, true);
        }
        return new RolloutProgress(plan.rolloutStageIndex() + 1, false);
    }

    private String thresholdBreachReason(
            VersionRolloutObservation observation,
            RolloutThresholds thresholds) {
        if (thresholds == null) {
            return null;
        }
        List<String> reasons = new ArrayList<>();
        addBreach(reasons, "命中率", observation.hitRate(), thresholds.maxHitRate());
        addBreach(reasons, "拦截率", observation.blockRate(), thresholds.maxBlockRate());
        addBreach(reasons, "人工否决率", observation.manualRejectionRate(), thresholds.maxManualRejectionRate());
        addBreach(reasons, "异常率", observation.anomalyRate(), thresholds.maxAnomalyRate());
        return reasons.isEmpty() ? null : "灰度指标越阈值：" + String.join("；", reasons);
    }

    private void addBreach(
            List<String> reasons,
            String label,
            BigDecimal actual,
            Double threshold) {
        if (threshold != null && actual.compareTo(BigDecimal.valueOf(threshold)) > 0) {
            reasons.add(label + " " + actual.stripTrailingZeros().toPlainString()
                + " > " + BigDecimal.valueOf(threshold).stripTrailingZeros().toPlainString());
        }
    }

    private RolloutPolicy requirePolicy(VersionReleasePlan plan) {
        if (plan.rolloutConfigJson() == null || plan.rolloutConfigJson().isBlank()) {
            throw new ApiException(ErrorCode.CONFLICT, "灰度发布计划缺少结构化放量策略");
        }
        return RolloutPolicyJson.decode(plan.rolloutConfigJson());
    }

    private void requireCommand(VersionRolloutObservationCommand command) {
        if (command == null || isBlank(command.tenantId()) || isBlank(command.planId())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "租户与发布计划不能为空");
        }
        if (command.stageIndex() == null || command.stageIndex() < 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "灰度批次不能为空且不能小于零");
        }
        if (command.sampleCount() == null || command.sampleCount() <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "灰度观测样本数必须大于零");
        }
        requireCount(command.hitCount(), command.sampleCount(), "命中数");
        requireCount(command.blockCount(), command.sampleCount(), "拦截数");
        requireCount(command.manualRejectionCount(), command.sampleCount(), "人工否决数");
        requireCount(command.anomalyCount(), command.sampleCount(), "异常数");
        if (isBlank(command.actor())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "操作人不能为空");
        }
    }

    private void requireRollbackCommand(VersionRolloutRollbackCommand command) {
        if (command == null || isBlank(command.tenantId()) || isBlank(command.planId())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "租户与发布计划不能为空");
        }
        if (isBlank(command.reason())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "回退原因不能为空");
        }
        if (!Boolean.TRUE.equals(command.confirmedOperation())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "执行回退前必须核对版本与影响");
        }
        if (isBlank(command.actor())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "操作人不能为空");
        }
    }

    private void requireCount(Long count, long sampleCount, String label) {
        if (count == null || count < 0 || count > sampleCount) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "必须在零到样本数之间");
        }
    }

    private BigDecimal rate(long count, long sampleCount) {
        return BigDecimal.valueOf(count)
            .divide(BigDecimal.valueOf(sampleCount), 6, RoundingMode.HALF_UP);
    }

    private int stagePercent(RolloutPolicy policy, int stageIndex) {
        return switch (policy.strategy()) {
            case STAGED -> policy.stages().get(stageIndex);
            case CANARY_BED_PERCENT -> policy.bedPercent();
            case ORG_LIST, ORG_SUBTREE, ALL -> 100;
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record RolloutProgress(int stageIndex, boolean readyForFullRelease) {}
}
