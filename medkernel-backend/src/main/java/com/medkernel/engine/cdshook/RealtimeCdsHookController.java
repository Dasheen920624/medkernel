package com.medkernel.engine.cdshook;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * P13-5 开医嘱实时 CDS Hook 入口。
 */
@RestController
@RequestMapping("/api/v1/engine/cds-hooks:evaluate")
@DataScope(requireTenant = true)
public class RealtimeCdsHookController {

    private final RealtimeCdsHookService service;

    public RealtimeCdsHookController(RealtimeCdsHookService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("@perm.has('recommendation.accept')")
    public ApiResult<CdsHookResponse> evaluate(@RequestBody @Valid CdsHookRequest request) {
        return ApiResult.ok(service.evaluate(request));
    }
}
