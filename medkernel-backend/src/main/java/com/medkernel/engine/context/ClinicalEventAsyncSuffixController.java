package com.medkernel.engine.context;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;

/**
 * API-02 客户面异步受理 suffix 路由。
 */
@RestController
@RequestMapping("/api/v1/engine/clinical-events:async")
@DataScope(requireTenant = true)
public class ClinicalEventAsyncSuffixController {

    private final ClinicalEventService service;

    public ClinicalEventAsyncSuffixController(ClinicalEventService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("@perm.has('event.write')")
    public ResponseEntity<ApiResult<ClinicalEventAcceptedResponse>> receiveAsync(
            @RequestBody @Valid ClinicalEventRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(ApiResult.ok(service.receiveAsync(request)));
    }
}
