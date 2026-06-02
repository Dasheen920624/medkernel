package com.medkernel.engine.versioning;

/**
 * 配置类资产类型。
 *
 * <p>仅登记已经存在配置资产主链路的类型；患者运行数据不得伪装成配置资产入版。
 */
public enum VersionedAssetType {
    KNOWLEDGE,
    TERMINOLOGY,
    RULE,
    PATHWAY,
    PACKAGE,
    EVALUATION
}
