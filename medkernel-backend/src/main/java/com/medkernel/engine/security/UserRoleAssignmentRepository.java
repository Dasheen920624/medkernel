package com.medkernel.engine.security;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 用户角色分配关系（Assignment）数据库仓储接口。
 *
 * <p>处理具体医院租户中，用户与角色的映射关系及组织作用域限制（如科室、院区范围等），
 * 支撑 GA-ENG-BASE-02 身份权限引擎的多维度精细化动作与数据范围计算。
 */
@Repository
public interface UserRoleAssignmentRepository extends ListCrudRepository<UserRoleAssignment, Long> {

    List<UserRoleAssignment> findByTenantId(String tenantId);

    @Query("""
        SELECT * FROM user_role_assignment
        WHERE tenant_id = :tenantId
          AND user_id = :userId
          AND active_flag = 'Y'
        ORDER BY role_code, scope_level, scope_code
        """)
    List<UserRoleAssignment> findActiveByTenantIdAndUserId(String tenantId, String userId);

    boolean existsByTenantIdAndUserIdAndRoleCodeAndActiveFlag(
        String tenantId,
        String userId,
        String roleCode,
        String activeFlag);
}
