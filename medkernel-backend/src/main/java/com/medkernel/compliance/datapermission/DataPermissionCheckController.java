package com.medkernel.compliance.datapermission;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.engine.security.DataScopeResolver;
import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;

/**
 * SYS-06 数据权限检查控制器。
 */
@Validated
@RestController
@RequestMapping("/api/v1/compliance")
@DataScope(requireTenant = true)
public class DataPermissionCheckController {

    private final DataPermissionService service;
    private final DataScopeResolver dataScopeResolver;

    public DataPermissionCheckController(
            DataPermissionService service,
            DataScopeResolver dataScopeResolver) {
        this.service = service;
        this.dataScopeResolver = dataScopeResolver;
    }

    @PostMapping("/data-permissions:check")
    @PreAuthorize("@perm.has('context.read') or @perm.has('audit.read') or @perm.has('audit.export')")
    public ApiResult<DataPermissionDecision> checkAccess(
            @Valid @RequestBody DataPermissionCheckRequest request,
            Authentication authentication) {
        var currentScope = RequestContext.currentOrgScope();
        var resolved = dataScopeResolver.resolve(
            authentication,
            currentScope,
            RequestContext.currentUserId().orElse(null));
        return ApiResult.ok(service.evaluate(resolved, request.toCheck(currentScope.tenantId())));
    }
}
