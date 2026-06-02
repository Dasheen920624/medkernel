package com.medkernel.engine.versioning;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 继承覆盖解释仓库。
 */
@Repository
public interface InheritanceOverrideRepository extends ListCrudRepository<InheritanceOverride, Long> {

    Optional<InheritanceOverride> findByTenantIdAndOverrideVersionId(String tenantId, String overrideVersionId);
}
