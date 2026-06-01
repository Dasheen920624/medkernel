package com.medkernel.engine.security.auth;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 受控密码重置 token 仓库。
 */
@Repository
public interface PasswordResetTokenRepository extends ListCrudRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTenantIdAndUserIdAndTokenHashAndUsedAtIsNull(
        String tenantId,
        String userId,
        String tokenHash);
}
