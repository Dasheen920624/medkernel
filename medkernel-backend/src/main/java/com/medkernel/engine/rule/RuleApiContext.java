package com.medkernel.engine.rule;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.medkernel.shared.api.ApiError;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * API-05 写入和执行类请求的标准上下文字段。
 *
 * <p>字段与 D2 统一入参保持一致；控制器在进入服务层前校验，确保租户、用户、角色与包版本
 * 不依赖前端兜底或旧路径默认值。
 */
public record RuleApiContext(
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
    public RuleApiContext {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    void validateTenant(String currentTenantId) {
        List<ApiError> errors = new ArrayList<>();
        requireText(errors, "request_id", requestId);
        requireText(errors, "trace_id", traceId);
        requireText(errors, "tenant_id", tenantId);
        requireText(errors, "user_id", userId);
        requireText(errors, "package_version", packageVersion);
        if (roleCodes.isEmpty()) {
            errors.add(new ApiError("role_codes", "NotEmpty", "标准上下文 role_codes 不能为空"));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "规则引擎 API 缺少统一入参字段", errors, null);
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

interface RuleContextRequest {
    RuleApiContext apiContext();
}
