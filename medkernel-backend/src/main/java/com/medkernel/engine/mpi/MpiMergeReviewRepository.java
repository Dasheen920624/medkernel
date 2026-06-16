package com.medkernel.engine.mpi;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
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

    @Query("""
        SELECT COUNT(*) FROM mk_mpi_merge_review
        WHERE tenant_id = :tenantId
          AND status = :status
        """)
    long countByTenantIdAndStatus(
        @Param("tenantId") String tenantId,
        @Param("status") String status
    );

    @Query("""
        SELECT * FROM mk_mpi_merge_review
        WHERE tenant_id = :tenantId
          AND status = :status
        ORDER BY requested_at DESC, review_id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<MpiMergeReview> pageByTenantIdAndStatus(
        @Param("tenantId") String tenantId,
        @Param("status") String status,
        @Param("offset") int offset,
        @Param("limit") int limit
    );
}
