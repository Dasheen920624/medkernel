package com.medkernel.engine.pkg;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.Size;

/**
 * 首发模板实例化请求。
 */
public record PilotPackageTemplateInstantiateRequest(
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
    @JsonAlias("package_version") String contextPackageVersion,

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
