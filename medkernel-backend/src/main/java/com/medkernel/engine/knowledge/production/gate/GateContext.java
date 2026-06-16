package com.medkernel.engine.knowledge.production.gate;

/**
 * 门禁评估上下文（AIK-STD-05）。
 *
 * <p>承载租户与归属生产 job，供门禁结果落库关联。PR2 临床门禁在此扩展源解析/红线租户等上下文。
 */
public record GateContext(String tenantId, String jobCode) {
}
