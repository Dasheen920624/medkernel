package com.medkernel.engine.knowledge.authority;

import java.util.Optional;

import org.springframework.data.repository.Repository;

/** 平台知识权威仓储端口；查询必须显式携带平台租户。 */
@org.springframework.stereotype.Repository
public interface AuthorityRepository extends Repository<Authority, Long> {

    Authority save(Authority authority);

    Optional<Authority> findByTenantId(String tenantId);

    Optional<Authority> findByTenantIdAndAuthorityId(String tenantId, String authorityId);
}
