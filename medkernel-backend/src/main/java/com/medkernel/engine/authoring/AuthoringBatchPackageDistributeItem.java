package com.medkernel.engine.authoring;

import java.util.List;

import com.medkernel.engine.pkg.ReleaseScopeType;
import com.medkernel.engine.pkg.ReleaseStrategy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * 一条配置包分发目标。
 */
public record AuthoringBatchPackageDistributeItem(
    @NotBlank String itemId,
    @NotBlank String packageId,
    @NotBlank String targetOrgUnitId,
    @NotNull ReleaseStrategy strategy,
    @NotNull ReleaseScopeType scopeType,
    String scopeValue,
    @NotEmpty List<String> adapterIds,
    @NotBlank String reason
) {
    public AuthoringBatchPackageDistributeItem {
        adapterIds = adapterIds == null ? null : List.copyOf(adapterIds);
    }
}
