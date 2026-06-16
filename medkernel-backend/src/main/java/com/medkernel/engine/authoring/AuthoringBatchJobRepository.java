package com.medkernel.engine.authoring;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 创作批量任务仓储。
 */
@Repository
public interface AuthoringBatchJobRepository extends ListCrudRepository<AuthoringBatchJob, Long> {

    Optional<AuthoringBatchJob> findByTenantIdAndJobId(String tenantId, String jobId);

    long countByTenantId(String tenantId);

    @Query("""
        SELECT * FROM mk_engine_authoring_batch_job
        WHERE tenant_id = :tenantId
        ORDER BY created_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<AuthoringBatchJob> pageByTenantId(String tenantId, int offset, int limit);
}
