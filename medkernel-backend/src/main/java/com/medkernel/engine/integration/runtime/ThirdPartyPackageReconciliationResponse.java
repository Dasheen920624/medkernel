package com.medkernel.engine.integration.runtime;

import com.medkernel.engine.pkg.SyncLogResponse;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;

/**
 * 第三方知识包分发对账响应。
 */
public record ThirdPartyPackageReconciliationResponse(
    String contractVersion,
    String packageId,
    ThirdPartyReconciliationStatus status,
    PageResponse<SyncLogResponse> logs
) {
    public ThirdPartyPackageReconciliationResponse {
        logs = logs == null ? PageResponse.empty(PageRequest.defaults()) : logs;
    }
}
