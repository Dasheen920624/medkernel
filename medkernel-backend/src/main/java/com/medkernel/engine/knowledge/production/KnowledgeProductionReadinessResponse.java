package com.medkernel.engine.knowledge.production;

import java.util.List;

import com.medkernel.engine.llm.provider.DeploymentForm;

/**
 * 知识生产模型 readiness 聚合响应。
 *
 * <p>{@code ready=true} 才允许进入真实模型生产器；否则调用方必须停止模型调用并展示阻断项。
 */
public record KnowledgeProductionReadinessResponse(
    String tenantId,
    KnowledgeProducer producer,
    String capabilityCode,
    String providerCode,
    DeploymentForm deploymentForm,
    boolean ready,
    boolean modelInvocationAllowed,
    List<KnowledgeProductionReadinessItem> items
) {

    public KnowledgeProductionReadinessResponse {
        items = items == null ? List.of() : List.copyOf(items);
        ready = items.stream().allMatch(item -> !item.required() || item.ready());
        modelInvocationAllowed = ready;
    }
}
