package com.medkernel.engine.projection;

import org.springframework.stereotype.Component;

import com.medkernel.engine.knowledge.KnowledgeProjectionRefreshPort;
import com.medkernel.engine.knowledge.KnowledgeProjectionRefreshResult;

/**
 * 将知识资产发布事件刷新为可重建的知识图与搜索投影。
 */
@Component
public class KnowledgeProjectionRefreshAdapter implements KnowledgeProjectionRefreshPort {

    private final ProjectionSyncService projectionSyncService;

    public KnowledgeProjectionRefreshAdapter(ProjectionSyncService projectionSyncService) {
        this.projectionSyncService = projectionSyncService;
    }

    @Override
    public KnowledgeProjectionRefreshResult refreshPublishedVersion(
        String tenantId,
        Long identityId,
        Long versionId,
        String requestedBy,
        String traceId
    ) {
        ProjectionRebuildResponse graph = projectionSyncService.rebuildKnowledgeGraph(tenantId, requestedBy, traceId);
        ProjectionRebuildResponse search = projectionSyncService.rebuildKnowledgeSearch(tenantId, requestedBy, traceId);
        return new KnowledgeProjectionRefreshResult(
            graph.syncId(),
            graph.status().name(),
            search.syncId(),
            search.status().name());
    }
}
