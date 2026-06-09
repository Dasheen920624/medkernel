package com.medkernel.engine.authoring;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.PlatformAuthority;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.hash.Sha256ContentHash;
import com.medkernel.shared.ids.Ulid;

/**
 * 将条件片段权威记录同步为统一版本资产。
 *
 * <p>条件片段是规则与路径复用的声明型子资产，不单独执行临床发布；其 ACTIVE 状态表示
 * 已可被同包规则和路径解析，因此在统一版本底座中对应 PUBLISHED。
 */
@Service
public class ConditionFragmentAssetVersionProjector {

    private final AssetVersionRepository versions;

    public ConditionFragmentAssetVersionProjector(AssetVersionRepository versions) {
        this.versions = versions;
    }

    public AssetVersion project(ConditionFragment fragment) {
        if (fragment == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "条件片段不能为空");
        }
        String tenantId = required(fragment.tenantId(), "租户");
        String fragmentId = required(fragment.fragmentId(), "条件片段 ID");
        String versionNo = Integer.toString(fragment.versionNo());
        String organizationScope = PlatformTenant.isPlatformTenant(tenantId)
            ? PlatformAuthority.PLATFORM_ORG_PATH
            : "tenant:" + tenantId;
        String applicableScope = required(fragment.packageVersion(), "条件片段包版本");
        String contentHash = Sha256ContentHash.sha256(
            required(fragment.bodyJson(), "条件片段正文"),
            "条件片段正文不能为空");
        String sourceRef = "condition-fragment:" + required(fragment.fragmentCode(), "条件片段编码");
        AssetVersionStatus targetStatus = targetStatus(fragment.status());
        Instant now = Instant.now();
        String actor = required(fragment.updatedBy(), "更新人");

        return versions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
                tenantId, VersionedAssetType.CONDITION_FRAGMENT, fragmentId, versionNo)
            .map(existing -> updateExisting(
                existing, organizationScope, applicableScope, contentHash, sourceRef,
                targetStatus, now, actor))
            .orElseGet(() -> createVersion(
                fragment, tenantId, fragmentId, versionNo, organizationScope,
                applicableScope, contentHash, sourceRef, targetStatus, now, actor));
    }

    private AssetVersion createVersion(
            ConditionFragment fragment,
            String tenantId,
            String fragmentId,
            String versionNo,
            String organizationScope,
            String applicableScope,
            String contentHash,
            String sourceRef,
            AssetVersionStatus targetStatus,
            Instant now,
            String actor) {
        String versionId = "av-" + Ulid.newUlid();
        return versions.save(new AssetVersion(
            null,
            versionId,
            tenantId,
            VersionedAssetType.CONDITION_FRAGMENT,
            fragmentId,
            versionNo,
            organizationScope,
            applicableScope,
            contentHash,
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            targetStatus,
            scopeKey(fragmentId, organizationScope, applicableScope, targetStatus, versionId),
            sourceRef,
            targetStatus == AssetVersionStatus.PUBLISHED ? now : null,
            targetStatus == AssetVersionStatus.RETIRED ? now : null,
            fragment.createdAt(),
            fragment.createdBy(),
            now,
            actor,
            fragment.traceId()
        ));
    }

    private AssetVersion updateExisting(
            AssetVersion existing,
            String organizationScope,
            String applicableScope,
            String contentHash,
            String sourceRef,
            AssetVersionStatus targetStatus,
            Instant now,
            String actor) {
        boolean sameContent = existing.organizationScope().equals(organizationScope)
            && existing.applicableScope().equals(applicableScope)
            && existing.contentHash().equals(contentHash)
            && java.util.Objects.equals(existing.sourceRef(), sourceRef);
        if (existing.status() == AssetVersionStatus.PUBLISHED) {
            if (!sameContent) {
                throw new ApiException(ErrorCode.CONFLICT, "已激活条件片段版本不可原地修改，请新建更高版本");
            }
            if (targetStatus == AssetVersionStatus.PUBLISHED) {
                return existing;
            }
            if (targetStatus == AssetVersionStatus.RETIRED) {
                return versions.save(existing.withStatusAndWindow(
                    AssetVersionStatus.RETIRED,
                    "version:" + existing.versionId(),
                    existing.effectiveFrom(),
                    now,
                    now,
                    actor
                ));
            }
            throw new ApiException(ErrorCode.CONFLICT, "已激活条件片段不能退回草稿");
        }
        if (existing.status() == AssetVersionStatus.RETIRED) {
            if (targetStatus == AssetVersionStatus.RETIRED && sameContent) {
                return existing;
            }
            throw new ApiException(ErrorCode.CONFLICT, "已退役条件片段版本不可修改或重新激活");
        }
        if (existing.status() != AssetVersionStatus.DRAFT) {
            throw new ApiException(ErrorCode.CONFLICT, "条件片段统一版本处于不可编辑状态: " + existing.status());
        }

        AssetVersion updated = existing.withDraftRegistration(
            existing.assetIdentity(),
            organizationScope,
            applicableScope,
            contentHash,
            sourceRef,
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            now,
            actor
        );
        if (targetStatus == AssetVersionStatus.DRAFT) {
            return versions.save(updated);
        }
        return versions.save(updated.withStatusAndWindow(
            targetStatus,
            scopeKey(
                existing.assetIdentity(), organizationScope, applicableScope,
                targetStatus, existing.versionId()),
            targetStatus == AssetVersionStatus.PUBLISHED ? now : null,
            targetStatus == AssetVersionStatus.RETIRED ? now : null,
            now,
            actor
        ));
    }

    private AssetVersionStatus targetStatus(ConditionFragmentStatus status) {
        if (status == null || status == ConditionFragmentStatus.DRAFT) {
            return AssetVersionStatus.DRAFT;
        }
        if (status == ConditionFragmentStatus.ACTIVE) {
            return AssetVersionStatus.PUBLISHED;
        }
        return AssetVersionStatus.RETIRED;
    }

    private String scopeKey(
            String fragmentId,
            String organizationScope,
            String applicableScope,
            AssetVersionStatus status,
            String versionId) {
        if (status == AssetVersionStatus.PUBLISHED) {
            return fragmentId + "|" + organizationScope + "|" + applicableScope;
        }
        return "version:" + (versionId == null ? fragmentId : versionId);
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "不能为空");
        }
        return value.trim();
    }
}
