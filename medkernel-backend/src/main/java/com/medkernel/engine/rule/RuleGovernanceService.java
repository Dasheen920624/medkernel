package com.medkernel.engine.rule;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.security.RoleCode;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.ids.Ulid;

/**
 * 规则知识治理状态与会签门禁。
 */
@Service
public class RuleGovernanceService {

    private static final Set<RoleCode> PEER_REVIEW_ROLES = EnumSet.of(
        RoleCode.PHARMACIST,
        RoleCode.SPECIALIST,
        RoleCode.DEPT_HEAD,
        RoleCode.INSURANCE_MANAGER,
        RoleCode.MEDICAL_AFFAIRS,
        RoleCode.QA_MANAGER
    );
    private static final Set<RoleCode> COMMITTEE_ROLES = EnumSet.of(
        RoleCode.SPECIALIST,
        RoleCode.DEPT_HEAD,
        RoleCode.INSURANCE_MANAGER,
        RoleCode.MEDICAL_AFFAIRS,
        RoleCode.QA_MANAGER
    );

    private final RuleGovernanceRepository governanceRepository;
    private final RuleSignoffRepository signoffRepository;
    private final Clock clock;

    @Autowired
    public RuleGovernanceService(
            RuleGovernanceRepository governanceRepository,
            RuleSignoffRepository signoffRepository) {
        this(governanceRepository, signoffRepository, Clock.systemUTC());
    }

    RuleGovernanceService(
            RuleGovernanceRepository governanceRepository,
            RuleSignoffRepository signoffRepository,
            Clock clock) {
        this.governanceRepository = governanceRepository;
        this.signoffRepository = signoffRepository;
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
        Instant now = clock.instant();
        int requiredSignoffs =
            riskLevel == RuleRiskLevel.HIGH || riskLevel == RuleRiskLevel.CRITICAL ? 2 : 1;
        RuleGovernance governance = new RuleGovernance(
            null,
            "rg-" + Ulid.newUlid(),
            required(tenantId, "租户"),
            required(ruleVersionId, "规则版本"),
            RuleGovernanceState.DRAFT,
            requiredSignoffs,
            1,
            required(authorId, "作者"),
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
     * 记录同行评审或委员会会签；拒绝会把版本退回草稿，证据仍保留。
     */
    @Transactional
    public RuleGovernance recordSignoff(
            String tenantId,
            String ruleVersionId,
            RuleSignoffStage stage,
            RuleSignoffDecision decision,
            String reason,
            String signerId,
            RoleCode signerRole,
            String traceId) {
        RuleGovernance current = requireGovernance(tenantId, ruleVersionId);
        validateSignoff(current, stage, signerId, signerRole);
        if (signoffRepository.existsByRuleVersionIdAndTenantIdAndStageAndReviewRoundAndSignerId(
                ruleVersionId, tenantId, stage, current.reviewRound(), signerId)) {
            throw new ApiException(ErrorCode.CONFLICT, "同一审核人不能重复会签");
        }
        Instant now = clock.instant();
        saveSignoff(new RuleSignoff(
                null,
                "rs-" + Ulid.newUlid(),
                tenantId,
                ruleVersionId,
                stage,
                current.reviewRound(),
                signerRole.code(),
                signerId,
                Objects.requireNonNull(decision, "签署结论不能为空"),
                required(reason, "签署说明"),
                now,
                traceId
            )
        );
        RuleGovernance updated = decision == RuleSignoffDecision.REJECTED
            ? current.reject("评审未通过，返回草稿", now, signerId, traceId)
            : current.transition(
                RuleGovernanceState.COMMITTEE,
                stage == RuleSignoffStage.PEER_REVIEW ? "同行评审已完成" : "委员会会签已记录",
                now,
                signerId,
                traceId
            );
        return saveGovernance(updated);
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
        String requiredActor = required(actor, "操作人");
        validateTransition(current, target, requiredActor);
        RuleGovernance updated = current.transition(
            target,
            required(reason, "推进说明"),
            clock.instant(),
            requiredActor,
            traceId
        );
        return saveGovernance(updated);
    }

    @Transactional(readOnly = true)
    public RuleGovernance requireGovernance(String tenantId, String ruleVersionId) {
        return governanceRepository.findByRuleVersionIdAndTenantId(ruleVersionId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.CONFLICT, "规则版本缺少治理事实"));
    }

    @Transactional(readOnly = true)
    public List<RuleSignoff> signoffs(String tenantId, String ruleVersionId) {
        return signoffRepository.findByRuleVersionIdAndTenantIdOrderBySignedAtAsc(
            ruleVersionId, tenantId);
    }

    private void validateSignoff(
            RuleGovernance governance,
            RuleSignoffStage stage,
            String signerId,
            RoleCode signerRole) {
        if (governance.authorId().equals(required(signerId, "审核人"))) {
            throw new ApiException(ErrorCode.FORBIDDEN, "作者不能审核自己的规则");
        }
        RuleGovernanceState expectedState = stage == RuleSignoffStage.PEER_REVIEW
            ? RuleGovernanceState.PEER_REVIEW
            : RuleGovernanceState.COMMITTEE;
        if (governance.state() != expectedState) {
            throw new ApiException(ErrorCode.CONFLICT, "当前治理阶段不接受该类签署");
        }
        Set<RoleCode> allowedRoles =
            stage == RuleSignoffStage.PEER_REVIEW ? PEER_REVIEW_ROLES : COMMITTEE_ROLES;
        if (signerRole == null || !allowedRoles.contains(signerRole)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "当前角色无权执行临床规则签署");
        }
    }

