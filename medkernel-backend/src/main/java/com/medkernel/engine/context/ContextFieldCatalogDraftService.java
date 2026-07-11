package com.medkernel.engine.context;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionDraftUpdateCommand;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionService;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 将字段目录工作区固化为统一版本资产草稿。
 */
@Service
public class ContextFieldCatalogDraftService {

    static final String ASSET_IDENTITY = ContextFieldCatalogAssets.CLINICAL_CONTEXT_IDENTITY;
    private static final String SCHEMA_VERSION = "1.0";
    private static final String APPLICABLE_SCOPE = "ALL";
    private static final String SOURCE_REF = "field-catalog:working-directory";

    private final ContextFieldCatalogService catalog;
    private final AssetVersionRepository versions;
    private final AssetVersionService versionService;
    private final ObjectMapper json;

    public ContextFieldCatalogDraftService(
            ContextFieldCatalogService catalog,
            AssetVersionRepository versions,
            AssetVersionService versionService,
            ObjectMapper json) {
        this.catalog = catalog;
        this.versions = versions;
        this.versionService = versionService;
        this.json = json;
    }

    /**
     * 固化当前字段目录；版本号由统一资产版本服务自动分配。
     */
    public AssetVersion snapshotDraft() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        String tenantId = scope.tenantId().trim();
        String actor = RequestContext.currentUserId()
            .filter(value -> !value.isBlank())
            .map(String::trim)
            .orElseThrow(() -> new ApiException(
                ErrorCode.UNAUTHORIZED, "认证上下文缺少操作人"));
        String traceId = RequestContext.currentTraceId();
        String content = serialize(catalog.query(null, null));
        List<AssetVersion> drafts =
            versions.findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
                tenantId,
                VersionedAssetType.FIELD_CATALOG,
                ASSET_IDENTITY,
                AssetVersionStatus.DRAFT
            );
        if (drafts.size() > 1) {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "字段目录存在多个未发布草稿，无法确定唯一工作版本");
        }
        if (drafts.isEmpty()) {
            return versionService.registerDraft(new AssetVersionRegisterCommand(
                tenantId,
                VersionedAssetType.FIELD_CATALOG,
                ASSET_IDENTITY,
                null,
                APPLICABLE_SCOPE,
                content,
                null,
                SOURCE_REF,
                actor,
                traceId,
                AssetVersionSafetyPolicy.NORMAL,
                AssetVersionOverridePolicy.FREE
            ));
        }
        return versionService.updateDraft(new AssetVersionDraftUpdateCommand(
            tenantId,
            drafts.get(0).versionId(),
            ASSET_IDENTITY,
            null,
            APPLICABLE_SCOPE,
            content,
            null,
            SOURCE_REF,
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            actor,
            traceId,
            List.of()
        ));
    }

    private String serialize(List<ContextFieldDescriptor> fields) {
        List<Map<String, Object>> contentFields = (fields == null ? List.<ContextFieldDescriptor>of() : fields)
            .stream()
            .sorted(java.util.Comparator.comparing(ContextFieldDescriptor::fieldPath))
            .map(ContextFieldCatalogDraftService::contentField)
            .toList();
        if (contentFields.isEmpty()) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED, "字段目录工作区不能为空");
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", SCHEMA_VERSION);
        root.put("fields", contentFields);
        try {
            return json.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                ErrorCode.INTERNAL_ERROR, "字段目录草稿正文无法序列化", exception);
        }
    }

    private static Map<String, Object> contentField(ContextFieldDescriptor field) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("category", field.category());
        value.put("group", field.group());
        value.put("resourceType", field.resourceType());
        value.put("fieldPath", field.fieldPath());
        value.put("displayName", field.displayName());
        value.put("dataType", field.dataType());
        value.put("unit", field.unit());
        value.put("codeSystem", field.codeSystem());
        value.put("description", ContextFieldDescriptionNormalizer.normalize(
            field.description(), field.displayName(), field.fieldPath()));
        value.put("derived", field.derived());
        return value;
    }
}
