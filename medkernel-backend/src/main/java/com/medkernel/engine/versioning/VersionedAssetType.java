package com.medkernel.engine.versioning;

/**
 * 统一版本化资产类型枚举。
 *
 * <p>作为资产身份、内容版本、依赖图、平台标准版本和机构生效版本的单一资产类型来源。
 *
 * <p>仅登记已存在或规划中配置资产主链路的类型；患者运行数据不得伪装成配置资产入版。
 */
public enum VersionedAssetType {
    /** 知识 */
    KNOWLEDGE,
    /** 字典术语 */
    TERMINOLOGY,
    /** 规则 */
    RULE,
    /** 路径 */
    PATHWAY,
    /** 评估指标 */
    EVALUATION,
    /** 随访 */
    FOLLOWUP,
    /** 上下文字段目录 */
    FIELD_CATALOG,
    /** 安全（红线 / 高危拦截） */
    SAFETY,
    /** CDSS 风险分级矩阵 */
    CDSS_RISK,
    /** 值集 */
    VALUE_SET,
    /** 受控临床公式 */
    FORMULA,
    /** 医嘱套餐 */
    ORDER_SET,
    /** 动作卡片 */
    ACTION_CARD;

    /**
     * 是否为最终可进入平台标准版本或机构生效版本的配置资产。
     */
    public boolean isRuntimeConfiguration() {
        return true;
    }

    /**
     * 是否由统一版本正文表承载完整可恢复内容。
     *
     * <p>这些资产没有其他领域实体作为正文权威源，因此禁止只保存版本元数据与哈希。
     */
    public boolean usesUnifiedContentStore() {
        return switch (this) {
            case FIELD_CATALOG, VALUE_SET, FORMULA, ORDER_SET, ACTION_CARD -> true;
            default -> false;
        };
    }
}
