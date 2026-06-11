package com.medkernel.compliance.masking;

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
 * SYS-06 脱敏预览控制器。
 */
@Validated
@RestController
@RequestMapping("/api/v1/compliance")
@DataScope(requireTenant = true)
public class MaskingPreviewController {

    private final MaskingService service;
    private final DataScopeResolver dataScopeResolver;

    public MaskingPreviewController(MaskingService service, DataScopeResolver dataScopeResolver) {
        this.service = service;
        this.dataScopeResolver = dataScopeResolver;
    }

    @PostMapping("/masking-rules:preview")
    @PreAuthorize("@perm.has('audit.read')")
    public ApiResult<MaskingResult> previewMasking(
            @Valid @RequestBody MaskingPreviewRequest request,
            Authentication authentication) {
        var scope = RequestContext.currentOrgScope();
        var resolved = dataScopeResolver.resolve(
            authentication,
            scope,
            RequestContext.currentUserId().orElse(null));
        return ApiResult.ok(service.mask(resolved, request.toMaskingRequest(scope.tenantId())));
    }
}
