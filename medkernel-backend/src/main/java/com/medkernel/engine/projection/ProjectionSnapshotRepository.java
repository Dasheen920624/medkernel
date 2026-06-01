package com.medkernel.engine.projection;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 投影快照仓储。
 */
@Repository
public interface ProjectionSnapshotRepository extends ListCrudRepository<ProjectionSnapshot, Long> {

    List<ProjectionSnapshot> findByTenantIdAndTargetType(String tenantId, ProjectionTargetType targetType);

    @Modifying
    @Query("""
        DELETE FROM mk_projection_snapshot
        WHERE tenant_id = :tenantId
          AND target_type = :targetType
        """)
    int deleteByTenantIdAndTargetType(String tenantId, ProjectionTargetType targetType);
}
