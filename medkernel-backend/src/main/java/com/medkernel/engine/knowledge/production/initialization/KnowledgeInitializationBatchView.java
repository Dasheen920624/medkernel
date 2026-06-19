package com.medkernel.engine.knowledge.production.initialization;

import java.util.List;

/** 初始化批次及其服务端固定候选集合。 */
public record KnowledgeInitializationBatchView(
    KnowledgeInitializationBatch batch,
    List<KnowledgeInitializationItem> items
) {
    public KnowledgeInitializationBatchView {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
