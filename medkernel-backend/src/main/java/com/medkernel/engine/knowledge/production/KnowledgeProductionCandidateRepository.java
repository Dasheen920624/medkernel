package com.medkernel.engine.knowledge.production;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 知识候选生产血缘仓储（AIK-STD-13 PR2）。所有查询强制带 tenantId。
 */
@Repository
public interface KnowledgeProductionCandidateRepository
    extends ListCrudRepository<KnowledgeProductionCandidate, Long> {

    @Query("""
        SELECT * FROM mk_knowledge_production_candidate
        WHERE tenant_id = :tenantId AND job_code = :jobCode
        ORDER BY created_at ASC, id ASC
        """)
    List<KnowledgeProductionCandidate> findByTenantIdAndJobCode(String tenantId, String jobCode);
}
