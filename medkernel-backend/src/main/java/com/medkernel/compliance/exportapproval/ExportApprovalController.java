package com.medkernel.compliance.exportapproval;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;

/**
 * SYS-06 敏感数据导出审批控制器。
 */
@Validated
@RestController
@RequestMapping("/api/v1/compliance")
@DataScope(requireTenant = true)
public class ExportApprovalController {

    private final ExportApprovalService service;

    public ExportApprovalController(ExportApprovalService service) {
        this.service = service;
    }

    @PostMapping("/exports:request")
    @PreAuthorize("@perm.has('audit.export')")
    public ApiResult<ExportApprovalResponse> requestExport(
            @Valid @RequestBody ExportApprovalRequest request,
            Authentication authentication) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        return ApiResult.ok(service.requestExport(tenantId, request, actor(authentication)));
    }

    @PostMapping("/exports/{approvalId}:approve")
    @PreAuthorize("@perm.has('audit.export')")
    public ApiResult<ExportApprovalResponse> reviewExport(
            @PathVariable String approvalId,
            @Valid @RequestBody ExportApprovalReviewRequest request,
            Authentication authentication) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        return ApiResult.ok(service.reviewExport(tenantId, approvalId, request, actor(authentication)));
    }

    @PostMapping("/exports/{approvalId}:complete")
    @PreAuthorize("@perm.has('audit.export')")
    public ApiResult<ExportApprovalResponse> completeExport(
            @PathVariable String approvalId,
            @Valid @RequestBody ExportCompletionRequest request,
            Authentication authentication) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        return ApiResult.ok(service.completeExport(tenantId, approvalId, request, actor(authentication)));
    }

    private String actor(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }
}
