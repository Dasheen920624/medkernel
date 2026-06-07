package com.medkernel.engine.pathway;

/**
 * 路径试运行模式。
 *
 * <p>单快照用于即时试运行，队列回放和时光机只读消费真实上下文快照，不写患者路径事实。
 */
public enum PathwaySimulationMode {
    SINGLE_SNAPSHOT,
    QUEUE_REPLAY,
    TIME_MACHINE
}
