package com.medkernel.engine.knowledge.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 候选生产来源溯源服务测试（AIK-STD-12，真实 H2）。
 *
 * <p>验证审核台候选经生产血缘反查 AI 工厂来源：{@code aiGenerated = producer ≠ MANUAL}；
 * 无血缘行（手建候选）/ 跨租户引用诚实不返回（铁律 #1 不臆造、不泄漏跨租户存在性）。
 */
@SpringBootTest
@ActiveProfiles("dev")
class CandidateProvenanceServiceTest {

    private static final String TENANT = "tenant-prov-it";

    @Autowired
    private CandidateProvenanceService service;
    @Autowired
    private KnowledgeProductionJobRepository jobs;
    @Autowired
    private KnowledgeProductionCandidateRepository candidates;

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private void saveJob(String jobCode, KnowledgeProducer producer) {
        Instant now = Instant.now();
        jobs.save(new KnowledgeProductionJob(null, TENANT, jobCode, "run", VersionedAssetType.KNOWLEDGE,
            producer, TargetPipeline.TENANT_OVERLAY, KnowledgeDomain.PHARMACY, "strategy-x",
            ProductionJobStatus.RUNNING, 1, "{}", now, "u", now, "u", "trace"));
    }

    private void saveCandidate(String tenantId, String jobCode, String ref) {
        saveCandidate(tenantId, jobCode, ref, null);
    }

    private void saveCandidate(String tenantId, String jobCode, String ref, String explainJson) {
        candidates.save(new KnowledgeProductionCandidate(null, tenantId, jobCode, "identity", "0".repeat(64),
            ref, KnowledgeRiskLevel.HIGH, Instant.now(), "u", explainJson));
    }

    @Test
    void resolvesAiAndManualProvenanceAndSkipsUnknownAndCrossTenant() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant(TENANT), "user"));
        // job_code 全局唯一约束 + 全量套件共享 H2 无回滚 → 用测试命名空间码避免撞键
        saveJob("prov-job-ai", KnowledgeProducer.API_MODEL);
        saveJob("prov-job-manual", KnowledgeProducer.MANUAL);
        saveCandidate(TENANT, "prov-job-ai", "kv:prov-10:v1", """
            {"modelTaskId":"task-prov-1","modelMode":"B2","modelVersion":"claude-opus-4",
             "promptVersion":"prompt:aikstd13-v1","toolVersion":"tool:submit-candidate-v1",
             "sourceCitations":[{"anchor":"source-fragment-candidate","version":"sv-2026"}],
             "confidence":0.87,"fallbackUsed":true,"fallbackReason":"B2 -> B1：外部 provider 限流，本地模型成功"}
            """);
        saveCandidate(TENANT, "prov-job-manual", "kv:prov-20:v1");
        // 跨租户同引用须排除
        saveJob("prov-job-other", KnowledgeProducer.API_MODEL);
        saveCandidate("tenant-prov-other", "prov-job-other", "kv:prov-30:v1");

        List<CandidateProvenanceView> views = service.resolve(
            List.of("kv:prov-10:v1", "kv:prov-20:v1", "kv:prov-30:v1", "kv:prov-99:unknown"));

        assertThat(views).extracting(CandidateProvenanceView::candidateRef)
            .containsExactlyInAnyOrder("kv:prov-10:v1", "kv:prov-20:v1");

        CandidateProvenanceView ai = views.stream()
            .filter(v -> v.candidateRef().equals("kv:prov-10:v1")).findFirst().orElseThrow();
        assertThat(ai.aiGenerated()).isTrue();
        assertThat(ai.producer()).isEqualTo(KnowledgeProducer.API_MODEL);
        assertThat(ai.jobCode()).isEqualTo("prov-job-ai");
        assertThat(ai.modelStrategy()).isEqualTo("strategy-x");
        assertThat(ai.domain()).isEqualTo(KnowledgeDomain.PHARMACY);
        assertThat(ai.modelTaskId()).isEqualTo("task-prov-1");
        assertThat(ai.modelMode()).isEqualTo("B2");
        assertThat(ai.modelVersion()).isEqualTo("claude-opus-4");
        assertThat(ai.promptVersion()).isEqualTo("prompt:aikstd13-v1");
        assertThat(ai.toolVersion()).isEqualTo("tool:submit-candidate-v1");
        assertThat(ai.sourceCitations()).contains("source-fragment-candidate");
        assertThat(ai.confidence()).isEqualTo(0.87);
        assertThat(ai.fallbackUsed()).isTrue();
        assertThat(ai.fallbackReason()).contains("B2 -> B1");

        CandidateProvenanceView manual = views.stream()
            .filter(v -> v.candidateRef().equals("kv:prov-20:v1")).findFirst().orElseThrow();
        assertThat(manual.aiGenerated()).isFalse();
        assertThat(manual.producer()).isEqualTo(KnowledgeProducer.MANUAL);
    }

    @Test
    void invalidExplainJsonFallsBackToEmptyEvidenceWithoutBreakingProvenance() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant(TENANT), "user"));
        saveJob("prov-job-bad-json", KnowledgeProducer.API_MODEL);
        saveCandidate(TENANT, "prov-job-bad-json", "kv:prov-bad-json:v1", "{not-json");

        List<CandidateProvenanceView> views = service.resolve(List.of("kv:prov-bad-json:v1"));

        assertThat(views).hasSize(1);
        CandidateProvenanceView view = views.getFirst();
        assertThat(view.aiGenerated()).isTrue();
        assertThat(view.jobCode()).isEqualTo("prov-job-bad-json");
        assertThat(view.modelTaskId()).isNull();
        assertThat(view.modelMode()).isNull();
        assertThat(view.sourceCitations()).isNull();
        assertThat(view.confidence()).isNull();
        assertThat(view.fallbackUsed()).isNull();
    }

    @Test
    void emptyRefsReturnEmptyWithoutQuery() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant(TENANT), "user"));
        assertThat(service.resolve(List.of())).isEmpty();
    }

    @Test
    void rejectsOversizedProvenanceRefBatchBeforeRepositoryLookup() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant(TENANT), "user"));
        List<String> oversized = IntStream.rangeClosed(1, 201)
            .mapToObj(index -> "kv:prov-" + index + ":v1")
            .toList();

        assertThatThrownBy(() -> service.resolve(oversized))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }
}
