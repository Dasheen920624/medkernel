package com.medkernel.engine.recommendation;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 推荐卡来源解释持久化仓库，按推荐卡查询来源证据链。
 */
@Repository
public interface RecommendationSourceRepository extends ListCrudRepository<RecommendationSource, Long> {

    List<RecommendationSource> findByCardIdAndTenantIdOrderByCreatedAtAsc(String cardId, String tenantId);

    List<RecommendationSource> findByTenantIdAndSourceTypeAndSourceRefIdOrderByCreatedAtAsc(
        String tenantId, RecommendationSourceType sourceType, String sourceRefId);
}
