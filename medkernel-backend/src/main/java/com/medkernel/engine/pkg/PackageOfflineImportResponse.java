package com.medkernel.engine.pkg;

/**
 * 配置包离线导入响应 DTO。
 */
public record PackageOfflineImportResponse(
    String packageId,
    String packageCode,
    String packageVersion,
    KnowledgePackageStatus status,
    int itemCount,
    String payloadSha256
) {}
