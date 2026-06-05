package com.medkernel.engine.versioning;

/**
 * 继承解析结果的来源层级，标注某机构实际生效的版本取自平台权威还是组织覆盖，供审核台与运行期审计追溯。
 *
 * @see ResolvedAssetVersion
 */
public enum SourceTier {
    /** 平台权威基线：未被任何租户/机构覆盖遮蔽时，解析回退到 {@code __platform__} 租户的 ACTIVE 版本（设计附录 G·D1）。 */
    PLATFORM,
    /** 组织覆盖：版本或覆盖取自当前租户组织闭包内某节点（本级命中或继承上级组织）。 */
    ORG,
    /**
     * 诚实降级回退：平台缺失基线时回退本租户遗留版本并显式标注（设计附录 G·sourceTier=LEGACY）。
     *
     * <p>保留枚举值；当前解析在平台缺失且组织闭包无版本时返回 {@code NOT_FOUND} 而非伪造，
     * LEGACY 回退随迁移期桥接（P1+）启用，届时由解析层产出。
     */
    LEGACY
}
