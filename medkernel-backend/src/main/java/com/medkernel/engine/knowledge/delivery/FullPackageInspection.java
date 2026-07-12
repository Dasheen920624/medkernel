package com.medkernel.engine.knowledge.delivery;

import java.util.List;

import com.medkernel.engine.knowledge.authority.PackageSignatureEnvelope;

/** 完整包完成纯只读容器与正文校验后的不可变事实。 */
public record FullPackageInspection(
    QuarantinedFullPackage artifact,
    FullPackageManifest manifest,
    PackageSignatureEnvelope signatureEnvelope,
    FullPackageReleaseDocument releaseDocument,
    List<PortableAssetDocument> documents,
    int archiveEntryCount,
    long expandedBytes
) {
    public FullPackageInspection {
        documents = List.copyOf(documents);
    }
}
