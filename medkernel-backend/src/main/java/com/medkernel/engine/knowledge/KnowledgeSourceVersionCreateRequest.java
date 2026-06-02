package com.medkernel.engine.knowledge;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.NotBlank;

/**
 * 来源文献版本登记的标准 API-03 请求。
 */
public record KnowledgeSourceVersionCreateRequest(
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
    @JsonAlias("version_no") @NotBlank String versionNo,
    @JsonAlias("published_at") Instant publishedAt,
    @JsonAlias("content_hash") String contentHash,
    @JsonAlias("file_uri") @NotBlank String fileUri,
    String language,
    String content
) {

    public KnowledgeSourceVersionCreateRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public KnowledgeApiContext context() {
        return KnowledgeApiContext.from(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion
        );
    }

    SourceVersionRegisterRequest toSourceVersionRegisterRequest(Long sourceDocumentId) {
        return new SourceVersionRegisterRequest(sourceDocumentId, versionNo, publishedAt, contentHash, fileUri, language, content);
    }
}
