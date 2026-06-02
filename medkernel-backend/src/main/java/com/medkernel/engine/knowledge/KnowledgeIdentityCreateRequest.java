package com.medkernel.engine.knowledge;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建知识身份的标准 API-03 请求。
 */
public record KnowledgeIdentityCreateRequest(
    @JsonAlias("request_id") String requestId,
    @JsonAlias("trace_id") String traceId,
    @JsonAlias("tenant_id") String tenantId,
    @JsonAlias("group_id") String groupId,
    @JsonAlias("hospital_id") String hospitalId,
    @JsonAlias("campus_id") String campusId,
    @JsonAlias("site_id") String siteId,
    @JsonAlias("department_id") String departmentId,
    @JsonAlias("specialty_id") String contextSpecialtyId,
    @JsonAlias("user_id") String userId,
    @JsonAlias("role_codes") List<String> roleCodes,
    @JsonAlias("package_version") String packageVersion,
    @JsonAlias("identity_code") @NotBlank String identityCode,
    @NotNull KnowledgeDomain domain,
    @NotBlank String subject,
    @JsonAlias("asset_specialty_id") String assetSpecialtyId,
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
