package com.medkernel.engine.knowledge.production.gate;

/**
 * 门禁评估上下文（AIK-STD-05）。
 *
 * <p>承载租户、归属生产 job 与可选目标身份，供门禁结果落库关联及权威冲突门禁定位现行版本。
 */
public record GateContext(String tenantId, String jobCode, Long targetIdentityId) {

    public GateContext(String tenantId, String jobCode) {
        this(tenantId, jobCode, null);
    }
}
