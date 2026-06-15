package com.medkernel.engine.knowledge;

import java.util.List;

/**
 * 候选审核分派计划（AIK-STD-13 PR4）：物化时据 PR3 会签路由决策列出应分派的审核角色码。
 *
 * <p>{@code reviewerRoleCodes} 为去重后的角色码（归口 ∪ 领域异于归口时）；空表示沿用默认（提交人单行，零回归）。
 */
public record ReviewAssignmentPlan(List<String> reviewerRoleCodes) {

    public ReviewAssignmentPlan {
        reviewerRoleCodes = reviewerRoleCodes == null ? List.of() : List.copyOf(reviewerRoleCodes);
    }

    public boolean isEmpty() {
        return reviewerRoleCodes.isEmpty();
    }
}
