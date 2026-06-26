package com.medkernel.engine.knowledge.production;

import com.medkernel.engine.security.RoleCode;

/**
 * 候选审核路由决策。
 *
 * @param reviewerRole 负责逐条确认候选的固定职责
 * @param domain 候选生产领域，用于风险展示和安全复核，不派生额外审核角色
 */
public record ReviewRoutingDecision(
    RoleCode reviewerRole,
    KnowledgeDomain domain
) {
}
