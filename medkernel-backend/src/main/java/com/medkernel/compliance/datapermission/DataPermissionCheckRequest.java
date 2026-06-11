package com.medkernel.compliance.datapermission;

import java.util.List;

import com.medkernel.shared.context.OrgScope;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * SYS-06 数据权限检查请求。
 *
 * <p>租户由服务端当前请求上下文解析，调用方只能声明目标数据所属组织范围。
 */
public record DataPermissionCheckRequest(
    @NotBlank @Size(max = 128) String resourceType,
    DataPermissionAction action,
    @Size(max = 64) String groupId,
    @Size(max = 64) String hospitalId,
    @Size(max = 64) String campusId,
    @Size(max = 64) String siteId,
    @Size(max = 64) String departmentId,
    @Size(max = 64) String specialtyId,
    @NotEmpty @Size(max = 128) List<
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}", message = "字段名仅允许字母、数字和下划线") String>
        requestedColumns
) {

    DataPermissionCheck toCheck(String tenantId) {
        return new DataPermissionCheck(
            tenantId,
            resourceType,
            action == null ? DataPermissionAction.READ : action,
            new OrgScope(tenantId, groupId, hospitalId, campusId, siteId, departmentId, specialtyId),
            requestedColumns
        );
    }
}
