package com.medkernel.engine.versioning;

/**
 * 配置类资产版本状态机。
 *
 * <p>对齐核心约束：草稿、待审核、已发布、生效中、已下线、已归档。
 */
public enum AssetVersionStatus {
    DRAFT,
    PENDING_REVIEW,
    PUBLISHED,
    ACTIVE,
    OFFLINE,
    ARCHIVED
}
