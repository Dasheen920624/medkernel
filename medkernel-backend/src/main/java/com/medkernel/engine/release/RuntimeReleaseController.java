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
 * 平台权威基线与医院运行修订的唯一发布入口。
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

    public RuntimeReleaseController(
            PlatformBaselineService baselines,
            ClinicalRuntimeReleaseService runtimes,
            RuntimeReleaseQueryService queries,
            ReleaseCandidateQueryService candidates) {
        this.baselines = baselines;
        this.runtimes = runtimes;
        this.queries = queries;
        this.candidates = candidates;
    }

    /**
     * 查询当前完整平台权威基线。
     */
    @GetMapping("/platform-baselines/current")
    @PreAuthorize("@perm.has('asset.read')")
    public ApiResult<PlatformBaselineDetailResponse> currentPlatformBaseline() {
        return ApiResult.ok(queries.currentPlatformBaseline());
    }

    /**
     * 分页查询可进入下一平台基线的草稿资产。
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
     * 查询指定医院当前完整运行修订。
     */
    @GetMapping("/hospitals/{hospitalId}/runtime-releases/current")
    @PreAuthorize("@perm.has('asset.read')")
    public ApiResult<ClinicalRuntimeReleaseDetailResponse> currentHospitalRuntime(
            @PathVariable String hospitalId) {
        return ApiResult.ok(queries.currentHospitalRuntime(tenantId(), hospitalId));
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
     * 分页查询指定医院全部不可变运行修订。
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
     * 发布新的完整平台权威基线。
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
     * 以完整期望启用集合原子生成医院运行修订。
     */
    @PostMapping("/hospitals/{hospitalId}/runtime-releases")
    @PreAuthorize("@perm.has('tenant.override')")
    public ApiResult<ClinicalRuntimeRelease> activateHospitalRuntime(
            @PathVariable String hospitalId,
            @Valid @RequestBody ClinicalRuntimeActivateRequest request) {
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
            actor(),
            RequestContext.currentTraceId()
        ));
    }

    private void requirePlatformContext() {
        if (!PlatformTenant.isPlatformTenant(tenantId())) {
            throw new ApiException(
                ErrorCode.FORBIDDEN, "只有平台权威空间可以发布平台基线");
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
}
