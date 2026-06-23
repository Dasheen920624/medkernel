package com.medkernel.engine.datasvc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.medkernel.engine.rule.RuleExecutionLog;
import com.medkernel.engine.rule.RuleExecutionLogRepository;
import com.medkernel.engine.rule.RuleExecutionStatus;
import com.medkernel.engine.rule.RuleRiskLevel;

/**
 * 规则使用统计聚合仓库集成测试（DATASVC-01 PR1）。
 *
 * <p>对真实 H2 播种 {@code rule_execution_log} 执行事实，验证 D2 聚合 SQL 与投影映射真实可执行：
 * 按 {@code rule_id} 分组的执行总数、命中数与分组计数，跨方言标准分页。
 */
@SpringBootTest
@ActiveProfiles("dev")
class RuleUsageStatsRepositoryIntegrationTest {

    @Autowired
    private RuleUsageStatsRepository repo;

    @Autowired
    private RuleExecutionLogRepository logRepo;

    private static final String TENANT = "tenant-datasvc-it";

    private RuleExecutionLog log(String suffix, String ruleId, boolean hit, Instant at) {
        return new RuleExecutionLog(null, "exec-" + suffix, TENANT, ruleId, "v1", null, "ORDER_SIGN",
            null, null, null, null, null, "digest-" + suffix, hit, RuleRiskLevel.LOW,
            null, null, RuleExecutionStatus.SUCCESS, null, null, null, at, at, null);
    }

    @Test
    void aggregatesRuleUsageByRuleWithHitCountsAndGroupTotal() {
        Instant now = Instant.now();
        logRepo.save(log("a1", "rule-it-a", true, now.minusSeconds(300)));
        logRepo.save(log("a2", "rule-it-a", true, now.minusSeconds(200)));
        logRepo.save(log("a3", "rule-it-a", false, now.minusSeconds(100)));
        logRepo.save(log("b1", "rule-it-b", true, now.minusSeconds(50)));

        Instant windowStart = now.minusSeconds(3600);
        Instant windowEnd = now.plusSeconds(60);

        long groups = repo.countRuleUsageGroups(TENANT, windowStart, windowEnd);
        List<RuleUsageStat> rows = repo.aggregateRuleUsage(TENANT, windowStart, windowEnd, 0, 20);

        assertThat(groups).isEqualTo(2L);
        assertThat(rows).hasSize(2);
        RuleUsageStat ruleA = rows.stream().filter(r -> r.ruleId().equals("rule-it-a")).findFirst().orElseThrow();
        assertThat(ruleA.totalExecutions()).isEqualTo(3L);
        assertThat(ruleA.hitCount()).isEqualTo(2L);
        assertThat(ruleA.failedCount()).isZero();
        assertThat(ruleA.lastExecutedAt()).isNotNull();
        // 执行总数倒序：rule-it-a(3) 在 rule-it-b(1) 之前
        assertThat(rows.get(0).ruleId()).isEqualTo("rule-it-a");
    }
}
