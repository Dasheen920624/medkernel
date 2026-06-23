package com.medkernel.engine.release;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 平台权威基线发布仓储。
 */
@Repository
public interface PlatformBaselineReleaseRepository
        extends ListCrudRepository<PlatformBaselineRelease, Long> {

    Optional<PlatformBaselineRelease> findByBaselineReleaseId(String baselineReleaseId);

    Optional<PlatformBaselineRelease> findFirstByOrderByRevisionNoDesc();
}
