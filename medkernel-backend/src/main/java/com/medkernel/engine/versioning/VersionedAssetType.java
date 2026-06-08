package com.medkernel.engine.versioning;

/**
 * 统一配置资产类型枚举（归一）。
 *
 * <p>合并原 {@code VersionedAssetType} 与 {@code com.medkernel.engine.pkg.PackageItemAssetType} 为单一权威类型，
 * 使版本底座 {@link AssetVersion} 与知识包条目 {@code PackageItem} 的资产类型取值同源。
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
    /** 知识包 */
    PACKAGE,
    /** 推荐卡 */
    RECOMMENDATION,
    /** 安全（红线 / 高危拦截） */
    SAFETY,
    /** CDSS 风险分级矩阵 */
    CDSS_RISK,
    /** 条件片段 */
    CONDITION_FRAGMENT,
    /** 值集 */
    VALUE_SET,
    /** 受控临床公式 */
    FORMULA,
    /** 医嘱套餐 */
    ORDER_SET,
    /** 动作卡片 */
    ACTION_CARD,
    /** 子路径 */
    SUBPATHWAY
}
