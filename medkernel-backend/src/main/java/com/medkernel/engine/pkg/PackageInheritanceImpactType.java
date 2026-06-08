package com.medkernel.engine.pkg;

/**
 * 平台上游版本变更对租户组织的继承影响类型。
 */
public enum PackageInheritanceImpactType {
    AUTO_INHERITS_UPSTREAM,
    REBASE_RECOMMENDED,
    DISABLE_REVIEW_RECOMMENDED,
    UNAFFECTED
}
