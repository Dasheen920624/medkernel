package com.medkernel.engine.security;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 租户用户目录仓库，所有查询必须携带租户边界。
 */
@Repository
public interface TenantUserRepository extends ListCrudRepository<TenantUser, Long> {

    Optional<TenantUser> findByTenantIdAndUserId(String tenantId, String userId);

    List<TenantUser> findByTenantIdOrderByDisplayNameAsc(String tenantId, Pageable pageable);

    List<TenantUser> findByTenantIdAndStatusOrderByDisplayNameAsc(
        String tenantId,
        String status,
        Pageable pageable
    );

    long countByTenantId(String tenantId);

    long countByTenantIdAndStatus(String tenantId, String status);

    @Query("""
        SELECT COUNT(*) FROM tenant_user
        WHERE tenant_id = :tenantId AND status = 'ACTIVE'
          AND (:keyword IS NULL
            OR LOWER(display_name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(user_id) LIKE LOWER(CONCAT('%', :keyword, '%')))
        """)
    long countActiveDirectory(
        @Param("tenantId") String tenantId,
        @Param("keyword") String keyword
    );

    @Query("""
        SELECT * FROM tenant_user
        WHERE tenant_id = :tenantId AND status = 'ACTIVE'
          AND (:keyword IS NULL
            OR LOWER(display_name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(user_id) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY display_name, user_id
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<TenantUser> pageActiveDirectory(
        @Param("tenantId") String tenantId,
        @Param("keyword") String keyword,
        @Param("offset") int offset,
        @Param("limit") int limit
    );
}
