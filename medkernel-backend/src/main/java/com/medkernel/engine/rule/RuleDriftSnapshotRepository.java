package com.medkernel.engine.rule;

import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 规则漂移监测快照仓库。
 */
@Repository
public interface RuleDriftSnapshotRepository extends ListCrudRepository<RuleDriftSnapshot, Long> {

    @Query("""
        SELECT * FROM rule_drift_snapshot
        WHERE tenant_id = :tenantId AND rule_id = :ruleId
        ORDER BY created_at DESC, id DESC
        FETCH NEXT 1 ROWS ONLY
        """)
    Optional<RuleDriftSnapshot> findLatestByTenantIdAndRuleId(String tenantId, String ruleId);
}
