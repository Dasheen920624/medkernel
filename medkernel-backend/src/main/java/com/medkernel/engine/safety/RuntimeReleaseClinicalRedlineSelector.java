package com.medkernel.engine.safety;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.medkernel.engine.context.ClinicalRuntimeReleaseContent;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContentResolver;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItem;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 从机构生效版本中选择本次临床运行可用的安全红线版本。
 *
 * <p>红线维护表是创作与审计界面；临床运行只消费机构生效版本清单里锁定的 SAFETY 资产。
 */
@Component
public class RuntimeReleaseClinicalRedlineSelector {

    private static final String SOURCE_PREFIX = "clinical-redline:";

    private final ClinicalRuntimeReleaseContentResolver runtime;
    private final AssetVersionRepository assetVersions;
    private final ClinicalRedlineRepository redlines;

    public RuntimeReleaseClinicalRedlineSelector(
            ClinicalRuntimeReleaseContentResolver runtime,
            AssetVersionRepository assetVersions,
            ClinicalRedlineRepository redlines) {
        this.runtime = runtime;
        this.assetVersions = assetVersions;
        this.redlines = redlines;
    }

    public List<ClinicalRedlineRule> select(String tenantId, String runtimeReleaseId) {
        ClinicalRuntimeReleaseContent content = resolve(tenantId, runtimeReleaseId);
        List<ClinicalRedlineRule> selected = new ArrayList<>();
        for (ClinicalRuntimeReleaseItem item : content.items()) {
            if (item.entryState() != ReleaseEntryState.ACTIVE
                    || item.assetType() != VersionedAssetType.SAFETY) {
                continue;
            }
            AssetVersion version = requireVersion(item);
            SourceRef source = parseSource(version.sourceRef());
            ClinicalRedlineRule redline = redlines
                .findByTenantIdAndRedlineId(version.tenantId(), source.redlineId())
                .orElseThrow(() -> invalid("机构生效版本锁定安全红线不存在：" + source.redlineId()));
            if (!Objects.equals(redline.redlineVersion(), source.redlineVersion())
                    || redline.status() != ClinicalRedlineStatus.ACTIVE) {
                throw invalid(
                    "机构生效版本锁定安全红线版本未激活："
                        + redline.redlineKey() + "@" + source.redlineVersion());
            }
            selected.add(redline);
        }
        return List.copyOf(selected);
    }

    private ClinicalRuntimeReleaseContent resolve(String tenantId, String runtimeReleaseId) {
        try {
            return runtime.resolve(
                requireText(tenantId, "tenantId"),
                requireText(runtimeReleaseId, "runtimeReleaseId"));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw invalid(exception.getMessage(), exception);
        }
    }

    private AssetVersion requireVersion(ClinicalRuntimeReleaseItem item) {
        AssetVersion version = assetVersions
            .findByVersionIdAndTenantId(
                requireText(item.versionId(), "安全红线资产版本"),
                requireText(item.sourceTenantId(), "安全红线来源租户"))
            .orElseThrow(() -> invalid("机构生效版本锁定安全红线资产版本不存在：" + item.versionId()));
        if (version.assetType() != VersionedAssetType.SAFETY
                || !Objects.equals(version.assetIdentity(), item.assetIdentity())
                || !Objects.equals(version.contentHash(), item.contentHash())) {
            throw invalid("机构生效版本安全红线资产与版本正文不一致：" + item.assetIdentity());
        }
        return version;
    }

    private SourceRef parseSource(String sourceRef) {
        String source = requireText(sourceRef, "安全红线资产来源");
        if (!source.startsWith(SOURCE_PREFIX)) {
            throw invalid("安全红线资产来源无效：" + source);
        }
        String payload = source.substring(SOURCE_PREFIX.length());
        int separator = payload.lastIndexOf(':');
        if (separator <= 0 || separator == payload.length() - 1) {
            throw invalid("安全红线资产来源无效：" + source);
        }
        return new SourceRef(payload.substring(0, separator), payload.substring(separator + 1));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field + " 不能为空");
        }
        return value.trim();
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.CONFLICT, message);
    }

    private static ApiException invalid(String message, Throwable cause) {
        return new ApiException(ErrorCode.CONFLICT, message, cause);
    }

    private record SourceRef(String redlineId, String redlineVersion) {
    }
}
