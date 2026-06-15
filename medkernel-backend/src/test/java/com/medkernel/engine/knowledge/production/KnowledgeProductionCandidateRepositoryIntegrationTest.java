package com.medkernel.engine.knowledge.production;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.medkernel.engine.knowledge.KnowledgeRiskLevel;

/**
 * 知识候选生产血缘仓储集成测试（AIK-STD-13 PR2）。
 *
 * <p>对真实 H2 验证 {@code mk_knowledge_production_candidate} 落库与按 job 回溯读取，强租户隔离。
 */
@SpringBootTest
@ActiveProfiles("dev")
class KnowledgeProductionCandidateRepositoryIntegrationTest {

    @Autowired
    private KnowledgeProductionCandidateRepository repository;

    private static final String TENANT = "tenant-prodcand-it";

    private KnowledgeProductionCandidate row(String jobCode, String identity) {
        return new KnowledgeProductionCandidate(null, TENANT, jobCode, identity, "0".repeat(64),
            "staged:" + identity, KnowledgeRiskLevel.MEDIUM, Instant.now(), "user-001");
    }

    @Test
    void persistsAndReadsBackLineageByJob() {
        repository.save(row("job-a", "discovery:SRC-1:v1:a"));
        repository.save(row("job-a", "discovery:SRC-2:v1:b"));
        repository.save(row("job-b", "discovery:SRC-3:v1:c"));
        // 跨租户须排除
        repository.save(new KnowledgeProductionCandidate(null, "tenant-prodcand-other", "job-a",
            "discovery:SRC-X:v1:x", "0".repeat(64), "staged:x", KnowledgeRiskLevel.MEDIUM, Instant.now(),
            "u"));

        List<KnowledgeProductionCandidate> jobA = repository.findByTenantIdAndJobCode(TENANT, "job-a");

        assertThat(jobA).hasSize(2);
        assertThat(jobA).extracting(KnowledgeProductionCandidate::assetIdentity)
            .containsExactly("discovery:SRC-1:v1:a", "discovery:SRC-2:v1:b");
        assertThat(jobA).allSatisfy(c -> assertThat(c.tenantId()).isEqualTo(TENANT));
    }

    @Test
    void reverseLooksUpByCandidateRefScopedToTenant() {
        // candidate_ref = "staged:" + identity（见 row 工厂）；独立 job 编码避免污染其它测试的 job 聚合
        repository.save(row("job-rev", "rev-1"));
        repository.save(row("job-rev", "rev-2"));
        // 跨租户同引用须排除（不泄漏跨租户存在性）
        repository.save(new KnowledgeProductionCandidate(null, "tenant-prodcand-other", "job-rev",
            "rev-3", "0".repeat(64), "staged:rev-1", KnowledgeRiskLevel.MEDIUM, Instant.now(), "u"));

        List<KnowledgeProductionCandidate> hits = repository.findByTenantIdAndCandidateRefIn(
            TENANT, List.of("staged:rev-1", "staged:rev-2", "staged:unknown"));

        assertThat(hits).extracting(KnowledgeProductionCandidate::candidateRef)
            .containsExactlyInAnyOrder("staged:rev-1", "staged:rev-2");
        assertThat(hits).allSatisfy(c -> assertThat(c.tenantId()).isEqualTo(TENANT));
    }
}
