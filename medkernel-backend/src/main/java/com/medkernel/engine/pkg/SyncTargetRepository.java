package com.medkernel.engine.pkg;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 同步发布目标 Repository 接口。
 */
@Repository
public interface SyncTargetRepository extends ListCrudRepository<SyncTarget, Long> {

    Optional<SyncTarget> findByTargetIdAndTenantId(String targetId, String tenantId);

    List<SyncTarget> findByTenantIdAndStatus(String tenantId, SyncTargetStatus status);
}
