package com.medkernel.engine.pathway;

/**
 * 路径结局指标绑定范围。
 *
 * <p>模板级用于整条路径疗效闭环，阶段级和里程碑级用于定位具体质量改进点。
 */
public enum PathwayOutcomeScope {
    TEMPLATE,
    PHASE,
    MILESTONE
}
