package com.medkernel.engine.pkg;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 同步日志 Repository 接口。
 */
@Repository
public interface SyncLogRepository extends ListCrudRepository<SyncLog, Long> {

    Optional<SyncLog> findByLogIdAndTenantId(String logId, String tenantId);

    List<SyncLog> findByTenantIdAndPlanId(String tenantId, String planId);

    @Query("""
        SELECT COUNT(*)
        FROM sync_log sl
        JOIN release_plan rp
          ON rp.plan_id = sl.plan_id
         AND rp.tenant_id = sl.tenant_id
        WHERE sl.tenant_id = :tenantId
          AND rp.package_id = :packageId
        """)
    long countByTenantIdAndPackageId(String tenantId, String packageId);

    @Query("""
        SELECT sl.*
        FROM sync_log sl
        JOIN release_plan rp
          ON rp.plan_id = sl.plan_id
         AND rp.tenant_id = sl.tenant_id
        WHERE sl.tenant_id = :tenantId
          AND rp.package_id = :packageId
        ORDER BY rp.created_at DESC, sl.updated_at DESC, sl.id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<SyncLog> pageByTenantIdAndPackageId(
        String tenantId,
        String packageId,
        int offset,
        int limit
    );
}
