package com.medkernel.engine.knowledge;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

/**
 * 来源文献版本登记的标准 API-03 请求。
 */
public record KnowledgeSourceVersionCreateRequest(
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
    @NotBlank String versionNo,
    Instant publishedAt,
    String contentHash,
    @NotBlank String fileUri,
    String language,
    String content
) {

    public KnowledgeSourceVersionCreateRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public KnowledgeApiContext context() {
        return KnowledgeApiContext.from(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes
        );
    }

    SourceVersionRegisterRequest toSourceVersionRegisterRequest(Long sourceDocumentId) {
        return new SourceVersionRegisterRequest(sourceDocumentId, versionNo, publishedAt, contentHash, fileUri, language, content);
    }
}
