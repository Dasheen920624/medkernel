package com.medkernel.engine.knowledge.acquisition;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 公域资料来源白名单仓储。所有查询强制带 tenantId。
 */
@Repository
public interface KnowledgeAcquisitionSourceRepository extends ListCrudRepository<KnowledgeAcquisitionSource, Long> {

    Optional<KnowledgeAcquisitionSource> findByTenantIdAndSourceCode(String tenantId, String sourceCode);

    @Query("SELECT COUNT(*) FROM mk_knowledge_acquisition_source WHERE tenant_id = :tenantId")
    long countByTenantId(String tenantId);

    @Query("""
        SELECT * FROM mk_knowledge_acquisition_source
        WHERE tenant_id = :tenantId
        ORDER BY updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<KnowledgeAcquisitionSource> pageByTenantId(String tenantId, int offset, int limit);
}
