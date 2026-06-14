package com.medkernel.engine.integration.masterdata;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 院内主数据原子同步批次。
 */
public record MasterDataSyncRequest(
    @NotBlank @Size(max = 128) String batchId,
    @NotBlank @Size(max = 128) String adapterId,
    @NotBlank @Size(max = 64) String sourceSystem,
    @NotNull MasterDataSyncMode mode,
    @Size(max = 256) String previousCursor,
    @NotBlank @Size(max = 256) String cursor,
    Set<MasterDataResourceType> authoritativeResourceTypes,
    @Valid @NotNull @Size(max = 5000) List<MasterDataSyncItem> items
) {
    public MasterDataSyncRequest {
        authoritativeResourceTypes = authoritativeResourceTypes == null
            ? Set.of()
            : Set.copyOf(authoritativeResourceTypes);
        items = items == null ? List.of() : List.copyOf(items);
    }
}
