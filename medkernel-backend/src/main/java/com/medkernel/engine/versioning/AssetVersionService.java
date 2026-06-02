package com.medkernel.engine.versioning;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.ids.Ulid;

/**
 * 通用配置资产不可变版本服务。
 */
@Service
public class AssetVersionService implements VersionedAssetPort {

    private final AssetVersionRepository repository;
    private final Clock clock;

    @Autowired
    public AssetVersionService(AssetVersionRepository repository) {
        this(repository, Clock.systemUTC());
    }

    AssetVersionService(AssetVersionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AssetVersion registerDraft(AssetVersionRegisterCommand command) {
        String tenantId = required(command.tenantId(), "租户 ID");
        VersionedAssetType assetType = required(command.assetType(), "资产类型");
        String assetIdentity = required(command.assetIdentity(), "资产身份");
        String versionNo = required(command.versionNo(), "版本号");
        String organizationScope = required(command.organizationScope(), "组织生效域");
        String applicableScope = required(command.applicableScope(), "适用人群或上下文");
        String createdBy = required(command.createdBy(), "创建人");

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
            VersionContentHash.resolve(command.content(), command.contentHash()),
            command.safetyPolicy() == null ? AssetVersionSafetyPolicy.NORMAL : command.safetyPolicy(),
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
        return repository.save(version);
    }

    @Override
    @Transactional
    public AssetVersion updateDraftContent(
            String tenantId,
            String versionId,
            String content,
            String contentHash,
            String actor) {
        AssetVersion version = findOwnedVersion(tenantId, versionId);
        if (version.status() != AssetVersionStatus.DRAFT && version.status() != AssetVersionStatus.PENDING_REVIEW) {
            throw new ApiException(ErrorCode.CONFLICT, "已发布版本不可原地修改，必须登记新版本");
        }
        return repository.save(version.withContentHash(
            VersionContentHash.resolve(content, contentHash),
            Instant.now(clock),
            required(actor, "操作人")
        ));
    }

    @Override
    @Transactional
    public AssetVersion publish(String tenantId, String versionId, String actor) {
        AssetVersion version = findOwnedVersion(tenantId, versionId);
        if (version.status() == AssetVersionStatus.PUBLISHED || version.status() == AssetVersionStatus.ACTIVE) {
            return version;
        }
        if (version.status() != AssetVersionStatus.DRAFT && version.status() != AssetVersionStatus.PENDING_REVIEW) {
            throw new ApiException(ErrorCode.CONFLICT, "当前版本状态不允许发布");
        }
        return repository.save(version.withStatus(
            AssetVersionStatus.PUBLISHED,
            draftScopeKey(version.versionId()),
            Instant.now(clock),
            required(actor, "操作人")
        ));
    }

    @Override
    @Transactional
    public AssetVersion activate(String tenantId, String versionId, String actor) {
        AssetVersion version = findOwnedVersion(tenantId, versionId);
        if (version.status() == AssetVersionStatus.ACTIVE) {
            return version;
        }
        if (version.status() != AssetVersionStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.CONFLICT, "只有已发布版本可以激活为 ACTIVE");
        }

        String activeScopeKey = activeScopeKey(version);
        boolean hasOtherActive = repository.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
                version.tenantId(), version.assetType(), activeScopeKey, AssetVersionStatus.ACTIVE)
            .stream()
            .anyMatch(active -> !active.versionId().equals(version.versionId()));
        if (hasOtherActive) {
            throw new ApiException(ErrorCode.CONFLICT, "同一资产生效域已存在 ACTIVE 版本，禁止双活");
        }

        return repository.save(version.withStatus(
            AssetVersionStatus.ACTIVE,
            activeScopeKey,
            Instant.now(clock),
            required(actor, "操作人")
        ));
    }

    static String activeScopeKey(AssetVersion version) {
        return String.join("|",
            required(version.assetIdentity(), "资产身份"),
            required(version.organizationScope(), "组织生效域"),
            required(version.applicableScope(), "适用人群或上下文")
        );
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
