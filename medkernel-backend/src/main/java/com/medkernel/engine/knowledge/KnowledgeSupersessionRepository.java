package com.medkernel.engine.knowledge;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 知识资产版本更替历史关系（Supersession）仓储接口。
 *
 * <p>提供针对医学指南逻辑及计算资产更迭（旧版隔离、原子替换及冲突审计）关系的处理，
 * 支撑 GA-ENG-KNOW-02 知识版本引擎在版本热更新、原子灰度发布与追溯重放时的演化世系链条（Lineage）。
 */
@Repository
public interface KnowledgeSupersessionRepository extends ListCrudRepository<KnowledgeSupersession, Long> {

    List<KnowledgeSupersession> findByTenantIdAndIdentityIdOrderByTransitionedAtAsc(String tenantId, Long identityId);

    @Query("""
        SELECT d.* FROM knowledge_supersession d
        WHERE d.transition_type = 'DEPRECATE'
          AND d.grace_period_end <= :now
          AND NOT EXISTS (
              SELECT 1 FROM knowledge_supersession r
              WHERE r.tenant_id = d.tenant_id
                AND r.identity_id = d.identity_id
                AND r.transition_type = 'RETIRE'
          )
        ORDER BY d.grace_period_end ASC, d.id ASC
        """)
    List<KnowledgeSupersession> findDueDeprecations(java.time.Instant now);

    @Query("""
        SELECT * FROM knowledge_supersession
        WHERE tenant_id = :tenantId AND identity_id = :identityId
          AND transitioned_at > :sinceExclusive
        ORDER BY transitioned_at ASC, id ASC
        OFFSET 0 ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<KnowledgeSupersession> lineageAfter(String tenantId, Long identityId,
                                             java.time.Instant sinceExclusive, int limit);

    @Query("""
        SELECT * FROM knowledge_supersession
        WHERE tenant_id = :tenantId
        ORDER BY transitioned_at ASC, id ASC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<KnowledgeSupersession> pageByTenantId(String tenantId, int offset, int limit);
}
