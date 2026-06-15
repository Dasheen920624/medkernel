package com.medkernel.engine.knowledge.production;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 知识生产 job 仓储集成测试（AIK-STD-13）。
 *
 * <p>对真实 H2 验证 {@code mk_knowledge_production_job} 落库与读回：生产器/目标管道/状态/血缘真实持久化，
 * 强租户隔离与分页台账。
 */
@SpringBootTest
@ActiveProfiles("dev")
class KnowledgeProductionJobRepositoryIntegrationTest {

    @Autowired
    private KnowledgeProductionJobRepository repository;

    private static final String TENANT = "tenant-prod-it";

    private KnowledgeProductionJob job(String jobCode, TargetPipeline pipeline) {
        Instant now = Instant.now();
        return new KnowledgeProductionJob(null, TENANT, jobCode, "探索 run r-1",
            VersionedAssetType.KNOWLEDGE, KnowledgeProducer.MANUAL, pipeline, KnowledgeDomain.GENERAL, null,
            ProductionJobStatus.PENDING, 0, "{\"producer\":\"MANUAL\"}", now, "user-001", now, "user-001",
            "trace-1");
    }

    @Test
    void persistsAndReadsBackJob() {
        KnowledgeProductionJob saved = repository.save(job("job-a", TargetPipeline.TENANT_OVERLAY));

        assertThat(saved.id()).isNotNull();
        KnowledgeProductionJob reloaded = repository.findByTenantIdAndJobCode(TENANT, "job-a").orElseThrow();
        assertThat(reloaded.producer()).isEqualTo(KnowledgeProducer.MANUAL);
        assertThat(reloaded.targetPipeline()).isEqualTo(TargetPipeline.TENANT_OVERLAY);
        assertThat(reloaded.assetType()).isEqualTo(VersionedAssetType.KNOWLEDGE);
        assertThat(reloaded.status()).isEqualTo(ProductionJobStatus.PENDING);
        assertThat(reloaded.candidateCount()).isZero();
        assertThat(reloaded.lineage()).contains("MANUAL");
    }

    @Test
    void pageAndCountAreTenantScoped() {
        repository.save(job("job-p1", TargetPipeline.TENANT_OVERLAY));
        repository.save(job("job-p2", TargetPipeline.TENANT_OVERLAY));
        repository.save(new KnowledgeProductionJob(null, "tenant-prod-other", "job-other", "s",
            VersionedAssetType.RULE, KnowledgeProducer.MANUAL, TargetPipeline.TENANT_OVERLAY,
            KnowledgeDomain.GENERAL, null, ProductionJobStatus.PENDING, 0, null, Instant.now(), "u",
            Instant.now(), "u", "t"));

        long count = repository.countByTenantId(TENANT);
        List<KnowledgeProductionJob> page = repository.pageByTenantId(TENANT, 0, 10);

        assertThat(count).isEqualTo(2);
        assertThat(page).extracting(KnowledgeProductionJob::jobCode)
            .containsExactlyInAnyOrder("job-p1", "job-p2");
    }
}
