package com.medkernel.engine.security;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 租户角色动作权限重写（Override）数据库仓储接口。
 *
 * <p>处理医院内部对默认角色权限映射表的增减定制，支持在默认策略上实现个性化动作授权重写，
 * 支撑 GA-ENG-BASE-02 身份权限引擎的多租户自定义授权隔离。
 */
@Repository
public interface RolePermissionOverrideRepository extends ListCrudRepository<RolePermissionOverride, Long> {

    @Query("""
        SELECT * FROM role_permission
        WHERE tenant_id = :tenantId
          AND role_code IN (:roleCodes)
        ORDER BY role_code, permission_code
        """)
    List<RolePermissionOverride> findByTenantIdAndRoleCodes(String tenantId, Collection<String> roleCodes);

    Optional<RolePermissionOverride> findByTenantIdAndRoleCodeAndPermissionCode(
        String tenantId,
        String roleCode,
        String permissionCode);
}
