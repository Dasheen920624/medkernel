package com.medkernel.engine.sandbox;

import java.util.List;

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

/**
 * 全真体验沙盘场景编排入口。
 */
@RestController
@RequestMapping("/api/v1/engine/sandbox")
public class SandboxScenarioController {

    private final SandboxScenarioCatalog catalog;
    private final SandboxOrchestrationService service;

    public SandboxScenarioController(SandboxScenarioCatalog catalog, SandboxOrchestrationService service) {
        this.catalog = catalog;
        this.service = service;
    }

    @GetMapping("/scenarios")
    @PreAuthorize("@perm.has('sandbox.run')")
    @DataScope(requireTenant = true)
    public ApiResult<List<SandboxScenarioCatalogItem>> scenarios() {
        return ApiResult.ok(catalog.all().stream()
            .map(SandboxScenarioCatalogItem::from)
            .toList());
    }

    @PostMapping("/scenarios/{scenarioId}/run")
    @PreAuthorize("@perm.has('sandbox.run')")
    @DataScope(requireTenant = true)
    public ApiResult<SandboxRunResponse> run(
            @PathVariable String scenarioId,
            @Valid @RequestBody(required = false) SandboxRunRequest request) {
        SandboxRunRequest effectiveRequest = request == null
            ? new SandboxRunRequest(null, null, null, null)
            : request;
        return ApiResult.ok(service.run(scenarioId, effectiveRequest));
    }
}
