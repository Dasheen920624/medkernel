package com.medkernel.engine.knowledge.production;

/**
 * 提交候选响应（AIK-STD-13 PR3，FR-6）：候选引用 + 会签路由决策。
 *
 * @param candidateRef intake 返回的候选引用标识
 * @param routing 会签路由决策（归口/领域角色 + 是否双签）
 */
public record CandidateSubmissionResponse(String candidateRef, ReviewRoutingDecision routing) {
}
