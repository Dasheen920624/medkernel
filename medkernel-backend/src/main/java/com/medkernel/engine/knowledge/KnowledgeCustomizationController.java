package com.medkernel.engine.knowledge;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 机构知识定制入口。
 */
@RestController
@RequestMapping("/api/v1/engine/knowledge/customizations")
@DataScope(requireTenant = true)
public class KnowledgeCustomizationController {

    private final KnowledgeCustomizationService service;

    public KnowledgeCustomizationController(KnowledgeCustomizationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<PageResponse<KnowledgeCustomizationResponse>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ApiResult.ok(service.list(new PageRequest(page, size, sort)));
    }

    @PostMapping
    @PreAuthorize("@perm.has('knowledge.write')")
    public ApiResult<KnowledgeCustomizationResponse> create(
            @Valid @RequestBody KnowledgeCustomizationCreateRequest request) {
        return ApiResult.ok(service.create(request));
    }

    @PostMapping("/{customizationId}:publish")
    @PreAuthorize("@perm.has('knowledge.publish') and @perm.has('tenant.override')")
    public ApiResult<KnowledgeCustomizationResponse> publish(
            @PathVariable String customizationId,
            @Valid @RequestBody PublishRequest request) {
        return ApiResult.ok(service.publish(
            customizationId,
            request.reason(),
            request.qualityGateRecordId()));
    }

    @PostMapping("/{customizationId}:restore-platform")
    @PreAuthorize("@perm.has('knowledge.withdraw') and @perm.has('tenant.override')")
    public ApiResult<KnowledgeCustomizationResponse> restorePlatform(
            @PathVariable String customizationId,
            @Valid @RequestBody RestoreRequest request) {
        return ApiResult.ok(service.restorePlatformStandard(
            customizationId, request.reason()));
    }

    /** 发布本地定制请求。 */
    public record PublishRequest(
        @NotBlank @Size(max = 1000) String reason,
        Long qualityGateRecordId
    ) {}

    /** 恢复平台标准请求。 */
    public record RestoreRequest(
        @NotBlank @Size(min = 4, max = 1000) String reason
    ) {
    }
}
