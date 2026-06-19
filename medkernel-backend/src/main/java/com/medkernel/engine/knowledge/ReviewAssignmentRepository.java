package com.medkernel.engine.knowledge;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 知识候选审核分派 Repository。
 */
@Repository
public interface ReviewAssignmentRepository extends ListCrudRepository<ReviewAssignment, Long> {

    List<ReviewAssignment> findByTenantIdAndIdentityIdOrderByCreatedAtDescIdDesc(String tenantId, Long identityId);

    List<ReviewAssignment> findByTenantIdAndCandidateClassificationIdOrderByCreatedAtAscIdAsc(
        String tenantId,
        Long candidateClassificationId
    );
}
