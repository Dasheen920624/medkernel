package com.medkernel.engine.context;

/**
 * 13 类标准临床资源类型，对齐 {@code MEDKERNEL_BUSINESS_SCENARIO_DETAIL_SPEC.md §7.4}
 * 与路径/规则创作所需的结构化过敏资源。
 *
 * <p>顺序与表 CHECK 约束保持一致，新增类型必须同时更新五方言迁移与本枚举。
 */
public enum CanonicalResourceType {
    PATIENT,
    ALLERGY_INTOLERANCE,
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
