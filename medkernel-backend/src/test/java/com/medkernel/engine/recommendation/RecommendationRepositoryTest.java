package com.medkernel.engine.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.medkernel.engine.cdss.risk.CdssAutomationLevel;
import com.medkernel.engine.cdss.risk.CdssReviewRequirement;
import com.medkernel.testsupport.ClinicalRuntimeReleaseFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:recommendation-repo-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class RecommendationRepositoryTest {

    @Autowired RecommendationTriggerRepository triggers;
    @Autowired RecommendationCardRepository cards;
    @Autowired RecommendationSourceRepository sources;
    @Autowired RecommendationFeedbackRepository feedback;
    @Autowired RecommendationFatigueSignalRepository fatigueSignals;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seedRuntimeRelease() {
        ClinicalRuntimeReleaseFixture.insert(
            jdbc, "tenant-A", "hospital-A", "runtime-release-test");
    }

    @AfterEach
    void wipe() {
        fatigueSignals.deleteAll();
        feedback.deleteAll();
        sources.deleteAll();
        cards.deleteAll();
        triggers.deleteAll();
        ClinicalRuntimeReleaseFixture.delete(jdbc, "runtime-release-test");
    }

    @Test
    void persistsRecommendationRuntimeFacts() {
        String triggerId = "rt-" + UUID.randomUUID();
        String cardId = "rc-" + UUID.randomUUID();

        RecommendationTrigger savedTrigger = triggers.save(sampleTrigger(triggerId, "tenant-A"));
        RecommendationCard savedCard = cards.save(sampleCard(cardId, "tenant-A", triggerId));
        RecommendationSource savedSource = sources.save(sampleSource("rs-" + UUID.randomUUID(), "tenant-A", cardId));
        RecommendationFeedback savedFeedback = feedback.save(sampleFeedback("rf-" + UUID.randomUUID(), "tenant-A", cardId));
        RecommendationFatigueSignal savedSignal =
            fatigueSignals.save(sampleFatigueSignal("rfs-" + UUID.randomUUID(), "tenant-A", triggerId, cardId));

        assertThat(savedTrigger.id()).isNotNull();
        assertThat(savedCard.id()).isNotNull();
        assertThat(savedSource.id()).isNotNull();
        assertThat(savedFeedback.id()).isNotNull();
        assertThat(savedSignal.id()).isNotNull();

        assertThat(triggers.findByTriggerIdAndTenantId(triggerId, "tenant-A")).isPresent();
        assertThat(cards.findByTriggerIdAndTenantIdOrderByCreatedAtAsc(triggerId, "tenant-A"))
            .extracting(RecommendationCard::cardCode)
            .containsExactly("CARD.ANTICOAG");
        assertThat(sources.findByCardIdAndTenantIdOrderByCreatedAtAsc(cardId, "tenant-A"))
            .extracting(RecommendationSource::sourceType)
            .containsExactly(RecommendationSourceType.RULE);
        assertThat(feedback.findByCardIdAndTenantIdOrderByCreatedAtAsc(cardId, "tenant-A"))
            .extracting(RecommendationFeedback::feedbackType)
            .containsExactly(RecommendationFeedbackType.ACCEPT);
        assertThat(feedback.findByCardIdAndTenantIdAndIdempotencyKey(
                cardId, "tenant-A", "idem-" + cardId))
            .map(RecommendationFeedback::feedbackId)
            .contains(savedFeedback.feedbackId());
        assertThat(fatigueSignals.findByCardIdAndTenantIdOrderByCreatedAtAsc(cardId, "tenant-A"))
            .extracting(RecommendationFatigueSignal::signalType)
            .containsExactly(RecommendationFatigueSignalType.ACCEPTED);
    }

    @Test
    void repositoryQueriesDoNotLeakAcrossTenants() {
        String triggerId = "rt-" + UUID.randomUUID();
        String cardId = "rc-" + UUID.randomUUID();
        triggers.save(sampleTrigger(triggerId, "tenant-A"));
        cards.save(sampleCard(cardId, "tenant-A", triggerId));

        Optional<RecommendationTrigger> wrongTenant = triggers.findByTriggerIdAndTenantId(triggerId, "tenant-B");
        assertThat(wrongTenant).isEmpty();

        // CDSS-M-04：卡片维度同样零泄露——错误租户查不到该卡、按 triggerId 列不出、按租户计数为 0
        assertThat(cards.findByCardIdAndTenantId(cardId, "tenant-B")).isEmpty();
        assertThat(cards.findByTriggerIdAndTenantIdOrderByCreatedAtAsc(triggerId, "tenant-B")).isEmpty();
        assertThat(cards.countByFilter("tenant-B", null, null, null, null, null, null)).isZero();

        // 正确租户下可正常读到，证明隔离来自 tenant 过滤而非数据缺失
        assertThat(cards.findByCardIdAndTenantId(cardId, "tenant-A")).isPresent();
    }

    @Test
    void acceptsClinicalRedlineSourceTypeForRecallEvidence() {
        String triggerId = "rt-" + UUID.randomUUID();
        String cardId = "rc-" + UUID.randomUUID();
        triggers.save(sampleTrigger(triggerId, "tenant-A"));
        cards.save(sampleCard(cardId, "tenant-A", triggerId));
        sources.save(sampleSource("rs-redline", "tenant-A", cardId, RecommendationSourceType.REDLINE));

        assertThat(sources.findByCardIdAndTenantIdOrderByCreatedAtAsc(cardId, "tenant-A"))
            .extracting(RecommendationSource::sourceType)
            .containsExactly(RecommendationSourceType.REDLINE);
    }

    @Test
    void cardListFiltersByPatientEncounterAndTriggerPoint() {
        String triggerId = "rt-" + UUID.randomUUID();
        String cardId = "rc-" + UUID.randomUUID();
        triggers.save(sampleTrigger(triggerId, "tenant-A"));
        cards.save(sampleCard(cardId, "tenant-A", triggerId));

        assertThat(cards.countByFilter("tenant-A", null, null, "WARD_ORDER",
            "patient-1", "enc-1", "order-sign")).isEqualTo(1);
        assertThat(cards.countByFilter("tenant-A", null, null, "WARD_ORDER",
            "patient-1", "enc-other", "order-sign")).isZero();
        assertThat(cards.pageByFilter("tenant-A", null, null, "WARD_ORDER",
                "patient-1", "enc-1", "order-sign", 0, 10))
            .extracting(RecommendationCard::cardId)
            .containsExactly(cardId);
    }

    @Test
    void openRecommendationCardsProjectToWorkflowTodoRows() {
        String triggerId = "rt-" + UUID.randomUUID();
        String cardId = "rc-" + UUID.randomUUID();
        triggers.save(sampleTrigger(triggerId, "tenant-A"));
        cards.save(sampleCard(cardId, "tenant-A", triggerId));

        List<RecommendationWorkflowTodoRow> rows = cards.pageOpenWorkflowRows("tenant-A", 0, 10);

        assertThat(rows).singleElement()
            .satisfies(row -> {
                assertThat(row.cardId()).isEqualTo(cardId);
                assertThat(row.cardType()).isEqualTo(RecommendationCardType.MEDICATION);
                assertThat(row.riskLevel()).isEqualTo(RecommendationRiskLevel.HIGH);
                assertThat(row.status()).isEqualTo(RecommendationCardStatus.PENDING);
                assertThat(row.patientId()).isEqualTo("patient-1");
                assertThat(row.triggerType()).isEqualTo("order-sign");
            });
    }

    @Test
    void openRecommendationWorkflowRowsCanBeScopedToClinicalEventSource() {
        String firstTriggerId = "rt-" + UUID.randomUUID();
        String firstCardId = "rc-" + UUID.randomUUID();
        String otherTriggerId = "rt-" + UUID.randomUUID();
        String otherCardId = "rc-" + UUID.randomUUID();
        triggers.save(sampleTrigger(firstTriggerId, "tenant-A", "evt-order-1"));
        cards.save(sampleCard(firstCardId, "tenant-A", firstTriggerId));
        triggers.save(sampleTrigger(otherTriggerId, "tenant-A", "evt-other"));
        cards.save(sampleCard(otherCardId, "tenant-A", otherTriggerId));

        List<RecommendationWorkflowTodoRow> rows =
            cards.pageOpenWorkflowRowsBySourceEventId("tenant-A", "evt-order-1", 0, 10);

        assertThat(rows).singleElement()
            .satisfies(row -> {
                assertThat(row.cardId()).isEqualTo(firstCardId);
                assertThat(row.patientId()).isEqualTo("patient-1");
                assertThat(row.scenarioCode()).isEqualTo("WARD_ORDER");
            });
    }

    @Test
    void fatigueRepositoryCountsRecentLowValueSignalsForSuppression() {
        String triggerId = "rt-" + UUID.randomUUID();
        String cardId = "rc-" + UUID.randomUUID();
        triggers.save(sampleTrigger(triggerId, "tenant-A"));
        cards.save(sampleCard(cardId, "tenant-A", triggerId));
        fatigueSignals.save(sampleFatigueSignal("rfs-accepted", "tenant-A", triggerId, cardId,
            RecommendationFatigueSignalType.ACCEPTED));
        fatigueSignals.save(sampleFatigueSignal("rfs-rejected", "tenant-A", triggerId, cardId,
            RecommendationFatigueSignalType.REJECTED));
        fatigueSignals.save(sampleFatigueSignal("rfs-dismissed", "tenant-A", triggerId, cardId,
            RecommendationFatigueSignalType.DISMISSED));

        assertThat(fatigueSignals.countLowValueSignals(
                "tenant-A", "patient-1", "WARD_ORDER:ANTICOAG", Instant.now().minusSeconds(3600)))
            .isEqualTo(2);
    }

    private RecommendationTrigger sampleTrigger(String triggerId, String tenantId) {
        return sampleTrigger(triggerId, tenantId, "event-1");
    }

    private RecommendationTrigger sampleTrigger(String triggerId, String tenantId, String sourceEventId) {
        Instant now = Instant.now();
        return new RecommendationTrigger(
            null, triggerId, tenantId, "TRG." + triggerId, "order-sign",
            sourceEventId, "snapshot-1", "patient-1", "enc-1", "pathway-1",
            "WARD_ORDER", "runtime-release-test", "sha256:trigger", RecommendationTriggerStatus.EVALUATED,
            null, now, now, "tester", now, "tester", "trace-recommendation");
    }

    private RecommendationCard sampleCard(String cardId, String tenantId, String triggerId) {
        Instant now = Instant.now();
        return new RecommendationCard(
            null, cardId, tenantId, triggerId, "CARD.ANTICOAG", RecommendationCardType.MEDICATION,
            "抗凝用药风险提醒", "患者当前医嘱满足抗凝风险规则", "请确认出血风险评估",
            RecommendationRiskLevel.HIGH, RecommendationInterruptLevel.WEAK_INTERRUPTIVE,
            RecommendationCardStatus.PENDING, true, false,
            "来源：抗凝用药规则 v1", "{\"reason\":\"规则命中\"}",
            "WARD_ORDER:ANTICOAG", now.plusSeconds(3600),
            now, "tester", now, "tester", "trace-recommendation",
            "builtin-risk-baseline", "baseline", CdssAutomationLevel.INTERRUPTIVE,
            CdssReviewRequirement.PHYSICIAN_CONFIRMATION, 72, "OPT04_SILENT_TRIAL",
            false, "NMPA_RESERVED", "TRACEABLE_EVIDENCE_REQUIRED", "高危 CDSS 输出必须医师确认");
    }

    private RecommendationSource sampleSource(String sourceId, String tenantId, String cardId) {
        return sampleSource(sourceId, tenantId, cardId, RecommendationSourceType.RULE);
    }

    private RecommendationSource sampleSource(
            String sourceId, String tenantId, String cardId, RecommendationSourceType sourceType) {
        Instant now = Instant.now();
        return new RecommendationSource(
            null, sourceId, tenantId, cardId, sourceType,
            "rule-1", "v1", "抗凝用药规则", "§2.1",
            "sha256:source", "规则命中抗凝药品类别",
            now, "tester", now, "tester", "trace-recommendation");
    }

    private RecommendationFeedback sampleFeedback(String feedbackId, String tenantId, String cardId) {
        Instant now = Instant.now();
        return new RecommendationFeedback(
            null, feedbackId, tenantId, cardId, "idem-" + cardId, RecommendationFeedbackType.ACCEPT,
            "CONFIRMED", "已完成出血风险评估", "doctor-1", "DOCTOR",
            now, "doctor-1", now, "doctor-1", "trace-recommendation");
    }

    private RecommendationFatigueSignal sampleFatigueSignal(
            String signalId, String tenantId, String triggerId, String cardId) {
        return sampleFatigueSignal(signalId, tenantId, triggerId, cardId, RecommendationFatigueSignalType.ACCEPTED);
    }

    private RecommendationFatigueSignal sampleFatigueSignal(
            String signalId,
            String tenantId,
            String triggerId,
            String cardId,
            RecommendationFatigueSignalType signalType) {
        Instant now = Instant.now();
        return new RecommendationFatigueSignal(
            null, signalId, tenantId, triggerId, cardId, "WARD_ORDER:ANTICOAG",
            "patient-1", "enc-1", "doctor-1", signalType,
            1, now.minusSeconds(300), now, "doctor-1", now, "doctor-1", "trace-recommendation");
    }
}
