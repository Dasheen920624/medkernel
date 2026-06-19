package com.medkernel.engine.knowledge;

import java.util.List;

/**
 * 候选审核分派计划（AIK-STD-13 PR4）：物化时据 PR3 会签路由决策列出应分派的审核角色码。
 *
 * <p>{@code reviewerRoleCodes} 按顺序表示实际签署席位。高风险归口与领域角色相同时允许重复角色码，
 * 由审核状态机强制两个席位必须由不同人员完成；空表示沿用默认（提交人单行，零回归）。
 */
public record ReviewAssignmentPlan(List<String> reviewerRoleCodes) {

    public ReviewAssignmentPlan {
        reviewerRoleCodes = reviewerRoleCodes == null ? List.of() : List.copyOf(reviewerRoleCodes);
    }

    public boolean isEmpty() {
        return reviewerRoleCodes.isEmpty();
    }
}
