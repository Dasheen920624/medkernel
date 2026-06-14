package com.medkernel.engine.integration.masterdata;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 院内主数据来源记录仓储。
 *
 * <p>按租户、来源系统、资源类型和来源记录 ID 保持唯一映射，并提供对账计数。
 */
@Repository
public interface MasterDataSyncRecordRepository extends ListCrudRepository<MasterDataSyncRecord, Long> {

    Optional<MasterDataSyncRecord> findByTenantIdAndSourceSystemAndResourceTypeAndSourceRecordId(
        String tenantId,
        String sourceSystem,
        MasterDataResourceType resourceType,
        String sourceRecordId
    );

    List<MasterDataSyncRecord> findByTenantIdAndSourceSystemAndResourceTypeAndStatus(
        String tenantId,
        String sourceSystem,
        MasterDataResourceType resourceType,
        MasterDataRecordStatus status
    );

    @Query("""
        SELECT COUNT(*) FROM mk_integration_master_data_sync_record
        WHERE tenant_id = :tenantId
          AND source_system = :sourceSystem
          AND resource_type = :resourceType
          AND status = :status
        """)
    long countByStatus(
        String tenantId,
        String sourceSystem,
        String resourceType,
        String status
    );
}
