package com.medkernel.engine.followup;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

/**
 * 随访问卷存储库。
 */
public interface FollowupQuestionnaireRepository extends CrudRepository<FollowupQuestionnaire, Long>, PagingAndSortingRepository<FollowupQuestionnaire, Long> {
    Optional<FollowupQuestionnaire> findByQuestionnaireId(String questionnaireId);
    Optional<FollowupQuestionnaire> findByTenantIdAndTaskId(String tenantId, String taskId);
    Optional<FollowupQuestionnaire> findByQuestionnaireIdAndTenantId(String questionnaireId, String tenantId);
    Optional<FollowupQuestionnaire> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);
}
