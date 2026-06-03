package com.medkernel.engine.mpi;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 高危 MPI 合并审核单仓储。
 */
@Repository
public interface MpiMergeReviewRepository extends ListCrudRepository<MpiMergeReview, String> {

    Optional<MpiMergeReview> findByTenantIdAndSourceMpiIdAndTargetMpiId(String tenantId,
                                                                        String sourceMpiId,
                                                                        String targetMpiId);

    Optional<MpiMergeReview> findByTenantIdAndReviewId(String tenantId, String reviewId);

    List<MpiMergeReview> findAllByTenantIdAndStatus(String tenantId, String status);
}
