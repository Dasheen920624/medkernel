package com.medkernel.engine.rule;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

/**
 * 规则真实执行入参（GA-ENG-API-05 {@code POST /api/v1/engine/rule/rules/evaluate}）。
 *
 * <p>{@code triggerPoint} 与精确资产版本触发绑定匹配且已进入运行修订的规则参与本次评估；
 * {@code ruleIds} 留空表示使用当前有效规则集合，否则限定到给定规则列表。
 */
public record RuleEvaluateRequest(
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
    @NotBlank String triggerPoint,
    @NotBlank String contextSnapshotId,
    String eventId,
    List<String> ruleIds
) implements RuleContextRequest {
    public RuleEvaluateRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
        ruleIds = ruleIds == null ? List.of() : List.copyOf(ruleIds);
    }

    public RuleEvaluateRequest(String triggerPoint, String contextSnapshotId,
                               String eventId, List<String> ruleIds) {
        this(null, null, null, null, null, null, null, null, null, null, List.of(),
            triggerPoint, contextSnapshotId, eventId, ruleIds);
    }

    public RuleApiContext apiContext() {
        return new RuleApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes
        );
    }
}
