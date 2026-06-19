package com.medkernel.engine.knowledge.production.initialization;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/** 初始化批次仓储。 */
@Repository
public interface KnowledgeInitializationBatchRepository
    extends ListCrudRepository<KnowledgeInitializationBatch, Long> {

    Optional<KnowledgeInitializationBatch> findByTenantIdAndBatchCode(String tenantId, String batchCode);

    Optional<KnowledgeInitializationBatch> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

    Optional<KnowledgeInitializationBatch>
        findFirstByTenantIdAndReleaseTypeAndReleaseVersionAndPhaseAndStatusOrderByIdDesc(
            String tenantId,
            InitializationReleaseType releaseType,
            String releaseVersion,
            InitializationPhase phase,
            KnowledgeInitializationBatchStatus status
        );

    List<KnowledgeInitializationBatch> findByTenantIdOrderByCreatedAtDescIdDesc(String tenantId);
}
