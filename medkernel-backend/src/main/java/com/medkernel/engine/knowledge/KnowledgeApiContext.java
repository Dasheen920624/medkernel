package com.medkernel.engine.knowledge;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.medkernel.shared.api.ApiError;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * API-03 写入类请求的标准上下文字段。
 *
 * <p>字段对齐 D2 标准 API 入参：request/trace、租户与六层组织、用户、角色和资产包版本。
 * 旧 GET 查询继续由 JWT → {@code RequestContext} 提供上下文；写入类请求必须显式携带本上下文。
 */
public record KnowledgeApiContext(
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
    @JsonProperty("package_version") String packageVersion
) {

    public KnowledgeApiContext {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public void validateTenant(String currentTenantId) {
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
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "标准知识资产 API 缺少统一入参字段", errors, null);
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

    static KnowledgeApiContext from(
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
        return new KnowledgeApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion
        );
    }
}
