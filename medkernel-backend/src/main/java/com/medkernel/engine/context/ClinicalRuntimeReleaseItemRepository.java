package com.medkernel.engine.context;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 医院运行修订精确资产版本条目仓储。
 */
@Repository
public interface ClinicalRuntimeReleaseItemRepository
        extends ListCrudRepository<ClinicalRuntimeReleaseItem, Long> {

    List<ClinicalRuntimeReleaseItem> findByReleaseIdOrderByAssetTypeAscAssetIdentityAsc(
        String releaseId
    );
}
