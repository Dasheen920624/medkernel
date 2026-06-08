package com.medkernel.engine.authoring;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 创作批量任务仓储。
 */
@Repository
public interface AuthoringBatchJobRepository extends ListCrudRepository<AuthoringBatchJob, Long> {

    Optional<AuthoringBatchJob> findByTenantIdAndJobId(String tenantId, String jobId);

    List<AuthoringBatchJob> findTop50ByTenantIdOrderByCreatedAtDesc(String tenantId);
}
