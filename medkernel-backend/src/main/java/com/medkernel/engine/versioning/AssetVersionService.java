package com.medkernel.engine.versioning;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.ids.Ulid;

/**
 * 通用配置资产不可变版本服务。
 */
@Service
public class AssetVersionService implements VersionedAssetPort {

    private final AssetVersionRepository repository;
    private final AssetDependencyService dependencies;
    private final AssetVersionContentRepository contents;
    private final AssetIdentityService identities;
    private final AssetScopeResolver scopes;
    private final Clock clock;

    @Autowired
    public AssetVersionService(
            AssetVersionRepository repository,
            AssetDependencyService dependencies,
            AssetVersionContentRepository contents,
            AssetIdentityService identities,
            AssetScopeResolver scopes) {
        this(repository, dependencies, contents, identities, scopes, Clock.systemUTC());
    }

    AssetVersionService(
            AssetVersionRepository repository,
            AssetDependencyService dependencies,
            AssetVersionContentRepository contents,
            AssetIdentityService identities,
            AssetScopeResolver scopes,
            Clock clock) {
        this.repository = repository;
        this.dependencies = dependencies;
        this.contents = contents;
        this.identities = identities;
        this.scopes = scopes;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AssetVersion registerDraft(AssetVersionRegisterCommand command) {
        String tenantId = required(command.tenantId(), "租户 ID");
        VersionedAssetType assetType = required(command.assetType(), "资产类型");
        String assetIdentity = required(command.assetIdentity(), "资产身份");
        String organizationScope = resolveOrganizationScope(
            tenantId,
            command.organizationScope());
        String applicableScope = ApplicableScopeMatcher.validateDeclaration(
            required(command.applicableScope(), "适用人群或上下文"));
        String createdBy = required(command.createdBy(), "创建人");
        requireRecoverableContent(assetType, command.content());
        String contentHash = VersionContentHash.resolve(command.content(), command.contentHash());
        AssetVersionAllocation allocation = identities.allocateNextVersion(
            tenantId,
            assetType,
            assetIdentity,
            createdBy,
            command.traceId()
        );
        String versionNo = allocation.versionNo();

        repository.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            tenantId, assetType, assetIdentity, versionNo
        ).ifPresent(existing -> {
            throw new ApiException(ErrorCode.CONFLICT, "同一资产版本号已存在，禁止覆盖旧版本");
        });

        String versionId = "av-" + Ulid.newUlid();
        Instant now = Instant.now(clock);
        AssetVersion version = new AssetVersion(
            null,
            versionId,
            tenantId,
            assetType,
            assetIdentity,
            versionNo,
            organizationScope,
            applicableScope,
            contentHash,
            command.safetyPolicy() == null ? AssetVersionSafetyPolicy.NORMAL : command.safetyPolicy(),
            command.overridePolicy() == null ? AssetVersionOverridePolicy.FREE : command.overridePolicy(),
            AssetVersionStatus.DRAFT,
            draftScopeKey(versionId),
            blankToNull(command.sourceRef()),
            null,
            null,
            now,
            createdBy,
            now,
            createdBy,
            blankToNull(command.traceId())
        );
        AssetVersion saved = repository.save(version);
        saveContent(saved, command.content(), createdBy, command.traceId(), now);
        registerDependencies(saved, command.dependencies(), createdBy, command.traceId());
        return saved;
    }

