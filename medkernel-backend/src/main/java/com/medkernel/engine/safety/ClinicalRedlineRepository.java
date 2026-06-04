package com.medkernel.engine.safety;

import java.util.List;

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

    List<ClinicalRedlineRule> findByTenantIdAndCategoryAndStatusOrderByRedlineKeyAscUpdatedAtDesc(
        String tenantId,
        ClinicalRedlineCategory category,
        ClinicalRedlineStatus status);
}
