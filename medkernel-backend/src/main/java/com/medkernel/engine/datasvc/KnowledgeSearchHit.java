package com.medkernel.engine.datasvc;

/**
 * 知识检索命中项（DATASVC-01，受控工具 {@code searchKnowledge} 的 D1 输出元素）。
 *
 * <p>承载一条匹配知识身份的已发布资产元数据（身份编码/主题/领域/状态），均为 D1，不含患者字段。
 */
public record KnowledgeSearchHit(
    String identityCode,
    String subject,
    String domain,
    String status
) {}
