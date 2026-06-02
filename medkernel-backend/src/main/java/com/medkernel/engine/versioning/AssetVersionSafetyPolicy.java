package com.medkernel.engine.versioning;

/**
 * 配置资产版本的继承安全策略。
 */
public enum AssetVersionSafetyPolicy {
    /** 普通配置资产，可按组织继承规则局部覆盖。 */
    NORMAL,
    /** 高风险禁忌红线，下级组织不得关闭或降级覆盖。 */
    SAFETY_REDLINE
}
