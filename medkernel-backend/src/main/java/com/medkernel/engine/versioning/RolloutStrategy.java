package com.medkernel.engine.versioning;

/**
 * 发布放量策略。
 *
 * <p>组织层级由 {@link VersionReleaseScopeType} 表达；床位比例、分批、机构清单等放量方式属于策略维度，
 * 不再作为组织作用域枚举值扩张。
 */
public enum RolloutStrategy {
    /** 按床位比例灰度放量。 */
    CANARY_BED_PERCENT
}
