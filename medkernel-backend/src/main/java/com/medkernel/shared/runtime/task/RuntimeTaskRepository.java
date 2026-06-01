package com.medkernel.shared.runtime.task;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * SYS-05 运行任务仓储。
 */
@Repository
public interface RuntimeTaskRepository extends ListCrudRepository<RuntimeTaskRecord, Long> {

    Optional<RuntimeTaskRecord> findByTenantIdAndTaskId(String tenantId, String taskId);
}
