package com.medkernel.compliance.datapermission;

import java.time.Instant;
import java.util.List;

import com.medkernel.shared.security.DataAccessLevel;

/**
 * SYS-06 数据权限策略响应。
 */
public record DataPermissionPolicyResponse(
    String policyId,
    String tenantId,
    String resourceType,
    DataPermissionAction action,
    DataAccessLevel minDataLevel,
    List<String> allowedColumns,
    String groupId,
    String hospitalId,
    String campusId,
    String siteId,
    String departmentId,
    String wardId,
    String specialtyId,
    DataPermissionStatus status,
    Long version,
    Instant createdAt,
    String createdBy,
    Instant updatedAt,
    String updatedBy,
    String traceId
) {

    static DataPermissionPolicyResponse from(DataPermissionPolicy policy, List<String> allowedColumns) {
        return new DataPermissionPolicyResponse(
            policy.policyId(),
            policy.tenantId(),
            policy.resourceType(),
            DataPermissionAction.valueOf(policy.action()),
            DataAccessLevel.valueOf(policy.minDataLevel()),
            List.copyOf(allowedColumns),
            policy.groupId(),
            policy.hospitalId(),
            policy.campusId(),
            policy.siteId(),
            policy.departmentId(),
            policy.wardId(),
            policy.specialtyId(),
            DataPermissionStatus.valueOf(policy.status()),
            policy.version(),
            policy.createdAt(),
            policy.createdBy(),
            policy.updatedAt(),
            policy.updatedBy(),
            policy.traceId());
    }
}
