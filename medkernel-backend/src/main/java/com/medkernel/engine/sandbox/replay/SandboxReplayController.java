package com.medkernel.engine.sandbox.replay;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;

/** 沙盘历史原样重放清单治理入口。 */
@RestController
@RequestMapping("/api/v1/engine/sandbox/replay-cases")
public class SandboxReplayController {

    private final SandboxReplayService service;

    public SandboxReplayController(SandboxReplayService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("@perm.has('package.publish')")
    @DataScope(requireTenant = true)
    public ApiResult<SandboxReplayCaseResponse> importCase(
            @Valid @RequestBody SandboxReplayImportRequest request) {
        return ApiResult.ok(service.importCase(request));
    }

    @GetMapping("/{replayCaseId}")
    @PreAuthorize("@perm.has('sandbox.run')")
    @DataScope(requireTenant = true)
    public ApiResult<SandboxReplayCaseResponse> get(@PathVariable String replayCaseId) {
        return ApiResult.ok(service.get(replayCaseId));
    }

    @PostMapping("/{replayCaseId}/revoke")
    @PreAuthorize("@perm.has('package.publish')")
    @DataScope(requireTenant = true)
    public ApiResult<SandboxReplayCaseResponse> revoke(
            @PathVariable String replayCaseId,
            @Valid @RequestBody SandboxReplayRevokeRequest request) {
        return ApiResult.ok(service.revoke(replayCaseId, request.reason()));
    }
}
