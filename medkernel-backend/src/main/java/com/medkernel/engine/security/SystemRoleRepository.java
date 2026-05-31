package com.medkernel.engine.security;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 系统角色目录仓储。
 */
@Repository
public interface SystemRoleRepository extends ListCrudRepository<SystemRole, Long> {

    /** 查询指定租户下的可用角色目录。 */
    List<SystemRole> findByTenantIdAndActiveFlag(String tenantId, String activeFlag);
}
