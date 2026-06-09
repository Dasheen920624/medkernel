package com.medkernel.engine.pkg;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.medkernel.engine.pathway.SpecialtyProfileRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 从专病画像定义构建统一路径知识包的请求。
 */
public record PathwayPackageBuildRequest(
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

    @NotBlank(message = "病种编码不能为空")
    @Size(max = 128, message = "病种编码长度不能超过128")
    String diseaseCode,

    @NotBlank(message = "包名称不能为空")
    @Size(max = 256, message = "包名称长度不能超过256")
    String name,

    @NotBlank(message = "包版本不能为空")
    @Size(max = 64, message = "包版本长度不能超过64")
    String packageVersion,

    @NotBlank(message = "来源引用不能为空")
    @Size(max = 512, message = "来源引用长度不能超过512")
    String sourceRef,

    @Size(max = 2000, message = "说明长度不能超过2000")
    String description,

    List<@Valid SpecialtyProfileRequest> profiles
) implements PackageContextRequest {
    public PathwayPackageBuildRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
        profiles = profiles == null ? List.of() : List.copyOf(profiles);
    }

    public PackageApiContext apiContext() {
        return new PackageApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, contextPackageVersion
        );
    }
}
