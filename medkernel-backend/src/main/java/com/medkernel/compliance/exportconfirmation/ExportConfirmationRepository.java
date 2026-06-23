package com.medkernel.compliance.exportconfirmation;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 敏感数据导出确认仓储。
 */
@Repository
public interface ExportConfirmationRepository extends ListCrudRepository<ExportConfirmation, Long> {

    Optional<ExportConfirmation> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

    Optional<ExportConfirmation> findByTenantIdAndConfirmationId(String tenantId, String confirmationId);

    @Query("""
        SELECT COUNT(*) FROM mk_compliance_export_confirmation
        WHERE tenant_id = :tenantId
          AND (:resourceType IS NULL OR resource_type = :resourceType)
          AND (:status IS NULL OR status = :status)
        """)
    long countByFilter(String tenantId, String resourceType, String status);

    @Query("""
        SELECT * FROM mk_compliance_export_confirmation
        WHERE tenant_id = :tenantId
          AND (:resourceType IS NULL OR resource_type = :resourceType)
          AND (:status IS NULL OR status = :status)
        ORDER BY confirmed_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<ExportConfirmation> pageByFilter(
        String tenantId,
        String resourceType,
        String status,
        int offset,
        int limit
    );
}
