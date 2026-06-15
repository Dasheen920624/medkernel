package com.medkernel.engine.knowledge.production;

import com.medkernel.engine.security.RoleCode;

/**
 * 候选会签路由决策（AIK-STD-13 PR3，FR-6）。
 *
 * <p>纯确定性路由结论：归口审核角色（按管道归属）+ 领域会签角色（按领域）+ 是否双签（高危）。
 * PR3 只产此决策记录，<b>不执行分派</b>（候选物化前不建 {@code ReviewAssignment}，消费者＝P2-C 物化链 / AIK-STD-12 审核台）。
 *
 * @param ownerReviewerRole 归口审核角色（平台主源→平台知识治理员 / 院内覆盖→机构知识治理员）
 * @param domainReviewerRole 领域会签角色（按领域，GENERAL 时等于归口角色）
 * @param requiresDualSign 是否双签（高危 HIGH 须归口 + 领域两签）
 * @param domain 候选生产领域
 */
public record ReviewRoutingDecision(
    RoleCode ownerReviewerRole,
    RoleCode domainReviewerRole,
    boolean requiresDualSign,
    KnowledgeDomain domain
) {
}
