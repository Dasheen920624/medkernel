package com.medkernel.engine.knowledge.acquisition;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 公域资料获取运行账本仓储。所有查询强制带 tenantId。
 */
@Repository
public interface KnowledgeAcquisitionRunRepository extends ListCrudRepository<KnowledgeAcquisitionRun, Long> {

    Optional<KnowledgeAcquisitionRun> findByTenantIdAndRunCode(String tenantId, String runCode);

    @Query("SELECT COUNT(*) FROM mk_knowledge_acquisition_run WHERE tenant_id = :tenantId")
    long countByTenantId(String tenantId);

    @Query("""
        SELECT * FROM mk_knowledge_acquisition_run
        WHERE tenant_id = :tenantId
        ORDER BY created_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<KnowledgeAcquisitionRun> pageByTenantId(String tenantId, int offset, int limit);
}
