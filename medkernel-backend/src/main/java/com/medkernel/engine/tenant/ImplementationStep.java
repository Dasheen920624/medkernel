package com.medkernel.engine.tenant;

import java.util.List;

/**
 * 租户实施向导单步真实就绪状态。
 *
 * @param key 步骤稳定键
 * @param title 中文步骤名称
 * @param status {@code DONE} 或 {@code BLOCKED}
 * @param blockers 阻塞原因清单
 * @param targetPath 对应配置页路由
 * @param evidence 已完成时的关系库证据摘要
 */
public record ImplementationStep(
    String key,
    String title,
    String status,
    List<String> blockers,
    String targetPath,
    String evidence
) {
    public ImplementationStep {
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
    }

    /**
     * 判断步骤是否已完成。
     *
     * @return 状态为 {@code DONE} 时返回 {@code true}
     */
    public boolean done() {
        return "DONE".equals(status);
    }
}
