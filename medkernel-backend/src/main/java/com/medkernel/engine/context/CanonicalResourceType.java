package com.medkernel.engine.context;

/**
 * 12 类标准临床资源类型，对齐 {@code MEDKERNEL_BUSINESS_SCENARIO_DETAIL_SPEC.md §7.4}。
 *
 * <p>顺序与表 CHECK 约束保持一致，新增类型必须同时更新五方言迁移与本枚举。
 */
public enum CanonicalResourceType {
    PATIENT,
    ENCOUNTER,
    CONDITION,
    NURSING_ASSESSMENT,
    OBSERVATION,
    DIAGNOSTIC_REPORT,
    MEDICATION,
    PROCEDURE,
    DOCUMENT,
    CARE_PLAN,
    FOLLOW_UP,
    CLAIM
}
