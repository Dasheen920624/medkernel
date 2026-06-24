package com.medkernel.engine.datasvc.export;

/**
 * 引擎数据服务层异步导出类型（DATASVC-01，三组 D2 去标识聚合读模型）。
 *
 * <p>每个类型携其导出确认资源类型标识（小写下划线），用于校验冻结范围；
 * 导出内容由 {@code EngineDataExportService} 按类型路由到既有读模型仓储分页拉取。
 */
public enum EngineDataExportType {
    /** 规则使用统计（rule_execution_log 聚合） */
    RULE_USAGE("engine_data_rule_usage"),
    /** 知识使用统计（recommendation_source KNOWLEDGE 子集聚合） */
    KNOWLEDGE_USAGE("engine_data_knowledge_usage"),
    /** 临床信号统计（recommendation_card 聚合） */
    CLINICAL_SIGNALS("engine_data_clinical_signals");

    private final String resourceType;

    EngineDataExportType(String resourceType) {
        this.resourceType = resourceType;
    }

    /** 导出确认资源类型标识（规范化小写下划线）。 */
    public String resourceType() {
        return resourceType;
    }
}
