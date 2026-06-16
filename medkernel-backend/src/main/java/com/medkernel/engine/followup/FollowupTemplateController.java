package com.medkernel.engine.followup;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.datascope.DataScope;
import com.medkernel.engine.versioning.AssetVersionStatus;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 随访模板配置资产入口。
 */
@RestController
@RequestMapping("/api/v1/engine/followup/templates")
@DataScope(requireTenant = true)
public class FollowupTemplateController {

    private final FollowupTemplateService service;

    public FollowupTemplateController(FollowupTemplateService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@perm.has('followup.read')")
    public ApiResult<PageResponse<FollowupTemplateResponse>> list(
            @RequestParam(required = false) AssetVersionStatus assetStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResult.ok(service.list(
            new FollowupTemplateFilter(assetStatus, keyword),
            new PageRequest(page, size, sort)));
    }

    @PostMapping
    @PreAuthorize("@perm.has('followup.write')")
    public ApiResult<FollowupTemplateResponse> create(
            @Valid @RequestBody FollowupTemplateCreateRequest request) {
        return ApiResult.ok(service.create(request));
    }

    @PostMapping("/{templateId}/publish")
    @PreAuthorize("@perm.has('package.publish')")
    public ApiResult<FollowupTemplateResponse> publish(
            @PathVariable String templateId,
            @Valid @RequestBody FollowupTemplatePublishRequest request) {
        return ApiResult.ok(service.publish(templateId, request));
    }
}
