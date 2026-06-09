package com.medkernel.engine.knowledge.diagnosis;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.medkernel.engine.knowledge.GradeEvidenceQuality;
import com.medkernel.engine.knowledge.GradeRecommendationStrength;
import com.medkernel.engine.knowledge.KnowledgeApiContext;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.SourceType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 一次创建证据完整的诊断知识草稿，统一复用知识身份、版本、来源和引用模型。
 */
public record DiagnosisAssetCreateRequest(
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
    @Valid @NotNull IdentityInput identity,
    @Valid @NotNull SourceInput source,
    @Valid @NotNull VersionInput version,
    @Valid @NotNull EvidenceInput evidence
) {
    public DiagnosisAssetCreateRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public KnowledgeApiContext context() {
        return new KnowledgeApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion);
    }

    /** 诊断知识身份。 */
    public record IdentityInput(
        @NotBlank @Size(max = 43) String identitySlug,
        @NotBlank String subject,
        String assetSpecialtyId,
        String description
    ) {}

    /** 权威来源及其版本。 */
    public record SourceInput(
        @NotBlank String sourceCode,
        @NotNull SourceType sourceType,
        @NotNull SourceAuthorityLevel authorityLevel,
        @NotBlank String authorityBasis,
        @NotBlank String title,
        String publisher,
        String license,
        String language,
        @NotBlank String versionNo,
        Instant publishedAt,
        @NotBlank String fileUri,
        @NotBlank String content
    ) {}

    /** 诊断知识版本治理字段。 */
    public record VersionInput(
        @NotBlank String versionNo,
        String versionLabel,
        @NotNull KnowledgeRiskLevel riskLevel,
        @NotNull GradeEvidenceQuality gradeQuality,
        GradeRecommendationStrength gradeStrength,
        @NotNull @Min(1) @Max(60) Integer reviewCycleMonths
    ) {}

    /** 首个真实来源锚点。 */
    public record EvidenceInput(
        @NotBlank String anchorPath,
        @NotBlank String anchorLabel,
        @NotBlank String textExcerpt
    ) {}
}
