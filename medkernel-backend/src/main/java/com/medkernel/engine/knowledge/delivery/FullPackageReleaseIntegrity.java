package com.medkernel.engine.knowledge.delivery;

import java.util.List;

import com.medkernel.engine.release.ReleaseManifestHash;

/** 完整包平台版本明细与统一发布账本共用的确定性摘要规则。 */
public final class FullPackageReleaseIntegrity {

    private FullPackageReleaseIntegrity() {
    }

    /** 按平台标准版本账本的规范行重算完整明细 SHA-256。 */
    public static String manifestSha256(List<FullPackageReleaseDocument.Entry> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("平台版本明细不能为空");
        }
        return ReleaseManifestHash.sha256(entries.stream().map(entry -> String.join(
            "\u001f",
            entry.assetType().name(),
            entry.assetIdentity(),
            entry.state().name(),
            nullToEmpty(entry.versionId()),
            nullToEmpty(entry.versionNo()),
            nullToEmpty(plainSha256(entry.sourceContentSha256()))
        )).toList());
    }

    /** 去除便携合同前缀，返回关系库统一使用的 64 位 SHA-256。 */
    public static String plainSha256(String value) {
        return value != null && value.startsWith("sha256:")
            ? value.substring("sha256:".length())
            : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
