package com.medkernel.engine.versioning;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.medkernel.engine.context.ClinicalRuntimeReleaseContent;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContentResolver;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItem;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 从医院运行发布解析声明式资产的精确版本正文。
 */
@Component
public class ClinicalRuntimeDeclarativeAssetResolver implements DeclarativeAssetRuntimePort {

    private final ClinicalRuntimeReleaseContentResolver runtime;
    private final AssetVersionRepository versions;
    private final AssetVersionContentRepository contents;

    public ClinicalRuntimeDeclarativeAssetResolver(
            ClinicalRuntimeReleaseContentResolver runtime,
            AssetVersionRepository versions,
            AssetVersionContentRepository contents) {
        this.runtime = runtime;
        this.versions = versions;
        this.contents = contents;
    }

    @Override
    public Optional<ResolvedDeclarativeAsset> resolve(
            String tenantId,
            String runtimeReleaseId,
            VersionedAssetType assetType,
            String assetIdentity) {
        String tenant = required(tenantId, "租户");
        String releaseId = required(runtimeReleaseId, "医院运行发布 ID");
        String identity = required(assetIdentity, "资产编码");
        if (assetType == null || !assetType.usesUnifiedContentStore()) {
            throw new ApiException(
                ErrorCode.ENG_ASSET_002,
                "运行解析器不支持该声明式资产类型：" + assetType
            );
        }
        ClinicalRuntimeReleaseContent release = runtime.resolve(tenant, releaseId);
        List<ClinicalRuntimeReleaseItem> candidates = release.items().stream()
            .filter(item -> item.entryState() == ReleaseEntryState.ACTIVE)
            .filter(item -> item.assetType() == assetType)
            .filter(item -> identity.equals(item.assetIdentity()))
            .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (candidates.size() != 1) {
            throw new ApiException(
                ErrorCode.ENG_ASSET_002,
                "同一医院运行发布中的声明式资产只能绑定一次："
                    + assetType + ":" + identity + "@" + releaseId
            );
        }
        ClinicalRuntimeReleaseItem selected = candidates.getFirst();
        AssetVersion resolved = versions
            .findByVersionIdAndTenantId(selected.versionId(), selected.sourceTenantId())
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_ASSET_002,
                "声明式资产版本不存在："
                    + selected.assetType() + ":" + selected.assetIdentity() + "@" + selected.versionNo()
            ));
        if (resolved.status() != AssetVersionStatus.PUBLISHED
                || resolved.assetType() != selected.assetType()
                || !resolved.assetIdentity().equals(selected.assetIdentity())
                || !resolved.versionNo().equals(selected.versionNo())
                || !resolved.contentHash().equals(selected.contentHash())) {
            throw new ApiException(
                ErrorCode.ENG_ASSET_002,
                "运行声明式资产版本与清单不一致："
                    + assetType + ":" + identity + "@" + selected.versionNo()
            );
        }
        AssetVersionContent body = contents
            .findByTenantIdAndVersionId(selected.sourceTenantId(), resolved.versionId())
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_ASSET_002,
                "运行声明式资产缺少可恢复正文："
                    + assetType + ":" + identity + "@" + selected.versionNo()
            ));
        String actualHash = sha256(body.contentJson());
        if (!resolved.contentHash().equals(body.contentHash())
                || !resolved.contentHash().equals(actualHash)) {
            throw new ApiException(
                ErrorCode.ENG_ASSET_002,
                "运行声明式资产正文摘要不一致："
                    + assetType + ":" + identity + "@" + selected.versionNo()
            );
        }
        return Optional.of(new ResolvedDeclarativeAsset(
            assetType,
            identity,
            selected.versionNo(),
            releaseId,
            body.contentJson(),
            body.contentHash()
        ));
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.ENG_ASSET_002, label + "不能为空");
        }
        return value.trim();
    }

    private String sha256(String content) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

}
