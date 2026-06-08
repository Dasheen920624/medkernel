package com.medkernel.engine.rule;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.medkernel.engine.versioning.VersionPublishEvidence;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 规则治理状态推进请求。
 */
public record RuleGovernanceTransitionRequest(
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
    @NotNull RuleGovernanceState targetState,
    @Size(max = 128) String impactDigest,
    @NotBlank @Size(max = 500) String reason,
    VersionPublishEvidence publishEvidence
) implements RuleContextRequest {
    public RuleGovernanceTransitionRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
        publishEvidence = VersionPublishEvidence.orEmpty(publishEvidence);
    }

    public RuleGovernanceTransitionRequest(
            String requestId,
            String traceId,
            String tenantId,
            String groupId,
            String hospitalId,
            String campusId,
            String siteId,
            String departmentId,
            String specialtyId,
            String userId,
            List<String> roleCodes,
            String packageVersion,
            RuleGovernanceState targetState,
            String impactDigest,
            String reason) {
        this(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion,
            targetState, impactDigest, reason, VersionPublishEvidence.empty()
        );
    }

    public RuleGovernanceTransitionRequest(
            RuleGovernanceState targetState,
            String impactDigest,
            String reason) {
        this(targetState, impactDigest, reason, VersionPublishEvidence.empty());
    }

    public RuleGovernanceTransitionRequest(
            RuleGovernanceState targetState,
            String impactDigest,
            String reason,
            VersionPublishEvidence publishEvidence) {
        this(null, null, null, null, null, null, null, null, null, null, List.of(), null,
            targetState, impactDigest, reason, publishEvidence);
    }

    public RuleApiContext apiContext() {
        return new RuleApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion
        );
    }
}
