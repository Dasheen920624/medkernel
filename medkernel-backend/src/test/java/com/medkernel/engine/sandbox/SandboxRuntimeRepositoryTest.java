package com.medkernel.engine.sandbox;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.data.relational.core.conversion.DbActionExecutionException;
import org.springframework.test.context.TestPropertySource;

import org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException;

import com.medkernel.engine.pkg.KnowledgePackage;
import com.medkernel.engine.pkg.KnowledgePackageRepository;
import com.medkernel.engine.pkg.KnowledgePackageStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Autowired SandboxRuntimeBindingRepository bindings;
    @Autowired SandboxRunRepository runs;
    @Autowired KnowledgePackageRepository packages;

    @AfterEach
    void wipe() {
        runs.deleteAll();
        bindings.deleteAll();
        packages.deleteAll();
    }

    @Test
    void persistsBindingAndFrozenRunWithoutLosingResolvedVersion() {
        Instant now = Instant.parse("2026-06-19T00:00:00Z");
        packages.save(pack("pkg-1", "1.0.0"));
        SandboxRuntimeBinding savedBinding = bindings.save(binding("binding-1", "pkg-1", "1.0.0", now));

        SandboxRun savedRun = runs.save(new SandboxRun(
            null, "run-1", "tenant-A", "sbx-lab-critical-k", SandboxRunMode.CURRENT,
            savedBinding.bindingId(), "baseline-1", "tenant-A", "pkg-1", "PKG.SANDBOX", "1.0.0",
            SandboxResolutionSource.TENANT_PACKAGE, "{\"items\":[]}", "a".repeat(64),
            SandboxExternalSideEffectStatus.SUPPRESSED, SandboxRunStatus.RUNNING,
            null, null, now, null, "trace-1", now, "doctor-1", now, "doctor-1"));

        assertThat(savedBinding.id()).isNotNull();
        assertThat(savedRun.id()).isNotNull();
        assertThat(runs.findByTenantIdAndRunId("tenant-A", "run-1"))
            .get()
            .satisfies(run -> {
                assertThat(run.packageVersion()).isEqualTo("1.0.0");
                assertThat(run.baselineHash()).isEqualTo("a".repeat(64));
                assertThat(run.externalSideEffectStatus())
                    .isEqualTo(SandboxExternalSideEffectStatus.SUPPRESSED);
            });
    }

    @Test
    void databaseRejectsSecondActiveBindingButAllowsInactiveHistory() {
        Instant now = Instant.parse("2026-06-19T00:00:00Z");
        packages.save(pack("pkg-1", "1.0.0"));
        packages.save(pack("pkg-2", "2.0.0"));
        bindings.save(binding("binding-1", "pkg-1", "1.0.0", now));

        assertThatThrownBy(() -> bindings.save(binding("binding-2", "pkg-2", "2.0.0", now)))
            .isInstanceOf(DbActionExecutionException.class)
            .hasRootCauseInstanceOf(JdbcSQLIntegrityConstraintViolationException.class);

        bindings.save(inactiveBinding("binding-history-1", "pkg-1", "1.0.0", now));
        bindings.save(inactiveBinding("binding-history-2", "pkg-2", "2.0.0", now));
        assertThat(bindings.findByTenantIdAndStatusOrderByActivatedAtDescIdDesc(
            "tenant-A", SandboxRuntimeBindingStatus.INACTIVE)).hasSize(2);
    }

    private static KnowledgePackage pack(String packageId, String version) {
        Instant now = Instant.parse("2026-06-19T00:00:00Z");
        return new KnowledgePackage(
            null, packageId, "tenant-A", "PKG.SANDBOX", version, "沙盘包", "测试",
            KnowledgePackageStatus.ACTIVE, now, "governor-1", now, "governor-1", "trace-1");
    }

    private static SandboxRuntimeBinding binding(
            String bindingId, String packageId, String version, Instant now) {
        return new SandboxRuntimeBinding(
            null, bindingId, "tenant-A", "hospital-A", "tenant-A", packageId,
            "PKG.SANDBOX", version, SandboxRuntimeBindingStatus.ACTIVE, "tenant-A|ACTIVE",
            now, "governor-1", now, "governor-1", now, "governor-1", "trace-1");
    }

    private static SandboxRuntimeBinding inactiveBinding(
            String bindingId, String packageId, String version, Instant now) {
        return new SandboxRuntimeBinding(
            null, bindingId, "tenant-A", "hospital-A", "tenant-A", packageId,
            "PKG.SANDBOX", version, SandboxRuntimeBindingStatus.INACTIVE, null,
            now, "governor-1", now, "governor-1", now, "governor-1", "trace-1");
    }
}
