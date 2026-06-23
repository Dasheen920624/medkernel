package com.medkernel.engine.versioning;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 稳定资产身份与自动版本序列服务。
 *
 * <p>所有知识、术语、规则、路径及其他运行配置资产共享该分配器，调用方不得手填版本号。
 */
@Service
public class AssetIdentityService {

    private final AssetIdentityRepository identities;
    private final Clock clock;

    @Autowired
    public AssetIdentityService(AssetIdentityRepository identities) {
        this(identities, Clock.systemUTC());
    }

    AssetIdentityService(AssetIdentityRepository identities, Clock clock) {
        this.identities = identities;
        this.clock = clock;
    }

    /**
     * 为稳定身份单调分配下一个 Vn。
     */
    @Transactional
    public AssetVersionAllocation allocateNextVersion(
            String tenantId,
            VersionedAssetType assetType,
            String assetIdentity,
            String actor,
            String traceId) {
        String normalizedTenantId = required(tenantId, "租户 ID");
        VersionedAssetType normalizedAssetType = required(assetType, "资产类型");
        String normalizedIdentity = required(assetIdentity, "资产身份");
        String normalizedActor = required(actor, "操作人");
        Instant now = clock.instant();

        AssetIdentity current = identities
            .findByTenantIdAndAssetTypeAndAssetIdentity(
                normalizedTenantId, normalizedAssetType, normalizedIdentity)
            .orElse(null);
        long nextSequence;
        AssetIdentity updated;
        if (current == null) {
            nextSequence = 1L;
            updated = new AssetIdentity(
                null,
                normalizedTenantId,
                normalizedAssetType,
                normalizedIdentity,
                AssetIdentityStatus.ACTIVE,
                nextSequence,
                now,
                normalizedActor,
                now,
                normalizedActor,
                blankToNull(traceId)
            );
        } else {
            if (current.status() == AssetIdentityStatus.RETIRED) {
                throw new ApiException(ErrorCode.CONFLICT, "资产身份已退役，不能继续创建新版本");
            }
            nextSequence = current.latestVersionSequence() + 1L;
            updated = new AssetIdentity(
                current.id(),
                current.tenantId(),
                current.assetType(),
                current.assetIdentity(),
                current.status(),
                nextSequence,
                current.createdAt(),
                current.createdBy(),
                now,
                normalizedActor,
                blankToNull(traceId)
            );
        }
        identities.save(updated);
        return new AssetVersionAllocation(nextSequence, "V" + nextSequence);
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