    private void validateTransition(
            RuleGovernance current,
            RuleGovernanceState target,
            String actor) {
        boolean valid = switch (current.state()) {
            case DRAFT -> target == RuleGovernanceState.PEER_REVIEW;
            case COMMITTEE -> target == RuleGovernanceState.SHADOW;
            case SHADOW -> target == RuleGovernanceState.CANARY;
            case CANARY -> target == RuleGovernanceState.FULL;
            case FULL -> target == RuleGovernanceState.MONITOR;
            case MONITOR -> target == RuleGovernanceState.RETIRED;
            case PEER_REVIEW, RETIRED -> false;
        };
        if (!valid) {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "非法治理状态迁移: " + current.state() + " -> " + target
            );
        }
        if (target == RuleGovernanceState.SHADOW
                || target == RuleGovernanceState.CANARY
                || target == RuleGovernanceState.FULL) {
            List<RuleSignoff> currentRoundSignoffs = signoffRepository
                .findByRuleVersionIdAndTenantIdOrderBySignedAtAsc(
                    current.ruleVersionId(), current.tenantId())
                .stream()
                .filter(signoff -> signoff.reviewRound() == current.reviewRound())
                .toList();
            boolean signerIsPublisher = currentRoundSignoffs.stream()
                .filter(signoff -> signoff.decision() == RuleSignoffDecision.APPROVED)
                .anyMatch(signoff -> signoff.signerId().equals(actor));
            if (current.authorId().equals(actor) || signerIsPublisher) {
                throw new ApiException(
                    ErrorCode.FORBIDDEN,
                    "规则作者、会签人和发布人必须相互分离"
                );
            }
            if (target != RuleGovernanceState.SHADOW) {
                return;
            }
            long approvalCount = currentRoundSignoffs.stream()
                .filter(signoff -> signoff.stage() == RuleSignoffStage.COMMITTEE)
                .filter(signoff -> signoff.decision() == RuleSignoffDecision.APPROVED)
                .map(RuleSignoff::signerId)
                .distinct()
                .count();
            int missing = current.requiredSignoffs() - (int) approvalCount;
            if (missing > 0) {
                throw new ApiException(
                    ErrorCode.CONFLICT,
                    "仍需 " + missing + " 名独立委员会成员会签"
                );
            }
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, field + "不能为空");
        }
        return value.trim();
    }

    private void saveSignoff(RuleSignoff signoff) {
        try {
            signoffRepository.save(signoff);
        } catch (RuntimeException exception) {
            if (hasCause(exception, DuplicateKeyException.class)) {
                throw new ApiException(ErrorCode.CONFLICT, "同一审核人不能重复会签");
            }
            throw exception;
        }
    }

    private RuleGovernance saveGovernance(RuleGovernance governance) {
        try {
            return governanceRepository.save(governance);
        } catch (OptimisticLockingFailureException exception) {
            throw new ApiException(ErrorCode.CONFLICT, "规则治理状态已变化，请刷新后重试");
        }
    }

    private static boolean hasCause(Throwable exception, Class<? extends Throwable> type) {
        Throwable current = exception;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
