package com.medkernel.engine.sandbox;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.medkernel.engine.sandbox.replay.SandboxReplayAssetBinding;
import com.medkernel.engine.sandbox.replay.SandboxReplayAssetBindingRepository;
import com.medkernel.engine.sandbox.replay.SandboxReplayCase;
import com.medkernel.engine.sandbox.replay.SandboxReplayCaseRepository;
import com.medkernel.engine.sandbox.replay.SandboxReplayStatus;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.SourceTier;
import com.medkernel.engine.versioning.VersionedAssetType;

import static org.assertj.core.api.Assertions.assertThat;

@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:sandbox-runtime-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class SandboxRuntimeRepositoryTest {

    @Autowired SandboxRunRepository runs;
    @Autowired SandboxReplayAssetBindingRepository replayAssets;
    @Autowired SandboxReplayCaseRepository replayCases;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void wipe() {
        runs.deleteAll();
        replayAssets.deleteAll();
        replayCases.deleteAll();
    }

    @Test
    void persistsHistoricalRunAgainstReplayCaseWithoutCurrentRuntimeReleaseForeignKey() {
        Instant now = Instant.parse("2026-06-19T00:00:00Z");
        replayCases.save(new SandboxReplayCase(
            null, "replay-1", "tenant-A", "sha256:" + "1".repeat(64),
            "sha256:" + "2".repeat(64), "sha256:" + "3".repeat(64),
            "sha256:" + "4".repeat(64), "{\"resources\":{}}", "a".repeat(64),
            "sha256:" + "6".repeat(64), 4L, now, "b".repeat(64),
            "MEDKERNEL_D4_STRICT_V1", SandboxReplayStatus.IMPORTED, now, "governor-1",
            null, null, null, now, now, "trace-1"));
        replayAssets.save(new SandboxReplayAssetBinding(
            null, "replay-binding-1", "tenant-A", "replay-1", VersionedAssetType.RULE,
            "RULE.OLD", "rv-old-1", "1", SourceTier.ORG,
            "sha256:" + "5".repeat(64), "{\"dsl\":{}}", "c".repeat(64),
            AssetVersionStatus.WITHDRAWN, now, "governor-1", "trace-1"));

        SandboxRun saved = runs.save(new SandboxRun(
            null, "run-history-1", "tenant-A", "sbx-lab-critical-k",
            SandboxRunMode.HISTORICAL_EXACT, "replay-1", "baseline-history-1",
            "sha256:" + "6".repeat(64), 4L, null, "b".repeat(64),
            SandboxResolutionSource.REPLAY_MANIFEST, "{\"items\":[]}", "d".repeat(64),
            SandboxExternalSideEffectStatus.SUPPRESSED, SandboxRunStatus.RUNNING,
            null, null, now, null, "trace-1", now, "doctor-1", now, "doctor-1"));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.replayCaseId()).isEqualTo("replay-1");
        assertThat(saved.runtimeReleaseRef()).isEqualTo("sha256:" + "6".repeat(64));
        assertThat(saved.runtimeRevisionNo()).isEqualTo(4L);
        assertThat(saved.resolutionSource()).isEqualTo(SandboxResolutionSource.REPLAY_MANIFEST);
    }

    @Test
    void persistsFrozenCurrentRuntimeReleaseWithoutSeparateSandboxBinding() {
        Instant now = Instant.parse("2026-06-19T00:00:00Z");
        SandboxRun saved = runs.save(new SandboxRun(
            null, "run-1", "tenant-A", "sbx-lab-critical-k", SandboxRunMode.CURRENT,
            null, "baseline-1", "runtime-release-7", 7L, "platform-baseline-3",
            "a".repeat(64), SandboxResolutionSource.CURRENT_RUNTIME_RELEASE,
            "{\"items\":[]}", "d".repeat(64), SandboxExternalSideEffectStatus.SUPPRESSED,
            SandboxRunStatus.RUNNING, null, null, now, null, "trace-1",
            now, "doctor-1", now, "doctor-1"));

        assertThat(saved.id()).isNotNull();
        assertThat(runs.findByTenantIdAndRunId("tenant-A", "run-1"))
            .get()
            .satisfies(run -> {
                assertThat(run.runtimeReleaseRef()).isEqualTo("runtime-release-7");
                assertThat(run.runtimeRevisionNo()).isEqualTo(7L);
                assertThat(run.platformBaselineReleaseId()).isEqualTo("platform-baseline-3");
                assertThat(run.manifestSha256()).isEqualTo("a".repeat(64));
                assertThat(run.externalSideEffectStatus())
                    .isEqualTo(SandboxExternalSideEffectStatus.SUPPRESSED);
            });
    }

    @Test
    void persistsFailedAttemptEvenWhenRuntimeReleaseCannotBeResolved() {
        Instant now = Instant.parse("2026-06-19T00:00:00Z");
        SandboxRun preparing = runs.save(new SandboxRun(
            null, "run-missing-baseline", "tenant-A", "sbx-lab-critical-k",
            SandboxRunMode.CURRENT, null, null, null, null, null, null, null, null, null,
            SandboxExternalSideEffectStatus.SUPPRESSED, SandboxRunStatus.PREPARING,
            null, null, now, null, "trace-missing", now, "doctor-1", now, "doctor-1"));

        SandboxRun failed = runs.save(new SandboxRun(
            preparing.id(), preparing.runId(), preparing.tenantId(), preparing.scenarioId(),
            preparing.mode(), null, null, null, null, null, null, null, null, null,
            SandboxExternalSideEffectStatus.SUPPRESSED, SandboxRunStatus.FAILED,
            "SANDBOX_RUNTIME_RELEASE_MISSING", "医院尚未启用机构生效版本",
            now, now, preparing.traceId(), preparing.createdAt(), preparing.createdBy(),
            now, "doctor-1"));

        assertThat(failed.status()).isEqualTo(SandboxRunStatus.FAILED);
        assertThat(failed.baselineId()).isNull();
        assertThat(failed.failureCode()).isEqualTo("SANDBOX_RUNTIME_RELEASE_MISSING");
    }

    @Test
    void cleanBaselineContainsOnlyAppendOnlySandboxRunLedger() {
        Integer count = jdbc.queryForObject(
            """
            SELECT COUNT(*)
              FROM information_schema.tables
             WHERE lower(table_name) = 'mk_sandbox_runtime_binding'
            """,
            Integer.class);

        assertThat(count).isZero();
    }
}
