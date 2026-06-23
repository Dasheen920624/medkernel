package com.medkernel.engine.authoring;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.medkernel.shared.api.ApiError;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 创作体验接口的标准上下文字段。
 *
 * <p>标准上下文只表达操作者和组织范围；预览所需的资产范围由草稿正文、运行修订或显式业务参数解析，
 * 不再把手工运行版本作为通用门槛。
 */
public record AuthoringApiContext(
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
    public AuthoringApiContext {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    void validateTenant(String currentTenantId) {
        List<ApiError> errors = new ArrayList<>();
        requireText(errors, "request_id", requestId);
        requireText(errors, "trace_id", traceId);
        requireText(errors, "tenant_id", tenantId);
        requireText(errors, "user_id", userId);
        if (roleCodes.isEmpty()) {
            errors.add(new ApiError("role_codes", "NotEmpty", "标准上下文 role_codes 不能为空"));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "创作预览 API 缺少统一入参字段", errors, null);
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
}
