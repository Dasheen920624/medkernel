package com.medkernel.engine.pkg;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 平台包租户授权开通或续期请求。
 */
public record PackageEntitlementGrantRequest(
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

    @NotBlank(message = "目标租户不能为空")
    @Size(max = 64, message = "目标租户长度不能超过64")
    String targetTenantId,

    @NotNull(message = "授权到期时间不能为空")
    Instant expiresAt,

    @NotBlank(message = "授权原因不能为空")
    @Size(max = 500, message = "授权原因长度不能超过500")
    String reason
) implements PackageContextRequest {

    public PackageEntitlementGrantRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public PackageEntitlementGrantRequest(String targetTenantId, Instant expiresAt, String reason) {
        this(null, null, null, null, null, null, null, null, null, null, List.of(), null,
            targetTenantId, expiresAt, reason);
    }

    @Override
    public PackageApiContext apiContext() {
        return new PackageApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, contextPackageVersion);
    }
}
