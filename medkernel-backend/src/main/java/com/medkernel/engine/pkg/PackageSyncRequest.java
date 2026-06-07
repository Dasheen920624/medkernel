package com.medkernel.engine.pkg;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * 知识包同步与发布请求 DTO。
 */
public record PackageSyncRequest(
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

    @NotBlank(message = "发布说明不能为空")
    String reason,

    @NotBlank(message = "目标组织 ID 不能为空")
    String targetOrgUnitId,

    @NotNull(message = "发布策略不能为空")
    ReleaseStrategy strategy,

    @NotNull(message = "作用域范围类型不能为空")
    ReleaseScopeType scopeType,

    String scopeValue,

    @NotEmpty(message = "发布适配器列表不能为空")
    List<String> adapterIds
) implements PackageContextRequest {
    public PackageSyncRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
        adapterIds = adapterIds == null ? null : List.copyOf(adapterIds);
    }

    public PackageSyncRequest(
            String targetOrgUnitId,
            ReleaseStrategy strategy,
            ReleaseScopeType scopeType,
            String scopeValue,
            List<String> adapterIds,
            String reason) {
        this(null, null, null, null, null, null, null, null, null, null, List.of(), null,
            reason, targetOrgUnitId, strategy, scopeType, scopeValue, adapterIds);
    }

    public PackageApiContext apiContext() {
        return new PackageApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion
        );
    }
}
