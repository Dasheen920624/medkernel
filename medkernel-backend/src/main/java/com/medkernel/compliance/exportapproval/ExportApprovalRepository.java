package com.medkernel.compliance.exportapproval;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * SYS-06 导出审批仓储。
 */
@Repository
public interface ExportApprovalRepository extends ListCrudRepository<ExportApproval, Long> {

    Optional<ExportApproval> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

    Optional<ExportApproval> findByTenantIdAndApprovalId(String tenantId, String approvalId);

    /**
     * 按租户、资源类型和审批状态统计导出审批，用于合规审批台服务端分页。
     */
    @Query("""
        SELECT COUNT(*) FROM mk_compliance_export_approval
        WHERE tenant_id = :tenantId
          AND (:resourceType IS NULL OR resource_type = :resourceType)
          AND (:status IS NULL OR status = :status)
        """)
    long countByFilter(String tenantId, String resourceType, String status);

    /**
     * 按租户、资源类型和审批状态分页查询导出审批，保持最新申请优先。
     */
    @Query("""
        SELECT * FROM mk_compliance_export_approval
        WHERE tenant_id = :tenantId
          AND (:resourceType IS NULL OR resource_type = :resourceType)
          AND (:status IS NULL OR status = :status)
        ORDER BY requested_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<ExportApproval> pageByFilter(
        String tenantId, String resourceType, String status, int offset, int limit);
}
