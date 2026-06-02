package com.medkernel.engine.versioning;

/**
 * 历史重放查询。
 */
public record VersionReplayQuery(
    String tenantId,
    String bindingId
) {}
