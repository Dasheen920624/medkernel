package com.medkernel.engine.versioning;

import java.time.Instant;

/**
 * 统一资产版本发布后的领域投影状态同步器。
 *
 * <p>平台标准版本和机构生效版本都是统一发布入口；各领域只在这里同步自己的可读投影，
 * 不再暴露旧的领域专属发布入口。
 */
public interface AssetPublicationStatusSynchronizer {

    /**
     * 资产版本已在同一事务中进入 {@link AssetVersionStatus#PUBLISHED} 后同步领域投影。
     */
    void afterPublished(
        AssetVersion publishedVersion,
        Instant publishedAt,
        String actor,
        String traceId
    );
}
