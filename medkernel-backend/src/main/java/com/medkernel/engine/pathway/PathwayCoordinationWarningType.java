package com.medkernel.engine.pathway;

/**
 * 多路径协调提示类型。
 *
 * <p>提示只用于人工协调，不自动修改医嘱、路径节点或患者状态。
 */
public enum PathwayCoordinationWarningType {
    ORDER_SET_CONFLICT,
    CLOCK_WINDOW_OVERLAP
}
