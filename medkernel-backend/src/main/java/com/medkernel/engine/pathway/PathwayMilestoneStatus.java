package com.medkernel.engine.pathway;

/**
 * 患者路径运行态里程碑状态。
 */
public enum PathwayMilestoneStatus {
    /**
     * 里程碑下所有节点已完成。
     */
    ACHIEVED,

    /**
     * 患者当前正在执行该里程碑下的节点。
     */
    CURRENT,

    /**
     * 里程碑尚未开始且未超过预期完成点。
     */
    PENDING,

    /**
     * 里程碑尚未达成且已超过预期完成点。
     */
    OVERDUE
}
