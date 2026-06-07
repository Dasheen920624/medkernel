package com.medkernel.engine.knowledge.diagnosis;

import java.util.List;

import com.medkernel.engine.knowledge.KnowledgeDomain;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityService;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;

import org.springframework.stereotype.Service;

/**
 * 诊断知识引用校验器：鉴别诊断必须指向其他有效诊断，诊疗建议只允许指向当前可运行资产。
 */
@Service
public class DiagnosisReferenceValidator {

    private final KnowledgeIdentityService identities;
    private final AssetVersionRepository assetVersions;

    public DiagnosisReferenceValidator(
            KnowledgeIdentityService identities,
            AssetVersionRepository assetVersions) {
        this.identities = identities;
        this.assetVersions = assetVersions;
    }

    public void validateDifferential(Long sourceIdentityId, Long targetIdentityId) {
        if (sourceIdentityId.equals(targetIdentityId)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "鉴别诊断不能指向自身");
        }
        KnowledgeIdentity target = identities.get(targetIdentityId);
        if (target.domain() != KnowledgeDomain.DIAGNOSIS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "鉴别诊断目标必须是诊断知识身份");
        }
        try {
            identities.getActiveVersion(targetIdentityId);
        } catch (ApiException error) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "鉴别诊断目标当前没有生效版本");
        }
    }

    public void validateCareTarget(DiagnosisCareTargetType targetType, String rawTargetRef) {
        String targetRef = rawTargetRef.trim();
        if (targetType == DiagnosisCareTargetType.KNOWLEDGE) {
            validateActiveKnowledge(targetRef);
            return;
        }
        VersionedAssetType assetType = targetType == DiagnosisCareTargetType.RULE
            ? VersionedAssetType.RULE
            : VersionedAssetType.PATHWAY;
        if (!hasActiveAsset(currentTenant(), assetType, targetRef)
                && !hasActiveAsset(PlatformTenant.ID, assetType, targetRef)) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "诊疗建议目标 " + targetType + ":" + targetRef + " 当前没有生效版本");
        }
    }

    private void validateActiveKnowledge(String targetRef) {
        try {
            KnowledgeIdentity target = identities.getByCode(targetRef);
            identities.getActiveVersion(target.id());
        } catch (ApiException error) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "诊疗建议目标 KNOWLEDGE:" + targetRef + " 当前没有生效版本");
        }
    }

    private boolean hasActiveAsset(String tenantId, VersionedAssetType assetType, String targetRef) {
        if (PlatformTenant.isPlatformTenant(currentTenant()) && !PlatformTenant.ID.equals(tenantId)) {
            return false;
        }
        List<AssetVersion> active = assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
            tenantId, assetType, targetRef, AssetVersionStatus.ACTIVE);
        return active != null && !active.isEmpty();
    }

    private String currentTenant() {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw ApiException.tenantMissing();
        }
        return tenantId;
    }
}
