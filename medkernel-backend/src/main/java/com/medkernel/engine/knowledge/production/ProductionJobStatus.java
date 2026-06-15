package com.medkernel.engine.knowledge.production;

/**
 * 知识生产 job 状态机（AIK-STD-13，变更类）。对应 {@code mk_knowledge_production_job.status} CHECK 约束。
 *
 * <p>PENDING → RUNNING → COMPLETED / FAILED；任意非终态 → CANCELLED（重放 / 中止见 PR2）。
 */
public enum ProductionJobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
