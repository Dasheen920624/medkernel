package com.medkernel.engine.recommendation;

/**
 * 推荐卡来源类型：RULE 规则命中 / REDLINE 临床安全红线 / PATHWAY 路径节点 / KNOWLEDGE 知识引用 /
 * CONTEXT 上下文事实 / TERMINOLOGY 术语映射 / MANUAL 人工录入。
 *
 * <p>每张推荐卡至少含一条来源，确保 100% 可追溯。
 */
public enum RecommendationSourceType {
    RULE,
    REDLINE,
    PATHWAY,
    KNOWLEDGE,
    CONTEXT,
    TERMINOLOGY,
    MANUAL
}
