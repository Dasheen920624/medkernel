package com.medkernel.engine.context;

import java.util.List;

/**
 * 一次医院临床运行修订命令。
 *
 * <p>{@code activeAssets} 是完整期望启用集合，可跨领域、跨类型任意组合；未选择但可用的资产
 * 会在新修订中物化为 {@code DISABLED}。
 */
public record ClinicalRuntimeReleaseCommand(
    String tenantId,
    String hospitalId,
    String platformBaselineReleaseId,
    String expectedCurrentReleaseId,
    List<ClinicalRuntimeAssetSelection> activeAssets,
    String actor,
    String traceId
) {
    public ClinicalRuntimeReleaseCommand {
        activeAssets = activeAssets == null ? List.of() : List.copyOf(activeAssets);
    }
}
