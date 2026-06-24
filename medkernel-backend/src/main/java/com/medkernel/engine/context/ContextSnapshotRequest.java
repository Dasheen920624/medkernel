package com.medkernel.engine.context;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * POST /api/v1/engine/context/snapshots 请求体。
 */
public record ContextSnapshotRequest(
    @JsonProperty("request_id") String requestId,
    @JsonProperty("trace_id") String traceId,
    @JsonProperty("tenant_id") String tenantId,
    @JsonProperty("group_id") String groupId,
    @JsonProperty("hospital_id") String hospitalId,
    @JsonProperty("campus_id") String campusId,
    @JsonProperty("site_id") String siteId,
    @JsonProperty("department_id") String departmentId,
    @JsonProperty("ward_id") String wardId,
    @JsonProperty("specialty_id") String specialtyId,
    @JsonProperty("user_id") String userId,
    @JsonProperty("role_codes") List<String> roleCodes,
    @NotBlank String patientId,
    String encounterId,
    @NotBlank String orgUnitId,
    @NotNull @Valid ContextSnapshotResources resources
) {

    public ContextSnapshotRequest(
            String requestId,
            String traceId,
            String tenantId,
            String groupId,
            String hospitalId,
            String campusId,
            String siteId,
            String departmentId,
            String specialtyId,
            String userId,
            List<String> roleCodes,
            String patientId,
            String encounterId,
            String orgUnitId,
            ContextSnapshotResources resources) {
        this(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, null, specialtyId, userId, roleCodes, patientId,
            encounterId, orgUnitId, resources);
    }

    public ContextSnapshotRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public String effectiveIdempotencyKey(String headerIdempotencyKey) {
        return firstNonBlank(requestId, headerIdempotencyKey);
    }

    public String effectiveTraceId(String currentTraceId) {
        return firstNonBlank(currentTraceId, traceId);
    }

    static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
