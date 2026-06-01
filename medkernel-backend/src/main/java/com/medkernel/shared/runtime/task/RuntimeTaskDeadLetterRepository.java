package com.medkernel.shared.runtime.task;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * SYS-05 运行任务死信仓储。
 */
@Repository
public interface RuntimeTaskDeadLetterRepository extends ListCrudRepository<RuntimeTaskDeadLetterRecord, Long> {

    Optional<RuntimeTaskDeadLetterRecord> findByTenantIdAndDeadLetterId(String tenantId, String deadLetterId);
}
