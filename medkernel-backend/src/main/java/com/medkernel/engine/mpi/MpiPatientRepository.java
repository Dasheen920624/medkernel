package com.medkernel.engine.mpi;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 患者主索引（MPI）仓储访问接口。
 */
@Repository
public interface MpiPatientRepository extends ListCrudRepository<MpiPatient, Long> {

    Optional<MpiPatient> findByTenantIdAndMpiId(String tenantId, String mpiId);

    @Query("SELECT * FROM mpi_patient WHERE tenant_id = :tenantId AND (:status IS NULL OR status = :status) " +
           "AND (:keyword IS NULL OR :keyword = '' OR masked_name LIKE CONCAT('%', :keyword, '%') OR mpi_id LIKE CONCAT('%', :keyword, '%')) " +
           "ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    List<MpiPatient> findPatients(
        @Param("tenantId") String tenantId,
        @Param("keyword") String keyword,
        @Param("status") String status,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    @Query("SELECT COUNT(*) FROM mpi_patient WHERE tenant_id = :tenantId AND (:status IS NULL OR status = :status) " +
           "AND (:keyword IS NULL OR :keyword = '' OR masked_name LIKE CONCAT('%', :keyword, '%') OR mpi_id LIKE CONCAT('%', :keyword, '%'))")
    long countPatients(
        @Param("tenantId") String tenantId,
        @Param("keyword") String keyword,
        @Param("status") String status
    );

    @Query("SELECT COUNT(*) FROM mpi_patient WHERE tenant_id = :tenantId AND status = 'ACTIVE'")
    long countActive(@Param("tenantId") String tenantId);

    @Query("""
        SELECT COALESCE(SUM(
            CASE WHEN masked_name IS NOT NULL AND TRIM(masked_name) <> '' THEN 1 ELSE 0 END
          + CASE WHEN gender IS NOT NULL AND TRIM(gender) <> '' THEN 1 ELSE 0 END
          + CASE WHEN age IS NOT NULL AND age > 0 THEN 1 ELSE 0 END
          + CASE WHEN id_last4 IS NOT NULL AND TRIM(id_last4) <> '' THEN 1 ELSE 0 END
        ), 0)
        FROM mpi_patient
        WHERE tenant_id = :tenantId AND status = 'ACTIVE'
        """)
    long countActiveRequiredFieldsPresent(@Param("tenantId") String tenantId);

    @Query("SELECT COUNT(*) FROM mpi_patient WHERE tenant_id = :tenantId AND status = 'MERGED_INTO'")
    long countMerged(@Param("tenantId") String tenantId);

    @Query("SELECT AVG(age) FROM mpi_patient WHERE tenant_id = :tenantId AND status = 'ACTIVE'")
    Double averageAge(@Param("tenantId") String tenantId);

    @Query("SELECT gender, COUNT(*) AS cnt FROM mpi_patient WHERE tenant_id = :tenantId AND status = 'ACTIVE' GROUP BY gender")
    List<GenderCount> countGender(@Param("tenantId") String tenantId);

    record GenderCount(String gender, Long cnt) {}
}
