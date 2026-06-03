package com.medkernel.engine.tenant;

import java.time.Instant;
import java.util.List;

/**
 * 租户开通就绪门结果。
 *
 * @param tenantId 租户标识
 * @param ready 是否满足开通前置条件
 * @param steps 实施向导各步骤真实状态
 * @param blockers 全局阻塞原因清单
 * @param checkedAt 检查时间
 */
public record OnboardingReadiness(
    String tenantId,
    boolean ready,
    List<ImplementationStep> steps,
    List<String> blockers,
    Instant checkedAt
) {
    public OnboardingReadiness {
        steps = steps == null ? List.of() : List.copyOf(steps);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }
}
