package com.medkernel.engine.knowledge;

/**
 * 知识身份状态。对应 {@code knowledge_identity.status} CHECK 约束。
 *
 * <p>注意区分：身份状态（identity）和版本状态（{@link KnowledgeVersionStatus}）。
 * 身份是跨版本的稳定主题（"瑞舒伐他汀说明书"）；版本是具体某次发布。
 */
public enum KnowledgeIdentityStatus {
    /** 有效身份，至少存在一个可用版本（DRAFT/CANDIDATE/UNDER_REVIEW/ACTIVE） */
    ACTIVE,
    /** 已指定后继并处于迁移宽限期，当前权威版本仍可读取。 */
    DEPRECATED,
    /** 整个身份撤回（如药品退市），所有版本不再参与新的临床决策 */
    WITHDRAWN,
    /** 归档：长期不再维护（如已废止的过期政策） */
    ARCHIVED
}
