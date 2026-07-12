package com.medkernel.engine.knowledge.authority;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

/** 发布实例仓储端口；不暴露无租户主键读取和删除能力。 */
@org.springframework.stereotype.Repository
public interface IssuerInstanceRepository extends Repository<IssuerInstance, Long> {

    IssuerInstance save(IssuerInstance issuerInstance);

    Optional<IssuerInstance> findByTenantIdAndAuthorityIdAndIssuerInstanceId(
        String tenantId,
        String authorityId,
        String issuerInstanceId
    );

    Optional<IssuerInstance> findFirstByTenantIdAndAuthorityIdAndStatus(
        String tenantId,
        String authorityId,
        IssuerInstanceStatus status
    );

    List<IssuerInstance> findByTenantIdAndAuthorityIdOrderByCreatedAtAscIdAsc(
        String tenantId,
        String authorityId
    );
}
