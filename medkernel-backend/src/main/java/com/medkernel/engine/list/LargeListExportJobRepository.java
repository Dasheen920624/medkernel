package com.medkernel.engine.list;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 大规模数据异步导出任务数据访问存储库。
 */
@Repository
public interface LargeListExportJobRepository extends CrudRepository<LargeListExportJob, String> {

    /**
     * 根据任务唯一ID查询导出任务详情。
     *
     * @param jobId 任务唯一ID
     * @return 任务实体
     */
    Optional<LargeListExportJob> findByJobId(String jobId);

    /**
     * 根据租户与幂等键查询已存在的导出任务。
     *
     * @param tenantId 租户ID
     * @param idempotencyKey 幂等键
     * @return 任务实体
     */
    Optional<LargeListExportJob> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);
}
