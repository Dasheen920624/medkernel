package com.medkernel.engine.pathway;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.medkernel.engine.context.ClinicalRuntimeReleaseContent;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContentResolver;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItem;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.versioning.AssetVersionNumbers;
import com.medkernel.engine.versioning.AssetTriggerBinding;
import com.medkernel.engine.versioning.AssetTriggerBindingRepository;
import com.medkernel.engine.versioning.AssetTriggerPurpose;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 按医院运行修订、触发用途和触发点选择精确路径版本。
 *
 * <p>选择结果只来自不可变运行修订条目，不读取当前模板指针，也不接受调用方指定版本或领域作为运行路由。
 */
@Component
public class RuntimeReleasePathwaySelector {

    private final ClinicalRuntimeReleaseContentResolver runtime;
    private final PathwayTemplateRepository templates;
    private final AssetTriggerBindingRepository triggers;

    public RuntimeReleasePathwaySelector(
            ClinicalRuntimeReleaseContentResolver runtime,
            PathwayTemplateRepository templates,
            AssetTriggerBindingRepository triggers) {
        this.runtime = runtime;
        this.templates = templates;
        this.triggers = triggers;
    }

    /**
     * 查询指定触发点下必须由医师确认的候选入径路径。
     */
    public RuntimePathwaySelection selectEntryCandidates(
            String tenantId,
            String runtimeReleaseId,
            String triggerPoint) {
        ClinicalRuntimeReleaseContent content =
            resolve(tenantId, runtimeReleaseId);
        List<RuntimePathwayReference> pathways = select(
            content,
            requireText(triggerPoint, "triggerPoint"),
            AssetTriggerPurpose.PATHWAY_ENTRY_CANDIDATE
        );
        return new RuntimePathwaySelection(
            content.release().releaseId(),
            content.release().platformBaselineReleaseId(),
            pathways
        );
    }

    /**
     * 确认医师选择的模板确实属于本次触发产生的候选路径。
     */
    public RuntimePathwayReference requireEntryCandidate(
            String tenantId,
            String runtimeReleaseId,
            String triggerPoint,
            String templateId) {
        String requiredTemplateId = requireText(templateId, "templateId");
        return selectEntryCandidates(tenantId, runtimeReleaseId, triggerPoint)
            .pathways()
            .stream()
            .filter(candidate -> requiredTemplateId.equals(candidate.templateId()))
            .findFirst()
            .orElseThrow(() -> invalid(
                "所选路径不是当前运行修订与触发点下的入径候选：" + requiredTemplateId));
    }

    /**
     * 读取在途患者路径固定的精确版本，并确认该版本允许在当前触发点推进。
     */
    public RuntimePathwayReference requireProgressPathway(
            String tenantId,
            String runtimeReleaseId,
            String pathwayVersionId,
            String triggerPoint) {
        ClinicalRuntimeReleaseContent content =
            resolve(tenantId, runtimeReleaseId);
        String requiredVersionId = requireText(pathwayVersionId, "pathwayVersionId");
        String requiredTrigger = requireText(triggerPoint, "triggerPoint");
        ClinicalRuntimeReleaseItem item = content.items().stream()
            .filter(candidate -> candidate.entryState() == ReleaseEntryState.ACTIVE)
            .filter(candidate -> candidate.assetType() == VersionedAssetType.PATHWAY)
            .filter(candidate -> requiredVersionId.equals(candidate.versionId()))
            .findFirst()
            .orElseThrow(() -> invalid(
                "在途路径版本未包含在医院运行修订中：" + requiredVersionId));
        if (!isBound(item, AssetTriggerPurpose.PATHWAY_PROGRESS, requiredTrigger)) {
            throw invalid(
                "在途路径版本未绑定推进触发点："
                    + item.assetIdentity() + "@" + requiredTrigger);
        }
        return resolveTemplate(item);
    }

    private List<RuntimePathwayReference> select(
            ClinicalRuntimeReleaseContent content,
            String triggerPoint,
            AssetTriggerPurpose purpose) {
        List<RuntimePathwayReference> selected = new ArrayList<>();
        for (ClinicalRuntimeReleaseItem item : content.items()) {
            if (item.entryState() != ReleaseEntryState.ACTIVE
                    || item.assetType() != VersionedAssetType.PATHWAY
                    || !isBound(item, purpose, triggerPoint)) {
                continue;
            }
            selected.add(resolveTemplate(item));
        }
        return List.copyOf(selected);
    }

    private boolean isBound(
            ClinicalRuntimeReleaseItem item,
            AssetTriggerPurpose purpose,
            String triggerPoint) {
        List<AssetTriggerBinding> matching = triggers
            .findByTenantIdAndVersionIdAndPurposeAndTriggerPointOrderByTriggerBindingIdAsc(
                item.sourceTenantId(),
                item.versionId(),
                purpose,
                triggerPoint
            );
        if (matching.stream().anyMatch(binding ->
                binding.assetType() != VersionedAssetType.PATHWAY
                    || !item.assetIdentity().equals(binding.assetIdentity()))) {
            throw invalid(
                "运行修订路径触发绑定与资产身份不一致：" + item.assetIdentity());
        }
        return !matching.isEmpty();
    }

    private RuntimePathwayReference resolveTemplate(ClinicalRuntimeReleaseItem item) {
        int versionNo = parseVersionNo(item);
        PathwayTemplate template = templates
            .findByTenantIdAndTemplateCodeAndTemplateVersion(
                item.sourceTenantId(),
                item.assetIdentity(),
                versionNo
            )
            .orElseThrow(() -> invalid(
                "运行修订锁定路径版本不存在："
                    + item.assetIdentity() + "@" + item.versionNo()));
        if (template.status() != PathwayTemplateStatus.PUBLISHED) {
            throw invalid(
                "运行修订锁定路径版本未发布："
                    + item.assetIdentity() + "@" + item.versionNo());
        }
        return new RuntimePathwayReference(
            item.sourceTenantId(),
            template.templateId(),
            template.templateCode(),
            item.versionId(),
            versionNo,
            template.name(),
            template.diseaseCode()
        );
    }

    private ClinicalRuntimeReleaseContent resolve(
            String tenantId,
            String runtimeReleaseId) {
        String normalizedTenantId = requireText(tenantId, "tenantId");
        String normalizedReleaseId = requireText(runtimeReleaseId, "runtimeReleaseId");
        try {
            return runtime.resolve(normalizedTenantId, normalizedReleaseId);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw invalid(exception.getMessage(), exception);
        }
    }

    private int parseVersionNo(ClinicalRuntimeReleaseItem item) {
        try {
            return AssetVersionNumbers.intSequence(item.versionNo(), "路径版本");
        } catch (ApiException exception) {
            throw invalid(
                "运行修订中的路径版本号无效："
                    + item.assetIdentity() + "@" + item.versionNo(),
                exception
            );
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field + " 不能为空");
        }
        return value.trim();
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.ENG_PATHWAY_006, message);
    }

    private static ApiException invalid(String message, Throwable cause) {
        return new ApiException(ErrorCode.ENG_PATHWAY_006, message, cause);
    }
}
