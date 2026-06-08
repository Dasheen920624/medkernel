package com.medkernel.engine.org;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import com.medkernel.shared.context.OrgLevel;

/**
 * 组织单元实体（对应 {@code org_unit} 表，5 方言一致）。
 *
 * <p>使用 Spring Data JDBC 映射；列名 snake_case 与 DDL 对齐。
 * {@code level_code} 列名规避 Oracle/DM 保留字 LEVEL；Java 字段对外仍叫 {@code level}。
 */
@Table("org_unit")
public record OrgUnit(
    @Id String id,
    @Column("parent_id") String parentId,
    @Column("tenant_id") String tenantId,
    @Column("org_path") String orgPath,
    @Column("level_code") OrgLevel level,
    @Column("code") String code,
    @Column("name") String name,
    @Column("name_pinyin") String namePinyin,
    @Column("facility_type") OrgFacilityType facilityType,
    @Column("specialty_id") String specialtyId,
    @Column("status") OrgUnitStatus status,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {

    /**
     * 判断当前组织是否为租户内根节点。
     *
     * @return 未设置父节点时返回 {@code true}
     */
    public boolean isRoot() {
        return parentId == null;
    }

    /**
     * 判断当前组织是否处于启用状态。
     *
     * @return 状态为 {@link OrgUnitStatus#ACTIVE} 时返回 {@code true}
     */
    public boolean isActive() {
        return status == OrgUnitStatus.ACTIVE;
    }
}
