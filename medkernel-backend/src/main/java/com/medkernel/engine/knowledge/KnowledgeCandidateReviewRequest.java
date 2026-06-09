package com.medkernel.engine.knowledge;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.medkernel.engine.versioning.VersionPublishEvidence;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 知识候选审核请求。
 */
public record KnowledgeCandidateReviewRequest(
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
    @NotNull KnowledgeCandidateReviewDecision decision,
    @Size(max = 500) String reason,
    VersionPublishEvidence publishEvidence
) {

    public KnowledgeCandidateReviewRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
        publishEvidence = VersionPublishEvidence.orEmpty(publishEvidence);
    }

    public KnowledgeApiContext context() {
        return KnowledgeApiContext.from(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion
        );
    }
}
