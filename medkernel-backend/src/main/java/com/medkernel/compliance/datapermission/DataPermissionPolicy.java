package com.medkernel.compliance.datapermission;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * SYS-06 行列数据权限策略持久化实体。
 */
@Table("mk_compliance_data_permission")
public record DataPermissionPolicy(
    @Id Long id,
    @Column("policy_id") String policyId,
    @Column("tenant_id") String tenantId,
    @Column("resource_type") String resourceType,
    @Column("action") String action,
    @Column("min_data_level") String minDataLevel,
    @Column("allowed_columns_json") String allowedColumnsJson,
    @Column("group_id") String groupId,
    @Column("hospital_id") String hospitalId,
    @Column("campus_id") String campusId,
    @Column("site_id") String siteId,
    @Column("department_id") String departmentId,
    @Column("specialty_id") String specialtyId,
    @Column("status") String status,
    @Column("version") Long version,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {

    public DataPermissionPolicy withId(Long newId) {
        return new DataPermissionPolicy(newId, policyId, tenantId, resourceType, action, minDataLevel,
            allowedColumnsJson, groupId, hospitalId, campusId, siteId, departmentId, specialtyId, status,
            version, createdAt, createdBy, updatedAt, updatedBy, traceId);
    }
}
