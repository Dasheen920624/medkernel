package com.medkernel.engine.pkg;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 知识包高危回滚确认请求。
 *
 * @param targetPackageId 回滚目标包 ID
 * @param confirmedCurrentVersion 操作人确认的当前在用版本
 * @param confirmedTargetVersion 操作人确认的目标回滚版本
 * @param reason 回滚原因，写入审计事实
 * @param confirmedHighRisk 高危回滚影响二次确认
 */
public record PackageRollbackRequest(
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

    @NotBlank(message = "回滚目标包不能为空")
    String targetPackageId,

    @NotBlank(message = "当前在用版本确认不能为空")
    String confirmedCurrentVersion,

    @NotBlank(message = "目标回滚版本确认不能为空")
    String confirmedTargetVersion,

    @NotBlank(message = "回滚原因不能为空")
    @Size(max = 500, message = "回滚原因不能超过 500 个字符")
    String reason,

    @NotNull(message = "高危回滚确认不能为空")
    Boolean confirmedHighRisk
) implements PackageContextRequest {
    public PackageRollbackRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public PackageRollbackRequest(
            String targetPackageId,
            String confirmedCurrentVersion,
            String confirmedTargetVersion,
            String reason,
            Boolean confirmedHighRisk) {
        this(null, null, null, null, null, null, null, null, null, null, List.of(), null,
            targetPackageId, confirmedCurrentVersion, confirmedTargetVersion, reason, confirmedHighRisk);
    }

    public PackageApiContext apiContext() {
        return new PackageApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion
        );
    }
}
