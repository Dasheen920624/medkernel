package com.medkernel.engine.knowledge.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 可信来源探索运行存证仓储集成测试（LLM-06，FR-2 检索时点存证）。
 *
 * <p>对真实 H2 验证 {@code mk_knowledge_discovery_run} 落库与读回：时点 + 源版本快照 + result_hash +
 * 状态/降级位的真实持久化，强租户隔离与分页台账。
 */
@SpringBootTest
@ActiveProfiles("dev")
class DiscoveryRunRepositoryIntegrationTest {

    @Autowired
    private DiscoveryRunRepository repository;

    private static final String TENANT = "tenant-run-it";

    private DiscoveryRun run(String runCode, DiscoveryRunStatus status, boolean degraded) {
        Instant now = Instant.now();
        return new DiscoveryRun(null, TENANT, runCode, "阿司匹林", "knowledge.discovery", now,
            "[{\"sourceCode\":\"SRC-A\",\"versionNo\":\"v1\",\"versionContentHash\":\"vh\"}]",
            2, 2, "0".repeat(64), status, degraded, "user-001", now);
    }

    @Test
    void persistsAndReadsBackDiscoveryRunWithSnapshotAndHash() {
        DiscoveryRun saved = repository.save(run("run-a", DiscoveryRunStatus.SUCCEEDED, false));

        assertThat(saved.id()).isNotNull();
        DiscoveryRun reloaded = repository.findByTenantIdAndId(TENANT, saved.id()).orElseThrow();
        assertThat(reloaded.runCode()).isEqualTo("run-a");
        assertThat(reloaded.queryText()).isEqualTo("阿司匹林");
        assertThat(reloaded.capabilityCode()).isEqualTo("knowledge.discovery");
        assertThat(reloaded.status()).isEqualTo(DiscoveryRunStatus.SUCCEEDED);
        assertThat(reloaded.degraded()).isFalse();
        assertThat(reloaded.hitCount()).isEqualTo(2);
        assertThat(reloaded.resultHash()).hasSize(64);
        assertThat(reloaded.sourceSnapshot()).contains("SRC-A");
        assertThat(reloaded.executedAt()).isNotNull();
    }

    @Test
    void pageAndCountAreTenantScoped() {
        repository.save(run("run-p1", DiscoveryRunStatus.SUCCEEDED, false));
        repository.save(run("run-p2", DiscoveryRunStatus.EMPTY, false));
        repository.save(new DiscoveryRun(null, "tenant-run-other", "run-other", "q", "knowledge.discovery",
            Instant.now(), "[]", 0, 0, "0".repeat(64), DiscoveryRunStatus.EMPTY, false, "u", Instant.now()));

        long count = repository.countByTenantId(TENANT);
        List<DiscoveryRun> page = repository.pageByTenantId(TENANT, 0, 10);

        assertThat(count).isEqualTo(2);
        assertThat(page).extracting(DiscoveryRun::runCode).containsExactlyInAnyOrder("run-p1", "run-p2");
    }
}
