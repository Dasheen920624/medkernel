package com.medkernel.engine.developer;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;

/**
 * 诊断工具 REST 入口（D6 DEVCON-01）。
 */
@RestController
@RequestMapping("/api/v1/system/dev-console")
public class DeveloperConsoleController {

    private final DeveloperConsoleService service;

    public DeveloperConsoleController(DeveloperConsoleService service) {
        this.service = service;
    }

    /**
     * 获取脱敏后的 API 契约目录。
     *
     * @return API 契约目录
     */
    @GetMapping("/api-contracts")
    @PreAuthorize("@perm.has('system.read')")
    public ApiResult<DeveloperApiContractDirectoryResponse> apiContracts() {
        return ApiResult.ok(service.apiContracts());
    }
}
