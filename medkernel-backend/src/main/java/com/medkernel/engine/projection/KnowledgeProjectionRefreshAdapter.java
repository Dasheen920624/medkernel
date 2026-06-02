package com.medkernel.engine.projection;

import org.springframework.stereotype.Component;

import com.medkernel.engine.knowledge.KnowledgeProjectionRefreshPort;

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
    public void refreshPublishedVersion(String tenantId, Long identityId, Long versionId, String requestedBy,
            String traceId) {
        projectionSyncService.rebuildKnowledgeGraph(tenantId, requestedBy, traceId);
        projectionSyncService.rebuildKnowledgeSearch(tenantId, requestedBy, traceId);
    }
}
