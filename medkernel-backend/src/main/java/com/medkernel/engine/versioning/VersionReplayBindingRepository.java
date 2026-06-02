package com.medkernel.engine.versioning;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 历史重放绑定仓库。
 */
@Repository
public interface VersionReplayBindingRepository extends ListCrudRepository<VersionReplayBinding, Long> {

    Optional<VersionReplayBinding> findByTenantIdAndBindingId(String tenantId, String bindingId);
}
