package com.medkernel.engine.integration.masterdata;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 院内主数据批次中的单条来源记录。
 *
 * <p>来源版本和更新时间用于拒绝乱序覆盖，载荷由对应资源适配器按标准契约解析。
 */
public record MasterDataSyncItem(
    @NotBlank @Size(max = 256) String recordId,
    @NotNull MasterDataResourceType resourceType,
    @NotNull MasterDataOperation operation,
    @Positive long sourceVersion,
    @NotNull Instant sourceUpdatedAt,
    @NotNull JsonNode payload
) {
}
