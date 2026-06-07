package com.medkernel.engine.rule;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建规则的入参（GA-ENG-API-05 {@code POST /api/v1/engine/rule/rules}）。
 *
 * <p>字段语义见规则引擎 API 设计文档：{@code ruleCode}/{@code name}/{@code sourceRef}/{@code dsl} 为必填，
 * {@code authoringMode}/{@code riskLevel} 缺省由服务端兜底（默认 DSL / MEDIUM）。
 */
public record RuleCreateRequest(
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
    @NotBlank String ruleCode,
    @NotBlank String name,
    @NotNull RuleType ruleType,
    RuleAuthoringMode authoringMode,
    RuleRiskLevel riskLevel,
    @Min(0) @Max(1000) Integer priority,
    @Size(max = 128) String suppressedBy,
    @Min(0) @Max(86400) Integer dedupeWindowSeconds,
    String applicableOrgUnitId,
    @NotBlank String sourceRef,
    String changeSummary,
    @NotNull JsonNode dsl,
    JsonNode explanation
) implements RuleContextRequest {
    public RuleCreateRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public RuleCreateRequest(String ruleCode,
                             String name,
                             RuleType ruleType,
                             RuleAuthoringMode authoringMode,
                             RuleRiskLevel riskLevel,
                             String packageVersion,
                             String applicableOrgUnitId,
                             String sourceRef,
                             String changeSummary,
                             JsonNode dsl,
                             JsonNode explanation) {
        this(null, null, null, null, null, null, null, null, null, null, List.of(), packageVersion,
            ruleCode, name, ruleType, authoringMode, riskLevel, 100, null, 0, applicableOrgUnitId,
            sourceRef, changeSummary, dsl, explanation);
    }

    public RuleApiContext apiContext() {
        return new RuleApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion
        );
    }
}
