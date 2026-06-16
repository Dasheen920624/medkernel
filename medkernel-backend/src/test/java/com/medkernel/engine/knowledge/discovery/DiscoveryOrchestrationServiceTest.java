package com.medkernel.engine.knowledge.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.factory.KnowledgeAssetSchemaValidator;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.SourceType;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.hash.Sha256ContentHash;

/**
 * 可信来源探索编排服务单元测试（LLM-06）。
 *
 * <p>验证：FR-1 仅检索受控源（关键词归一）、FR-2 检索时点存证（时点 + 源版本快照 + result_hash）、
 * FR-3 每候选带真实来源锚点 + 可信分级且过校验闸、FR-4 产 DRAFT 候选不写权威、
 * FR-5 无源诚实 EMPTY / 上游不可用诚实 DEGRADED（铁律 #1 不臆造）。
 */
class DiscoveryOrchestrationServiceTest {

    private ControlledSourceSearchRepository searchRepository;
    private DiscoveryRunRepository runRepository;
    private KnowledgeDiffDetectionService diffDetectionService;
    private AuditRecorder auditRecorder;
    private DiscoveryOrchestrationService service;

    @BeforeEach
    void setUp() {
        searchRepository = mock(ControlledSourceSearchRepository.class);
        runRepository = mock(DiscoveryRunRepository.class);
        diffDetectionService = mock(KnowledgeDiffDetectionService.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new DiscoveryOrchestrationService(
            searchRepository, runRepository, new KnowledgeAssetSchemaValidator(), auditRecorder, diffDetectionService);
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(diffDetectionService.detect(any(), any())).thenReturn(
            new KnowledgeDiffDetection(true, KnowledgeDiffType.REVISED, 10L, 5L, null,
                null, "发现修订差异"));
        RequestContext.restore(new RequestContext.Snapshot("trace-1", OrgScope.tenant("tenant-1"), "user-001"));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private DiscoveryFragmentHit hit(long id, String suffix, SourceAuthorityLevel authority) {
        return new DiscoveryFragmentHit(
            id, "section-" + suffix, "锚点-" + suffix, "受控来源正文摘录 " + suffix, "fh-" + suffix,
            10L + id, "v1", "vh-" + suffix,
            100L + id, "SRC-" + suffix, "来源标题-" + suffix, SourceType.GUIDELINE, authority);
    }

    @Test
    void exploreProducesOneDraftCandidatePerHitWithRealSourceAnchors() {
        when(searchRepository.searchControlledFragments(eq("tenant-1"), anyString(), anyInt()))
            .thenReturn(List.of(
                hit(1, "a", SourceAuthorityLevel.A_REGULATION),
                hit(2, "b", SourceAuthorityLevel.C_CONSENSUS_LITERATURE)));

        DiscoveryResponse response = service.explore(new DiscoveryRequest("阿司匹林", null, null));

        assertThat(response.status()).isEqualTo(DiscoveryRunStatus.SUCCEEDED);
        assertThat(response.degraded()).isFalse();
        assertThat(response.hitCount()).isEqualTo(2);
        assertThat(response.candidateCount()).isEqualTo(2);
        assertThat(response.candidates()).hasSize(2);

        KnowledgeAssetEnvelope first = response.candidates().get(0);
        assertThat(first.assetType()).isEqualTo(VersionedAssetType.KNOWLEDGE);
        assertThat(first.lifecycleStatus()).isEqualTo(AssetVersionStatus.DRAFT);
        assertThat(first.sources()).hasSize(1);
        assertThat(first.sources().get(0).sourceRef()).isEqualTo("SRC-a:v1:section-a");
        assertThat(first.sources().get(0).authorityLevel()).isEqualTo(SourceAuthorityLevel.A_REGULATION);
        assertThat(first.trustLevel()).isEqualTo(SourceAuthorityLevel.A_REGULATION);
        assertThat(first.payload()).isEqualTo("受控来源正文摘录 a");
        assertThat(first.contentHash())
            .isEqualTo(Sha256ContentHash.sha256("受控来源正文摘录 a", "内容不能为空"));
        assertThat(first.orgScope()).isNotBlank();
    }

    @Test
    void exploreNormalizesQueryToControlledKeywordAndCapsLimit() {
        when(searchRepository.searchControlledFragments(anyString(), anyString(), anyInt()))
            .thenReturn(List.of());

        service.explore(new DiscoveryRequest("  Aspirin  ", 999, null));

        ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        verify(searchRepository).searchControlledFragments(eq("tenant-1"), keyword.capture(), limit.capture());
        assertThat(keyword.getValue()).isEqualTo("%aspirin%");
        assertThat(limit.getValue()).isEqualTo(50);
    }

    @Test
    void exploreRecordsSucceededRunWithTimepointSnapshotAndResultHash() {
        when(searchRepository.searchControlledFragments(anyString(), anyString(), anyInt()))
            .thenReturn(List.of(hit(1, "a", SourceAuthorityLevel.A_REGULATION)));

        DiscoveryResponse response = service.explore(new DiscoveryRequest("阿司匹林", null, null));

        ArgumentCaptor<DiscoveryRun> saved = ArgumentCaptor.forClass(DiscoveryRun.class);
        verify(runRepository).save(saved.capture());
        DiscoveryRun run = saved.getValue();
        assertThat(run.tenantId()).isEqualTo("tenant-1");
        assertThat(run.runCode()).isNotBlank();
        assertThat(run.queryText()).isEqualTo("阿司匹林");
        assertThat(run.capabilityCode()).isEqualTo("knowledge.discovery");
        assertThat(run.executedAt()).isNotNull();
        assertThat(run.hitCount()).isEqualTo(1);
        assertThat(run.candidateCount()).isEqualTo(1);
        assertThat(run.status()).isEqualTo(DiscoveryRunStatus.SUCCEEDED);
        assertThat(run.degraded()).isFalse();
        assertThat(run.createdBy()).isEqualTo("user-001");
        assertThat(run.resultHash()).matches("^[0-9a-f]{64}$").isEqualTo(response.resultHash());
        assertThat(run.sourceSnapshot()).contains("SRC-a").contains("v1").contains("vh-a");

        verify(auditRecorder).record(eq(AuditAction.EXECUTE), eq("mk_knowledge_discovery_run"),
            anyString(), anyString());
    }

    @Test
    void exploreReturnsHonestEmptyWhenNoControlledMatch() {
        when(searchRepository.searchControlledFragments(anyString(), anyString(), anyInt()))
            .thenReturn(List.of());

        DiscoveryResponse response = service.explore(new DiscoveryRequest("不存在的主题", null, null));

        assertThat(response.status()).isEqualTo(DiscoveryRunStatus.EMPTY);
        assertThat(response.degraded()).isFalse();
        assertThat(response.hitCount()).isZero();
        assertThat(response.candidates()).isEmpty();

        ArgumentCaptor<DiscoveryRun> saved = ArgumentCaptor.forClass(DiscoveryRun.class);
        verify(runRepository).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(DiscoveryRunStatus.EMPTY);
        assertThat(saved.getValue().degraded()).isFalse();
    }

    @Test
    void exploreDegradesHonestlyWhenControlledSourceUpstreamFails() {
        when(searchRepository.searchControlledFragments(anyString(), anyString(), anyInt()))
            .thenThrow(new RuntimeException("source store down"));

        DiscoveryResponse response = service.explore(new DiscoveryRequest("阿司匹林", null, null));

        assertThat(response.status()).isEqualTo(DiscoveryRunStatus.DEGRADED);
        assertThat(response.degraded()).isTrue();
        assertThat(response.candidates()).isEmpty();

        ArgumentCaptor<DiscoveryRun> saved = ArgumentCaptor.forClass(DiscoveryRun.class);
        verify(runRepository).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(DiscoveryRunStatus.DEGRADED);
        assertThat(saved.getValue().degraded()).isTrue();
    }

    @Test
    void exploreResultHashIsDeterministicOverSameControlledResultSet() {
        when(searchRepository.searchControlledFragments(anyString(), anyString(), anyInt()))
            .thenReturn(List.of(hit(1, "a", SourceAuthorityLevel.A_REGULATION)));

        String first = service.explore(new DiscoveryRequest("阿司匹林", null, null)).resultHash();
        String second = service.explore(new DiscoveryRequest("阿司匹林", null, null)).resultHash();

        assertThat(first).isEqualTo(second).matches("^[0-9a-f]{64}$");
    }

    @Test
    void exploreRejectsBlankQuery() {
        assertThatThrownBy(() -> service.explore(new DiscoveryRequest("   ", null, null)))
            .isInstanceOf(ApiException.class);
        verify(searchRepository, times(0)).searchControlledFragments(anyString(), anyString(), anyInt());
    }

    @Test
    void exploreRunsDiffDetectionWhenTargetIdentityIsProvided() {
        when(searchRepository.searchControlledFragments(anyString(), anyString(), anyInt()))
            .thenReturn(List.of(hit(1, "a", SourceAuthorityLevel.A_REGULATION)));

        DiscoveryResponse response = service.explore(new DiscoveryRequest("阿司匹林", null, 10L));

        assertThat(response.diffs()).hasSize(1);
        assertThat(response.diffs().get(0).diffType()).isEqualTo(KnowledgeDiffType.REVISED);
        ArgumentCaptor<KnowledgeDiffContext> context = ArgumentCaptor.forClass(KnowledgeDiffContext.class);
        verify(diffDetectionService).detect(any(KnowledgeAssetEnvelope.class), context.capture());
        assertThat(context.getValue().runCode()).isNotBlank();
        assertThat(context.getValue().targetIdentityId()).isEqualTo(10L);
    }

    @Test
    void listRunsReturnsTenantScopedPage() {
        DiscoveryRun row = new DiscoveryRun(7L, "tenant-1", "run-x", "阿司匹林", "knowledge.discovery",
            java.time.Instant.now(), "[]", 0, 0, "h", DiscoveryRunStatus.EMPTY, false, "user-001",
            java.time.Instant.now());
        when(runRepository.countByTenantId("tenant-1")).thenReturn(21L);
        when(runRepository.pageByTenantId(eq("tenant-1"), anyInt(), anyInt())).thenReturn(List.of(row));

        PageResponse<DiscoveryRun> runs = service.listRuns(1, 20);

        assertThat(runs.items()).containsExactly(row);
        assertThat(runs.page()).isEqualTo(1);
        assertThat(runs.size()).isEqualTo(20);
        assertThat(runs.total()).isEqualTo(21);
        assertThat(runs.hasNext()).isTrue();
        verify(runRepository).countByTenantId("tenant-1");
        verify(runRepository).pageByTenantId("tenant-1", 0, 20);
    }
}
