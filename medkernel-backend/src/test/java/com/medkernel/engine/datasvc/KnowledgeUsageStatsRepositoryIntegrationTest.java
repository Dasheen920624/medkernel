package com.medkernel.engine.datasvc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.medkernel.engine.recommendation.RecommendationSource;
import com.medkernel.engine.recommendation.RecommendationSourceRepository;
import com.medkernel.engine.recommendation.RecommendationSourceType;

/**
 * 知识使用统计聚合仓库集成测试（DATASVC-01 PR1）。
 *
 * <p>对真实 H2 播种 {@code recommendation_source} 来源解释事实，验证 D2 聚合 SQL 与投影映射真实可执行：
 * 仅取 {@code source_type='KNOWLEDGE'} 且 {@code source_ref_id} 非空子集，按知识引用聚合引用次数、去重卡片数、
 * 最近使用时刻与分组计数；非知识来源（RULE）与无引用键（ref 为空）的知识来源均排除。
 */
@SpringBootTest
@ActiveProfiles("dev")
class KnowledgeUsageStatsRepositoryIntegrationTest {

    @Autowired
    private KnowledgeUsageStatsRepository repo;

    @Autowired
    private RecommendationSourceRepository sourceRepo;

    private static final String TENANT = "tenant-datasvc-know-it";

    private RecommendationSource source(
            String suffix, String cardId, RecommendationSourceType type, String refId, Instant at) {
        return new RecommendationSource(null, "src-" + suffix, TENANT, cardId, type, refId, "v1",
            "知识标题-" + (refId == null ? "none" : refId), null, "hash-" + suffix, null,
            at, "system", at, "system", "trace-" + suffix);
    }

    @Test
    void aggregatesKnowledgeUsageByRefWithCardCountsAndGroupTotal() {
        Instant now = Instant.now();
        // know-a：三次引用，跨两张卡（card-1 两次、card-2 一次）→ citations=3, distinctCards=2
        sourceRepo.save(source("a1", "card-1", RecommendationSourceType.KNOWLEDGE, "know-a", now.minusSeconds(300)));
        sourceRepo.save(source("a2", "card-1", RecommendationSourceType.KNOWLEDGE, "know-a", now.minusSeconds(250)));
        sourceRepo.save(source("a3", "card-2", RecommendationSourceType.KNOWLEDGE, "know-a", now.minusSeconds(200)));
        // know-b：一次引用，一张卡
        sourceRepo.save(source("b1", "card-3", RecommendationSourceType.KNOWLEDGE, "know-b", now.minusSeconds(50)));
        // 非知识来源（规则）须排除
        sourceRepo.save(source("r1", "card-4", RecommendationSourceType.RULE, "rule-x", now.minusSeconds(40)));
        // 无引用键的知识来源（source_ref_id 为空）须排除——缺稳定知识键无法成统计
        sourceRepo.save(source("n1", "card-5", RecommendationSourceType.KNOWLEDGE, null, now.minusSeconds(30)));

        Instant windowStart = now.minusSeconds(3600);
        Instant windowEnd = now.plusSeconds(60);

        long groups = repo.countKnowledgeUsageGroups(TENANT, windowStart, windowEnd);
        List<KnowledgeUsageStat> rows = repo.aggregateKnowledgeUsage(TENANT, windowStart, windowEnd, 0, 20);

        assertThat(groups).isEqualTo(2L);
        assertThat(rows).hasSize(2);
        KnowledgeUsageStat knowA = rows.stream()
            .filter(r -> r.knowledgeRefId().equals("know-a")).findFirst().orElseThrow();
        assertThat(knowA.citationCount()).isEqualTo(3L);
        assertThat(knowA.distinctCardCount()).isEqualTo(2L);
        assertThat(knowA.knowledgeTitle()).isNotBlank();
        assertThat(knowA.lastUsedAt()).isNotNull();
        // 引用次数倒序：know-a(3) 在 know-b(1) 之前
        assertThat(rows.get(0).knowledgeRefId()).isEqualTo("know-a");
    }
}
