package com.medkernel.engine.pathway;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Size;

/**
 * 路径发布等无额外业务字段操作的统一入参。
 */
public record PathwayOperationRequest(
    @JsonProperty("request_id") String requestId,
    @JsonProperty("trace_id") String traceId,
    @JsonProperty("tenant_id") String tenantId,
    @JsonProperty("group_id") String groupId,
    @JsonProperty("hospital_id") String hospitalId,
    @JsonProperty("campus_id") String campusId,
    @JsonProperty("site_id") String siteId,
    @JsonProperty("department_id") String departmentId,
    @JsonProperty("specialty_id") String specialtyId,
    @JsonProperty("user_id") String userId,
    @JsonProperty("role_codes") List<String> roleCodes,
    @JsonProperty("package_version") String packageVersion,
    @Size(max = 128) String impactDigest,
    @Size(max = 500) String reason,
    String releaseStep,
    Boolean directFullRollout,
    String rollbackTargetTemplateId
) implements PathwayContextRequest {
    public PathwayOperationRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public PathwayOperationRequest(String impactDigest, String reason) {
        this(null, null, null, null, null, null, null, null, null, null, List.of(), null,
            impactDigest, reason, "submit_review", false, null);
    }

    public PathwayApiContext apiContext() {
        return new PathwayApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion
        );
    }
}
