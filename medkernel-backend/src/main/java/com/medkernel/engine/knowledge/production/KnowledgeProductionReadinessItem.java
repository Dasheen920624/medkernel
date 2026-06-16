package com.medkernel.engine.knowledge.production;

/**
 * 知识生产模型 readiness 单项裁决。
 *
 * @param code 前置项代码
 * @param ready 该项是否满足
 * @param required 是否强制前置
 * @param message 中文裁决说明
 * @param evidence 真实证据摘要；缺失时说明缺口，不伪造
 */
public record KnowledgeProductionReadinessItem(
    String code,
    boolean ready,
    boolean required,
    String message,
    String evidence
) {

    public static KnowledgeProductionReadinessItem pass(String code, String message, String evidence) {
        return new KnowledgeProductionReadinessItem(code, true, true, message, evidence);
    }

    public static KnowledgeProductionReadinessItem block(String code, String message, String evidence) {
        return new KnowledgeProductionReadinessItem(code, false, true, message, evidence);
    }
}
