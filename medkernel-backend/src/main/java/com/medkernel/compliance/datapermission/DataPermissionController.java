package com.medkernel.compliance.datapermission;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;

/**
 * SYS-06 数据权限策略控制器。
 */
@Validated
@RestController
@RequestMapping("/api/v1/compliance/data-permissions")
@DataScope(requireTenant = true)
public class DataPermissionController {

    private final DataPermissionService service;

    public DataPermissionController(DataPermissionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@perm.has('audit.read')")
    public ApiResult<List<DataPermissionPolicyResponse>> listPolicies(
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) DataPermissionAction action) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        return ApiResult.ok(service.listPolicies(tenantId, resourceType, action));
    }

    @PutMapping
    @PreAuthorize("@perm.has('system.manage')")
    public ApiResult<DataPermissionPolicyResponse> upsertPolicy(
            @Valid @RequestBody DataPermissionPolicyRequest request,
            Authentication authentication) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        String actor = authentication == null ? null : authentication.getName();
        return ApiResult.ok(service.upsertPolicy(tenantId, request, actor));
    }
}
