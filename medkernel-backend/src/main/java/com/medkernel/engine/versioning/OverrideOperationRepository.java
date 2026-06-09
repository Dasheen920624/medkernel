package com.medkernel.engine.versioning;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 覆盖批量操作仓库。
 */
@Repository
public interface OverrideOperationRepository extends ListCrudRepository<OverrideOperation, Long> {

    Optional<OverrideOperation> findByOperationIdAndTenantId(String operationId, String tenantId);
}
