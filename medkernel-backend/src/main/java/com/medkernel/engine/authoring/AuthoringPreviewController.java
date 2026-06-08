package com.medkernel.engine.authoring;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.datascope.DataScope;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 创作体验 REST 入口（规则条件树与路径守卫自然语言预览）。
 */
@RestController
@RequestMapping("/api/v1/engine/authoring")
@DataScope(requireTenant = true)
public class AuthoringPreviewController {

    private final AuthoringPreviewService service;

    public AuthoringPreviewController(AuthoringPreviewService service) {
        this.service = service;
    }

    /**
     * 将规则 when 或路径 guard 渲染为可读中文预览。
     */
    @PostMapping("/preview")
    @PreAuthorize("@perm.hasAny('rule.read','pathway.read')")
    public ApiResult<AuthoringPreviewResponse> preview(@RequestBody @Valid AuthoringPreviewRequest request) {
        request.apiContext().validateTenant(RequestContext.currentOrgScope().tenantId());
        return ApiResult.ok(service.preview(request));
    }
}
