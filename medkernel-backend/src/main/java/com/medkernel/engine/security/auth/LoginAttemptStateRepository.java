package com.medkernel.engine.security.auth;

import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 登录失败状态仓库：按租户 + 用户名聚合查询，避免爆破尝试绕过凭证存在性。
 */
@Repository
public interface LoginAttemptStateRepository extends ListCrudRepository<LoginAttemptState, Long> {

    Optional<LoginAttemptState> findByTenantIdAndUsername(String tenantId, String username);

    /**
     * 锁定同一租户与用户名的登录尝试状态，保证失败累加与成功清零串行生效。
     */
    @Query("""
        SELECT * FROM sys_login_attempt
        WHERE tenant_id = :tenantId AND username = :username
        FOR UPDATE
        """)
    Optional<LoginAttemptState> findByTenantIdAndUsernameForUpdate(String tenantId, String username);
}
