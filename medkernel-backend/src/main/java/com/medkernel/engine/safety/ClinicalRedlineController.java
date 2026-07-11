package com.medkernel.engine.safety;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * OPT-04 临床安全红线目录 API。
 */
@RestController
@RequestMapping("/api/v1/engine/safety")
@DataScope(requireTenant = true)
public class ClinicalRedlineController {

    private final ClinicalRedlineService service;

    public ClinicalRedlineController(ClinicalRedlineService service) {
        this.service = service;
    }

    @GetMapping("/redlines")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<ClinicalRedlineCatalogResponse> activeCatalog(
            @RequestParam(required = false) ClinicalRedlineCategory category) {
        return ApiResult.ok(service.activeCatalog(category));
    }

    @PostMapping("/redlines")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<ClinicalRedlineResponse> createDraft(
            @RequestBody @Valid ClinicalRedlineDraftRequest request) {
        return ApiResult.ok(service.createDraft(request));
    }

    @PostMapping("/redlines:dry-run")
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<ClinicalRedlineTrialResponse> dryRun(
            @RequestBody @Valid ClinicalRedlineDryRunRequest request) {
        return ApiResult.ok(service.dryRun(request));
    }

    @PostMapping("/redlines:promote")
    @PreAuthorize("@perm.has('knowledge.publish')")
    public ApiResult<ClinicalRedlineResponse> promote(
            @RequestBody @Valid ClinicalRedlinePromoteRequest request) {
        return ApiResult.ok(service.promote(request));
    }
}
