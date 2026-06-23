package com.medkernel.engine.knowledge.diagnosis;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.medkernel.engine.knowledge.KnowledgeApiContext;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 在既有诊断知识身份下创建证据完整的新版本。
 */
public record DiagnosisVersionCreateRequest(
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
    @Valid @NotNull DiagnosisAssetCreateRequest.SourceInput source,
    @Valid @NotNull DiagnosisAssetCreateRequest.VersionInput version,
    @Valid @NotNull DiagnosisAssetCreateRequest.EvidenceInput evidence
) {
    public DiagnosisVersionCreateRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public KnowledgeApiContext context() {
        return new KnowledgeApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes);
    }
}
