package com.medkernel.compliance.masking;

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
 * SYS-06 脱敏规则控制器。
 */
@Validated
@RestController
@RequestMapping("/api/v1/compliance/masking-rules")
@DataScope(requireTenant = true)
public class MaskingRuleController {

    private final MaskingService service;

    public MaskingRuleController(MaskingService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@perm.has('audit.read')")
    public ApiResult<List<MaskingRuleResponse>> listRules(
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String fieldName) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        return ApiResult.ok(service.listRules(tenantId, resourceType, fieldName));
    }

    @PutMapping
    @PreAuthorize("@perm.has('system.manage')")
    public ApiResult<MaskingRuleResponse> upsertRule(
            @Valid @RequestBody MaskingRuleRequest request,
            Authentication authentication) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        String actor = authentication == null ? null : authentication.getName();
        return ApiResult.ok(service.upsertRule(tenantId, request, actor));
    }
}
