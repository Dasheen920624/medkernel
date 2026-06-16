package com.medkernel.engine.llm;

import java.util.List;

/**
 * LLM-04 版本治理导出响应。
 *
 * <p>导出仅含版本号、状态与 hash，不含提示词正文、工具契约明文或凭据。
 */
public record ModelVersionExportResponse(
    String tenantId,
    String capabilityCode,
    List<ModelVersionBundleResponse> bundles
) {

    public ModelVersionExportResponse {
        bundles = bundles == null ? List.of() : List.copyOf(bundles);
    }
}
