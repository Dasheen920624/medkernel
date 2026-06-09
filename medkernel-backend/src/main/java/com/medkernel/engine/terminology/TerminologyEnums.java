// 术语映射模块全部枚举类型聚合：术语分类、风险等级及各类状态机。
// 这里所有枚举值与 db/migration/.../V4__terminology_mapping_baseline.sql 的 CHECK 约束严格对齐。
package com.medkernel.engine.terminology;

/**
 * 术语映射模块全部枚举类型聚合（Terminology Enums）。
 *
 * <p>这里定义了术语分类、风险等级及各类状态机。
 * 所有枚举值与 db/migration/.../V4__terminology_mapping_baseline.sql 的 CHECK 约束严格对齐。
 */
public final class TerminologyEnums {
    private TerminologyEnums() {}
}


/** 术语分类（诊断/手术/药品/器械/检验/检查/医嘱/医保/科室/文书/随访/其他）。 */
enum TermCategory {
    DIAGNOSIS,
    PROCEDURE,
    DRUG,
    DEVICE,
    LAB,
    EXAM,
    ORDER,
    INSURANCE,
    DEPARTMENT,
    DOCUMENT,
    FOLLOWUP,
    OTHER
}

/** 术语映射风险等级（低/中/高），用于审核优先级与高风险拦截。 */
enum TermRiskLevel {
    LOW,
    MEDIUM,
    HIGH
}

/** 高危近似规则类型：互斥词组 / 剂量量级 / 单位强度。 */
enum HighRiskRuleType {
    MUTUALLY_EXCLUSIVE_TERMS,
    DOSE_MAGNITUDE,
    UNIT_STRENGTH
}

/** 高危近似规则状态：ACTIVE 可用 / DISABLED 已停用。 */
enum HighRiskRuleStatus {
    ACTIVE,
    DISABLED
}

/** 标准术语状态：ACTIVE 可被引用 / DISABLED 已禁用。 */
enum StandardTermStatus {
    ACTIVE,
    DISABLED
}

/** 本地术语状态：UNMAPPED 未映射 / MAPPED 已映射 / DISABLED 已禁用。 */
enum LocalTermStatus {
    UNMAPPED,
    MAPPED,
    DISABLED
}

/** 正式术语映射状态：草稿/已确认/被替换/已回滚。 */
enum TermMappingStatus {
    DRAFT,
    CONFIRMED,
    SUPERSEDED,
    ROLLED_BACK
}

/** 候选映射状态：待审核/已确认/已驳回/已过期。 */
enum MappingCandidateStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    EXPIRED
}

/** 候选来源：规则引擎/AI 自动识别/人工录入/批量导入。 */
enum MappingCandidateSource {
    RULE,
    AI,
    MANUAL,
    IMPORT
}

/** 冲突类型：一对多/多对一/标准码已禁用/跨体系不一致/同名异义/同义不匹配。 */
enum MappingConflictType {
    ONE_TO_MANY,
    MANY_TO_ONE,
    DISABLED_CODE,
    CROSS_SYSTEM_INCONSISTENT,
    HOMONYM,
    SYNONYM_MISMATCH
}

/** 冲突处置状态：未处置/已处置/已忽略。 */
enum MappingConflictStatus {
    OPEN,
    RESOLVED,
    IGNORED
}

/** 术语映射包生命周期状态：草稿/灰度/已发布/被替换/已回滚/已归档。 */
enum TermMappingPackageStatus {
    DRAFT,
    GRAY,
    PUBLISHED,
    SUPERSEDED,
    ROLLED_BACK,
    ARCHIVED
}

/** 包发布模式：GRAY 灰度发布到部分作用域 / FULL 全量发布替换旧版。 */
enum PackageReleaseMode {
    GRAY,
    FULL
}
