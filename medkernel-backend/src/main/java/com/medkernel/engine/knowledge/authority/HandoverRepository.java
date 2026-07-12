package com.medkernel.engine.knowledge.authority;

import java.util.Optional;

import org.springframework.data.repository.Repository;

/** 权威迁移交接仓储端口。 */
@org.springframework.stereotype.Repository
public interface HandoverRepository extends Repository<Handover, Long> {

    Handover save(Handover handover);

    Optional<Handover> findByTenantIdAndAuthorityIdAndHandoverId(
        String tenantId,
        String authorityId,
        String handoverId
    );

    Optional<Handover> findByTenantIdAndAuthorityIdAndHandoverSequence(
        String tenantId,
        String authorityId,
        long handoverSequence
    );

    Optional<Handover> findFirstByTenantIdAndAuthorityIdOrderByHandoverSequenceDesc(
        String tenantId,
        String authorityId
    );
}
