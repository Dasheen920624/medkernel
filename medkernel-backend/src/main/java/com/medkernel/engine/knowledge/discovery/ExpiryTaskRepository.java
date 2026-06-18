package com.medkernel.engine.knowledge.discovery;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 过期治理任务仓储。
 */
@Repository
public interface ExpiryTaskRepository extends ListCrudRepository<ExpiryTask, Long> {

    List<ExpiryTask> findByTenantIdAndIdentityIdOrderByReviewDueAtAscIdAsc(String tenantId, Long identityId);
}
