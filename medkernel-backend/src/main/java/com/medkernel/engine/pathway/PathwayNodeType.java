package com.medkernel.engine.pathway;

/**
 * 路径节点类型。
 *
 * <p>覆盖临床活动节点，以及决策、并行、等待计时、人工确认和医嘱套餐等流程控制节点。
 */
public enum PathwayNodeType {
    SCREENING,
    ASSESSMENT,
    EXAM,
    LAB,
    MEDICATION,
    SURGERY,
    NURSING,
    REHAB,
    DISCHARGE,
    FOLLOWUP,
    QUALITY,
    DECISION,
    PARALLEL,
    WAIT_TIMER,
    MANUAL_GATE,
    ORDER_SET
}
