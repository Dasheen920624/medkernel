package com.medkernel.engine.knowledge.acquisition;

/**
 * 公域资料获取触发方式。
 */
public enum AcquisitionTriggerType {
    /** 治理员在知识生产中心手工触发。 */
    MANUAL,
    /** 调度任务触发。 */
    SCHEDULED,
    /** 受控 Agent 工具触发。 */
    AGENT_TOOL
}
