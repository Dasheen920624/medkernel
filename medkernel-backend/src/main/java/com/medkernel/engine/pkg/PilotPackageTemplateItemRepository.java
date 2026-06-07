package com.medkernel.engine.pkg;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 试点首发配置包模板资产项仓储。
 */
@Repository
public interface PilotPackageTemplateItemRepository extends ListCrudRepository<PilotPackageTemplateItem, String> {

    List<PilotPackageTemplateItem> findByTenantIdAndTemplateIdOrderBySortOrderAsc(
        String tenantId, String templateId);
}
