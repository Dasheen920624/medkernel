package com.medkernel.engine.pkg;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 发布计划 Repository 接口。
 */
@Repository
public interface ReleasePlanRepository extends ListCrudRepository<ReleasePlan, Long> {

    Optional<ReleasePlan> findByPlanIdAndTenantId(String planId, String tenantId);

    List<ReleasePlan> findByTenantIdAndPackageIdOrderByCreatedAtDesc(String tenantId, String packageId);

    List<ReleasePlan> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
