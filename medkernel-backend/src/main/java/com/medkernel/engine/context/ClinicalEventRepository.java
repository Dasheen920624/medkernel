package com.medkernel.engine.context;

import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 临床事件（Clinical Event）核心数据库仓储接口。
 *
 * <p>提供针对实时临床就诊、诊断、医嘱变更事件的持久化、多条件筛选及分页检索支持，
 * 支撑 GA-ENG-API-02 临床事件引擎的审计与诊断回放。
 */
@Repository
public interface ClinicalEventRepository extends ListCrudRepository<ClinicalEvent, Long> {

    Optional<ClinicalEvent> findByEventIdAndTenantId(String eventId, String tenantId);

    Optional<ClinicalEvent> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

    @Query("""
        SELECT * FROM clinical_event
        WHERE tenant_id = :tenantId
          AND (:patientId IS NULL OR patient_id = :patientId)
          AND (:encounterId IS NULL OR encounter_id = :encounterId)
          AND (:status IS NULL OR processing_status = :status)
          AND (:eventType IS NULL OR event_type = :eventType)
        ORDER BY received_at DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    java.util.List<ClinicalEvent> pageByFilter(String tenantId, String patientId, String encounterId,
                                               String status, String eventType, int offset, int limit);

    @Query("""
        SELECT COUNT(*) FROM clinical_event
        WHERE tenant_id = :tenantId
          AND (:patientId IS NULL OR patient_id = :patientId)
          AND (:encounterId IS NULL OR encounter_id = :encounterId)
          AND (:status IS NULL OR processing_status = :status)
          AND (:eventType IS NULL OR event_type = :eventType)
        """)
    long countByFilter(String tenantId, String patientId, String encounterId,
                       String status, String eventType);
}
