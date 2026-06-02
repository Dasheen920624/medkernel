package com.medkernel.engine.knowledge;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 来源文献登记的标准 API-03 请求。
 */
public record KnowledgeSourceCreateRequest(
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
    @JsonAlias("source_code") @NotBlank String sourceCode,
    @JsonAlias("source_type") @NotNull SourceType sourceType,
    @JsonAlias("authority_level") @NotNull SourceAuthorityLevel authorityLevel,
    @NotBlank String title,
    String publisher,
    String license,
    String language
) {

    public KnowledgeSourceCreateRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public KnowledgeApiContext context() {
        return KnowledgeApiContext.from(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion
        );
    }

    SourceRegisterRequest toSourceRegisterRequest() {
        return new SourceRegisterRequest(sourceCode, sourceType, authorityLevel, title, publisher, license, language);
    }
}
