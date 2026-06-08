package com.medkernel.engine.versioning;

/**
 * 配置类资产统一生命周期状态机。
 *
 * <p>平台版本与租户覆盖共用同一状态机；只有 {@link #PUBLISHED} 参与新请求解析，
 * {@link #RETIRED} 保留历史重放但不再参与解析。
 */
public enum AssetVersionStatus {
    DRAFT,
    IN_REVIEW,
    APPROVED,
    PUBLISHED,
    DEPRECATED,
    RETIRED
}
