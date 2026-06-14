package com.medkernel.engine.datasvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.engine.knowledge.KnowledgeDomain;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityRepository;
import com.medkernel.engine.knowledge.KnowledgeIdentityStatus;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 引擎数据服务层 · 知识检索服务单元测试（DATASVC-01 PR2-c）。
 *
 * <p>验证：关键词检索映射为 D1 命中列表 + 服务端分页 + 审计、真实无匹配诚实返回（非降级）、
 * 上游不可用诚实降级（空结果不伪装真实无匹配，铁律 #1）。
 */
class KnowledgeSearchServiceTest {

    private KnowledgeIdentityRepository repo;
    private AuditRecorder auditRecorder;
    private KnowledgeSearchService service;

    @BeforeEach
    void setUp() {
        repo = mock(KnowledgeIdentityRepository.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new KnowledgeSearchService(repo, auditRecorder);
        RequestContext.restore(new RequestContext.Snapshot("trace-xyz", OrgScope.tenant("tenant-1"), "quality-001"));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private KnowledgeIdentity identity(String code) {
        return new KnowledgeIdentity(1L, "tenant-1", code, KnowledgeDomain.GUIDELINE, "糖尿病诊疗指南",
            "spec-1", "描述", KnowledgeIdentityStatus.ACTIVE, 7L,
            Instant.parse("2026-06-01T00:00:00Z"), "creator",
            Instant.parse("2026-06-10T00:00:00Z"), "editor");
    }

    @Test
    void search_matchingKeyword_returnsD1HitsWithPaginationAndAudit() {
        // 关键词归一为小写 + % 包裹后下传仓库。
        when(repo.countByFilter("tenant-1", null, null, null, "%糖尿病%")).thenReturn(1L);
        when(repo.pageByFilter(eq("tenant-1"), any(), any(), any(), eq("%糖尿病%"), anyInt(), anyInt()))
            .thenReturn(List.of(identity("K-DM")));

        KnowledgeSearchResult result = service.search("糖尿病", 0, 20);

        assertThat(result.dataLevel()).isEqualTo(EngineDataLevel.D1);
        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.degraded()).isFalse();
        assertThat(result.hits()).singleElement().satisfies(h -> {
            assertThat(h.identityCode()).isEqualTo("K-DM");
            assertThat(h.subject()).isEqualTo("糖尿病诊疗指南");
            assertThat(h.domain()).isEqualTo("GUIDELINE");
            assertThat(h.status()).isEqualTo("ACTIVE");
        });
        verify(auditRecorder).record(eq(AuditAction.EXECUTE), eq("knowledge_identity"), any(), contains("糖尿病"));
    }

    @Test
    void search_noMatch_returnsEmptyHonestlyNotDegraded() {
        when(repo.countByFilter(any(), any(), any(), any(), any())).thenReturn(0L);
        when(repo.pageByFilter(any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());

        KnowledgeSearchResult result = service.search("不存在的主题", 0, 20);

        // 真实无匹配＝诚实空结果，非降级（铁律 #1）。
        assertThat(result.total()).isEqualTo(0L);
        assertThat(result.hits()).isEmpty();
        assertThat(result.degraded()).isFalse();
    }

    @Test
    void search_upstreamDown_degradesHonestlyNotFakingEmpty() {
        when(repo.countByFilter(any(), any(), any(), any(), any())).thenThrow(new RuntimeException("db down"));

        KnowledgeSearchResult result = service.search("糖尿病", 0, 20);

        // 上游不可用：诚实标降级、空结果不伪装真实无匹配。
        assertThat(result.degraded()).isTrue();
        assertThat(result.degradeReason()).isNotBlank();
        assertThat(result.hits()).isEmpty();
    }
}
