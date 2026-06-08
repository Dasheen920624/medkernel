package com.medkernel.engine.authoring;

import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.datascope.DataScope;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一创作资产库 REST 入口。
 */
@RestController
@RequestMapping("/api/v1/engine/authoring/assets")
@DataScope(requireTenant = true)
public class AuthoringAssetLibraryController {

    private final AuthoringAssetLibraryService service;

    public AuthoringAssetLibraryController(AuthoringAssetLibraryService service) {
        this.service = service;
    }

    /**
     * 查询统一资产库。
     */
    @GetMapping
    @PreAuthorize("@perm.hasAny('rule.read','pathway.read')")
    public ApiResult<PageResponse<AuthoringAssetLibraryItem>> list(
            @RequestParam(required = false) VersionedAssetType assetType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Boolean favoriteOnly,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ApiResult.ok(service.list(new AuthoringAssetLibraryQuery(
            assetType,
            keyword,
            tag,
            Boolean.TRUE.equals(favoriteOnly),
            new PageRequest(page, size, sort)
        )));
    }

    /**
     * 更新资产分类与标签。
     */
    @PutMapping("/{assetType}/{assetId}/profile")
    @PreAuthorize("@perm.hasAny('rule.write','pathway.write')")
    public ApiResult<AuthoringAssetProfileResponse> updateProfile(
            @PathVariable VersionedAssetType assetType,
            @PathVariable String assetId,
            @RequestBody @Valid AuthoringAssetProfileRequest request) {
        return ApiResult.ok(service.updateProfile(assetType, assetId, request));
    }

    /**
     * 收藏资产。
     */
    @PostMapping("/{assetType}/{assetId}/favorite")
    @PreAuthorize("@perm.hasAny('rule.write','pathway.write')")
    public ApiResult<AuthoringAssetFavoriteResponse> favorite(
            @PathVariable VersionedAssetType assetType,
            @PathVariable String assetId) {
        return ApiResult.ok(service.favorite(assetType, assetId));
    }

    /**
     * 取消收藏资产。
     */
    @DeleteMapping("/{assetType}/{assetId}/favorite")
    @PreAuthorize("@perm.hasAny('rule.write','pathway.write')")
    public ApiResult<AuthoringAssetFavoriteResponse> unfavorite(
            @PathVariable VersionedAssetType assetType,
            @PathVariable String assetId) {
        return ApiResult.ok(service.unfavorite(assetType, assetId));
    }

    /**
     * 克隆或另存为独立草稿。
     */
    @PostMapping("/{assetType}/{assetId}/clone")
    @PreAuthorize("@perm.hasAny('rule.write','pathway.write')")
    public ApiResult<AuthoringAssetCloneResponse> cloneAsset(
            @PathVariable VersionedAssetType assetType,
            @PathVariable String assetId,
            @RequestBody @Valid AuthoringAssetCloneRequest request) {
        return ApiResult.ok(service.cloneAsset(assetType, assetId, request));
    }
}
