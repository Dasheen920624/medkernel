package com.medkernel.engine.versioning;

/**
 * 配置类资产内容版本生命周期。
 *
 * <p>评审、批准、灰度、回滚等过程状态属于发布计划或机构生效版本，不写入内容版本。
 */
public enum AssetVersionStatus {
    DRAFT,
    PUBLISHED,
    WITHDRAWN;

    /**
     * 返回上线模型允许写入和解释的全部内容生命周期。
     */
    public static AssetVersionStatus[] canonicalValues() {
        return new AssetVersionStatus[] {DRAFT, PUBLISHED, WITHDRAWN};
    }
}
