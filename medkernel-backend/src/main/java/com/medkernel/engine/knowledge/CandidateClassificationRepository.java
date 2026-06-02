package com.medkernel.engine.knowledge;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 知识候选分类 Repository。
 */
@Repository
public interface CandidateClassificationRepository extends ListCrudRepository<CandidateClassification, Long> {

    Optional<CandidateClassification> findByTenantIdAndId(String tenantId, Long id);

    List<CandidateClassification> findByTenantIdAndIdentityIdOrderByCreatedAtDescIdDesc(String tenantId, Long identityId);
}
