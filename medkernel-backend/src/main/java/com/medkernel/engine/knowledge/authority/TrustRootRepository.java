package com.medkernel.engine.knowledge.authority;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

/** 固定信任根仓储端口。 */
@org.springframework.stereotype.Repository
public interface TrustRootRepository extends Repository<TrustRoot, Long> {

    TrustRoot save(TrustRoot trustRoot);

    Optional<TrustRoot> findByTenantIdAndAuthorityIdAndRootFingerprint(
        String tenantId,
        String authorityId,
        String rootFingerprint
    );

    List<TrustRoot> findByTenantIdAndAuthorityIdAndStatusOrderByEffectiveHandoverSequenceDesc(
        String tenantId,
        String authorityId,
        TrustRootStatus status
    );
}
