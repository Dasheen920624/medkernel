package com.medkernel.engine.knowledge;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 知识版本失效记录仓储。
 */
@Repository
public interface KnowledgeInvalidationRepository extends ListCrudRepository<KnowledgeInvalidation, Long> {

    List<KnowledgeInvalidation> findByTenantIdAndIdentityIdOrderByInvalidatedAtDesc(String tenantId, Long identityId);

    @Query("""
        SELECT * FROM mk_knowledge_invalidation
        WHERE tenant_id = :tenantId
        ORDER BY invalidated_at ASC, id ASC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<KnowledgeInvalidation> pageByTenantId(String tenantId, int offset, int limit);
}
