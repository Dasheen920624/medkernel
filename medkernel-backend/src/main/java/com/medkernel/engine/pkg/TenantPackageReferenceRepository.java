package com.medkernel.engine.pkg;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 租户平台包引用仓储。
 */
@Repository
public interface TenantPackageReferenceRepository extends ListCrudRepository<TenantPackageReference, Long> {

    Optional<TenantPackageReference> findByTenantIdAndPackageCodeAndPackageVersionAndTargetOrgUnitId(
        String tenantId,
        String packageCode,
        String packageVersion,
        String targetOrgUnitId);

    List<TenantPackageReference> findByTenantIdAndStatusOrderByUpdatedAtDesc(
        String tenantId,
        TenantPackageReferenceStatus status);
}
