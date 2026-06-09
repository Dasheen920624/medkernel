package com.medkernel.engine.context;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 标准上下文聚合根仓库。
 *
 * <p>所有方法都按 {@code tenantId} 强制过滤；不提供未带租户的全表查询，避免跨租户泄漏。
 */
@Repository
public interface ContextSnapshotRepository extends ListCrudRepository<ContextSnapshot, Long> {

    Optional<ContextSnapshot> findBySnapshotIdAndTenantId(String snapshotId, String tenantId);

    @Query("""
        SELECT * FROM context_snapshot
        WHERE tenant_id = :tenantId AND patient_id = :patientId
        ORDER BY created_at DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<ContextSnapshot> pageByTenantIdAndPatientIdOrderByCreatedAtDesc(
        String tenantId, String patientId, int offset, int limit);

    @Query("SELECT COUNT(*) FROM context_snapshot WHERE tenant_id = :tenantId AND patient_id = :patientId")
    long countByTenantIdAndPatientId(String tenantId, String patientId);

    @Query("""
        SELECT * FROM context_snapshot
        WHERE tenant_id = :tenantId AND encounter_id = :encounterId
        ORDER BY created_at DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<ContextSnapshot> pageByTenantIdAndEncounterIdOrderByCreatedAtDesc(
        String tenantId, String encounterId, int offset, int limit);

    @Query("SELECT COUNT(*) FROM context_snapshot WHERE tenant_id = :tenantId AND encounter_id = :encounterId")
    long countByTenantIdAndEncounterId(String tenantId, String encounterId);

    @Query("""
        SELECT * FROM context_snapshot
        WHERE tenant_id = :tenantId
          AND status = 'ACTIVE'
          AND created_at >= :since
        ORDER BY created_at DESC
        OFFSET 0 ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<ContextSnapshot> findRecentActiveByTenantId(
        String tenantId,
        Instant since,
        int limit
    );
}
