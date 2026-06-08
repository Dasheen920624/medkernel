package com.medkernel.engine.pkg;

import java.util.List;

/**
 * 应用首发模板推荐引用结果。
 */
public record PilotPackageTemplateApplyResponse(
    String templateCode,
    List<TenantPackageReferenceResponse> references,
    List<PilotPackageInitialOverrideResponse> initialOverrides
) {
    public PilotPackageTemplateApplyResponse {
        references = references == null ? List.of() : List.copyOf(references);
        initialOverrides = initialOverrides == null ? List.of() : List.copyOf(initialOverrides);
    }
}
