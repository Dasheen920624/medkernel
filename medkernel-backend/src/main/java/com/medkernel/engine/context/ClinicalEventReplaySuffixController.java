package com.medkernel.engine.context;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;

/**
 * API-02 客户面回放 suffix 路由。
 */
@RestController
@RequestMapping("/api/v1/engine/clinical-events:replay")
@DataScope(requireTenant = true)
public class ClinicalEventReplaySuffixController {

    private final ClinicalEventService service;

    public ClinicalEventReplaySuffixController(ClinicalEventService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("@perm.has('event.write')")
    public ApiResult<ClinicalEventReplayResponse> replayByRequest(
            @RequestBody @Valid ClinicalEventReplayRequest request) {
        return ApiResult.ok(service.replay(request.sourceEventId()));
    }
}
