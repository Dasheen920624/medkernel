package com.medkernel.engine.datasvc;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 引擎数据服务字段分级元数据仓储。字段策略按租户隔离。
 */
@Repository
public interface EngineDataFieldPolicyRepository extends ListCrudRepository<EngineDataFieldPolicy, Long> {

    Optional<EngineDataFieldPolicy> findByTenantIdAndFieldPath(String tenantId, String fieldPath);

    List<EngineDataFieldPolicy> findByTenantIdAndDataLevelAndStatus(
        String tenantId, EngineDataLevel dataLevel, String status);
}
