package com.medkernel.engine.authoring;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 条件片段创建或更新请求。
 */
public record ConditionFragmentUpsertRequest(
    @NotBlank @Size(max = 64) String fragmentCode,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 64) String category,
    @NotNull JsonNode bodyJson,
    @NotNull @Min(1) Integer versionNo,
    @NotBlank @Size(max = 40) String packageVersion,
    ConditionFragmentStatus status
) {}
