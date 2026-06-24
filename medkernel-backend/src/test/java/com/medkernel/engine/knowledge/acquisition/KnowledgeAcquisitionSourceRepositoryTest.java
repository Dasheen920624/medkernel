package com.medkernel.engine.knowledge.acquisition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;

import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.SourceType;
import com.medkernel.engine.knowledge.parsing.DocumentFormat;

/** 公域资料来源仓储切片测试：真实 H2 + Flyway，验证调度原子 claim 不拿过期允许清单快照。 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:knowledge-acquisition-source-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class KnowledgeAcquisitionSourceRepositoryTest {

    @Autowired
    KnowledgeAcquisitionSourceRepository repository;
    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void wipe() {
        repository.deleteAll();
    }

    @Test
    void markScheduleSubmittedRefusesSourceDisabledAfterDueScan() {
        KnowledgeAcquisitionSource saved = repository.save(scheduledSource());
        jdbc.update("UPDATE mk_knowledge_acquisition_source SET enabled_flag = 'N' WHERE id = ?", saved.id());
        Instant now = Instant.parse("2026-06-17T03:00:00Z");

        int updated = repository.markScheduleSubmitted(
            saved.tenantId(), saved.id(), saved.version(), now, now.plusSeconds(3600),
            "knowledge-acquisition-scheduler");

        assertThat(updated).isZero();
        assertThat(repository.findById(saved.id())).get()
            .extracting(KnowledgeAcquisitionSource::lastCheckAt)
            .isNull();
    }

    @Test
    void staleGovernanceSnapshotCannotOverwriteNewerDecision() {
        KnowledgeAcquisitionSource saved = repository.save(scheduledSource());
        KnowledgeAcquisitionSource first = repository.findById(saved.id()).orElseThrow();
        KnowledgeAcquisitionSource stale = repository.findById(saved.id()).orElseThrow();

        repository.save(copyWithTitle(first, "先提交的治理决定"));

        assertThatThrownBy(() -> repository.save(copyWithTitle(stale, "陈旧治理决定")))
            .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void scheduleClaimInvalidatesGovernanceSnapshotReadBeforeClaim() {
        KnowledgeAcquisitionSource saved = repository.save(scheduledSource());
        KnowledgeAcquisitionSource stale = repository.findById(saved.id()).orElseThrow();
        Instant now = Instant.parse("2026-06-17T03:00:00Z");

        int updated = repository.markScheduleSubmitted(
            saved.tenantId(), saved.id(), saved.version(), now, now.plusSeconds(3600),
            "knowledge-acquisition-scheduler");

        assertThat(updated).isOne();
        assertThatThrownBy(() -> repository.save(copyWithTitle(stale, "覆盖调度状态的陈旧决定")))
            .isInstanceOf(OptimisticLockingFailureException.class);
    }

    private KnowledgeAcquisitionSource copyWithTitle(KnowledgeAcquisitionSource source, String title) {
        return new KnowledgeAcquisitionSource(
            source.id(), source.tenantId(), source.sourceCode(), source.domain(), source.baseUrl(),
            source.sourceType(), source.authorityLevel(), source.authorityBasis(), title, source.publisher(),
            source.license(), source.licensePolicy(), source.robotsPolicy(), source.enabledFlag(),
            source.scheduleEnabledFlag(), source.scheduleIntervalMinutes(),
            source.nextCheckAt(), source.lastCheckAt(), source.defaultFormat(), source.generationPlanJson(),
            source.createdAt(), source.createdBy(), Instant.now(), "concurrent-editor", source.version());
    }

    private KnowledgeAcquisitionSource scheduledSource() {
        Instant configuredAt = Instant.parse("2026-06-17T00:00:00Z");
        return new KnowledgeAcquisitionSource(
            null,
            "tenant-A",
            "NHC-HTN",
            "guideline.example.org",
            "https://guideline.example.org/htn.txt",
            SourceType.GUIDELINE,
            SourceAuthorityLevel.B_GUIDELINE,
            "国家卫生健康委公开指南",
            "高血压诊疗指南",
            "国家卫生健康委",
            "公开资料许可",
            AcquisitionLicensePolicy.PERMITTED,
            AcquisitionRobotsPolicy.ALLOW_FETCH,
            "Y",
            "Y",
            60,
            Instant.parse("2026-06-17T02:00:00Z"),
            null,
            DocumentFormat.STRUCTURED_TEXT,
            null,
            configuredAt,
            "super-admin",
            configuredAt,
            "super-admin",
            null);
    }
}
