package com.medkernel.engine.pathway;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.Size;

/**
 * 路径发布等无额外业务字段操作的统一入参。
 */
public record PathwayOperationRequest(
    @JsonAlias("request_id") String requestId,
    @JsonAlias("trace_id") String traceId,
    @JsonAlias("tenant_id") String tenantId,
    @JsonAlias("group_id") String groupId,
    @JsonAlias("hospital_id") String hospitalId,
    @JsonAlias("campus_id") String campusId,
    @JsonAlias("site_id") String siteId,
    @JsonAlias("department_id") String departmentId,
    @JsonAlias("specialty_id") String specialtyId,
    @JsonAlias("user_id") String userId,
    @JsonAlias("role_codes") List<String> roleCodes,
    @JsonAlias("package_version") String packageVersion,
    @Size(max = 128) String impactDigest,
    @Size(max = 500) String reason,
    @JsonAlias("release_step") String releaseStep,
    @JsonAlias("direct_full_rollout") Boolean directFullRollout,
    @JsonAlias("rollback_target_template_id") String rollbackTargetTemplateId
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
