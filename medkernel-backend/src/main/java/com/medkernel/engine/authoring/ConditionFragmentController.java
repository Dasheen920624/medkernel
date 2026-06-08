package com.medkernel.engine.authoring;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.datascope.DataScope;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 条件片段库 REST 入口。
 */
@RestController
@RequestMapping("/api/v1/engine/authoring/fragments")
@DataScope(requireTenant = true)
public class ConditionFragmentController {

    private final ConditionFragmentService service;

    public ConditionFragmentController(ConditionFragmentService service) {
        this.service = service;
    }

    /**
     * 分页查询条件片段。
     */
    @GetMapping
    @PreAuthorize("@perm.hasAny('rule.read','pathway.read')")
    public ApiResult<PageResponse<ConditionFragmentResponse>> list(
            @RequestParam(required = false) ConditionFragmentStatus status,
            @RequestParam(required = false) String packageVersion,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ApiResult.ok(service.list(status, packageVersion, keyword, new PageRequest(page, size, sort)));
    }

    /**
     * 创建条件片段。
     */
    @PostMapping
    @PreAuthorize("@perm.hasAny('rule.write','pathway.write')")
    public ResponseEntity<ApiResult<ConditionFragmentResponse>> create(
            @RequestBody @Valid ConditionFragmentUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.ok(service.create(request)));
    }

    /**
     * 更新条件片段。
     */
    @PutMapping("/{fragmentId}")
    @PreAuthorize("@perm.hasAny('rule.write','pathway.write')")
    public ApiResult<ConditionFragmentResponse> update(
            @PathVariable String fragmentId,
            @RequestBody @Valid ConditionFragmentUpsertRequest request) {
        return ApiResult.ok(service.update(fragmentId, request));
    }

    /**
     * 查询条件片段变更影响分析。
     */
    @GetMapping("/{fragmentId}/impact")
    @PreAuthorize("@perm.hasAny('rule.read','pathway.read')")
    public ApiResult<ConditionFragmentImpactResponse> impact(@PathVariable String fragmentId) {
        return ApiResult.ok(service.impact(fragmentId));
    }
}
