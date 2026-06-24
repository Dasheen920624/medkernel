package com.medkernel.engine.release;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 平台标准版本精确资产版本条目仓储。
 */
@Repository
public interface PlatformBaselineItemRepository
        extends ListCrudRepository<PlatformBaselineItem, Long> {

    List<PlatformBaselineItem> findByBaselineReleaseIdOrderByAssetTypeAscAssetIdentityAsc(
        String baselineReleaseId
    );
}
