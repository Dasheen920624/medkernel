package com.medkernel.compliance.datapermission;

import java.util.List;

import com.medkernel.shared.security.DataAccessLevel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * SYS-06 数据权限策略写入请求。
 */
public record DataPermissionPolicyRequest(
    @NotBlank @Size(max = 128) String resourceType,
    @NotNull DataPermissionAction action,
    @NotNull DataAccessLevel minDataLevel,
    @NotEmpty @Size(max = 128) List<
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}", message = "字段名仅允许字母、数字和下划线") String>
        allowedColumns,
    @Size(max = 64) String groupId,
    @Size(max = 64) String hospitalId,
    @Size(max = 64) String campusId,
    @Size(max = 64) String siteId,
    @Size(max = 64) String departmentId,
    @Size(max = 64) String specialtyId,
    @NotNull DataPermissionStatus status,
    @NotBlank @Size(max = 512) String reason,
    Long expectedVersion
) {
}
