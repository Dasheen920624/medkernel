package com.medkernel.engine.versioning;

/**
 * 发布放量策略。
 *
 * <p>组织层级由 {@link VersionReleaseScopeType} 表达；床位比例、分批、机构清单等放量方式属于策略维度，
 * 不再作为组织作用域枚举值扩张。
 */
public enum RolloutStrategy {
    /** 全量生效。 */
    ALL,
    /** 以一个组织节点为根覆盖整棵子树。 */
    ORG_SUBTREE,
    /** 仅在明确选择的组织清单内生效。 */
    ORG_LIST,
    /** 按床位比例灰度放量。 */
    CANARY_BED_PERCENT,
    /** 按递增比例分批放量并在批次间观察。 */
    STAGED
}
