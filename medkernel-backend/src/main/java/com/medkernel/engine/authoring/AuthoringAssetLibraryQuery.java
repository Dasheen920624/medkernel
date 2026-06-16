package com.medkernel.engine.authoring;

import java.util.Set;

import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.PageRequest;

/**
 * 统一创作资产库查询条件。
 */
public record AuthoringAssetLibraryQuery(
    VersionedAssetType assetType,
    String keyword,
    String tag,
    boolean favoriteOnly,
    PageRequest page,
    Set<VersionedAssetType> allowedAssetTypes
) {
    public AuthoringAssetLibraryQuery(
            VersionedAssetType assetType,
            String keyword,
            String tag,
            boolean favoriteOnly,
            PageRequest page) {
        this(assetType, keyword, tag, favoriteOnly, page, null);
    }
}
