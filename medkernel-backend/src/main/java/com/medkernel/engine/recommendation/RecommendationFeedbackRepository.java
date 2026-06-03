package com.medkernel.engine.recommendation;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 医师反馈持久化仓库，按推荐卡查询反馈事实流。
 */
@Repository
public interface RecommendationFeedbackRepository extends ListCrudRepository<RecommendationFeedback, Long> {

    List<RecommendationFeedback> findByCardIdAndTenantIdOrderByCreatedAtAsc(String cardId, String tenantId);

    Optional<RecommendationFeedback> findByCardIdAndTenantIdAndIdempotencyKey(
        String cardId, String tenantId, String idempotencyKey);
}
