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
 * 引擎数据服务层 · 临床信号统计服务单元测试（DATASVC-01，D2 去标识聚合）。
 *
 * <p>验证：D2 数据级别标注 + 服务端分页（FR-1/FR-2）、空上游诚实返回不伪造（铁律 #1）、
 * 上游不可用诚实降级不以空数据伪装（FR-7）、每次查询留审计（FR-6）。临床信号＝推荐引擎真实投递的
 * CDSS 决策信号运行事实（{@code recommendation_card} 按 {@code card_type} 聚合，无患者标识 D2）。
 */
class ClinicalSignalsServiceTest {

    private ClinicalSignalsRepository repo;
    private AuditRecorder auditRecorder;
    private ClinicalSignalsService service;

    @BeforeEach
    void setUp() {
        repo = mock(ClinicalSignalsRepository.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new ClinicalSignalsService(repo, auditRecorder);
        RequestContext.restore(new RequestContext.Snapshot("t", OrgScope.tenant("tenant-1"), "quality-001"));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private ClinicalSignalStat aStat(String type, long total, long highRisk) {
        return new ClinicalSignalStat(type, total, highRisk, 0L, 0L,
            Instant.parse("2026-06-14T00:00:00Z"));
    }

    @Test
    void queryClinicalSignals_tagsD2AndReturnsServerSidePage() {
        when(repo.countClinicalSignalGroups(eq("tenant-1"), any(), any())).thenReturn(2L);
        when(repo.aggregateClinicalSignals(eq("tenant-1"), any(), any(), anyInt(), anyInt()))
            .thenReturn(List.of(aStat("MEDICATION", 10, 4), aStat("LAB", 4, 0)));

        ClinicalSignalsResponse response = service.queryClinicalSignals(null, null, 0, 20);

        assertThat(response.dataLevel()).isEqualTo(EngineDataLevel.D2);
        assertThat(response.total()).isEqualTo(2L);
        assertThat(response.rows()).extracting(ClinicalSignalStat::signalType)
            .containsExactly("MEDICATION", "LAB");
        assertThat(response.degraded()).isFalse();
    }

    @Test
    void queryClinicalSignals_emptyUpstream_returnsHonestEmptyNotFakeData() {
        when(repo.countClinicalSignalGroups(eq("tenant-1"), any(), any())).thenReturn(0L);
        when(repo.aggregateClinicalSignals(eq("tenant-1"), any(), any(), anyInt(), anyInt())).thenReturn(List.of());

        ClinicalSignalsResponse response = service.queryClinicalSignals(null, null, 0, 20);

        assertThat(response.total()).isZero();
        assertThat(response.rows()).isEmpty();
        // 空上游＝真实无数据，诚实返回空，不伪造信号量/采纳率（铁律 #1）。
        assertThat(response.degraded()).isFalse();
    }

    @Test
    void queryClinicalSignals_upstreamUnavailable_degradesHonestlyNotFakeEmpty() {
        when(repo.countClinicalSignalGroups(eq("tenant-1"), any(), any()))
            .thenThrow(new RuntimeException("recommendation_card 暂不可用"));

        ClinicalSignalsResponse response = service.queryClinicalSignals(null, null, 0, 20);

        // 上游不可用：诚实标 degraded，不以空数据伪装真实统计（铁律 #1 / FR-7）。
        assertThat(response.degraded()).isTrue();
        assertThat(response.degradeReason()).isNotBlank();
        assertThat(response.rows()).isEmpty();
    }

    @Test
    void queryClinicalSignals_recordsAuditOnEveryQuery() {
        when(repo.countClinicalSignalGroups(eq("tenant-1"), any(), any())).thenReturn(0L);
        when(repo.aggregateClinicalSignals(eq("tenant-1"), any(), any(), anyInt(), anyInt())).thenReturn(List.of());

        service.queryClinicalSignals(null, null, 0, 20);

        verify(auditRecorder, times(1)).record(eq(AuditAction.EXECUTE), eq("recommendation_card"),
            anyString(), anyString());
    }
}
