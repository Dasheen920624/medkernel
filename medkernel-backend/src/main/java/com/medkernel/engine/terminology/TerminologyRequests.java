package com.medkernel.engine.terminology;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.medkernel.engine.versioning.VersionPublishEvidence;
import com.medkernel.shared.api.ApiError;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 术语映射模块写操作请求对象。
 *
 * <p>聚合确认候选、候选生成、冲突处置、映射版本构建、发布及回滚等入参契约。
 */
public final class TerminologyRequests {
    private TerminologyRequests() {}
}

/**
 * API-04 写入类请求的标准上下文字段。
 *
 * <p>标准上下文只校验操作者和组织范围；字典映射快照、发布清单和导出制品不再通过通用运行版本门槛传递。
 */
record TerminologyApiContext(
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
    @JsonProperty("role_codes") List<String> roleCodes
) {
    TerminologyApiContext {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    void validateTenant(String currentTenantId) {
        List<ApiError> errors = new ArrayList<>();
        requireText(errors, "request_id", requestId);
        requireText(errors, "trace_id", traceId);
        requireText(errors, "tenant_id", tenantId);
        requireText(errors, "user_id", userId);
        if (roleCodes == null || roleCodes.isEmpty()) {
            errors.add(new ApiError("role_codes", "NotEmpty", "标准上下文 role_codes 不能为空"));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "字典映射 API 缺少统一入参字段", errors, null);
        }
        if (currentTenantId != null && !currentTenantId.isBlank() && !currentTenantId.equals(tenantId)) {
            throw new ApiException(ErrorCode.ORG_SCOPE_DENIED, "请求租户与当前会话租户不一致");
        }
    }

    private static void requireText(List<ApiError> errors, String field, String value) {
        if (value == null || value.isBlank()) {
            errors.add(new ApiError(field, "NotBlank", "标准上下文 " + field + " 不能为空"));
        }
    }

    static TerminologyApiContext from(
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
            List<String> roleCodes) {
        return new TerminologyApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes
        );
    }
}

interface TerminologyContextRequest {
    TerminologyApiContext context();
}

/**
 * 候选生成请求体；sourceSystem 指定院内来源系统。
 */
record TerminologyCandidateGenerationRequest(
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
    @NotBlank @Size(max = 64) String sourceSystem,
    Double minimumScore,
    Boolean semanticAssistEnabled
) implements TerminologyContextRequest {
    TerminologyCandidateGenerationRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public TerminologyApiContext context() {
        return TerminologyApiContext.from(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes
        );
    }
}

/**
 * 标准术语登记请求体；用于把试点所需标准字典条目登记为当前租户覆盖或平台基线。
 */
record StandardTermRegistrationRequest(
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
    @NotBlank @Size(max = 64) String standardSystem,
    @NotBlank @Size(max = 128) String termCode,
    @NotNull TermCategory category,
    @NotBlank @Size(max = 255) String displayName,
    @Size(max = 512) String normalizedName,
    @NotBlank @Size(max = 64) String versionNo,
    Long sourceVersionId,
    @Size(max = 1024) String evidenceText
) implements TerminologyContextRequest {
    StandardTermRegistrationRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public TerminologyApiContext context() {
        return TerminologyApiContext.from(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes
        );
    }
}

/**
 * 院内术语登记请求体；用于把 HIS/LIS/PACS 等来源系统原始码登记进当前租户。
 */
record LocalTermRegistrationRequest(
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
    @NotBlank @Size(max = 64) String sourceSystem,
    @NotBlank @Size(max = 128) String localCode,
    @NotNull TermCategory category,
    @NotBlank @Size(max = 255) String localName,
    @Size(max = 512) String normalizedName,
    @JsonProperty("local_department_id") @Size(max = 64) String localDepartmentId
) implements TerminologyContextRequest {
    LocalTermRegistrationRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public TerminologyApiContext context() {
        return TerminologyApiContext.from(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes
        );
    }
}

/**
 * 确认候选映射请求体。高危候选必须逐条确认，但不再设置额外双签字段。
 */
record TerminologyCandidateConfirmRequest(
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
    @Size(max = 500) String reviewNote,
    @Size(max = 1024) String evidenceOverride
) implements TerminologyContextRequest {
    TerminologyCandidateConfirmRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public TerminologyApiContext context() {
        return TerminologyApiContext.from(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes
        );
    }
}

/**
 * 驳回候选映射请求体。
 *
 * <p>驳回是高危错配候选的安全处置出口，必须填写可追溯的驳回理由。
 */
record TerminologyCandidateRejectRequest(
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
    @NotBlank @Size(max = 500) String reviewNote
) implements TerminologyContextRequest {
    TerminologyCandidateRejectRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public TerminologyApiContext context() {
        return TerminologyApiContext.from(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes
        );
    }
}

/**
 * 批量确认候选请求体；服务层会拒绝任何高风险候选。
 */
record TerminologyCandidateBatchConfirmRequest(
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
    @NotEmpty List<Long> candidateIds,
    @Size(max = 500) String reviewNote
) implements TerminologyContextRequest {
    TerminologyCandidateBatchConfirmRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
        candidateIds = candidateIds == null ? List.of() : List.copyOf(candidateIds);
    }

    public TerminologyApiContext context() {
        return TerminologyApiContext.from(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes
        );
    }
}

/**
 * 冲突处置请求体；resolutionNote 必填，作为冲突处置原因留痕。
 */
record ResolveConflictRequest(
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
    @NotBlank @Size(max = 500) String resolutionNote
) implements TerminologyContextRequest {
    ResolveConflictRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public TerminologyApiContext context() {
        return TerminologyApiContext.from(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes
        );
    }
}

/**
 * 候选响应，显式携带 API-04 要求的语义分与高危标识。
 */
record TerminologyCandidateResponse(
    Long id,
    Long localTermId,
    Long standardTermId,
    Double semanticMatchScore,
    boolean highRiskFlag,
    TermRiskLevel riskLevel,
    MappingCandidateSource source,
    MappingCandidateStatus status,
    String evidenceText,
    String generationJobCode
) {
    static TerminologyCandidateResponse from(MappingCandidate candidate) {
        return new TerminologyCandidateResponse(
            candidate.id(),
            candidate.localTermId(),
            candidate.standardTermId(),
            candidate.confidence(),
            candidate.riskLevel() == TermRiskLevel.HIGH,
            candidate.riskLevel(),
            candidate.candidateSource(),
            candidate.status(),
            candidate.evidenceText(),
            candidate.generationJobCode()
        );
    }
}

record TerminologyBatchConfirmResponse(int confirmedCount, List<Long> confirmedCandidateIds) {
    TerminologyBatchConfirmResponse {
        confirmedCandidateIds = confirmedCandidateIds == null ? List.of() : List.copyOf(confirmedCandidateIds);
    }
}
