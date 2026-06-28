package com.medkernel.engine.runtime.diagnostics;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;

/**
 * 运行诊断 REST 入口。
 */
@RestController
@RequestMapping("/api/v1/system/runtime-diagnostics")
public class RuntimeDiagnosticsController {

    private final RuntimeDiagnosticsService service;

    public RuntimeDiagnosticsController(RuntimeDiagnosticsService service) {
        this.service = service;
    }

    /**
     * 获取脱敏后的 API 契约目录。
     *
     * @return API 契约目录
     */
    @GetMapping("/api-contracts")
    @PreAuthorize("@perm.has('system.read')")
    public ApiResult<RuntimeDiagnosticsApiContractDirectoryResponse> apiContracts() {
        return ApiResult.ok(service.apiContracts());
    }
}
