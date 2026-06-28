package com.medkernel.engine.datasvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
 * 引擎数据服务层 · 规则使用统计服务单元测试（DATASVC-01，D2 去标识聚合）。
 *
 * <p>验证：D2 数据级别标注 + 服务端分页（FR-1/FR-2）、空上游诚实返回不伪造（铁律 #1）、
 * 每次查询留审计（FR-6）。
 */
class RuleUsageStatsServiceTest {

    private RuleUsageStatsRepository repo;
    private AuditRecorder auditRecorder;
    private RuleUsageStatsService service;

    @BeforeEach
    void setUp() {
        repo = mock(RuleUsageStatsRepository.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new RuleUsageStatsService(repo, auditRecorder);
        RequestContext.restore(new RequestContext.Snapshot("t", OrgScope.tenant("tenant-1"), "quality-001"));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private RuleUsageStat aStat(String ruleId, long total, long hits) {
        return new RuleUsageStat(ruleId, total, hits, 0L, Instant.parse("2026-06-14T00:00:00Z"));
    }

    @Test
    void queryRuleUsage_tagsD2AndReturnsServerSidePage() {
        when(repo.countRuleUsageGroups(eq("tenant-1"), any(), any())).thenReturn(2L);
        when(repo.aggregateRuleUsage(eq("tenant-1"), any(), any(), anyInt(), anyInt()))
            .thenReturn(List.of(aStat("rule-a", 10, 6), aStat("rule-b", 4, 1)));

        RuleUsageStatsResponse response = service.queryRuleUsage(null, null, 0, 20);

        assertThat(response.dataLevel()).isEqualTo(EngineDataLevel.D2);
        assertThat(response.total()).isEqualTo(2L);
        assertThat(response.rows()).extracting(RuleUsageStat::ruleId).containsExactly("rule-a", "rule-b");
        assertThat(response.degraded()).isFalse();
    }

    @Test
    void queryRuleUsage_emptyUpstream_returnsHonestEmptyNotFakeData() {
        when(repo.countRuleUsageGroups(eq("tenant-1"), any(), any())).thenReturn(0L);
        when(repo.aggregateRuleUsage(eq("tenant-1"), any(), any(), anyInt(), anyInt())).thenReturn(List.of());

        RuleUsageStatsResponse response = service.queryRuleUsage(null, null, 0, 20);

        assertThat(response.total()).isZero();
        assertThat(response.rows()).isEmpty();
        // 空上游＝真实无数据，诚实返回空，不伪造命中/采纳率（铁律 #1）。
        assertThat(response.degraded()).isFalse();
    }

    @Test
    void queryRuleUsage_clampsPageSizeToServerMaximum() {
        when(repo.countRuleUsageGroups(eq("tenant-1"), any(), any())).thenReturn(0L);
        when(repo.aggregateRuleUsage(eq("tenant-1"), any(), any(), anyInt(), eq(200)))
            .thenReturn(List.of());

        RuleUsageStatsResponse response = service.queryRuleUsage(null, null, 0, 99999);

        assertThat(response.size()).isEqualTo(200);
        verify(repo).aggregateRuleUsage(eq("tenant-1"), any(), any(), eq(0), eq(200));
    }

    @Test
    void queryRuleUsage_recordsAuditOnEveryQuery() {
        when(repo.countRuleUsageGroups(eq("tenant-1"), any(), any())).thenReturn(0L);
        when(repo.aggregateRuleUsage(eq("tenant-1"), any(), any(), anyInt(), anyInt())).thenReturn(List.of());

        service.queryRuleUsage(null, null, 0, 20);

        verify(auditRecorder, times(1)).record(eq(AuditAction.EXECUTE), eq("rule_execution_log"),
            anyString(), anyString());
    }
}
