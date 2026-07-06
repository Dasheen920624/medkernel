package com.medkernel.engine.release;

import java.util.List;

import com.medkernel.engine.context.ClinicalRuntimeAssetSelection;

import jakarta.validation.constraints.NotBlank;

/**
 * 机构生效版本启用请求。
 *
 * <p>{@code activeAssets} 是完整期望启用集合，可混合任意领域、类型和来源。
 */
public record ClinicalRuntimeActivateRequest(
    @NotBlank(message = "平台标准版本不能为空")
    String platformBaselineReleaseId,
    String expectedCurrentReleaseId,
    String confirmedPlatformUpgradeDigest,
    List<ClinicalRuntimeAssetSelection> activeAssets
) {
    public ClinicalRuntimeActivateRequest {
        activeAssets = activeAssets == null ? List.of() : List.copyOf(activeAssets);
    }
}
