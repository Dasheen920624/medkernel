package com.medkernel.engine.security.bootstrap;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;

import jakarta.validation.Valid;

/**
 * 首次部署引导控制器：不签发业务 JWT，只完成一次性接管初始化。
 */
@RestController
@RequestMapping("/api/v1/bootstrap")
public class BootstrapController {

    private final BootstrapIdentityService service;
    private final MfaPolicyService mfaPolicyService;

    public BootstrapController(BootstrapIdentityService service, MfaPolicyService mfaPolicyService) {
        this.service = service;
        this.mfaPolicyService = mfaPolicyService;
    }

    @PostMapping("/init-token")
    public ApiResult<BootstrapStartResponse> checkInitToken(@Valid @RequestBody BootstrapStartRequest request) {
        return ApiResult.ok(service.check(request));
    }

    @PostMapping("/password")
    public ApiResult<BootstrapPasswordResponse> createFirstAdmin(@Valid @RequestBody BootstrapPasswordRequest request) {
        return ApiResult.ok(service.createFirstAdmin(request));
    }

    @PostMapping("/mfa")
    public ApiResult<BootstrapMfaResponse> bindMfa(@Valid @RequestBody BootstrapMfaRequest request) {
        return ApiResult.ok(mfaPolicyService.bindForCurrentUser(request));
    }
}
