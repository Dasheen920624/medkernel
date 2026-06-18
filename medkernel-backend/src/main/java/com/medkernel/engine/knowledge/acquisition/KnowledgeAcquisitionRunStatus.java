package com.medkernel.engine.knowledge.acquisition;

/**
 * 公域资料获取运行状态。
 */
public enum KnowledgeAcquisitionRunStatus {
    /** 抓取、入库、解析链路完成。 */
    SUCCEEDED,
    /** 内容指纹已存在，复用既有来源版本。 */
    DUPLICATE,
    /** 触发前被部署形态、白名单、许可或 robots 门禁阻断。 */
    BLOCKED,
    /** 抓取或解析失败，已记录真实原因。 */
    FAILED
}
