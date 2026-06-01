package com.medkernel.engine.projection;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 投影同步任务仓储。
 */
@Repository
public interface ProjectionSyncRepository extends ListCrudRepository<ProjectionSync, Long> {

    Optional<ProjectionSync> findByTenantIdAndSyncId(String tenantId, String syncId);

    List<ProjectionSync> findByTenantIdAndTargetTypeOrderByStartedAtDesc(
        String tenantId,
        ProjectionTargetType targetType
    );
}
