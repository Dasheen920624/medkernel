package com.medkernel.engine.context;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.release.ReleaseManifestHash;

/**
 * 由机构生效版本 ID 唯一解析并校验本次临床运行的完整物化版本明细。
 */
@Service
public class ClinicalRuntimeReleaseContentResolver {

    private final ClinicalRuntimeReleaseRepository releases;
    private final ClinicalRuntimeReleaseItemRepository items;

    public ClinicalRuntimeReleaseContentResolver(
            ClinicalRuntimeReleaseRepository releases,
            ClinicalRuntimeReleaseItemRepository items) {
        this.releases = releases;
        this.items = items;
    }

    @Transactional(readOnly = true)
    public ClinicalRuntimeReleaseContent resolve(String tenantId, String releaseId) {
        String normalizedTenantId = requireText(tenantId, "租户");
        ClinicalRuntimeRelease release = releases.findByTenantIdAndReleaseId(
                normalizedTenantId, requireText(releaseId, "机构生效版本"))
            .orElseThrow(() -> new IllegalArgumentException("机构生效版本不存在"));
        List<ClinicalRuntimeReleaseItem> materialized =
            items.findByReleaseIdOrderByAssetTypeAscAssetIdentityAsc(release.releaseId());
        String actualHash = ReleaseManifestHash.sha256(
            materialized.stream().map(ClinicalRuntimeReleaseContentResolver::canonicalLine).toList());
        if (!actualHash.equals(release.manifestSha256())) {
            throw new IllegalStateException("机构生效版本明细校验码不一致，禁止继续执行");
        }
        return new ClinicalRuntimeReleaseContent(release, materialized);
    }

    private static String canonicalLine(ClinicalRuntimeReleaseItem item) {
        return String.join(
            "\u001f",
            item.sourceTenantId(),
            item.sourceLayer().name(),
            item.assetType().name(),
            item.assetIdentity(),
            item.entryState().name(),
            nullToEmpty(item.versionId()),
            nullToEmpty(item.versionNo()),
            nullToEmpty(item.contentHash())
        );
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return value.trim();
    }
}
