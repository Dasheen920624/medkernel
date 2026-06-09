package com.medkernel.engine.pkg;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 平台包租户授权仓储。
 */
@Repository
public interface PackageEntitlementRepository extends ListCrudRepository<PackageEntitlement, Long> {

    Optional<PackageEntitlement> findByTenantIdAndPlatformPackageId(
        String tenantId, String platformPackageId);

    List<PackageEntitlement> findByTenantIdAndPlatformPackageIdIn(
        String tenantId, Set<String> platformPackageIds);

    @Query("""
        SELECT * FROM mk_pkg_package_entitlement
        WHERE platform_package_id = :platformPackageId
        ORDER BY updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<PackageEntitlement> pageByPlatformPackageId(String platformPackageId, int offset, int limit);

    @Query("""
        SELECT COUNT(*) FROM mk_pkg_package_entitlement
        WHERE platform_package_id = :platformPackageId
        """)
    long countByPlatformPackageId(String platformPackageId);
}
