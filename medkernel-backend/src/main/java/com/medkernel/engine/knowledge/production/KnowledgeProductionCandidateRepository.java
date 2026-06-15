package com.medkernel.engine.knowledge.production;

import java.util.Collection;
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

    /** 按候选引用反查生产血缘（AIK-STD-12 PR1，审核台来源溯源），强租户隔离。 */
    @Query("""
        SELECT * FROM mk_knowledge_production_candidate
        WHERE tenant_id = :tenantId AND candidate_ref IN (:candidateRefs)
        ORDER BY created_at ASC, id ASC
        """)
    List<KnowledgeProductionCandidate> findByTenantIdAndCandidateRefIn(
        String tenantId, Collection<String> candidateRefs);
}
