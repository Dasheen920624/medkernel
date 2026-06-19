package com.medkernel.engine.sandbox.replay;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/** 历史重放精确资产绑定仓储。 */
@Repository
public interface SandboxReplayAssetBindingRepository
        extends ListCrudRepository<SandboxReplayAssetBinding, Long> {
    List<SandboxReplayAssetBinding> findBySandboxTenantIdAndReplayCaseIdOrderByIdAsc(
        String sandboxTenantId,
        String replayCaseId
    );
}
