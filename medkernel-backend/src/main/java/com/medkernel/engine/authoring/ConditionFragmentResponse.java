package com.medkernel.engine.authoring;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 条件片段详情响应。
 */
public record ConditionFragmentResponse(
    String fragmentId,
    String tenantId,
    String fragmentCode,
    String name,
    String category,
    JsonNode bodyJson,
    Integer versionNo,
    ConditionFragmentStatus status,
    String packageVersion,
    Instant createdAt,
    String createdBy,
    Instant updatedAt,
    String updatedBy,
    String traceId
) {}
