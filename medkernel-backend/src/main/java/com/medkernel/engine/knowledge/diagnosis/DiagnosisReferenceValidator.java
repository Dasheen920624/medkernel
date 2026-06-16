package com.medkernel.engine.knowledge.diagnosis;

import java.util.List;

import com.medkernel.engine.knowledge.Citation;
import com.medkernel.engine.knowledge.CitationRepository;
import com.medkernel.engine.knowledge.KnowledgeDomain;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityService;
import com.medkernel.engine.terminology.StandardTermRepository;
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

    private static final List<String> FINDING_DICTIONARIES = List.of(
        "TERM.DIAGNOSIS",
        "TERM.LAB",
        "TERM.DRUG",
        "TERM.PROCEDURE");

    private final KnowledgeIdentityService identities;
    private final AssetVersionRepository assetVersions;
    private final StandardTermRepository standardTerms;
    private final CitationRepository citations;

    public DiagnosisReferenceValidator(
            KnowledgeIdentityService identities,
            AssetVersionRepository assetVersions,
            StandardTermRepository standardTerms,
            CitationRepository citations) {
        this.identities = identities;
        this.assetVersions = assetVersions;
        this.standardTerms = standardTerms;
        this.citations = citations;
    }

    public void validateCriterion(KnowledgeAssetVersion diagnosisVersion, String findingTermCode, Long citationId) {
        String tenantId = currentTenant();
        validateFindingTerm(tenantId, findingTermCode);
        if (citationId != null) {
            validateCitation(tenantId, diagnosisVersion, citationId);
        }
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

    private void validateFindingTerm(String tenantId, String rawFindingTermCode) {
        String findingTermCode = rawFindingTermCode == null ? "" : rawFindingTermCode.trim();
        if (findingTermCode.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "诊断标准发现项必须填写 TERM-01 标准术语编码");
        }
        List<String> tenantIds = standardTermSources(tenantId);
        for (String dictionary : FINDING_DICTIONARIES) {
            if (standardTerms.findFirstActiveByTenantIdsAndStandardSystemAndTermCode(
                    tenantIds, tenantId, dictionary, findingTermCode).isPresent()) {
                return;
            }
        }
        throw new ApiException(
            ErrorCode.VALIDATION_FAILED,
            "诊断标准发现项 " + findingTermCode + " 未绑定可运行的 TERM-01 标准术语");
    }

    private void validateCitation(String tenantId, KnowledgeAssetVersion diagnosisVersion, Long citationId) {
        Citation citation = citations.findByTenantIdAndId(tenantId, citationId)
            .orElseThrow(() -> new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "诊断标准证据引用不存在 id=" + citationId));
        if (!diagnosisVersion.id().equals(citation.assetVersionId())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "诊断标准证据引用不属于当前诊断版本");
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
            tenantId, assetType, targetRef, AssetVersionStatus.PUBLISHED);
        return active != null && !active.isEmpty();
    }

    private static List<String> standardTermSources(String tenantId) {
        if (PlatformTenant.isPlatformTenant(tenantId)) {
            return List.of(PlatformTenant.ID);
        }
        return List.of(PlatformTenant.ID, tenantId);
    }

    private String currentTenant() {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw ApiException.tenantMissing();
        }
        return tenantId;
    }
}
