package com.medkernel.engine.knowledge.delivery;

import java.util.List;

import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 从可移植资产中抽出的不可变来源、许可、依赖和合成测试事实。
 *
 * <p>正文由统一内容表权威保存，本记录保留重建、审计和再次交付所需的全部非正文事实，
 * 避免把同一大正文复制到两个关系表。
 */
public record FullPackageProvenanceDocument(
    String schemaVersion,
    VersionedAssetType assetType,
    String assetIdentity,
    String versionId,
    String versionNo,
    String organizationScope,
    String applicableScope,
    AssetVersionSafetyPolicy safetyPolicy,
    AssetVersionOverridePolicy overridePolicy,
    String contentSha256,
    String contentDigest,
    List<PortableAssetDocument.Source> sources,
    List<PortableAssetDocument.License> licenses,
    List<PortableAssetDocument.Dependency> dependencies,
    PortableAssetDocument.Validation validation,
    List<PortableAssetDocument.TestVector> testVectors
) {
    public FullPackageProvenanceDocument {
        sources = List.copyOf(sources);
        licenses = List.copyOf(licenses);
        dependencies = List.copyOf(dependencies);
        testVectors = List.copyOf(testVectors);
    }

    /** 从已校验的便携资产文档生成不含正文的完整来源事实。 */
    public static FullPackageProvenanceDocument from(PortableAssetDocument source) {
        return new FullPackageProvenanceDocument(
            source.schemaVersion(),
            source.assetType(),
            source.assetIdentity(),
            source.versionId(),
            source.versionNo(),
            source.organizationScope(),
            source.applicableScope(),
            source.safetyPolicy(),
            source.overridePolicy(),
            source.contentSha256(),
            source.contentDigest(),
            source.sources(),
            source.licenses(),
            source.dependencies(),
            source.validation(),
            source.testVectors());
    }
}
