package com.medkernel.engine.llm;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;

/**
 * LLM-04 prompt/tool/model 版本治理 API。
 *
 * <p>版本治理接口只暴露版本号、状态与 hash，不返回提示词正文或工具契约明文。
 */
@RestController
@RequestMapping("/api/v1/model-versions")
@DataScope(requireTenant = true)
public class ModelVersionGovernanceController {

    private final ModelVersionGovernanceService service;

    public ModelVersionGovernanceController(ModelVersionGovernanceService service) {
        this.service = service;
    }

    @PostMapping("/bundles")
    @PreAuthorize("@perm.has('llm.manage')")
    public ApiResult<ModelVersionBundleResponse> publish(@Valid @RequestBody ModelVersionBundleRequest request) {
        return ApiResult.ok(service.publish(request));
    }

    @PostMapping("/capabilities/{capabilityCode}/rollback/{bundleId}")
    @PreAuthorize("@perm.has('llm.manage')")
    public ApiResult<ModelVersionBundleResponse> rollback(@PathVariable String capabilityCode,
                                                          @PathVariable Long bundleId) {
        return ApiResult.ok(service.rollback(capabilityCode, bundleId));
    }

    @GetMapping("/capabilities/{capabilityCode}/active")
    @PreAuthorize("@perm.has('llm.read')")
    public ApiResult<ModelVersionBundleResponse> active(@PathVariable String capabilityCode) {
        return ApiResult.ok(service.active(capabilityCode));
    }

    @GetMapping("/capabilities/{capabilityCode}/export")
    @PreAuthorize("@perm.has('llm.read')")
    public ApiResult<ModelVersionExportResponse> export(@PathVariable String capabilityCode) {
        return ApiResult.ok(service.export(capabilityCode));
    }
}
