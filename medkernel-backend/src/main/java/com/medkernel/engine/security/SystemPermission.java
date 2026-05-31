package com.medkernel.engine.security;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 系统权限点目录实体。
 *
 * <p>目录记录权限编码、五维归属、目标对象和风险级别，供配置、审计与前端权限画像共享同一元数据。
 */
@Table("sys_permission")
public record SystemPermission(
    @Id Long id,
    @Column("permission_code") String permissionCode,
    @Column("dimension") PermissionDimension dimension,
    @Column("target") String target,
    @Column("display_name") String displayName,
    @Column("risk_level") String riskLevel,
    @Column("active_flag") String activeFlag,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {

    /** 将目录编码映射回权限枚举。 */
    public Optional<PermissionCode> permission() {
        return PermissionCode.fromCode(permissionCode);
    }

    /** 是否仍可用于授权。 */
    public boolean active() {
        return "Y".equalsIgnoreCase(activeFlag);
    }
}
