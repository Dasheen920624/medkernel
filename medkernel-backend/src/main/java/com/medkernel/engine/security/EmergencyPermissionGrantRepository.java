package com.medkernel.engine.security;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 应急权限授予记录仓储。
 */
@Repository
public interface EmergencyPermissionGrantRepository extends ListCrudRepository<EmergencyPermissionGrant, Long> {

    @Query("""
        SELECT * FROM emergency_permission_grant
        WHERE tenant_id = :tenantId
          AND user_id = :userId
          AND permission_code = :permissionCode
          AND active_flag = 'Y'
          AND revoked_at IS NULL
          AND expires_at > :now
        ORDER BY expires_at DESC
        """)
    List<EmergencyPermissionGrant> findActiveByTenantIdAndUserIdAndPermissionCode(
        String tenantId,
        String userId,
        String permissionCode,
        Instant now
    );
}