    @Override
    @Transactional
    public AssetVersion updateDraft(AssetVersionDraftUpdateCommand command) {
        AssetVersion version = findOwnedVersion(command.tenantId(), command.versionId());
        if (version.status() == AssetVersionStatus.PUBLISHED
                || version.status() == AssetVersionStatus.WITHDRAWN) {
            return registerDraft(new AssetVersionRegisterCommand(
                version.tenantId(),
                version.assetType(),
                required(command.assetIdentity(), "资产身份"),
                command.organizationScope(),
                command.applicableScope(),
                command.content(),
                command.contentHash(),
                command.sourceRef(),
                required(command.actor(), "操作人"),
                command.traceId(),
                command.safetyPolicy(),
                command.overridePolicy(),
                command.dependencies()
            ));
        }
        if (version.status() != AssetVersionStatus.DRAFT) {
            throw new ApiException(ErrorCode.CONFLICT, "只有草稿版本可以原地修改");
        }
        requireRecoverableContent(version.assetType(), command.content());
        String assetIdentity = required(command.assetIdentity(), "资产身份");
        repository.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            version.tenantId(), version.assetType(), assetIdentity, version.versionNo()
        ).filter(existing -> !existing.versionId().equals(version.versionId()))
            .ifPresent(existing -> {
                throw new ApiException(ErrorCode.CONFLICT, "同一资产版本号已存在，禁止覆盖旧版本");
            });
        String organizationScope = resolveOrganizationScope(
            version.tenantId(),
            command.organizationScope());
        AssetVersion saved = repository.save(version.withDraftRegistration(
            assetIdentity,
            organizationScope,
            ApplicableScopeMatcher.validateDeclaration(
                required(command.applicableScope(), "适用人群或上下文")),
            VersionContentHash.resolve(command.content(), command.contentHash()),
            blankToNull(command.sourceRef()),
            command.safetyPolicy() == null ? AssetVersionSafetyPolicy.NORMAL : command.safetyPolicy(),
            command.overridePolicy() == null ? AssetVersionOverridePolicy.FREE : command.overridePolicy(),
            Instant.now(clock),
            required(command.actor(), "操作人")
        ));
        saveContent(
            saved,
            command.content(),
            required(command.actor(), "操作人"),
            command.traceId(),
            Instant.now(clock));
        registerDependencies(saved, command.dependencies(), command.actor(), command.traceId());
        return saved;
    }

    private void saveContent(
            AssetVersion version,
            String content,
            String actor,
            String traceId,
            Instant now) {
        if (content == null || content.isBlank() || contents == null) {
            return;
        }
        AssetVersionContent existing = contents
            .findByTenantIdAndVersionId(version.tenantId(), version.versionId())
            .orElse(null);
        contents.save(new AssetVersionContent(
            existing == null ? null : existing.id(),
            version.versionId(),
            version.tenantId(),
            content,
            version.contentHash(),
            existing == null ? now : existing.createdAt(),
            existing == null ? actor : existing.createdBy(),
            now,
            actor,
            blankToNull(traceId)
        ));
    }

    private void requireRecoverableContent(VersionedAssetType assetType, String content) {
        if (assetType.usesUnifiedContentStore() && (content == null || content.isBlank())) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                assetType + " 资产正文不能为空，禁止登记只有哈希的不可恢复版本");
        }
        if (assetType.usesUnifiedContentStore() && contents == null) {
            throw new ApiException(
                ErrorCode.INTERNAL_ERROR,
                assetType + " 资产正文仓库未配置，禁止登记不可恢复版本");
        }
    }

    private AssetVersion findOwnedVersion(String tenantId, String versionId) {
        return repository.findByVersionIdAndTenantId(
                required(versionId, "版本 ID"),
                required(tenantId, "租户 ID")
            )
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "资产版本不存在: " + versionId));
    }

    private static String draftScopeKey(String versionId) {
        return "version:" + versionId;
    }

    private void registerDependencies(
            AssetVersion version,
            java.util.List<AssetDependencyDeclaration> declarations,
            String actor,
            String traceId) {
        if (dependencies != null) {
            dependencies.registerDependencies(version, declarations, actor, traceId);
        }
    }

    private String resolveOrganizationScope(String tenantId, String requestedPath) {
        if (requestedPath == null || requestedPath.isBlank()) {
            return scopes.resolve(tenantId, RequestContext.currentOrgScope())
                .organizationPath();
        }
        return scopes.resolveOrganizationPath(tenantId, requestedPath)
            .organizationPath();
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
