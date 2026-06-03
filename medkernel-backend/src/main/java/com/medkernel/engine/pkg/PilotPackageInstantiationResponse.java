package com.medkernel.engine.pkg;

import java.util.List;

/**
 * 首发模板实例化结果。
 */
public record PilotPackageInstantiationResponse(
    String templateCode,
    PackageResponse packageInfo,
    List<PackageItemResponse> items
) {
    public PilotPackageInstantiationResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
