package com.medkernel.engine.knowledge.production;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 知识生产 job 仓储（AIK-STD-13）。所有查询强制带 tenantId。
 */
@Repository
public interface KnowledgeProductionJobRepository extends ListCrudRepository<KnowledgeProductionJob, Long> {

    Optional<KnowledgeProductionJob> findByTenantIdAndJobCode(String tenantId, String jobCode);

    @Query("""
        SELECT * FROM mk_knowledge_production_job
        WHERE tenant_id = :tenantId
        ORDER BY created_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<KnowledgeProductionJob> pageByTenantId(String tenantId, int offset, int limit);

    @Query("SELECT COUNT(*) FROM mk_knowledge_production_job WHERE tenant_id = :tenantId")
    long countByTenantId(String tenantId);
}
