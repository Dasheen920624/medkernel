package com.medkernel.engine.datasvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 引擎数据服务层 · 知识使用统计服务单元测试（DATASVC-01，D2 去标识聚合）。
 *
 * <p>验证：D2 数据级别标注 + 服务端分页（FR-1/FR-2）、空上游诚实返回不伪造（铁律 #1）、
 * 上游不可用诚实降级不以空数据伪装（FR-7）、每次查询留审计（FR-6）。知识使用＝推荐卡真实引用
 * 知识源的运行事实（{@code recommendation_source} 中 {@code source_type='KNOWLEDGE'} 子集）。
 */
class KnowledgeUsageStatsServiceTest {

    private KnowledgeUsageStatsRepository repo;
    private AuditRecorder auditRecorder;
    private KnowledgeUsageStatsService service;

    @BeforeEach
    void setUp() {
        repo = mock(KnowledgeUsageStatsRepository.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new KnowledgeUsageStatsService(repo, auditRecorder);
        RequestContext.restore(new RequestContext.Snapshot("t", OrgScope.tenant("tenant-1"), "quality-001"));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private KnowledgeUsageStat aStat(String refId, long citations, long cards) {
        return new KnowledgeUsageStat(refId, "知识-" + refId, citations, cards,
            Instant.parse("2026-06-14T00:00:00Z"));
    }

    @Test
    void queryKnowledgeUsage_tagsD2AndReturnsServerSidePage() {
        when(repo.countKnowledgeUsageGroups(eq("tenant-1"), any(), any())).thenReturn(2L);
        when(repo.aggregateKnowledgeUsage(eq("tenant-1"), any(), any(), anyInt(), anyInt()))
            .thenReturn(List.of(aStat("know-a", 10, 6), aStat("know-b", 4, 1)));

        KnowledgeUsageStatsResponse response = service.queryKnowledgeUsage(null, null, 0, 20);

        assertThat(response.dataLevel()).isEqualTo(EngineDataLevel.D2);
        assertThat(response.total()).isEqualTo(2L);
        assertThat(response.rows()).extracting(KnowledgeUsageStat::knowledgeRefId)
            .containsExactly("know-a", "know-b");
        assertThat(response.degraded()).isFalse();
    }

    @Test
    void queryKnowledgeUsage_emptyUpstream_returnsHonestEmptyNotFakeData() {
        when(repo.countKnowledgeUsageGroups(eq("tenant-1"), any(), any())).thenReturn(0L);
        when(repo.aggregateKnowledgeUsage(eq("tenant-1"), any(), any(), anyInt(), anyInt())).thenReturn(List.of());

        KnowledgeUsageStatsResponse response = service.queryKnowledgeUsage(null, null, 0, 20);

        assertThat(response.total()).isZero();
        assertThat(response.rows()).isEmpty();
        // 空上游＝真实无数据，诚实返回空，不伪造引用/采纳率（铁律 #1）。
        assertThat(response.degraded()).isFalse();
    }

    @Test
    void queryKnowledgeUsage_upstreamUnavailable_degradesHonestlyNotFakeEmpty() {
        when(repo.countKnowledgeUsageGroups(eq("tenant-1"), any(), any()))
            .thenThrow(new RuntimeException("recommendation_source 暂不可用"));

        KnowledgeUsageStatsResponse response = service.queryKnowledgeUsage(null, null, 0, 20);

        // 上游不可用：诚实标 degraded，不以空数据伪装真实统计（铁律 #1 / FR-7）。
        assertThat(response.degraded()).isTrue();
        assertThat(response.degradeReason()).isNotBlank();
        assertThat(response.rows()).isEmpty();
    }

    @Test
    void queryKnowledgeUsage_recordsAuditOnEveryQuery() {
        when(repo.countKnowledgeUsageGroups(eq("tenant-1"), any(), any())).thenReturn(0L);
        when(repo.aggregateKnowledgeUsage(eq("tenant-1"), any(), any(), anyInt(), anyInt())).thenReturn(List.of());

        service.queryKnowledgeUsage(null, null, 0, 20);

        verify(auditRecorder, times(1)).record(eq(AuditAction.EXECUTE), eq("recommendation_source"),
            anyString(), anyString());
    }
}
