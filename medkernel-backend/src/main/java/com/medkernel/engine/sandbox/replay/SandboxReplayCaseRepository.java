package com.medkernel.engine.sandbox.replay;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/** 历史重放清单仓储。 */
@Repository
public interface SandboxReplayCaseRepository extends ListCrudRepository<SandboxReplayCase, Long> {
    Optional<SandboxReplayCase> findBySandboxTenantIdAndReplayCaseId(
        String sandboxTenantId,
        String replayCaseId
    );
}
