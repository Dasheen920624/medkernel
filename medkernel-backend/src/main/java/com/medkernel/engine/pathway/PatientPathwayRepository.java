package com.medkernel.engine.pathway;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 患者路径实例仓库。
 *
 * <p>保存患者与入径时已激活路径模板版本的运行实例、当前节点、状态和退出/完成事实。
 */
@Repository
public interface PatientPathwayRepository extends ListCrudRepository<PatientPathway, Long> {

    /**
     * 按患者路径业务 ID 和租户查询运行实例。
     */
    Optional<PatientPathway> findByPatientPathwayIdAndTenantId(String patientPathwayId, String tenantId);

    /**
     * 查询模板下的患者路径实例，并按入径时间倒序排列。
     */
    List<PatientPathway> findByTemplateIdAndTenantIdOrderByEnteredAtDesc(String templateId, String tenantId);

    /**
     * 按租户、患者和状态统计患者路径实例，用于服务端分页总数。
     */
    @Query("""
        SELECT COUNT(*) FROM patient_pathway
        WHERE tenant_id = :tenantId
          AND (:patientId IS NULL OR :patientId = '' OR patient_id = :patientId)
          AND (:status IS NULL OR :status = '' OR status = :status)
        """)
    long countByTenantIdAndFilters(
        @Param("tenantId") String tenantId,
        @Param("patientId") String patientId,
        @Param("status") String status);

    /**
     * 按租户、患者和状态分页查询患者路径实例。
     */
    @Query("""
        SELECT * FROM patient_pathway
        WHERE tenant_id = :tenantId
          AND (:patientId IS NULL OR :patientId = '' OR patient_id = :patientId)
          AND (:status IS NULL OR :status = '' OR status = :status)
        ORDER BY entered_at DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<PatientPathway> pageByTenantIdAndFilters(
        @Param("tenantId") String tenantId,
        @Param("patientId") String patientId,
        @Param("status") String status,
        @Param("offset") int offset,
        @Param("limit") int limit);

    /**
     * 统计当前租户仍在运行中的患者路径实例。
     */
    @Query("""
        SELECT COUNT(*) FROM patient_pathway
        WHERE tenant_id = :tenantId
          AND status IN ('ENTERED', 'NODE_EXECUTING', 'VARIANCE')
        """)
    long countActiveByTenantId(@Param("tenantId") String tenantId);

    /**
     * 统计指定患者仍在运行中的路径实例。
     */
    @Query("""
        SELECT COUNT(*) FROM patient_pathway
        WHERE tenant_id = :tenantId
          AND patient_id = :patientId
          AND status IN ('ENTERED', 'NODE_EXECUTING', 'VARIANCE')
        """)
    long countActiveByTenantIdAndPatientId(
        @Param("tenantId") String tenantId,
        @Param("patientId") String patientId);

    /**
     * 查询指定患者最近的活跃路径实例。
     */
    @Query("""
        SELECT * FROM patient_pathway
        WHERE tenant_id = :tenantId
          AND patient_id = :patientId
          AND status IN ('ENTERED', 'NODE_EXECUTING', 'VARIANCE')
        ORDER BY entered_at DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<PatientPathway> findActiveByTenantIdAndPatientIdOrderByEnteredAtDesc(
        @Param("tenantId") String tenantId,
        @Param("patientId") String patientId,
        @Param("offset") int offset,
        @Param("limit") int limit);
}
