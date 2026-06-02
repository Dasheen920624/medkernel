package com.medkernel.engine.rule;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotNull;

/**
 * 新增规则测试用例的入参（GA-ENG-API-05 {@code POST /api/v1/engine/rule/rules/{ruleId}/test-cases}）。
 *
 * <p>{@code caseType} 与 {@code inputPayload} 必填；{@code expectedSeverity}/{@code expectedActionCode}
 * 在期望命中场景下为发布门禁的对照值。
 */
public record RuleTestCaseRequest(
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
    @NotNull RuleTestCaseType caseType,
    @NotNull JsonNode inputPayload,
    boolean expectedHit,
    RuleRiskLevel expectedSeverity,
    String expectedActionCode
) implements RuleContextRequest {
    public RuleTestCaseRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public RuleTestCaseRequest(RuleTestCaseType caseType,
                               JsonNode inputPayload,
                               boolean expectedHit,
                               RuleRiskLevel expectedSeverity,
                               String expectedActionCode) {
        this(null, null, null, null, null, null, null, null, null, null, List.of(), null,
            caseType, inputPayload, expectedHit, expectedSeverity, expectedActionCode);
    }

    public RuleApiContext apiContext() {
        return new RuleApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion
        );
    }
}
