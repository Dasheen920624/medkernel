package com.medkernel.engine.pathway;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 专病画像仓库。
 *
 * <p>保存路径知识包下的分型、风险分层、准入、退出和随访计划摘要。
 */
@Repository
public interface SpecialtyProfileRepository extends ListCrudRepository<SpecialtyProfile, Long> {

    /**
     * 按画像业务 ID 和租户查询专病画像。
     */
    Optional<SpecialtyProfile> findByProfileIdAndTenantId(String profileId, String tenantId);

    /**
     * 查询路径知识包下所有画像，并按画像编码升序排列。
     */
    List<SpecialtyProfile> findByPackageIdAndTenantIdOrderByProfileCodeAsc(String packageId, String tenantId);
}
