package com.medkernel.engine.versioning;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 灰度观测事实仓库。
 */
@Repository
public interface VersionRolloutObservationRepository
        extends ListCrudRepository<VersionRolloutObservation, Long> {

    List<VersionRolloutObservation> findByTenantIdAndPlanIdOrderByObservedAtAsc(
        String tenantId,
        String planId
    );
}
