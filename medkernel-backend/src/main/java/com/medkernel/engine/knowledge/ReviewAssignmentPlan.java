package com.medkernel.engine.knowledge;

import java.util.List;

/** 候选审核分派计划；当前只分派固定运营职责，空表示沿用提交人。 */
public record ReviewAssignmentPlan(List<String> reviewerRoleCodes) {

    public ReviewAssignmentPlan {
        reviewerRoleCodes = reviewerRoleCodes == null ? List.of() : List.copyOf(reviewerRoleCodes);
    }

    public boolean isEmpty() {
        return reviewerRoleCodes.isEmpty();
    }
}
