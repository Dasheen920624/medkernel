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

    long countByTenantIdAndTargetType(String tenantId, ProjectionTargetType targetType);

    @Query("""
        SELECT COUNT(*) FROM mk_projection_snapshot
        WHERE tenant_id = :tenantId
          AND target_type = :targetType
          AND (
            :keyword IS NULL
            OR LOWER(fact_key) LIKE :keyword
            OR LOWER(object_type) LIKE :keyword
            OR LOWER(object_id) LIKE :keyword
            OR LOWER(subject_key) LIKE :keyword
            OR LOWER(predicate_name) LIKE :keyword
            OR LOWER(object_key) LIKE :keyword
            OR LOWER(trace_id) LIKE :keyword
          )
        """)
    long countByFilter(
        String tenantId,
        ProjectionTargetType targetType,
        String keyword
    );

    @Query("""
        SELECT * FROM mk_projection_snapshot
        WHERE tenant_id = :tenantId
          AND target_type = :targetType
          AND (
            :keyword IS NULL
            OR LOWER(fact_key) LIKE :keyword
            OR LOWER(object_type) LIKE :keyword
            OR LOWER(object_id) LIKE :keyword
            OR LOWER(subject_key) LIKE :keyword
            OR LOWER(predicate_name) LIKE :keyword
            OR LOWER(object_key) LIKE :keyword
            OR LOWER(trace_id) LIKE :keyword
          )
        ORDER BY fact_key
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<ProjectionSnapshot> pageByFilter(
        String tenantId,
        ProjectionTargetType targetType,
        String keyword,
        int offset,
        int limit
    );

    @Modifying
    @Query("""
        DELETE FROM mk_projection_snapshot
        WHERE tenant_id = :tenantId
          AND target_type = :targetType
        """)
    int deleteByTenantIdAndTargetType(String tenantId, ProjectionTargetType targetType);
}
