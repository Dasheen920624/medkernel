package com.medkernel.engine.pkg;

import java.util.List;

/**
 * 首发配置包模板响应。
 */
public record PilotPackageTemplateResponse(
    String templateId,
    String templateCode,
    String tenantId,
    String name,
    String description,
    String packageCodePrefix,
    String defaultPackageVersion,
    int itemCount,
    List<PilotPackageTemplateItemResponse> items
) {
    public PilotPackageTemplateResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }

    static PilotPackageTemplateResponse from(
            PilotPackageTemplate template,
            List<PilotPackageTemplateItem> items) {
        List<PilotPackageTemplateItemResponse> itemResponses = items.stream()
            .map(PilotPackageTemplateItemResponse::from)
            .toList();
        return new PilotPackageTemplateResponse(
            template.templateId(),
            template.templateCode(),
            template.tenantId(),
            template.name(),
            template.description(),
            template.packageCodePrefix(),
            template.defaultPackageVersion(),
            itemResponses.size(),
            itemResponses
        );
    }
}
