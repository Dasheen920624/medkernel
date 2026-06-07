package com.medkernel.engine.versioning;

/**
 * 继承解析结果的来源层级，标注某机构实际生效的版本取自平台权威还是组织覆盖，供审核台与运行期审计追溯。
 *
 * @see ResolvedAssetVersion
 */
public enum SourceTier {
    /** 平台权威基线：未被客户租户或机构覆盖遮蔽时，解析回退到平台主租户的 ACTIVE 版本。 */
    PLATFORM,
    /** 组织覆盖：版本或覆盖取自当前租户组织闭包内某节点（本级命中或继承上级组织）。 */
    ORG
}
