package com.medkernel.engine.authoring;

import java.time.Instant;
import java.util.List;

import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 统一创作资产库列表项。
 */
public record AuthoringAssetLibraryItem(
    VersionedAssetType assetType,
    String assetId,
    String assetCode,
    String name,
    String category,
    List<String> tags,
    String version,
    String status,
    String packageVersion,
    boolean favorite,
    boolean cloneable,
    Instant updatedAt
) {
    public AuthoringAssetLibraryItem {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
