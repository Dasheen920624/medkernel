package com.medkernel.engine.pkg;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 从当前组织范围的已确认术语映射构建统一知识包请求。
 */
public record TerminologyPackageBuildRequest(
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

    @NotBlank(message = "包编码不能为空")
    @Size(max = 128, message = "包编码长度不能超过128")
    String packageCode,

    @NotBlank(message = "包版本不能为空")
    @Size(max = 64, message = "包版本长度不能超过64")
    String packageVersion,

    @NotBlank(message = "包名称不能为空")
    @Size(max = 256, message = "包名称长度不能超过256")
    String name,

    @NotBlank(message = "范围层级不能为空")
    @Size(max = 32, message = "范围层级长度不能超过32")
    String scopeLevel,

    @NotBlank(message = "范围编码不能为空")
    @Size(max = 64, message = "范围编码长度不能超过64")
    String scopeCode
) implements PackageContextRequest {
    public TerminologyPackageBuildRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public PackageApiContext apiContext() {
        return new PackageApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, contextPackageVersion
        );
    }
}
