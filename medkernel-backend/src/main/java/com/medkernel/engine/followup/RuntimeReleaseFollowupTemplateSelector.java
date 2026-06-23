package com.medkernel.engine.followup;

import org.springframework.stereotype.Component;

import com.medkernel.engine.context.ClinicalRuntimeReleaseContent;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContentResolver;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItem;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionNumbers;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 从医院运行修订中解析本次临床可使用的随访模板版本。
 *
 * <p>随访计划生成不得按模板 ID 直接读取全租户已发布模板；必须由运行修订锁定
 * {@code FOLLOWUP} 资产身份、版本号和内容摘要后，再反查对应模板实体。
 */
@Component
public class RuntimeReleaseFollowupTemplateSelector {

    private final ClinicalRuntimeReleaseContentResolver runtime;
    private final AssetVersionRepository assetVersions;
    private final FollowupTemplateRepository templates;

    public RuntimeReleaseFollowupTemplateSelector(
            ClinicalRuntimeReleaseContentResolver runtime,
            AssetVersionRepository assetVersions,
            FollowupTemplateRepository templates) {
        this.runtime = runtime;
        this.assetVersions = assetVersions;
        this.templates = templates;
    }

    public FollowupTemplate requireByTemplateId(
            String tenantId,
            String runtimeReleaseId,
            String templateId) {
        String requestedTemplateId = required(templateId, "随访模板 ID");
        ClinicalRuntimeReleaseContent content = resolve(tenantId, runtimeReleaseId);
        for (ClinicalRuntimeReleaseItem item : content.items()) {
            if (item.entryState() != ReleaseEntryState.ACTIVE
                    || item.assetType() != VersionedAssetType.FOLLOWUP) {
                continue;
            }
            FollowupTemplate template = resolveTemplate(item);
            if (requestedTemplateId.equals(template.templateId())) {
                return template;
            }
        }
        throw invalid("当前医院运行修订未启用随访模板: " + requestedTemplateId);
    }

    private FollowupTemplate resolveTemplate(ClinicalRuntimeReleaseItem item) {
        AssetVersion version = assetVersions
            .findByVersionIdAndTenantId(
                required(item.versionId(), "随访资产版本 ID"),
                required(item.sourceTenantId(), "随访资产来源租户"))
            .orElseThrow(() -> invalid(
                "运行修订锁定随访资产版本不存在：" + item.assetIdentity() + "@" + item.versionNo()));
        if (version.status() != AssetVersionStatus.PUBLISHED
                || version.assetType() != VersionedAssetType.FOLLOWUP
                || !version.assetIdentity().equals(item.assetIdentity())
                || !version.versionNo().equals(item.versionNo())
                || !version.contentHash().equals(item.contentHash())) {
            throw invalid(
                "运行修订锁定随访资产版本不一致：" + item.assetIdentity() + "@" + item.versionNo());
        }
        int versionNo = parseVersionNo(item.versionNo(), item.assetIdentity());
        FollowupTemplate template = templates
            .findByTenantIdAndTemplateCodeAndVersionNo(
                item.sourceTenantId(), item.assetIdentity(), versionNo)
            .orElseThrow(() -> invalid(
                "运行修订锁定随访模板实体不存在：" + item.assetIdentity() + "@" + item.versionNo()));
        if (!version.versionId().equals(template.assetVersionId())) {
            throw invalid(
                "随访模板实体与运行资产版本不一致：" + template.templateId());
        }
        return template;
    }

    private ClinicalRuntimeReleaseContent resolve(String tenantId, String runtimeReleaseId) {
        try {
            return runtime.resolve(
                required(tenantId, "租户"),
                required(runtimeReleaseId, "医院运行修订"));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw invalid(exception.getMessage(), exception);
        }
    }

    private static int parseVersionNo(String value, String templateCode) {
        try {
            return AssetVersionNumbers.intSequence(value, "随访模板版本");
        } catch (ApiException exception) {
            throw invalid("运行随访模板版本号无效：" + templateCode + "@" + value, exception);
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw invalid(label + "不能为空");
        }
        return value.trim();
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.ENG_FOLLOW_004, message);
    }

    private static ApiException invalid(String message, Throwable cause) {
        return new ApiException(ErrorCode.ENG_FOLLOW_004, message, cause);
    }
}
