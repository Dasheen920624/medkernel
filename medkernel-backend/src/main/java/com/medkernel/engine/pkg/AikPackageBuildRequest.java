package com.medkernel.engine.pkg;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * AIK 已审知识资产装配为知识包的请求 DTO。
 */
public record AikPackageBuildRequest(
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

    String description,

    @NotEmpty(message = "已审知识资产版本列表不能为空")
    List<Long> assetVersionIds
) implements PackageContextRequest {
    public AikPackageBuildRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
        assetVersionIds = assetVersionIds == null ? List.of() : List.copyOf(assetVersionIds);
    }

    public PackageApiContext apiContext() {
        return new PackageApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, contextPackageVersion
        );
    }
}
