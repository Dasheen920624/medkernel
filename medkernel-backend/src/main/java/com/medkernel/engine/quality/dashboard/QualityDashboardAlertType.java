package com.medkernel.engine.quality.dashboard;

/**
 * 质量风险概览预警类型。
 */
public enum QualityDashboardAlertType {
    HIGH_RISK_FINDING,
    OVERDUE_RECTIFICATION,
    RULE_OVERRIDE,
    PATHWAY_VARIANCE,
    CLOCK_SLA_BREACH
}
