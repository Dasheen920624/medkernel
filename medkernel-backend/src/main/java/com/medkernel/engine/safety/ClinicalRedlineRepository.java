package com.medkernel.engine.safety;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 临床安全红线规则版本仓库。
 */
@Repository
public interface ClinicalRedlineRepository extends ListCrudRepository<ClinicalRedlineRule, Long> {

    List<ClinicalRedlineRule> findByTenantIdAndStatusOrderByCategoryAscRedlineKeyAscUpdatedAtDesc(
        String tenantId,
        ClinicalRedlineStatus status);

    @Query("""
        SELECT DISTINCT tenant_id FROM mk_engine_clinical_redline
        WHERE status = 'ACTIVE'
        ORDER BY tenant_id
        """)
    List<String> findTenantIdsWithActiveRedlines();

    List<ClinicalRedlineRule> findByTenantIdAndCategoryAndStatusOrderByRedlineKeyAscUpdatedAtDesc(
        String tenantId,
        ClinicalRedlineCategory category,
        ClinicalRedlineStatus status);

    Optional<ClinicalRedlineRule> findByTenantIdAndRedlineId(String tenantId, String redlineId);

    Optional<ClinicalRedlineRule> findByTenantIdAndActiveScopeKeyAndStatus(
        String tenantId,
        String activeScopeKey,
        ClinicalRedlineStatus status);
}
