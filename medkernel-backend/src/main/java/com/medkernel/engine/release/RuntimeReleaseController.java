package com.medkernel.engine.release;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseCommand;
import com.medkernel.engine.context.ClinicalRuntimeReleaseService;
import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.datascope.DataScope;
import com.medkernel.engine.versioning.VersionedAssetType;

import jakarta.validation.Valid;

/**
 * 平台标准版本与机构生效版本的唯一发布入口。
 *
 * <p>平台只能生成 A 基线，机构只能生成或回滚本医院 H 修订；领域和页面筛选不会进入运行合同。
 */
@RestController
@RequestMapping("/api/v1/engine/releases")
@DataScope(requireTenant = true)
public class RuntimeReleaseController {

    private final PlatformBaselineService baselines;
    private final ClinicalRuntimeReleaseService runtimes;
    private final RuntimeReleaseQueryService queries;
    private final ReleaseCandidateQueryService candidates;
    private final RuntimeReleaseOfflineDeliveryService offlineDelivery;

    public RuntimeReleaseController(
            PlatformBaselineService baselines,
            ClinicalRuntimeReleaseService runtimes,
            RuntimeReleaseQueryService queries,
            ReleaseCandidateQueryService candidates,
            RuntimeReleaseOfflineDeliveryService offlineDelivery) {
        this.baselines = baselines;
        this.runtimes = runtimes;
        this.queries = queries;
        this.candidates = candidates;
        this.offlineDelivery = offlineDelivery;
    }

    /**
     * 查询当前完整平台标准版本。
     */
    @GetMapping("/platform-baselines/current")
    @PreAuthorize("@perm.has('asset.read')")
    public ApiResult<PlatformBaselineDetailResponse> currentPlatformBaseline() {
        return ApiResult.ok(queries.currentPlatformBaseline().orElse(null));
    }

    /**
     * 分页查询可进入下一平台标准版本的草稿或已发布资产。
     */
    @GetMapping("/platform-baselines/candidates")
    @PreAuthorize("@perm.has('platform.publish')")
    public ApiResult<PageResponse<ReleaseCandidateAsset>> platformReleaseCandidates(
            @RequestParam(required = false) VersionedAssetType assetType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        requirePlatformContext();
        return ApiResult.ok(candidates.platformCandidates(
            assetType, keyword, new PageRequest(page, size, sort)));
    }

    /**
     * 查询指定医院当前完整机构生效版本。
     */
    @GetMapping("/hospitals/{hospitalId}/runtime-releases/current")
    @PreAuthorize("@perm.has('asset.read')")
    public ApiResult<ClinicalRuntimeReleaseDetailResponse> currentHospitalRuntime(
            @PathVariable String hospitalId) {
        return ApiResult.ok(queries.currentHospitalRuntime(tenantId(), hospitalId).orElse(null));
    }

    /**
     * 分页查询指定医院可启用的集团或医院本地资产版本。
     */
    @GetMapping("/hospitals/{hospitalId}/runtime-candidates")
    @PreAuthorize("@perm.has('asset.read')")
    public ApiResult<PageResponse<ReleaseCandidateAsset>> hospitalReleaseCandidates(
            @PathVariable String hospitalId,
            @RequestParam(required = false) VersionedAssetType assetType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ApiResult.ok(candidates.hospitalCandidates(
            tenantId(),
            hospitalId,
            assetType,
            keyword,
            new PageRequest(page, size, sort)
        ));
    }

    /**
     * 分页查询指定医院全部不可变机构生效版本。
     */
    @GetMapping("/hospitals/{hospitalId}/runtime-releases")
    @PreAuthorize("@perm.has('asset.read')")
    public ApiResult<PageResponse<ClinicalRuntimeRelease>> hospitalRuntimeHistory(
            @PathVariable String hospitalId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ApiResult.ok(queries.hospitalRuntimeHistory(
                tenantId(), hospitalId, new PageRequest(page, size, sort)));
    }

    /**
     * 只读分析目标平台标准版本升级到当前机构生效版本前的差异和冲突。
     */
    @GetMapping("/hospitals/{hospitalId}/platform-upgrade-analysis")
    @PreAuthorize("@perm.has('asset.read')")
    public ApiResult<PlatformUpgradeAnalysisResponse> analyzeHospitalPlatformUpgrade(
            @PathVariable String hospitalId,
            @RequestParam String targetBaselineReleaseId) {
        return ApiResult.ok(queries.analyzePlatformUpgrade(
            tenantId(), hospitalId, targetBaselineReleaseId));
    }

    /**
     * 发布新的完整平台标准版本。
     */
    @PostMapping("/platform-baselines")
    @PreAuthorize("@perm.has('platform.publish')")
    public ApiResult<PlatformBaselineRelease> publishPlatformBaseline(
            @Valid @RequestBody PlatformBaselinePublishRequest request) {
        requirePlatformContext();
        return ApiResult.ok(baselines.publish(new PlatformBaselinePublishCommand(
            request.publishVersionIds(),
            request.disabledAssets(),
            actor(),
            RequestContext.currentTraceId()
        )));
    }

