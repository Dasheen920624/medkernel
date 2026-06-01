package com.medkernel.engine.pkg;

import java.util.List;

/**
 * 知识包差异计算与影响分析响应 DTO。
 */
public record PackageDiffResponse(
    String packageId,
    String baseVersion,
    String targetVersion,
    int addedCount,
    int updatedCount,
    int removedCount,
    List<String> affectedDepartments,
    List<PackageDiffChange> changes
) {}
