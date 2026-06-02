package com.medkernel.engine.rule;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 规则真实执行入参（GA-ENG-API-05 {@code POST /api/v1/engine/rule/rules/evaluate}）。
 *
 * <p>{@code triggerPoint} 与 DSL 中 {@code trigger} 匹配的已发布规则参与本次评估；
 * {@code ruleIds} 留空表示用全租户已发布规则集合，否则限定到给定规则列表。
 */
public record RuleEvaluateRequest(
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
    @NotBlank String triggerPoint,
    @NotNull JsonNode context,
    String eventId,
    List<String> ruleIds
) implements RuleContextRequest {
    public RuleEvaluateRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
        ruleIds = ruleIds == null ? List.of() : List.copyOf(ruleIds);
    }

    public RuleEvaluateRequest(String triggerPoint, JsonNode context, String eventId, List<String> ruleIds) {
        this(null, null, null, null, null, null, null, null, null, null, List.of(), null,
            triggerPoint, context, eventId, ruleIds);
    }

    public RuleApiContext apiContext() {
        return new RuleApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion
        );
    }
}