    /**
     * 以完整期望启用集合原子生成机构生效版本。
     */
    @PostMapping("/hospitals/{hospitalId}/runtime-releases")
    @PreAuthorize("@perm.has('tenant.override')")
    public ApiResult<ClinicalRuntimeRelease> activateHospitalRuntime(
            @PathVariable String hospitalId,
            @Valid @RequestBody ClinicalRuntimeActivateRequest request) {
        confirmPlatformUpgradeDigestIfRequired(hospitalId, request);
        return ApiResult.ok(runtimes.activate(new ClinicalRuntimeReleaseCommand(
            tenantId(),
            hospitalId,
            request.platformBaselineReleaseId(),
            request.expectedCurrentReleaseId(),
            request.activeAssets(),
            actor(),
            RequestContext.currentTraceId()
        )));
    }

    /**
     * 复制历史完整清单生成更高编号的医院回滚修订。
     */
    @PostMapping("/hospitals/{hospitalId}/runtime-releases:rollback")
    @PreAuthorize("@perm.has('tenant.override')")
    public ApiResult<ClinicalRuntimeRelease> rollbackHospitalRuntime(
            @PathVariable String hospitalId,
            @Valid @RequestBody ClinicalRuntimeRollbackRequest request) {
        return ApiResult.ok(runtimes.rollback(
            tenantId(),
            hospitalId,
            request.targetReleaseId(),
            request.expectedCurrentReleaseId(),
            actor(),
            RequestContext.currentTraceId()
        ));
    }

    /**
     * 导出当前机构生效版本离线交付文件。
     *
     * <p>文件只用于传输和导入预检，不作为临床运行指针。
     */
    @PostMapping("/hospitals/{hospitalId}/runtime-releases/offline-delivery")
    @PreAuthorize("@perm.has('tenant.override')")
    public ApiResult<RuntimeReleaseOfflineDeliveryResponse> exportHospitalRuntimeOfflineDelivery(
            @PathVariable String hospitalId) {
        return ApiResult.ok(offlineDelivery.exportCurrentRuntimeRelease(
            tenantId(),
            hospitalId,
            actor(),
            RequestContext.currentTraceId()
        ));
    }

    /**
     * 校验机构生效版本离线交付文件。
     *
     * <p>仅执行验签和清单对账，不修改当前机构生效版本。
     */
    @PostMapping("/hospitals/{hospitalId}/runtime-releases/offline-delivery:validate-import")
    @PreAuthorize("@perm.has('asset.read')")
    public ApiResult<RuntimeReleaseOfflineImportPreviewResponse> validateHospitalRuntimeOfflineImport(
            @PathVariable String hospitalId,
            @Valid @RequestBody RuntimeReleaseOfflineImportPreviewRequest request) {
        if (!hospitalId.equals(request.expectedHospitalId())) {
            throw new ApiException(ErrorCode.CONFLICT, "路径医院与离线交付预期医院不一致");
        }
        return ApiResult.ok(offlineDelivery.validateImportPreview(tenantId(), request));
    }

    /**
     * 将已验签的机构生效版本离线交付文件恢复为新的不可变机构生效版本。
     */
    @PostMapping("/hospitals/{hospitalId}/runtime-releases/offline-delivery:restore")
    @PreAuthorize("@perm.has('tenant.override')")
    public ApiResult<RuntimeReleaseOfflineRestoreResponse> restoreHospitalRuntimeOfflineDelivery(
            @PathVariable String hospitalId,
            @Valid @RequestBody RuntimeReleaseOfflineRestoreRequest request) {
        if (!hospitalId.equals(request.expectedHospitalId())) {
            throw new ApiException(ErrorCode.CONFLICT, "路径医院与离线交付预期医院不一致");
        }
        return ApiResult.ok(offlineDelivery.restoreImport(
            tenantId(),
            request,
            actor(),
            RequestContext.currentTraceId()
        ));
    }

    private void requirePlatformContext() {
        if (!PlatformTenant.isPlatformTenant(tenantId())) {
            throw new ApiException(
                ErrorCode.FORBIDDEN, "只有平台权威范围可以发布平台标准版本");
        }
    }

    private String tenantId() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "认证上下文缺少租户");
        }
        return scope.tenantId().trim();
    }

    private String actor() {
        return RequestContext.currentUserId()
            .filter(value -> !value.isBlank())
            .map(String::trim)
            .orElseThrow(() -> new ApiException(
                ErrorCode.UNAUTHORIZED, "认证上下文缺少操作人"));
    }

    private void confirmPlatformUpgradeDigestIfRequired(
            String hospitalId,
            ClinicalRuntimeActivateRequest request) {
        PlatformUpgradeAnalysisResponse analysis;
        try {
            analysis = queries.analyzePlatformUpgrade(
                tenantId(), hospitalId, request.platformBaselineReleaseId());
        } catch (ApiException exception) {
            if (exception.errorCode() == ErrorCode.CONFLICT
                    && exception.getMessage().contains("尚未建立当前生效版本")) {
                return;
            }
            throw exception;
        }
        if (analysis == null) {
            return;
        }
        if (analysis.currentRuntime().platformBaselineReleaseId()
                .equals(request.platformBaselineReleaseId())) {
            return;
        }
        String confirmed = request.confirmedPlatformUpgradeDigest();
        if (confirmed == null || confirmed.isBlank()) {
            throw new ApiException(ErrorCode.CONFLICT, "平台升级前必须先完成差异与冲突分析");
        }
        if (!analysis.analysisDigest().equals(confirmed.trim())) {
            throw new ApiException(ErrorCode.CONFLICT, "平台升级分析摘要已变化，请重新评估");
        }
        if (analysis.diffSummary().conflictCount() > 0) {
            throw new ApiException(ErrorCode.CONFLICT, "平台升级分析仍存在机构覆盖冲突，请先处理后再生成机构生效版本");
        }
    }
}
