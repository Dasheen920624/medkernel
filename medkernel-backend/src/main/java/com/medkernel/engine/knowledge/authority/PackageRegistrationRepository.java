package com.medkernel.engine.knowledge.authority;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

/** 不可变包注册表仓储端口。 */
@org.springframework.stereotype.Repository
public interface PackageRegistrationRepository extends Repository<PackageRegistration, Long> {

    PackageRegistration save(PackageRegistration registration);

    Optional<PackageRegistration> findByTenantIdAndAuthorityIdAndDeliveryId(
        String tenantId,
        String authorityId,
        String deliveryId
    );

    Optional<PackageRegistration> findByTenantIdAndAuthorityIdAndReleaseSequence(
        String tenantId,
        String authorityId,
        long releaseSequence
    );

    Optional<PackageRegistration> findByTenantIdAndAuthorityIdAndDeliveryIdAndManifestDigest(
        String tenantId,
        String authorityId,
        String deliveryId,
        String manifestDigest
    );

    List<PackageRegistration> findByTenantIdAndAuthorityIdOrderByReleaseSequenceDesc(
        String tenantId,
        String authorityId
    );
}
