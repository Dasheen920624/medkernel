package com.medkernel.compliance.identitybinding;

import org.springframework.security.access.prepost.PreAuthorize;
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
 * D5 外部身份绑定管理接口。
 */
@RestController
@RequestMapping("/api/v1/compliance/identity-bindings")
@DataScope(requireTenant = true)
public class IdentityBindingController {

    private final IdentityBindingService service;

    public IdentityBindingController(IdentityBindingService service) {
        this.service = service;
    }

    /**
     * 查询当前租户的外部身份绑定关系。
     */
    @GetMapping
    @PreAuthorize("@perm.has('org.read')")
    public ApiResult<PageResponse<IdentityBindingResponse>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ApiResult.ok(service.list(
            RequestContext.currentOrgScope().tenantId(),
            new PageRequest(page, size, sort)));
    }

    /**
     * 为当前租户成员创建外部身份绑定。
     */
    @PostMapping
    @PreAuthorize("@perm.has('org.write')")
    public ApiResult<IdentityBindingResponse> create(
            @Valid @RequestBody IdentityBindingCreateRequest request) {
        return ApiResult.ok(service.create(RequestContext.currentOrgScope().tenantId(), request));
    }

    /**
     * 解除当前租户内的外部身份绑定。
     */
    @PostMapping("/{bindingId}:unbind")
    @PreAuthorize("@perm.has('org.write')")
    public ApiResult<IdentityBindingResponse> unbind(
            @PathVariable String bindingId,
            @Valid @RequestBody IdentityBindingUnbindRequest request) {
        return ApiResult.ok(service.unbind(
            RequestContext.currentOrgScope().tenantId(), bindingId, request));
    }
}
