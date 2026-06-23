package com.medkernel.engine.versioning;

/**
 * 资产版本触发绑定用途。
 */
public enum AssetTriggerPurpose {
    /**
     * 在临床触发点执行规则。
     */
    RULE_EXECUTION,

    /**
     * 在临床触发点筛选候选入径路径。
     */
    PATHWAY_ENTRY_CANDIDATE,

    /**
     * 在临床触发点推进已确认的患者路径。
     */
    PATHWAY_PROGRESS
}
