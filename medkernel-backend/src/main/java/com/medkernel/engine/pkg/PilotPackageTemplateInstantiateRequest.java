package com.medkernel.engine.pkg;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Size;

/**
 * 首发模板实例化请求。
 */
public record PilotPackageTemplateInstantiateRequest(
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

    @Size(max = 128, message = "包编码长度不能超过128")
    String packageCode,

    @Size(max = 64, message = "包版本长度不能超过64")
    String packageVersion,

    @Size(max = 256, message = "包名称长度不能超过256")
    String name,

    String description
) implements PackageContextRequest {
    public PilotPackageTemplateInstantiateRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public PilotPackageTemplateInstantiateRequest(
            String packageCode,
            String packageVersion,
            String name,
            String description) {
        this(null, null, null, null, null, null, null, null, null, null, List.of(), null,
            packageCode, packageVersion, name, description);
    }

    public PackageApiContext apiContext() {
        return new PackageApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, contextPackageVersion
        );
    }
}
