package com.medkernel.engine.datasvc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.medkernel.engine.cdss.risk.CdssAutomationLevel;
import com.medkernel.engine.cdss.risk.CdssReviewRequirement;
import com.medkernel.engine.recommendation.RecommendationCard;
import com.medkernel.engine.recommendation.RecommendationCardRepository;
import com.medkernel.engine.recommendation.RecommendationCardStatus;
import com.medkernel.engine.recommendation.RecommendationCardType;
import com.medkernel.engine.recommendation.RecommendationInterruptLevel;
import com.medkernel.engine.recommendation.RecommendationRiskLevel;

/**
 * 临床信号统计聚合仓库集成测试（DATASVC-01 PR1）。
 *
 * <p>对真实 H2 播种 {@code recommendation_card} CDSS 决策信号事实，验证 D2 聚合 SQL 与投影映射真实可执行：
 * 按 {@code card_type} 聚合信号总数、高危数（HIGH/CRITICAL）、采纳数（ACCEPTED）、最近信号时刻与分组计数；
 * 按信号总数倒序，跨方言标准分页。无患者标识落地（D2）。
 */
@SpringBootTest
@ActiveProfiles("dev")
class ClinicalSignalsRepositoryIntegrationTest {

    @Autowired
    private ClinicalSignalsRepository repo;

    @Autowired
    private RecommendationCardRepository cardRepo;

    private static final String TENANT = "tenant-datasvc-signal-it";

    private RecommendationCard card(String suffix, RecommendationCardType type,
            RecommendationRiskLevel risk, RecommendationCardStatus status, Instant at) {
        return new RecommendationCard(
            null, "card-" + suffix, TENANT, "trig-" + suffix, "CARD.SIG", type,
            "信号-" + suffix, "聚合测试信号", "请确认",
            risk, RecommendationInterruptLevel.WEAK_INTERRUPTIVE,
            status, true, false,
            "来源：测试", "{\"reason\":\"测试\"}",
            "WARD:SIG", at.plusSeconds(3600),
            at, "tester", at, "tester", "trace-" + suffix,
            "builtin-risk-baseline", "baseline", CdssAutomationLevel.INTERRUPTIVE,
            CdssReviewRequirement.PHYSICIAN_CONFIRMATION, 72, "OPT04_SILENT_TRIAL",
            false, "NMPA_RESERVED", "TRACEABLE_EVIDENCE_REQUIRED", "测试 CDSS 信号");
    }

    @Test
    void aggregatesClinicalSignalsByCardTypeWithRiskAndAdoptionCounts() {
        Instant now = Instant.now();
        // MEDICATION：3 条（2 高危 / 1 采纳）
        cardRepo.save(card("m1", RecommendationCardType.MEDICATION,
            RecommendationRiskLevel.HIGH, RecommendationCardStatus.ACCEPTED, now.minusSeconds(300)));
        cardRepo.save(card("m2", RecommendationCardType.MEDICATION,
            RecommendationRiskLevel.CRITICAL, RecommendationCardStatus.PENDING, now.minusSeconds(250)));
        cardRepo.save(card("m3", RecommendationCardType.MEDICATION,
            RecommendationRiskLevel.LOW, RecommendationCardStatus.REJECTED, now.minusSeconds(200)));
        // LAB：1 条（低危 / 待处理）
        cardRepo.save(card("l1", RecommendationCardType.LAB,
            RecommendationRiskLevel.MEDIUM, RecommendationCardStatus.PENDING, now.minusSeconds(50)));

        Instant windowStart = now.minusSeconds(3600);
        Instant windowEnd = now.plusSeconds(60);

        long groups = repo.countClinicalSignalGroups(TENANT, windowStart, windowEnd);
        List<ClinicalSignalStat> rows = repo.aggregateClinicalSignals(TENANT, windowStart, windowEnd, 0, 20);

        assertThat(groups).isEqualTo(2L);
        assertThat(rows).hasSize(2);
        ClinicalSignalStat med = rows.stream()
            .filter(r -> r.signalType().equals("MEDICATION")).findFirst().orElseThrow();
        assertThat(med.totalSignals()).isEqualTo(3L);
        assertThat(med.highRiskCount()).isEqualTo(2L);
        assertThat(med.acceptedCount()).isEqualTo(1L);
        assertThat(med.rejectedCount()).isEqualTo(1L);
        assertThat(med.lastSignalAt()).isNotNull();
        // 信号总数倒序：MEDICATION(3) 在 LAB(1) 之前
        assertThat(rows.get(0).signalType()).isEqualTo("MEDICATION");
    }
}
