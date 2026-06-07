package com.medkernel.engine.rule;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 规则同行评审或临床委员会会签请求。
 */
public record RuleSignoffRequest(
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
    @NotNull RuleSignoffStage stage,
    @NotNull RuleSignoffDecision decision,
    @NotBlank @Size(max = 500) String reason
) implements RuleContextRequest {
    public RuleSignoffRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public RuleSignoffRequest(
            RuleSignoffStage stage,
            RuleSignoffDecision decision,
            String reason) {
        this(null, null, null, null, null, null, null, null, null, null, List.of(), null,
            stage, decision, reason);
    }

    public RuleApiContext apiContext() {
        return new RuleApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion
        );
    }
}
