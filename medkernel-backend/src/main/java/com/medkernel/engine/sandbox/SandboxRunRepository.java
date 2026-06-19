package com.medkernel.engine.sandbox;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/** 沙盘运行账本仓储。 */
@Repository
public interface SandboxRunRepository extends ListCrudRepository<SandboxRun, Long> {

    Optional<SandboxRun> findByTenantIdAndRunId(String tenantId, String runId);
}
