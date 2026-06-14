package com.medkernel.engine.datasvc;

import java.time.Instant;

/**
 * 知识使用统计聚合行（DATASVC-01 PR1，D2 去标识）。
 *
 * <p>从 {@code recommendation_source} 中 {@code source_type='KNOWLEDGE'} 的真实来源解释事实按
 * {@code source_ref_id}（知识引用键）聚合：被引用次数、去重推荐卡片数、最近一次被引用时刻；
 * {@code knowledgeTitle} 取该引用键代表性来源标题。仅聚合计量，不含患者标识（D2）；
 * 禁伪造引用/采纳率（铁律 #1）。
 */
public record KnowledgeUsageStat(
    String knowledgeRefId,
    String knowledgeTitle,
    long citationCount,
    long distinctCardCount,
    Instant lastUsedAt
) {}
