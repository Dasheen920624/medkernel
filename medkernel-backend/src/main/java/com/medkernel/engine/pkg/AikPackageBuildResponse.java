package com.medkernel.engine.pkg;

/**
 * AIK 知识包装配响应 DTO。
 */
public record AikPackageBuildResponse(
    String jobId,
    PackageResponse packageResponse,
    int itemCount,
    String manifestSha256
) {}
