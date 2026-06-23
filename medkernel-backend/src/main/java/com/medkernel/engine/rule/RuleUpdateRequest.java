package com.medkernel.engine.rule;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.engine.versioning.AssetTriggerBindingInput;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 更新当前草稿规则版本的入参。
 *
 * <p>初始草稿可修改完整定义；已发布规则复制出的下一版草稿只允许修改版本正文与来源说明，
 * 稳定编码、风险和适用域元数据保持不变。
 */
public record RuleUpdateRequest(
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
    @NotNull List<AssetTriggerBindingInput> triggers,
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
    public RuleUpdateRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
        triggers = triggers == null ? List.of() : List.copyOf(triggers);
    }

    public RuleApiContext apiContext() {
        return new RuleApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes
        );
    }
}
