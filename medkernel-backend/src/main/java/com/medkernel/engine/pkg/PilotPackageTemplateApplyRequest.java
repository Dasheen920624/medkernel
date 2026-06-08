package com.medkernel.engine.pkg;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 应用首发模板推荐引用请求。
 */
public record PilotPackageTemplateApplyRequest(
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
    @JsonProperty("package_version") String contextPackageVersion,
    @JsonProperty("target_org_unit_id") String targetOrgUnitId,
    @JsonProperty("initial_overrides") List<PilotPackageInitialOverrideRequest> initialOverrides
) implements PackageContextRequest {
    public PilotPackageTemplateApplyRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
        initialOverrides = initialOverrides == null ? List.of() : List.copyOf(initialOverrides);
    }

    public PilotPackageTemplateApplyRequest(
            String targetOrgUnitId,
            List<PilotPackageInitialOverrideRequest> initialOverrides) {
        this(null, null, null, null, null, null, null, null, null, null, List.of(), null,
            targetOrgUnitId, initialOverrides);
    }

    public PackageApiContext apiContext() {
        return new PackageApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, contextPackageVersion
        );
    }
}
