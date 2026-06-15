package com.medkernel.engine.knowledge.production;

/**
 * 知识生产目标管道（AIK-STD-13，FR-4 双形态物理隔离）。对应 {@code mk_knowledge_production_job.target_pipeline}。
 *
 * <p>核心 §7/§9：外网平台主源管道产物归 {@code t-1} 平台主源；内网院内覆盖管道产物只归本客户租户，
 * 禁与平台主资产混、禁反写平台主源。
 */
public enum TargetPipeline {
    /** 外网平台主源管道（产物归 t-1 平台主源，仅平台租户可产）。 */
    PLATFORM_SOURCE,
    /** 内网院内覆盖管道（产物只归本客户租户，禁反写主源）。 */
    TENANT_OVERLAY
}
