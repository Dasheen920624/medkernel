package com.medkernel.engine.pkg;

/**
 * 知识包通用响应 DTO。
 */
public record PackageResponse(
    String packageId,
    String packageCode,
    String packageVersion,
    String name,
    String description,
    PackageAccessPolicy accessPolicy,
    KnowledgePackageStatus status
) {
    public static PackageResponse from(KnowledgePackage entity) {
        return new PackageResponse(
            entity.packageId(),
            entity.packageCode(),
            entity.packageVersion(),
            entity.name(),
            entity.description(),
            entity.accessPolicy(),
            entity.status()
        );
    }
}
