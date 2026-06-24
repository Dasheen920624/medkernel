package com.medkernel.engine.versioning;

import com.medkernel.engine.release.ReleaseSourceLayer;

/**
 * 配置资产的规范归属范围。
 *
 * @param sourceLayer 平台、集团或机构来源层
 * @param organizationPath 真实组织树中的平台、集团或机构根路径
 */
public record AssetOwnershipScope(
    ReleaseSourceLayer sourceLayer,
    String organizationPath
) {
}
