package com.medkernel.engine.rule;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.ids.Ulid;

/**
 * 规则知识治理状态服务。
 *
 * <p>治理只保留单操作人安全复核与发布链路，不引入多人会签、委员会或角色分离门禁。
 */
@Service
public class RuleGovernanceService {

    private final RuleGovernanceRepository governanceRepository;
    private final Clock clock;

    @Autowired
    public RuleGovernanceService(RuleGovernanceRepository governanceRepository) {
        this(governanceRepository, Clock.systemUTC());
    }

    RuleGovernanceService(
            RuleGovernanceRepository governanceRepository,
            Clock clock) {
        this.governanceRepository = governanceRepository;
        this.clock = clock;
    }

    /**
     * 为新规则版本建立唯一治理事实。
     */
    @Transactional
    public RuleGovernance initialize(
            String tenantId,
            String ruleVersionId,
            RuleRiskLevel riskLevel,
            String authorId,
            String traceId) {
        Objects.requireNonNull(riskLevel, "规则风险等级不能为空");
        Instant now = clock.instant();
        RuleGovernance governance = new RuleGovernance(
            null,
            "rg-" + Ulid.newUlid(),
            required(tenantId, "租户"),
            required(ruleVersionId, "规则版本"),
            RuleGovernanceState.DRAFT,
            required(authorId, "负责人"),
            "规则草稿已创建",
            now,
            authorId,
            now,
            authorId,
            traceId,
            null
        );
        return governanceRepository.save(governance);
    }

    /**
     * 按闭集顺序推进治理状态。
     */
    @Transactional
    public RuleGovernance transition(
            String tenantId,
            String ruleVersionId,
            RuleGovernanceState target,
            String reason,
            String actor,
            String traceId) {
        RuleGovernance current = requireGovernance(tenantId, ruleVersionId);
        validateTransition(current, Objects.requireNonNull(target, "目标状态不能为空"));
        RuleGovernance updated = current.transition(
            target,
            required(reason, "推进说明"),
            clock.instant(),
            required(actor, "操作人"),
            traceId
        );
        return saveGovernance(updated);
    }

    @Transactional(readOnly = true)
    public RuleGovernance requireGovernance(String tenantId, String ruleVersionId) {
        return governanceRepository.findByRuleVersionIdAndTenantId(ruleVersionId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.CONFLICT, "规则版本缺少治理事实"));
    }

    private static void validateTransition(
            RuleGovernance current,
            RuleGovernanceState target) {
        boolean valid = switch (current.state()) {
            case DRAFT -> target == RuleGovernanceState.REVIEWED;
            case REVIEWED -> target == RuleGovernanceState.SHADOW;
            case SHADOW -> target == RuleGovernanceState.CANARY;
            case CANARY -> target == RuleGovernanceState.FULL;
            case FULL -> target == RuleGovernanceState.MONITOR;
            case MONITOR -> target == RuleGovernanceState.RETIRED;
            case RETIRED -> false;
        };
        if (!valid) {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "非法治理状态迁移: " + current.state() + " -> " + target
            );
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, field + "不能为空");
        }
        return value.trim();
    }

    private RuleGovernance saveGovernance(RuleGovernance governance) {
        try {
            return governanceRepository.save(governance);
        } catch (OptimisticLockingFailureException exception) {
            throw new ApiException(ErrorCode.CONFLICT, "规则治理状态已变化，请刷新后重试");
        }
    }
}
