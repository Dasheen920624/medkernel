package com.medkernel.compliance.exportconfirmation;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;

/**
 * 敏感数据导出确认控制器。
 */
@Validated
@RestController
@RequestMapping("/api/v1/compliance")
@DataScope(requireTenant = true)
public class ExportConfirmationController {

    private final ExportConfirmationService service;

    public ExportConfirmationController(ExportConfirmationService service) {
        this.service = service;
    }

    @GetMapping("/exports")
    @PreAuthorize("@perm.has('list.export')")
    public ApiResult<PageResponse<ExportConfirmationResponse>> listExports(
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) ExportConfirmationStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        return ApiResult.ok(service.listConfirmations(
            tenantId,
            resourceType,
            status,
            new PageRequest(page, size, sort)
        ));
    }

    @PostMapping("/exports:confirm")
    @PreAuthorize("@perm.has('list.export')")
    public ApiResult<ExportConfirmationResponse> confirmExport(
            @Valid @RequestBody ExportConfirmationRequest request,
            Authentication authentication) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        return ApiResult.ok(service.confirmExport(tenantId, request, actor(authentication)));
    }

    @PostMapping("/exports/{confirmationId}:complete-from-job")
    @PreAuthorize("@perm.has('list.export')")
    public ApiResult<ExportConfirmationResponse> completeExportFromJob(
            @PathVariable String confirmationId,
            @Valid @RequestBody ExportJobCompletionRequest request,
            Authentication authentication) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        return ApiResult.ok(
            service.completeExportFromJob(tenantId, confirmationId, request, actor(authentication))
        );
    }

    private String actor(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }
}
