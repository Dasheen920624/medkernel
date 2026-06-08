package com.medkernel.engine.versioning;

/**
 * 继承覆盖生命周期状态。
 */
public enum InheritanceOverrideStatus {
    /** 草稿，尚未提交。 */
    DRAFT,
    /** 待评审，不能参与解析。 */
    IN_REVIEW,
    /** 已评审通过，等待发布。 */
    APPROVED,
    /** 已发布，可参与继承解析。 */
    PUBLISHED,
    /** 已弃用，不再新用。 */
    DEPRECATED,
    /** 已退役，保留审计但不参与解析。 */
    RETIRED
}
