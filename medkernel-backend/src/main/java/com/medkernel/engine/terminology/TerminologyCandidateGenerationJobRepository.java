package com.medkernel.engine.terminology;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 术语候选生成异步任务仓储；按租户隔离，对外以 job_code 寻址。
 */
@Repository
public interface TerminologyCandidateGenerationJobRepository
        extends ListCrudRepository<TerminologyCandidateGenerationJob, Long> {

    Optional<TerminologyCandidateGenerationJob> findByTenantIdAndJobCode(String tenantId, String jobCode);

    List<TerminologyCandidateGenerationJob> findTop100ByTenantIdOrderByCreatedAtDesc(String tenantId);
}
