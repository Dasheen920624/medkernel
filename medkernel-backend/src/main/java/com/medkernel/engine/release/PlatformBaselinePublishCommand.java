package com.medkernel.engine.release;

import java.util.List;

/**
 * 发布一个新平台标准版本的原子命令。
 */
public record PlatformBaselinePublishCommand(
    List<String> publishVersionIds,
    List<ReleaseAssetRef> disabledAssets,
    String actor,
    String traceId
) {
    public PlatformBaselinePublishCommand {
        publishVersionIds = publishVersionIds == null
            ? List.of() : List.copyOf(publishVersionIds);
        disabledAssets = disabledAssets == null
            ? List.of() : List.copyOf(disabledAssets);
    }
}
