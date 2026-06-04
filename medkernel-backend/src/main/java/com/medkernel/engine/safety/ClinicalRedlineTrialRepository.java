package com.medkernel.engine.safety;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;

/**
 * OPT-04 临床安全红线静默试运行证据仓储。
 */
public interface ClinicalRedlineTrialRepository extends ListCrudRepository<ClinicalRedlineTrial, Long> {

    Optional<ClinicalRedlineTrial> findByTenantIdAndRedlineIdAndTrialId(
        String tenantId,
        String redlineId,
        String trialId);
}
