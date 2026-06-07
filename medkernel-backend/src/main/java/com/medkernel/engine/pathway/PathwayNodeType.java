package com.medkernel.engine.pathway;

/**
 * 路径节点类型。
 *
 * <p>覆盖临床活动节点，以及决策、并行、等待计时、子路径、人工闸门和医嘱集等流程控制节点。
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
    SUBPATHWAY,
    MANUAL_GATE,
    ORDER_SET
}
