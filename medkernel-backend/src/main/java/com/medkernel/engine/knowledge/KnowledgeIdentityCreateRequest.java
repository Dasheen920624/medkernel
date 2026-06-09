package com.medkernel.engine.knowledge;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建知识身份的标准 API-03 请求。
 */
public record KnowledgeIdentityCreateRequest(
    @JsonProperty("request_id") String requestId,
    @JsonProperty("trace_id") String traceId,
    @JsonProperty("tenant_id") String tenantId,
    @JsonProperty("group_id") String groupId,
    @JsonProperty("hospital_id") String hospitalId,
    @JsonProperty("campus_id") String campusId,
    @JsonProperty("site_id") String siteId,
    @JsonProperty("department_id") String departmentId,
    @JsonProperty("specialty_id") String contextSpecialtyId,
    @JsonProperty("user_id") String userId,
    @JsonProperty("role_codes") List<String> roleCodes,
    @JsonProperty("package_version") String packageVersion,
    @NotBlank @Size(max = 43) String identitySlug,
    @NotNull KnowledgeDomain domain,
    @NotBlank String subject,
    String assetSpecialtyId,
    String description
) {

    public KnowledgeIdentityCreateRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public KnowledgeApiContext context() {
        return KnowledgeApiContext.from(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, contextSpecialtyId, userId, roleCodes, packageVersion
        );
    }
}
