package com.medkernel.engine.context;

/**
 * 标准临床资源类型，供上下文、审计和互操作链路统一使用。
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
