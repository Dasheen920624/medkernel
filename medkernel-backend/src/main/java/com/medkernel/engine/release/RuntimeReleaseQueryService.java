package com.medkernel.engine.release;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItemRepository;
import com.medkernel.engine.context.ClinicalRuntimeReleaseRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;

/**
 * 平台标准版本和机构生效版本只读查询服务。
 */
@Service
public class RuntimeReleaseQueryService {

    private final PlatformBaselineReleaseRepository baselines;
    private final PlatformBaselineItemRepository baselineItems;
    private final ClinicalRuntimeReleaseRepository runtimes;
    private final ClinicalRuntimeReleaseItemRepository runtimeItems;

    public RuntimeReleaseQueryService(
            PlatformBaselineReleaseRepository baselines,
            PlatformBaselineItemRepository baselineItems,
            ClinicalRuntimeReleaseRepository runtimes,
            ClinicalRuntimeReleaseItemRepository runtimeItems) {
        this.baselines = baselines;
        this.baselineItems = baselineItems;
        this.runtimes = runtimes;
        this.runtimeItems = runtimeItems;
    }

    /**
     * 返回当前完整平台标准版本。
     */
    @Transactional(readOnly = true)
    public Optional<PlatformBaselineDetailResponse> currentPlatformBaseline() {
        return baselines.findFirstByOrderByRevisionNoDesc().map(release -> new PlatformBaselineDetailResponse(
            release,
            baselineItems.findByBaselineReleaseIdOrderByAssetTypeAscAssetIdentityAsc(
                release.baselineReleaseId())
        ));
    }

    /**
     * 返回指定医院当前完整机构生效版本。
     */
    @Transactional(readOnly = true)
    public Optional<ClinicalRuntimeReleaseDetailResponse> currentHospitalRuntime(
            String tenantId,
            String hospitalId) {
        return runtimes.findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(
                required(tenantId, "租户"),
                required(hospitalId, "医院"))
            .map(release -> new ClinicalRuntimeReleaseDetailResponse(
            release,
            runtimeItems.findByReleaseIdOrderByAssetTypeAscAssetIdentityAsc(
                release.releaseId())
            ));
    }

    /**
     * 分页返回指定医院的不可变机构生效版本历史。
     */
    @Transactional(readOnly = true)
    public PageResponse<ClinicalRuntimeRelease> hospitalRuntimeHistory(
            String tenantId,
            String hospitalId,
            PageRequest pageRequest) {
        String normalizedTenant = required(tenantId, "租户");
        String normalizedHospital = required(hospitalId, "医院");
        PageRequest page = pageRequest == null ? PageRequest.defaults() : pageRequest;
        return PageResponse.of(
            runtimes.pageByTenantIdAndHospitalId(
                normalizedTenant,
                normalizedHospital,
                page.offset(),
                page.safeSize()),
            page,
            runtimes.countByTenantIdAndHospitalId(
                normalizedTenant, normalizedHospital)
        );
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, label + "不能为空");
        }
        return value.trim();
    }
}
