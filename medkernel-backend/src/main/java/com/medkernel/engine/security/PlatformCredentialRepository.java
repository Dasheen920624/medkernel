package com.medkernel.engine.security;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 平台自建身份凭证仓库：按租户 + 用户名 / 用户标识查登录凭证，按业务 ID 查单条，按租户列出（管理用）。
 */
@Repository
public interface PlatformCredentialRepository extends ListCrudRepository<PlatformCredential, Long> {

    Optional<PlatformCredential> findByTenantIdAndUsername(String tenantId, String username);

    Optional<PlatformCredential> findByTenantIdAndUserId(String tenantId, String userId);

    List<PlatformCredential> findByTenantIdAndUserIdIn(String tenantId, List<String> userIds);

    Optional<PlatformCredential> findByCredentialId(String credentialId);

    List<PlatformCredential> findByTenantIdOrderByUsernameAsc(String tenantId);

    List<PlatformCredential> findByTenantIdOrderByUsernameAsc(String tenantId, Pageable pageable);

    long countByTenantId(String tenantId);

    long countByTenantIdAndStatus(String tenantId, String status);
}
