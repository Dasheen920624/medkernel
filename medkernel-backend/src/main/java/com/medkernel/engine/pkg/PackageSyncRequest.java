package com.medkernel.engine.pkg;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * 知识包同步与发布请求 DTO。
 */
public record PackageSyncRequest(
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

    @NotBlank(message = "目标组织 ID 不能为空")
    String targetOrgUnitId,

    @NotNull(message = "发布策略不能为空")
    ReleaseStrategy strategy,

    @NotNull(message = "作用域范围类型不能为空")
    ReleaseScopeType scopeType,

    String scopeValue,

    @NotEmpty(message = "同步通道目标列表不能为空")
    List<String> targetIds
) implements PackageContextRequest {
    public PackageSyncRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
        targetIds = targetIds == null ? null : List.copyOf(targetIds);
    }

    public PackageSyncRequest(
            String targetOrgUnitId,
            ReleaseStrategy strategy,
            ReleaseScopeType scopeType,
            String scopeValue,
            List<String> targetIds) {
        this(null, null, null, null, null, null, null, null, null, null, List.of(), null,
            targetOrgUnitId, strategy, scopeType, scopeValue, targetIds);
    }

    public PackageApiContext apiContext() {
        return new PackageApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion
        );
    }
}
