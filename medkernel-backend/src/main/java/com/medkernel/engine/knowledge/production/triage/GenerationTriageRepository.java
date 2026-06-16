package com.medkernel.engine.knowledge.production.triage;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * AIK-STD-10 生成期分流结果仓储。
 */
@Repository
public interface GenerationTriageRepository extends ListCrudRepository<GenerationTriage, Long> {

    List<GenerationTriage> findByTenantIdAndJobCodeOrderByIdAsc(String tenantId, String jobCode);

    List<GenerationTriage> findByTenantIdAndTargetIdentityIdOrderByCreatedAtDescIdDesc(
        String tenantId, Long targetIdentityId);
}
