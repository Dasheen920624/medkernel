package com.medkernel.engine.datasvc.export;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 引擎数据服务层异步导出作业仓储。租户隔离查询，对外以 {@code job_code} 寻址。
 */
@Repository
public interface EngineDataExportJobRepository extends ListCrudRepository<EngineDataExportJob, Long> {

    Optional<EngineDataExportJob> findByTenantIdAndJobCode(String tenantId, String jobCode);

    Optional<EngineDataExportJob> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

    long countByTenantId(String tenantId);

    @Query("""
        SELECT * FROM mk_engine_data_export_job
        WHERE tenant_id = :tenantId
        ORDER BY created_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<EngineDataExportJob> pageByTenantId(String tenantId, int offset, int limit);
}
