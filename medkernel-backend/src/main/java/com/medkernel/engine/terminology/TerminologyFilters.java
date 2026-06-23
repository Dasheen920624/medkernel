// 术语映射模块各分页 API 的 Filter 入参聚合（按各资源类型一对一匹配 Controller / Service 入参）。
package com.medkernel.engine.terminology;

/**
 * 术语映射模块过滤条件（Terminology Filters）。
 *
 * <p>聚合了标准术语、本地术语、映射关系、候选、冲突及映射版本的各种筛选条件，
 * 支撑 GA-ENG-API-04 字典映射 API 的多条件分页检索。
 */
public final class TerminologyFilters {
    private TerminologyFilters() {}
}


/** 标准术语分页过滤：标准体系 / 分类 / 状态 / 关键词（display_name 或 term_code 模糊）。 */
record StandardTermFilter(String standardSystem, TermCategory category, StandardTermStatus status, String keyword) {
    static StandardTermFilter empty() {
        return new StandardTermFilter(null, null, null, null);
    }
}

/** 本地术语分页过滤：来源系统 / 分类 / 状态 / 关键词（local_name 或 local_code 模糊）。 */
record LocalTermFilter(String sourceSystem, TermCategory category, LocalTermStatus status, String keyword) {
    static LocalTermFilter empty() {
        return new LocalTermFilter(null, null, null, null);
    }
}

/** 正式映射分页过滤：来源系统 / 分类 / 状态 / 关键词（证据文本模糊）。 */
record MappingFilter(String sourceSystem, TermCategory category, TermMappingStatus status, String keyword) {
    static MappingFilter empty() {
        return new MappingFilter(null, null, null, null);
    }
}

/** 候选映射分页过滤：状态 / 风险等级 / 是否冲突 / 生成任务。 */
record CandidateFilter(
    MappingCandidateStatus status,
    TermRiskLevel riskLevel,
    Boolean conflictFlag,
    String generationJobCode
) {
    CandidateFilter(MappingCandidateStatus status, TermRiskLevel riskLevel, Boolean conflictFlag) {
        this(status, riskLevel, conflictFlag, null);
    }

    static CandidateFilter empty() {
        return new CandidateFilter(null, null, null, null);
    }
}

/** 冲突分页过滤：状态 / 风险等级 / 冲突类型。 */
record ConflictFilter(MappingConflictStatus status, TermRiskLevel riskLevel, MappingConflictType conflictType) {
    static ConflictFilter empty() {
        return new ConflictFilter(null, null, null);
    }
}
