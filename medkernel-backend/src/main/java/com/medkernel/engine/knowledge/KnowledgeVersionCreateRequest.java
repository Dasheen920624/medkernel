package com.medkernel.engine.knowledge;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 知识身份下创建版本的标准 API-03 请求。
 */
public record KnowledgeVersionCreateRequest(
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
    @JsonAlias("version_label") String versionLabel,
    @JsonAlias("source_document_id") @NotNull Long sourceDocumentId,
    @JsonAlias("source_version_id") @NotNull Long sourceVersionId,
    @NotBlank String content,
    String anchors,
    @JsonAlias("risk_level") @NotNull KnowledgeRiskLevel riskLevel,
    @JsonAlias("grade_quality") GradeEvidenceQuality gradeQuality,
    @JsonAlias("grade_strength") GradeRecommendationStrength gradeStrength
) {

    public KnowledgeVersionCreateRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public KnowledgeApiContext context() {
        return KnowledgeApiContext.from(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion
        );
    }

}
