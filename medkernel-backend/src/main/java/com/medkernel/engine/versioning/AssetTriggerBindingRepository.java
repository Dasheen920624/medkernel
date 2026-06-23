package com.medkernel.engine.versioning;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 资产版本触发绑定仓库。
 */
@Repository
public interface AssetTriggerBindingRepository
        extends ListCrudRepository<AssetTriggerBinding, Long> {

    List<AssetTriggerBinding> findByTenantIdAndVersionIdAndPurposeOrderByTriggerPointAsc(
        String tenantId,
        String versionId,
        AssetTriggerPurpose purpose
    );

    List<AssetTriggerBinding>
        findByTenantIdAndVersionIdAndPurposeAndTriggerPointOrderByTriggerBindingIdAsc(
            String tenantId,
            String versionId,
            AssetTriggerPurpose purpose,
            String triggerPoint
        );

    List<AssetTriggerBinding> findByTenantIdAndVersionIdOrderByPurposeAscTriggerPointAsc(
        String tenantId,
        String versionId
    );

    void deleteByTenantIdAndVersionId(String tenantId, String versionId);
}
