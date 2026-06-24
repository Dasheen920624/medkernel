package com.medkernel.engine.authoring;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.versioning.AssetDependencyDeclaration;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionService;
import com.medkernel.engine.versioning.RemovedRuntimeSelectorFields;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.hash.Sha256ContentHash;
import org.springframework.stereotype.Component;

/**
 * 统一自动生成资产创作入口。
 *
 * <p>模型、模板和导入候选只允许在这里进入统一资产草稿版本；本类不创建第二套
 * 知识、规则或路径状态机，也不接受调用方手工运行定位和手工版本号。
 */
@Component
public class AssetAuthoringRegistry {

    private static final Set<VersionedAssetType> GENERATED_TYPES =
        Set.of(VersionedAssetType.KNOWLEDGE, VersionedAssetType.RULE, VersionedAssetType.PATHWAY);
    private static final Set<String> MANUAL_VERSION_FIELDS =
        Set.of("versionNo", "manualVersionNo", "assetVersionNo");

    private final AssetVersionService versions;
    private final Map<VersionedAssetType, GeneratedAssetCandidateValidator> validators;

    public AssetAuthoringRegistry(
            ObjectMapper json,
            AssetVersionService versions,
            List<GeneratedAssetCandidateValidator> validators) {
        this.versions = versions;
        this.validators = mapValidators(validators);
    }

    /**
     * 将自动生成候选物化为统一资产草稿版本。
     */
    public GeneratedAssetDraftResponse materializeDraft(GeneratedAssetCandidateRequest request) {
        String tenantId = required(request.tenantId(), "租户 ID");
        VersionedAssetType assetType = required(request.assetType(), "资产类型");
        if (!GENERATED_TYPES.contains(assetType)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "自动生成入口只接收知识、规则和路径资产");
        }
        String assetIdentity = required(request.assetIdentity(), "资产身份");
        JsonNode content = request.content();
        if (content == null || !content.isObject()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "自动生成资产正文必须是 JSON 对象");
        }
        rejectLegacyInputs(content);
        GeneratedAssetCandidateValidator validator = validators.get(assetType);
        if (validator == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "缺少自动生成资产校验器: " + assetType);
        }
        GeneratedAssetValidation validation = validator.validate(assetIdentity, content);
        List<AssetDependencyDeclaration> dependencies = mergeDependencies(
            validation.dependencies(),
            request.dependencies());
        String canonicalContent = required(validation.canonicalContent(), "规范化正文");
        AssetVersion saved = versions.registerDraft(new AssetVersionRegisterCommand(
            tenantId,
            assetType,
            assetIdentity,
            request.organizationScope(),
            required(request.applicableScope(), "适用范围"),
            canonicalContent,
            Sha256ContentHash.sha256(canonicalContent, "自动生成资产正文不能为空"),
            required(request.sourceRef(), "来源依据"),
            required(request.createdBy(), "创建人"),
            request.traceId(),
            AssetVersionSafetyPolicy.NORMAL,
            null,
            dependencies
        ));
        return new GeneratedAssetDraftResponse(
            saved.versionId(),
            saved.assetType(),
            saved.assetIdentity(),
            saved.versionNo(),
            saved.status(),
            saved.contentHash(),
            saved.traceId()
        );
    }

    private void rejectLegacyInputs(JsonNode content) {
        List<String> removedRuntimeSelectorFields = new ArrayList<>();
        List<String> manualVersionFields = new ArrayList<>();
        removedRuntimeSelectorFields.addAll(RemovedRuntimeSelectorFields.presentIn(content));
        for (String field : MANUAL_VERSION_FIELDS) {
            if (content.has(field)) {
                manualVersionFields.add(field);
            }
        }
        if (!removedRuntimeSelectorFields.isEmpty() || !manualVersionFields.isEmpty()) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "自动生成资产候选不得携带调用方运行定位或手工版本号: removedRuntimeSelectorFields="
                    + removedRuntimeSelectorFields + ", manualVersionFields=" + manualVersionFields);
        }
    }

    private Map<VersionedAssetType, GeneratedAssetCandidateValidator> mapValidators(
            List<GeneratedAssetCandidateValidator> validators) {
        Map<VersionedAssetType, GeneratedAssetCandidateValidator> mapped = new LinkedHashMap<>();
        for (GeneratedAssetCandidateValidator validator : validators == null ? List.<GeneratedAssetCandidateValidator>of() : validators) {
            GeneratedAssetCandidateValidator previous = mapped.putIfAbsent(validator.assetType(), validator);
            if (previous != null) {
                throw new IllegalArgumentException("重复的自动生成资产校验器: " + validator.assetType());
            }
        }
        return Map.copyOf(mapped);
    }

    private List<AssetDependencyDeclaration> mergeDependencies(
            List<AssetDependencyDeclaration> generated,
            List<AssetDependencyDeclaration> requested) {
        Map<String, AssetDependencyDeclaration> merged = new LinkedHashMap<>();
        for (AssetDependencyDeclaration declaration : generated == null ? List.<AssetDependencyDeclaration>of() : generated) {
            addDependency(merged, declaration);
        }
        for (AssetDependencyDeclaration declaration : requested == null ? List.<AssetDependencyDeclaration>of() : requested) {
            addDependency(merged, declaration);
        }
        return List.copyOf(merged.values());
    }

    private void addDependency(
            Map<String, AssetDependencyDeclaration> dependencies,
            AssetDependencyDeclaration declaration) {
        if (declaration == null || declaration.dependsOnAssetType() == null
                || declaration.dependsOnIdentity() == null || declaration.dependsOnIdentity().isBlank()) {
            return;
        }
        dependencies.putIfAbsent(
            declaration.dependsOnAssetType() + "|" + declaration.dependsOnIdentity() + "|" + declaration.kind(),
            declaration);
    }

    private static <T> T required(T value, String label) {
        if (value == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "不能为空");
        }
        return value;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "不能为空");
        }
        return value.trim();
    }
}
