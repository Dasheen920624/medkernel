package com.medkernel.engine.knowledge.production;

/**
 * 提交候选响应：候选引用与审核归口。
 *
 * @param candidateRef intake 返回的候选引用标识
 * @param routing 审核归口决策
 */
public record CandidateSubmissionResponse(String candidateRef, ReviewRoutingDecision routing) {
}
