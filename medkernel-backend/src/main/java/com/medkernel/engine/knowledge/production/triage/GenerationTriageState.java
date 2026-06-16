package com.medkernel.engine.knowledge.production.triage;

/**
 * AIK-STD-10 生成期候选 8 态分流状态。
 */
public enum GenerationTriageState {
    /** 新知识身份首个候选。 */
    NEW_ASSET,
    /** 内容指纹重复，不重复入审。 */
    DUPLICATE,
    /** 同身份小修订，进入合并/对照审核。 */
    MINOR_REVISION,
    /** 高权威来源升级或高影响变更，进入升级审核。 */
    MAJOR_UPGRADE,
    /** 候选声明与现行知识存在冲突，进入冲突审核。 */
    CONFLICT,
    /** 低阶来源试图替代高阶现行知识，按降级态处理。 */
    DOWNGRADE,
    /** 候选声明废止/退役现行知识。 */
    DEPRECATION,
    /** 缺少现行基线或判定依据不足，人工分流。 */
    UNCERTAIN
}
