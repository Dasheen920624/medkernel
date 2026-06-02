package com.medkernel.engine.rule;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * 规则测试执行等无额外业务字段操作的统一入参。
 */
public record RuleOperationRequest(
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
    @JsonAlias("package_version") String packageVersion
) implements RuleContextRequest {
    public RuleOperationRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public RuleApiContext apiContext() {
        return new RuleApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion
        );
    }
}
