package com.medkernel.engine.projection;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.knowledge.KnowledgeProjectionRefreshResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeProjectionRefreshAdapterTest {

    private final ProjectionSyncService projectionSyncService = mock(ProjectionSyncService.class);
    private final KnowledgeProjectionRefreshAdapter adapter = new KnowledgeProjectionRefreshAdapter(projectionSyncService);

    @Test
    void refreshPublishedVersionReturnsGraphAndSearchSyncEvidence() {
        when(projectionSyncService.rebuildKnowledgeGraph("t-1", "reviewer", "trace-1"))
            .thenReturn(response("ps-graph", ProjectionTargetType.KNOWLEDGE_GRAPH, ProjectionSyncStatus.SUCCESS));
        when(projectionSyncService.rebuildKnowledgeSearch("t-1", "reviewer", "trace-1"))
            .thenReturn(response("ps-search", ProjectionTargetType.KNOWLEDGE_SEARCH, ProjectionSyncStatus.NOT_SYNCED));

        KnowledgeProjectionRefreshResult result =
            adapter.refreshPublishedVersion("t-1", 1L, 10L, "reviewer", "trace-1");

        assertThat(result.graphSyncId()).isEqualTo("ps-graph");
        assertThat(result.graphStatus()).isEqualTo("SUCCESS");
        assertThat(result.searchSyncId()).isEqualTo("ps-search");
        assertThat(result.searchStatus()).isEqualTo("NOT_SYNCED");
        verify(projectionSyncService).rebuildKnowledgeGraph("t-1", "reviewer", "trace-1");
        verify(projectionSyncService).rebuildKnowledgeSearch("t-1", "reviewer", "trace-1");
    }

    private ProjectionRebuildResponse response(String syncId, ProjectionTargetType targetType, ProjectionSyncStatus status) {
        return new ProjectionRebuildResponse(
            syncId,
            targetType,
            status,
            1,
            1,
            "a".repeat(64),
            "a".repeat(64),
            "trace-1",
            status,
            status == ProjectionSyncStatus.SUCCESS ? "投影重建完成" : "未配置真实同步执行器");
    }
}
