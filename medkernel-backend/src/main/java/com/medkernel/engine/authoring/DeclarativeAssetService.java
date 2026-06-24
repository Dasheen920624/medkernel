package com.medkernel.engine.authoring;

import java.util.List;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionContent;
import com.medkernel.engine.versioning.AssetVersionContentRepository;
import com.medkernel.engine.versioning.AssetVersionDraftUpdateCommand;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionService;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.DeclarativeAssetContentValidator;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 值集、公式、医嘱套餐和动作卡的独立维护服务。
 *
 * <p>四类资产共享统一的自动版本与审计底座，正文按类型严格校验。发布修订只选取
 * 精确资产版本，不在维护期绑定包或手工版本号。字段目录使用专用服务；路径使用完整
 * 路径工作台，避免形成第二份内容真相源。
 */
@Service
public class DeclarativeAssetService {

    private static final Set<VersionedAssetType> MAINTAINABLE_TYPES = Set.of(
        VersionedAssetType.VALUE_SET,
        VersionedAssetType.FORMULA,
        VersionedAssetType.ORDER_SET,
        VersionedAssetType.ACTION_CARD
    );

    private final ObjectMapper json;
    private final DeclarativeAssetContentValidator validator;
    private final AssetVersionService versions;
    private final AssetVersionRepository versionRepository;
    private final AssetVersionContentRepository contents;

    public DeclarativeAssetService(
            ObjectMapper json,
            DeclarativeAssetContentValidator validator,
            AssetVersionService versions,
            AssetVersionRepository versionRepository,
            AssetVersionContentRepository contents) {
        this.json = json;
        this.validator = validator;
        this.versions = versions;
        this.versionRepository = versionRepository;
        this.contents = contents;
    }

    @Transactional
    public DeclarativeAssetDetailResponse create(DeclarativeAssetUpsertRequest request) {
        String tenantId = currentTenant();
        VersionedAssetType assetType = maintainable(request.assetType());
        String assetIdentity = required(request.assetIdentity(), "资产编码");
        String content = validator.validateAndCanonicalize(assetType, request.content());
        String actor = actor();
        String traceId = RequestContext.currentTraceId();
        AssetVersion saved;
        try {
            saved = versions.registerDraft(new AssetVersionRegisterCommand(
                tenantId,
                assetType,
                assetIdentity,
                null,
                applicableScope(request.applicableScope()),
                content,
                null,
                required(request.sourceRef(), "来源依据"),
                actor,
                traceId,
                AssetVersionSafetyPolicy.NORMAL,
                AssetVersionOverridePolicy.FREE
            ));
        } catch (DuplicateKeyException exception) {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "配置资产版本并发创建冲突，请刷新后重试：" + assetIdentity,
                exception
            );
        }
        return response(saved, readJson(content));
    }

    @Transactional
    public DeclarativeAssetDetailResponse update(
            String versionId,
            DeclarativeAssetUpsertRequest request) {
        String tenantId = currentTenant();
        AssetVersion existing = ownedVersion(tenantId, versionId);
        VersionedAssetType assetType = maintainable(request.assetType());
        if (existing.assetType() != assetType) {
            throw new ApiException(ErrorCode.CONFLICT, "资产类型不能修改");
        }
        if (!existing.assetIdentity().equals(required(request.assetIdentity(), "资产编码"))) {
            throw new ApiException(ErrorCode.CONFLICT, "资产编码不能原地修改，请创建新版本");
        }
        if (existing.status() != AssetVersionStatus.DRAFT) {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "已发布或撤回版本不可原地修改，请创建下一版本"
            );
        }
        String content = validator.validateAndCanonicalize(assetType, request.content());
        String actor = actor();
        String traceId = RequestContext.currentTraceId();
        AssetVersion saved = versions.updateDraft(new AssetVersionDraftUpdateCommand(
            tenantId,
            versionId,
            required(request.assetIdentity(), "资产编码"),
            existing.organizationScope(),
            applicableScope(request.applicableScope()),
            content,
            null,
            required(request.sourceRef(), "来源依据"),
            existing.safetyPolicy(),
            existing.overridePolicy(),
            actor,
            traceId,
            List.of()
        ));
        return response(saved, readJson(content));
    }

    @Transactional(readOnly = true)
    public DeclarativeAssetDetailResponse detail(String versionId) {
        String tenantId = currentTenant();
        AssetVersion version = ownedVersion(tenantId, versionId);
        maintainable(version.assetType());
        AssetVersionContent body = contents.findByTenantIdAndVersionId(tenantId, version.versionId())
            .orElseThrow(() -> new ApiException(
                ErrorCode.INTERNAL_ERROR,
                "声明式资产缺少可恢复正文：" + version.versionId()
            ));
        return response(version, readJson(body.contentJson()));
    }

    @Transactional(readOnly = true)
    public PageResponse<DeclarativeAssetSummaryResponse> list(
            VersionedAssetType assetType,
            PageRequest pageRequest) {
        String tenantId = currentTenant();
        VersionedAssetType type = maintainable(assetType);
        PageRequest page = pageRequest == null ? PageRequest.defaults() : pageRequest;
        List<DeclarativeAssetSummaryResponse> items =
            versionRepository.pageByTenantIdAndAssetType(
                    tenantId, type.name(), page.offset(), page.safeSize())
                .stream()
                .map(version -> new DeclarativeAssetSummaryResponse(
                    version.versionId(),
                    version.assetType(),
                    version.assetIdentity(),
                    version.versionNo(),
                    version.status(),
                    version.organizationScope(),
                    version.applicableScope(),
                    version.sourceRef(),
                    version.updatedAt()
                ))
                .toList();
        long total = versionRepository.countByTenantIdAndAssetType(tenantId, type.name());
        return PageResponse.of(items, page, total);
    }

    private AssetVersion ownedVersion(String tenantId, String versionId) {
        return versionRepository.findByVersionIdAndTenantId(
                required(versionId, "版本 ID"), tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "资产版本不存在：" + versionId));
    }

    private VersionedAssetType maintainable(VersionedAssetType type) {
        if (type == VersionedAssetType.PATHWAY) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "路径必须使用路径工作台维护，禁止登记第二份通用 JSON 正文"
            );
        }
        if (!MAINTAINABLE_TYPES.contains(type)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "该资产不属于独立配置资产工作台：" + type);
        }
        return type;
    }

    private DeclarativeAssetDetailResponse response(
            AssetVersion version,
            JsonNode content) {
        return new DeclarativeAssetDetailResponse(
            version.versionId(),
            version.assetType(),
            version.assetIdentity(),
            version.versionNo(),
            version.status(),
            version.organizationScope(),
            version.applicableScope(),
            version.sourceRef(),
            version.contentHash(),
            content,
            version.updatedAt(),
            RequestContext.currentTraceId()
        );
    }

    private JsonNode readJson(String value) {
        try {
            return json.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "声明式资产正文结构不合法", exception);
        }
    }

    private String currentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw new ApiException(ErrorCode.TENANT_CONTEXT_MISSING, "缺少租户上下文");
        }
        return scope.tenantId();
    }

    private String actor() {
        return RequestContext.currentUserId().orElse("system");
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "不能为空");
        }
        return value.trim();
    }

    private String applicableScope(String value) {
        return value == null || value.isBlank() ? "ALL" : value.trim();
    }
}
