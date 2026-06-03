package com.medkernel.engine.mpi;

import org.springframework.data.relational.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

import com.medkernel.shared.ids.Ulid;

/**
 * 保存 MPI 合并审核单前补齐 ULID 形态业务主键。
 */
@Component
class MpiMergeReviewIdCallback implements BeforeConvertCallback<MpiMergeReview> {

    @Override
    public MpiMergeReview onBeforeConvert(MpiMergeReview aggregate) {
        if (aggregate.reviewId() != null && !aggregate.reviewId().isBlank()) {
            return aggregate;
        }
        return new MpiMergeReview(
            "mrv-" + Ulid.newUlid(),
            aggregate.tenantId(),
            aggregate.sourceMpiId(),
            aggregate.targetMpiId(),
            aggregate.riskLevel(),
            aggregate.status(),
            aggregate.riskReason(),
            aggregate.requestedBy(),
            aggregate.requestedAt(),
            aggregate.reviewedBy(),
            aggregate.reviewedAt(),
            aggregate.reviewReason(),
            aggregate.createdAt(),
            aggregate.createdBy(),
            aggregate.updatedAt(),
            aggregate.updatedBy(),
            aggregate.traceId()
        );
    }
}
