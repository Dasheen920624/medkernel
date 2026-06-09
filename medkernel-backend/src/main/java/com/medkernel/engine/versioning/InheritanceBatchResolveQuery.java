package com.medkernel.engine.versioning;

import java.time.Instant;
import java.util.List;

/**
 * 同一租户、目标组织与解析时点下的一组资产解析查询。
 */
public record InheritanceBatchResolveQuery(
    String tenantId,
    List<VersionedAssetIdentity> declaredAssets,
    List<String> applicableScopes,
    String targetOrgUnitId,
    Instant effectiveAt
) {
    public InheritanceBatchResolveQuery {
        declaredAssets = List.copyOf(declaredAssets == null ? List.of() : declaredAssets);
        applicableScopes = List.copyOf(applicableScopes == null ? List.of() : applicableScopes);
    }
}
