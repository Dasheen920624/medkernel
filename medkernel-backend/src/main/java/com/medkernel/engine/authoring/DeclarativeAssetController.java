package com.medkernel.engine.authoring;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;

/**
 * 值集、计算公式、医嘱套餐和临床提示卡的独立维护入口。
 */
@RestController
@RequestMapping("/api/v1/engine/authoring/declarative-assets")
@DataScope(requireTenant = true)
public class DeclarativeAssetController {

    private final DeclarativeAssetService service;

    public DeclarativeAssetController(DeclarativeAssetService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@perm.has('asset.read')")
    public ApiResult<PageResponse<DeclarativeAssetSummaryResponse>> list(
            @RequestParam VersionedAssetType assetType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ApiResult.ok(service.list(assetType, new PageRequest(page, size, sort)));
    }

    @GetMapping("/{versionId}")
    @PreAuthorize("@perm.has('asset.read')")
    public ApiResult<DeclarativeAssetDetailResponse> detail(@PathVariable String versionId) {
        return ApiResult.ok(service.detail(versionId));
    }

    @PostMapping
    @PreAuthorize("@perm.has('asset.write')")
    public ApiResult<DeclarativeAssetDetailResponse> create(
            @Valid @RequestBody DeclarativeAssetUpsertRequest request) {
        return ApiResult.ok(service.create(request));
    }

    @PutMapping("/{versionId}")
    @PreAuthorize("@perm.has('asset.write')")
    public ApiResult<DeclarativeAssetDetailResponse> update(
            @PathVariable String versionId,
            @Valid @RequestBody DeclarativeAssetUpsertRequest request) {
        return ApiResult.ok(service.update(versionId, request));
    }
}
