package com.medkernel.engine.authoring;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 创作批量任务逐项结果仓储。
 */
@Repository
public interface AuthoringBatchItemRepository extends ListCrudRepository<AuthoringBatchItem, Long> {

    List<AuthoringBatchItem> findByTenantIdAndJobIdOrderByIdAsc(String tenantId, String jobId);
}
