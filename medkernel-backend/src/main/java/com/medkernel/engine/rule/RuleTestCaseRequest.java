package com.medkernel.engine.rule;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 新增规则测试用例的入参（GA-ENG-API-05 {@code POST /api/v1/engine/rule/rules/{ruleId}/test-cases}）。
 *
 * <p>{@code caseType} 与 {@code contextSnapshotId} 必填；服务端读取当前租户 ACTIVE 快照并固化资源副本，
 * 不接受客户端提交任意上下文载荷。{@code expectedSeverity}/{@code expectedActionCode} 在期望命中场景下
 * 为发布门禁的对照值。
 */
public record RuleTestCaseRequest(
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
    @NotNull RuleTestCaseType caseType,
    @NotBlank String contextSnapshotId,
    boolean expectedHit,
    RuleRiskLevel expectedSeverity,
    String expectedActionCode
) implements RuleContextRequest {
    public RuleTestCaseRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public RuleTestCaseRequest(RuleTestCaseType caseType,
                               String contextSnapshotId,
                               boolean expectedHit,
                               RuleRiskLevel expectedSeverity,
                               String expectedActionCode) {
        this(null, null, null, null, null, null, null, null, null, null, List.of(),
            caseType, contextSnapshotId, expectedHit, expectedSeverity, expectedActionCode);
    }

    public RuleApiContext apiContext() {
        return new RuleApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes
        );
    }
}
