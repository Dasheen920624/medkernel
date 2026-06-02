package com.medkernel.engine.pkg;

import java.util.List;

/**
 * 包发布前校验响应。
 */
public record PackageValidateResponse(
    String packageId,
    KnowledgePackageStatus status,
    int itemCount,
    String contentSha256,
    boolean valid,
    List<PackageValidateIssue> issues
) {
    public PackageValidateResponse {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
