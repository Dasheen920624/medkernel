package com.medkernel.engine.tenant;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;

/**
 * SVC-PILOT-01 租户开通与实施服务包 API。
 *
 * <p>该控制器暴露 engine 路由，旧 {@code /platform} 路由保留给存量页面兼容。
 */
@RestController
@RequestMapping("/api/v1/engine/tenant")
@DataScope(requireTenant = true)
public class TenantEngineController {

    private final TenantPilotService service;

    public TenantEngineController(TenantPilotService service) {
        this.service = service;
    }

    /**
     * 获取当前租户品牌配置。
     *
     * @return 品牌配置
     */
    @GetMapping("/branding")
    @PreAuthorize("@perm.has('tenant.read')")
    public ApiResult<Branding> getBranding() {
        return ApiResult.ok(service.getBranding(requireTenantId()));
    }

    /**
     * 更新当前租户品牌配置。
     *
     * @param dto 品牌配置输入
     * @return 更新后的品牌配置
     */
    @PostMapping("/branding")
    @PreAuthorize("@perm.has('tenant.write')")
    public ApiResult<Branding> saveBranding(@Valid @RequestBody BrandingController.BrandingUpdateDto dto) {
        String tenantId = requireTenantId();
        Branding input = new Branding(
            null,
            tenantId,
            dto.hospitalName(),
            dto.logoUrl(),
            dto.themeColor(),
            dto.expertMode(),
            dto.customBrandingJson(),
            null, null, null, null
        );
        return ApiResult.ok(service.saveBranding(tenantId, input));
    }

    /**
     * 获取租户成功计划。
     *
     * @return 客户成功计划
     */
    @GetMapping("/success-plan")
    @PreAuthorize("@perm.has('tenant.read')")
    public ApiResult<SuccessPlan> getSuccessPlan() {
        return ApiResult.ok(service.getSuccessPlan(requireTenantId()));
    }

    /**
     * 推进租户成功计划阶段。
     *
     * @param request 阶段推进请求
     * @return 更新后的客户成功计划
     */
    @PostMapping("/success-plan/transition")
    @PreAuthorize("@perm.has('tenant.write')")
    public ApiResult<SuccessPlan> transitionSuccessPlan(
            @Valid @RequestBody SuccessController.TransitionRequest request) {
        return ApiResult.ok(service.transitionStage(requireTenantId(), request.nextStage()));
    }

    /**
     * 获取实施向导步骤状态。
     *
     * @return 步骤状态清单
     */
    @GetMapping("/implementation-steps")
    @PreAuthorize("@perm.has('tenant.read')")
    public ApiResult<List<ImplementationStep>> implementationSteps() {
        return ApiResult.ok(service.getImplementationSteps(requireTenantId()));
    }

    /**
     * 获取开通就绪门状态。
     *
     * @return 开通就绪门结果
     */
    @GetMapping("/onboarding-readiness")
    @PreAuthorize("@perm.has('tenant.read')")
    public ApiResult<OnboardingReadiness> onboardingReadiness() {
        return ApiResult.ok(service.getOnboardingReadiness(requireTenantId()));
    }

    /**
     * 执行开通门禁，未就绪时返回 {@code TENANT_ONBOARD_NOT_READY}。
     *
     * @return 就绪结果
     */
    @PostMapping("/onboarding-readiness/activate")
    @PreAuthorize("@perm.has('tenant.write')")
    public ApiResult<OnboardingReadiness> activateReadiness() {
        String tenantId = requireTenantId();
        service.assertOnboardingReady(tenantId);
        return ApiResult.ok(service.getOnboardingReadiness(tenantId));
    }

    private String requireTenantId() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }
}
