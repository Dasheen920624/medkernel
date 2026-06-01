package com.medkernel.engine.security.auth;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 登录失败状态仓库：按租户 + 用户名聚合查询，避免爆破尝试绕过凭证存在性。
 */
@Repository
public interface LoginAttemptStateRepository extends ListCrudRepository<LoginAttemptState, Long> {

    Optional<LoginAttemptState> findByTenantIdAndUsername(String tenantId, String username);
}
