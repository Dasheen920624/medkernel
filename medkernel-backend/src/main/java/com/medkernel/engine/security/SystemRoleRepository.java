package com.medkernel.engine.security;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 系统角色目录仓储。
 */
@Repository
public interface SystemRoleRepository extends ListCrudRepository<SystemRole, Long> {

    /** 查询指定租户下的可用角色目录。 */
    List<SystemRole> findByTenantIdAndActiveFlag(String tenantId, String activeFlag);

    /**
     * 锁定内置角色目录行，用作首次部署等系统级单例操作的数据库互斥点。
     */
    @Query("""
        SELECT * FROM sys_role
        WHERE tenant_id = :tenantId AND role_code = :roleCode
        FOR UPDATE
        """)
    Optional<SystemRole> findByTenantIdAndRoleCodeForUpdate(String tenantId, String roleCode);
}
