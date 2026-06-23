package com.medkernel.engine.authoring;

import java.util.LinkedHashSet;
import java.util.Set;

import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.engine.security.PermissionEvaluator;
import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.datascope.DataScope;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
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
    private final PermissionEvaluator permissions;

    public AuthoringAssetLibraryController(
            AuthoringAssetLibraryService service,
            PermissionEvaluator permissions) {
        this.service = service;
        this.permissions = permissions;
    }

    /**
     * 查询统一资产库。
     */
    @GetMapping
    @PreAuthorize("@perm.hasAny('rule.read','pathway.read','followup.read')")
    public ApiResult<PageResponse<AuthoringAssetLibraryItem>> list(
            @RequestParam(required = false) VersionedAssetType assetType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Boolean favoriteOnly,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        Set<VersionedAssetType> allowedAssetTypes = allowedAssetTypes(assetType);
        return ApiResult.ok(service.list(new AuthoringAssetLibraryQuery(
            assetType,
            keyword,
            tag,
            Boolean.TRUE.equals(favoriteOnly),
            new PageRequest(page, size, sort),
            allowedAssetTypes
        )));
    }

    private Set<VersionedAssetType> allowedAssetTypes(VersionedAssetType requestedType) {
        Set<VersionedAssetType> allowedTypes = new LinkedHashSet<>();
        if (permissions.has("rule.read")) {
            allowedTypes.add(VersionedAssetType.RULE);
        }
        if (permissions.has("pathway.read")) {
            allowedTypes.add(VersionedAssetType.PATHWAY);
        }
        if (permissions.has("followup.read")) {
            allowedTypes.add(VersionedAssetType.FOLLOWUP);
        }
        if (allowedTypes.isEmpty()) {
            throw new AccessDeniedException("统一资产库查询需要至少一种创作资产读权限");
        }
        if (requestedType == null) {
            return allowedTypes;
        }
        if (!allowedTypes.contains(requestedType)) {
            throw new AccessDeniedException("读取 " + requestedType + " 资产需要对应读权限");
        }
        return Set.of(requestedType);
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

}
