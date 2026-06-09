package com.medkernel.engine.pathway;

import java.time.Instant;

/**
 * 专病包领域视图。
 *
 * <p>数据来自统一 {@code knowledge_package + package_item + mk_version_asset_version}，
 * 不再对应独立物理表。
 */
public record SpecialtyPackage(
    Long id,
    String packageId,
    String tenantId,
    String packageCode,
    String diseaseCode,
    String name,
    String packageVersion,
    SpecialtyPackageStatus status,
    String sourceRef,
    String description,
    Instant publishedAt,
    String publishedBy,
    Instant createdAt,
    String createdBy,
    Instant updatedAt,
    String updatedBy,
    String traceId
) {}
