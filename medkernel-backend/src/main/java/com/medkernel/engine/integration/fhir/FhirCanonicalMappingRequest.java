package com.medkernel.engine.integration.fhir;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * FHIR 入站资源映射为标准临床资源的请求。
 */
public record FhirCanonicalMappingRequest(
    String tenantId,
    String runtimeReleaseId,
    String snapshotId,
    Integer seqNo,
    String traceId,
    Instant receivedAt,
    JsonNode resource
) {}
