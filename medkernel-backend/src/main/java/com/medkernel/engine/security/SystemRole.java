package com.medkernel.engine.security;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 系统角色目录实体。
 *
 * <p>内置 13 个业务角色与系统超管以 {@code tenant_id = SYSTEM} 入库，租户级扩展角色后续仍按
 * {@code (tenant_id, role_code)} 唯一约束隔离。
 */
@Table("sys_role")
public record SystemRole(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("role_code") String roleCode,
    @Column("display_name") String displayName,
    @Column("description") String description,
    @Column("built_in_flag") String builtInFlag,
    @Column("active_flag") String activeFlag,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {

    /** 将目录编码映射回内置角色枚举。 */
    public Optional<RoleCode> role() {
        return RoleCode.fromCode(roleCode);
    }

    /** 是否为系统内置角色。 */
    public boolean builtIn() {
        return "Y".equalsIgnoreCase(builtInFlag);
    }

    /** 是否仍可用于授权。 */
    public boolean active() {
        return "Y".equalsIgnoreCase(activeFlag);
    }
}
