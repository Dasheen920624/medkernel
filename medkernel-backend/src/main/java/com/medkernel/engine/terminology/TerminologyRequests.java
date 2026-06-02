package com.medkernel.engine.terminology;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
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
 * <p>聚合确认候选、候选生成、冲突处置、映射包构建、发布及回滚等入参契约。
 */
public final class TerminologyRequests {
    private TerminologyRequests() {}
}

/**
 * API-04 写入类请求的标准上下文字段。
 */
record TerminologyApiContext(
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
    @JsonAlias("package_version") String packageVersion
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
        requireText(errors, "package_version", packageVersion);
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
            List<String> roleCodes,
            String packageVersion) {
        return new TerminologyApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion
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
    @NotBlank @Size(max = 64) String sourceSystem,
    Double minimumScore
) implements TerminologyContextRequest {
    TerminologyCandidateGenerationRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public TerminologyApiContext context() {
        return TerminologyApiContext.from(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion
        );
    }
}

/**
 * 确认候选映射请求体。
 *
 * <p>高危候选必须逐条提交 highRiskAcknowledged 与 highRiskReason。
 */
record TerminologyCandidateConfirmRequest(
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
    @Size(max = 500) String reviewNote,
    @Size(max = 1024) String evidenceOverride,
    Boolean highRiskAcknowledged,
    @Size(max = 500) String highRiskReason
) implements TerminologyContextRequest {
    TerminologyCandidateConfirmRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public TerminologyApiContext context() {
        return TerminologyApiContext.from(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion
        );
    }
}

/**
 * 批量确认候选请求体；服务层会拒绝任何高风险候选。
 */
record TerminologyCandidateBatchConfirmRequest(
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
            departmentId, specialtyId, userId, roleCodes, packageVersion
        );
    }
}

/**
 * 冲突处置请求体；resolutionNote 必填，作为冲突处置原因留痕。
 */
record ResolveConflictRequest(
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
    @NotBlank @Size(max = 500) String resolutionNote
) implements TerminologyContextRequest {
    ResolveConflictRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public TerminologyApiContext context() {
        return TerminologyApiContext.from(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion
        );
    }
}

/**
 * 构建术语映射包请求体。
 *
 * <p>packageCode 业务编码 + packageVersion 版本号 + scopeLevel/scopeCode 作用域，
 * 与 (tenant_id) 一起构成包业务唯一键；displayName 用于前台展示。
 */
record BuildTerminologyPackageRequest(
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
    @JsonAlias("package_version") String contextPackageVersion,
    @NotBlank @Size(max = 128) String packageCode,
    @NotBlank @Size(max = 64) String packageVersion,
    @NotBlank @Size(max = 32) String scopeLevel,
    @NotBlank @Size(max = 64) String scopeCode,
    @NotBlank @Size(max = 256) String displayName
) implements TerminologyContextRequest {
    BuildTerminologyPackageRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public TerminologyApiContext context() {
        return TerminologyApiContext.from(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, contextPackageVersion
        );
    }
}

/**
 * 发布术语映射包请求体。
 *
 * <p>releaseMode GRAY/FULL；reason 必填留痕；grayScopeJson 灰度发布时声明灰度作用域 JSON。
 */
record PublishTerminologyPackageRequest(
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
    @NotNull PackageReleaseMode releaseMode,
    @NotBlank @Size(max = 500) String reason,
    @Size(max = 2048) String grayScopeJson
) implements TerminologyContextRequest {
    PublishTerminologyPackageRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public TerminologyApiContext context() {
        return TerminologyApiContext.from(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion
        );
    }
}

/**
 * 回滚术语映射包请求体；targetPackageId 必须是同 packageCode + scope 下的可回滚版本。
 */
record RollbackTerminologyPackageRequest(
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
    @NotNull Long targetPackageId,
    @NotBlank @Size(max = 500) String reason
) implements TerminologyContextRequest {
    RollbackTerminologyPackageRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public TerminologyApiContext context() {
        return TerminologyApiContext.from(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion
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
    String evidenceText
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
            candidate.evidenceText()
        );
    }
}

record TerminologyCandidateGenerationResponse(
    int generatedCount,
    List<TerminologyCandidateResponse> candidates
) {
    TerminologyCandidateGenerationResponse {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}

record TerminologyBatchConfirmResponse(int confirmedCount, List<Long> confirmedCandidateIds) {
    TerminologyBatchConfirmResponse {
        confirmedCandidateIds = confirmedCandidateIds == null ? List.of() : List.copyOf(confirmedCandidateIds);
    }
}
