package com.medkernel.engine.release;

import java.util.List;

/**
 * 平台标准版本发布请求。
 *
 * <p>调用方只提交本次版本替换和停用集合；租户、操作者、追踪标识均取认证上下文。
 */
public record PlatformBaselinePublishRequest(
    List<String> publishVersionIds,
    List<ReleaseAssetRef> disabledAssets
) {
    public PlatformBaselinePublishRequest {
        publishVersionIds = publishVersionIds == null
            ? List.of() : List.copyOf(publishVersionIds);
        disabledAssets = disabledAssets == null
            ? List.of() : List.copyOf(disabledAssets);
    }
}
