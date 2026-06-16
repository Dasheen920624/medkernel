package com.medkernel.engine.integration.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

import com.medkernel.engine.integration.domain.IntegrationOnboarding;

/**
 * 第三方业务接口接入生命周期仓储。
 */
@Repository
public interface IntegrationOnboardingRepository extends ListCrudRepository<IntegrationOnboarding, Long> {

    List<IntegrationOnboarding> findAllByTenantId(String tenantId);

    @Query("""
        SELECT COUNT(*) FROM mk_integration_onboarding
        WHERE tenant_id = :tenantId
        """)
    long countByTenantId(String tenantId);

    @Query("""
        SELECT * FROM mk_integration_onboarding
        WHERE tenant_id = :tenantId
        ORDER BY updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<IntegrationOnboarding> pageByTenantId(String tenantId, int offset, int limit);

    Optional<IntegrationOnboarding> findByOnboardingIdAndTenantId(String onboardingId, String tenantId);
}
