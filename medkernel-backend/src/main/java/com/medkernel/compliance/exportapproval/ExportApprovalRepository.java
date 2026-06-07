package com.medkernel.compliance.exportapproval;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * SYS-06 导出审批仓储。
 */
@Repository
public interface ExportApprovalRepository extends ListCrudRepository<ExportApproval, Long> {

    Optional<ExportApproval> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

    Optional<ExportApproval> findByTenantIdAndApprovalId(String tenantId, String approvalId);

    List<ExportApproval> findByTenantIdOrderByRequestedAtDesc(String tenantId);

    List<ExportApproval> findByTenantIdAndStatusOrderByRequestedAtDesc(String tenantId, String status);

    List<ExportApproval> findByTenantIdAndResourceTypeOrderByRequestedAtDesc(
        String tenantId, String resourceType);

    List<ExportApproval> findByTenantIdAndResourceTypeAndStatusOrderByRequestedAtDesc(
        String tenantId, String resourceType, String status);
}
