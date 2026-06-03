package com.medkernel.engine.integration.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

import com.medkernel.engine.integration.domain.IntegrationOnboarding;

/**
 * 第三方业务接口接入生命周期仓储。
 */
@Repository
public interface IntegrationOnboardingRepository extends ListCrudRepository<IntegrationOnboarding, Long> {

    List<IntegrationOnboarding> findAllByTenantId(String tenantId);

    Optional<IntegrationOnboarding> findByOnboardingIdAndTenantId(String onboardingId, String tenantId);
}
