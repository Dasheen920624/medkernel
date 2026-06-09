package com.medkernel.engine.versioning;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 覆盖模板项仓库。
 */
@Repository
public interface OverrideTemplateItemRepository extends ListCrudRepository<OverrideTemplateItem, Long> {

    List<OverrideTemplateItem> findByTemplateIdOrderByAssetTypeAscAssetIdentityAsc(String templateId);
}
