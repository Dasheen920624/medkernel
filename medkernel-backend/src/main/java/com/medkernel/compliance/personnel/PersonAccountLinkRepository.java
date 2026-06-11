package com.medkernel.compliance.personnel;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/** 人员账号关联仓库。 */
@Repository
public interface PersonAccountLinkRepository
        extends ListCrudRepository<PersonAccountLink, String> {

    Optional<PersonAccountLink> findByTenantIdAndPersonId(String tenantId, String personId);

    Optional<PersonAccountLink> findByTenantIdAndUserId(String tenantId, String userId);
}
