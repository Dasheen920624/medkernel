package com.medkernel.engine.knowledge.production.initialization;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/** 初始化批次条目仓储。 */
@Repository
public interface KnowledgeInitializationItemRepository
    extends ListCrudRepository<KnowledgeInitializationItem, Long> {

    List<KnowledgeInitializationItem> findByTenantIdAndBatchIdOrderBySequenceNoAscIdAsc(
        String tenantId,
        Long batchId
    );

    @Query("""
        SELECT DISTINCT i.canonical_id
        FROM mk_knowledge_initialization_item i
        JOIN mk_knowledge_initialization_batch b
          ON b.id = i.batch_id AND b.tenant_id = i.tenant_id
        WHERE i.tenant_id = :tenantId
          AND i.status = 'APPROVED'
          AND b.status = 'COMPLETE'
        ORDER BY i.canonical_id
        """)
    List<String> findCompletedCanonicalIds(String tenantId);

    @Query("""
        SELECT i.*
        FROM mk_knowledge_initialization_item i
        JOIN mk_knowledge_initialization_batch b
          ON b.id = i.batch_id AND b.tenant_id = i.tenant_id
        WHERE i.tenant_id = :tenantId
          AND i.canonical_id = :canonicalId
          AND i.status = 'APPROVED'
          AND b.status = 'COMPLETE'
        ORDER BY b.id DESC, i.id DESC
        """)
    List<KnowledgeInitializationItem> findCompletedHistory(String tenantId, String canonicalId);
}
