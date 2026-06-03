package com.medkernel.engine.knowledge;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 知识失效影响处置任务仓储。
 */
@Repository
public interface AffectedCaseTaskRepository extends ListCrudRepository<AffectedCaseTask, Long> {

    Optional<AffectedCaseTask> findByTenantIdAndTaskKey(String tenantId, String taskKey);

    List<AffectedCaseTask> findByTenantIdAndInvalidationIdOrderByCreatedAtAsc(String tenantId, Long invalidationId);

    @Query("""
        SELECT * FROM mk_knowledge_affected_case_task
        WHERE tenant_id = :tenantId
        ORDER BY created_at ASC, id ASC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<AffectedCaseTask> pageByTenantId(String tenantId, int offset, int limit);
}
