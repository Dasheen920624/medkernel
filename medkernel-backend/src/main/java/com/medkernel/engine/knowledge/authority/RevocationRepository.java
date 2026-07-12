package com.medkernel.engine.knowledge.authority;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

/** 单调密钥吊销仓储端口。 */
@org.springframework.stereotype.Repository
public interface RevocationRepository extends Repository<Revocation, Long> {

    Revocation save(Revocation revocation);

    Optional<Revocation> findByTenantIdAndAuthorityIdAndRevocationSequence(
        String tenantId,
        String authorityId,
        long revocationSequence
    );

    Optional<Revocation> findFirstByTenantIdAndAuthorityIdOrderByRevocationSequenceDesc(
        String tenantId,
        String authorityId
    );

    List<Revocation> findByTenantIdAndAuthorityIdAndKeyIdOrderByRevocationSequenceAsc(
        String tenantId,
        String authorityId,
        String keyId
    );
}
