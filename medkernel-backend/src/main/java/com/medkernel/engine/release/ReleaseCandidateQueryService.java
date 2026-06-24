package com.medkernel.engine.release;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.org.OrgFacilityType;
import com.medkernel.engine.org.OrgHierarchyRepository;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgLevel;
import com.medkernel.shared.context.PlatformTenant;

/**
 * 平台标准版本和机构生效版本的可选择资产查询。
 *
 * <p>平台只看到平台草稿；医院只看到真实组织祖先范围内的本地草稿或正式版本。
 * 平台标准版本资产由当前基线清单提供，不在本地候选中重复返回。
 */
@Service
public class ReleaseCandidateQueryService {

    private final AssetVersionRepository versions;
    private final OrgUnitRepository organizations;
    private final OrgHierarchyRepository hierarchy;

    public ReleaseCandidateQueryService(
            AssetVersionRepository versions,
            OrgUnitRepository organizations,
            OrgHierarchyRepository hierarchy) {
        this.versions = versions;
        this.organizations = organizations;
        this.hierarchy = hierarchy;
    }

    /**
     * 查询可进入下一平台标准版本的草稿资产版本。
     */
    @Transactional(readOnly = true)
    public PageResponse<ReleaseCandidateAsset> platformCandidates(
            VersionedAssetType assetType,
            String keyword,
            PageRequest pageRequest) {
        requireRuntimeTypeOrNull(assetType);
        PageRequest page = pageRequest == null ? PageRequest.defaults() : pageRequest;
        String normalizedKeyword = blankToNull(keyword);
        List<ReleaseCandidateAsset> items = versions.pagePlatformReleaseCandidates(
                PlatformTenant.ID,
                assetType,
                normalizedKeyword,
                page.offset(),
                page.safeSize())
            .stream()
            .filter(version -> version.assetType().isRuntimeConfiguration())
            .map(version -> candidate(version, ReleaseSourceLayer.PLATFORM))
            .toList();
        long total = versions.countPlatformReleaseCandidates(
            PlatformTenant.ID, assetType, normalizedKeyword);
        return PageResponse.of(items, page, total);
    }

    /**
     * 查询指定医院可启用的集团或医院本地资产版本。
     */
    @Transactional(readOnly = true)
    public PageResponse<ReleaseCandidateAsset> hospitalCandidates(
            String tenantId,
            String hospitalId,
            VersionedAssetType assetType,
            String keyword,
            PageRequest pageRequest) {
        String normalizedTenant = required(tenantId, "租户");
        String normalizedHospital = required(hospitalId, "医院");
        requireRuntimeTypeOrNull(assetType);
        OrgUnit hospital = organizations
            .findByTenantIdAndId(normalizedTenant, normalizedHospital)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "医院不存在"));
        if (hospital.level() != OrgLevel.FACILITY
                || hospital.facilityType() != OrgFacilityType.HOSPITAL
                || !hospital.isActive()) {
            throw validation("机构生效版本目标必须是启用的医院");
        }
        List<String> scopes = hierarchy
            .findAncestorsAndSelf(normalizedTenant, normalizedHospital)
            .stream()
            .filter(OrgUnit::isActive)
            .filter(unit -> unit.level() == OrgLevel.TENANT
                || unit.level() == OrgLevel.REGION
                || unit.level() == OrgLevel.FACILITY)
            .map(OrgUnit::orgPath)
            .map(ReleaseCandidateQueryService::blankToNull)
            .filter(value -> value != null)
            .distinct()
            .toList();
        if (scopes.isEmpty() || !scopes.contains(hospital.orgPath())) {
            throw new ApiException(
                ErrorCode.CONFLICT, "医院组织路径不完整，不能选择运行资产");
        }
        PageRequest page = pageRequest == null ? PageRequest.defaults() : pageRequest;
        String normalizedKeyword = blankToNull(keyword);
        List<ReleaseCandidateAsset> items = versions.pageHospitalReleaseCandidates(
                normalizedTenant,
                scopes,
                assetType,
                normalizedKeyword,
                page.offset(),
                page.safeSize())
            .stream()
            .filter(version -> version.assetType().isRuntimeConfiguration())
            .map(version -> candidate(
                version,
                hospital.orgPath().equals(version.organizationScope())
                    ? ReleaseSourceLayer.HOSPITAL
                    : ReleaseSourceLayer.GROUP))
            .toList();
        long total = versions.countHospitalReleaseCandidates(
            normalizedTenant, scopes, assetType, normalizedKeyword);
        return PageResponse.of(items, page, total);
    }

    private static ReleaseCandidateAsset candidate(
            AssetVersion version,
            ReleaseSourceLayer sourceLayer) {
        return new ReleaseCandidateAsset(
            sourceLayer,
            version.assetType(),
            version.assetIdentity(),
            version.versionId(),
            version.versionNo(),
            version.status(),
            version.organizationScope(),
            version.contentHash(),
            version.sourceRef(),
            version.updatedAt()
        );
    }

    private static void requireRuntimeTypeOrNull(VersionedAssetType assetType) {
        if (assetType != null && !assetType.isRuntimeConfiguration()) {
            throw validation("只能查询正式运行配置资产");
        }
    }

    private static String required(String value, String label) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw validation(label + "不能为空");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ApiException validation(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }
}
