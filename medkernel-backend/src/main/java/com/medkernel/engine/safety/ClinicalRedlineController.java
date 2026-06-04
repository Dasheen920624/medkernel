package com.medkernel.engine.safety;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OPT-04 临床安全红线目录 API。
 */
@RestController
@RequestMapping("/api/v1/engine/safety/redlines")
@DataScope(requireTenant = true)
public class ClinicalRedlineController {

    private final ClinicalRedlineService service;

    public ClinicalRedlineController(ClinicalRedlineService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<ClinicalRedlineCatalogResponse> activeCatalog(
            @RequestParam(required = false) ClinicalRedlineCategory category) {
        return ApiResult.ok(service.activeCatalog(category));
    }
}
