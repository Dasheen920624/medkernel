package com.medkernel.engine.knowledge.authority;

import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

/** 平台知识权威仓储端口；查询必须显式携带平台租户。 */
@org.springframework.stereotype.Repository
public interface AuthorityRepository extends Repository<Authority, Long> {

    Authority save(Authority authority);

    Optional<Authority> findByTenantId(String tenantId);

    Optional<Authority> findByTenantIdAndAuthorityId(String tenantId, String authorityId);

    /** 锁定权威发布游标，串行化完整包登记与首次签发实例接管。 */
    @Query("SELECT * FROM mk_knowledge_authority "
        + "WHERE tenant_id = :tenantId AND authority_id = :authorityId FOR UPDATE")
    Optional<Authority> findByTenantIdAndAuthorityIdForUpdate(
        String tenantId,
        String authorityId
    );
}
