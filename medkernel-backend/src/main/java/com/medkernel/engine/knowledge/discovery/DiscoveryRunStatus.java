package com.medkernel.engine.knowledge.discovery;

/**
 * 可信来源探索运行状态（LLM-06，FR-2/5）。对应 {@code mk_knowledge_discovery_run.status} CHECK 约束。
 */
public enum DiscoveryRunStatus {
    /** 命中受控来源并产出候选。 */
    SUCCEEDED,
    /** 无受控匹配，诚实空态（非报错非降级，铁律 #1）。 */
    EMPTY,
    /** 上游受控来源不可用，诚实降级（不以空数据伪装，铁律 #1）。 */
    DEGRADED
}
