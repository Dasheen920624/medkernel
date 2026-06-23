package com.medkernel.engine.context;

import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

/** 医院临床运行发布记录仓储。 */
public interface ClinicalRuntimeReleaseRepository
        extends ListCrudRepository<ClinicalRuntimeRelease, Long> {

    Optional<ClinicalRuntimeRelease> findByTenantIdAndReleaseId(
        String tenantId,
        String releaseId
    );

    Optional<ClinicalRuntimeRelease> findFirstByTenantIdAndHospitalIdOrderByRevisionNoDesc(
        String tenantId,
        String hospitalId
    );

    @Query("""
        SELECT * FROM clinical_runtime_release
        WHERE tenant_id = :tenantId
          AND hospital_id = :hospitalId
        ORDER BY revision_no DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    java.util.List<ClinicalRuntimeRelease> pageByTenantIdAndHospitalId(
        String tenantId,
        String hospitalId,
        int offset,
        int limit
    );

    @Query("""
        SELECT COUNT(*) FROM clinical_runtime_release
        WHERE tenant_id = :tenantId
          AND hospital_id = :hospitalId
        """)
    long countByTenantIdAndHospitalId(String tenantId, String hospitalId);

    @Query("""
        SELECT COUNT(DISTINCT hospital_id) FROM clinical_runtime_release
        WHERE tenant_id = :tenantId
        """)
    long countDistinctHospitalsByTenantId(String tenantId);
}
