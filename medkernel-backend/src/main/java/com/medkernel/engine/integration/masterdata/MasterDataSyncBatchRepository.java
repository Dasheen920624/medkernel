package com.medkernel.engine.integration.masterdata;

import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 院内主数据同步批次仓储。
 */
@Repository
public interface MasterDataSyncBatchRepository extends ListCrudRepository<MasterDataSyncBatch, Long> {

    Optional<MasterDataSyncBatch> findByTenantIdAndSourceSystemAndBatchId(
        String tenantId,
        String sourceSystem,
        String batchId
    );

    @Query("""
        SELECT * FROM mk_integration_master_data_sync_batch
        WHERE tenant_id = :tenantId
          AND source_system = :sourceSystem
          AND status = 'SUCCESS'
        ORDER BY processed_at DESC, id DESC
        FETCH FIRST 1 ROWS ONLY
        """)
    Optional<MasterDataSyncBatch> findLatestSuccessful(String tenantId, String sourceSystem);
}
