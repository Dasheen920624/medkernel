package com.medkernel.engine.knowledge.production.initialization;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/** 来源版本批准仓储。 */
@Repository
public interface SourceVersionApprovalRepository extends ListCrudRepository<SourceVersionApproval, Long> {

    Optional<SourceVersionApproval> findByTenantIdAndSourceVersionId(String tenantId, Long sourceVersionId);
}
