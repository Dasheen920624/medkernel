package com.medkernel.engine.knowledge.discovery;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 可信来源探索运行存证仓储（LLM-06）。所有查询强制带 tenantId。
 */
@Repository
public interface DiscoveryRunRepository extends ListCrudRepository<DiscoveryRun, Long> {

    Optional<DiscoveryRun> findByTenantIdAndId(String tenantId, Long id);

    @Query("SELECT COUNT(*) FROM mk_knowledge_discovery_run WHERE tenant_id = :tenantId")
    long countByTenantId(String tenantId);

    @Query("""
        SELECT * FROM mk_knowledge_discovery_run
        WHERE tenant_id = :tenantId
        ORDER BY executed_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<DiscoveryRun> pageByTenantId(String tenantId, int offset, int limit);
}
