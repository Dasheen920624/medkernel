package com.medkernel.engine.pkg;

/**
 * 首发模板资产项响应。
 */
public record PilotPackageTemplateItemResponse(
    PackageItemAssetType assetType,
    String assetId,
    String assetVersion,
    boolean required,
    Integer sortOrder,
    String dependencyNote
) {
    static PilotPackageTemplateItemResponse from(PilotPackageTemplateItem item) {
        return new PilotPackageTemplateItemResponse(
            item.assetType(),
            item.assetId(),
            item.assetVersion(),
            item.required(),
            item.sortOrder(),
            item.dependencyNote()
        );
    }
}
