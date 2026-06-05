package com.medkernel.compliance.datapermission;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * SYS-06 数据权限策略仓储。
 */
@Repository
public interface DataPermissionPolicyRepository extends ListCrudRepository<DataPermissionPolicy, Long> {

    Optional<DataPermissionPolicy> findByTenantIdAndResourceTypeAndAction(
        String tenantId, String resourceType, String action);

    @Query("SELECT * FROM mk_compliance_data_permission "
        + "WHERE tenant_id = :tenantId AND resource_type = :resourceType "
        + "AND action = :action AND status = 'ACTIVE'")
    Optional<DataPermissionPolicy> findActivePolicy(
        @Param("tenantId") String tenantId,
        @Param("resourceType") String resourceType,
        @Param("action") String action);

    @Query("SELECT * FROM mk_compliance_data_permission WHERE tenant_id = :tenantId "
        + "AND (:resourceType IS NULL OR resource_type = :resourceType) "
        + "AND (:action IS NULL OR action = :action) "
        + "ORDER BY resource_type ASC, action ASC")
    List<DataPermissionPolicy> findPolicies(
        @Param("tenantId") String tenantId,
        @Param("resourceType") String resourceType,
        @Param("action") String action);
}
