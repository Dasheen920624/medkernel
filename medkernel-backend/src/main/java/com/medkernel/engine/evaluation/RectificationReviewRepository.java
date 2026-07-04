package com.medkernel.engine.evaluation;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 整改复核记录持久化仓库（GA-ENG-API-08）。
 *
 * <p>复核记录追加留痕不覆写，按问题 ID 升序回放全部复核历史。
 */
@Repository
public interface RectificationReviewRepository extends ListCrudRepository<RectificationReview, Long> {

    /**
     * 按质量问题 ID 与租户 ID 查询全部复核记录，按复核时间升序用于详情展示。
     */
    List<RectificationReview> findByFindingIdAndTenantIdOrderByReviewedAtAsc(String findingId, String tenantId);
}
