package com.medkernel.engine.followup;

import com.medkernel.engine.versioning.AssetVersionStatus;

/**
 * 随访模板列表过滤条件。
 *
 * @param assetStatus 统一资产版本状态过滤；为空表示不限状态
 * @param keyword     模板编码、名称、适用范围或模板 ID 模糊搜索关键词
 */
public record FollowupTemplateFilter(
    AssetVersionStatus assetStatus,
    String keyword
) {
}
