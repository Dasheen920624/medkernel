package com.medkernel.engine.integration.runtime;

import java.util.List;

import com.medkernel.engine.pkg.SyncLogResponse;

/**
 * 第三方知识包分发对账响应。
 */
public record ThirdPartyPackageReconciliationResponse(
    String contractVersion,
    String packageId,
    ThirdPartyReconciliationStatus status,
    List<SyncLogResponse> logs
) {
    public ThirdPartyPackageReconciliationResponse {
        logs = List.copyOf(logs == null ? List.of() : logs);
    }
}
