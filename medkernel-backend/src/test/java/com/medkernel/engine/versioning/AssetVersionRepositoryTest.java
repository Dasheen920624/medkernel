package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.data.relational.core.conversion.DbActionExecutionException;
import org.springframework.test.context.TestPropertySource;

import com.medkernel.shared.ids.Ulid;

@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:asset-version-repo-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class AssetVersionRepositoryTest {

    @Autowired AssetVersionRepository repository;

    @AfterEach
    void wipe() {
        repository.deleteAll();
    }

    @Test
    void persistsAssetVersionAndFindsActiveVersionByEffectiveDomain() {
        AssetVersion saved = repository.save(sample(
            newVersionId(),
            "1.0.0",
            AssetVersionStatus.ACTIVE,
            "RULE.VTE.RISK|/GROUP/g-1/HOSPITAL/h-1|adult|inpatient"
        ));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.safetyPolicy()).isEqualTo(AssetVersionSafetyPolicy.NORMAL);
        assertThat(repository.findByVersionIdAndTenantId(saved.versionId(), "tenant-A")).contains(saved);
        assertThat(repository.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK|/GROUP/g-1/HOSPITAL/h-1|adult|inpatient",
            AssetVersionStatus.ACTIVE
        )).containsExactly(saved);
    }

    @Test
    void databaseRejectsSecondActiveVersionInSameEffectiveDomain() {
        String activeScopeKey = "RULE.VTE.RISK|/GROUP/g-1/HOSPITAL/h-1|adult|inpatient";
        repository.save(sample(newVersionId(), "1.0.0", AssetVersionStatus.ACTIVE, activeScopeKey));

        assertThatThrownBy(() ->
            repository.save(sample(newVersionId(), "1.0.1", AssetVersionStatus.ACTIVE, activeScopeKey))
        )
            .isInstanceOf(DbActionExecutionException.class)
            .hasRootCauseInstanceOf(JdbcSQLIntegrityConstraintViolationException.class);
    }

    private AssetVersion sample(
            String versionId,
            String versionNo,
            AssetVersionStatus status,
            String activeScopeKey) {
        Instant now = Instant.parse("2026-06-03T08:00:00Z");
        return new AssetVersion(
            null,
            versionId,
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            versionNo,
            "/GROUP/g-1/HOSPITAL/h-1",
            "adult|inpatient",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            status,
            activeScopeKey,
            "rule/RULE.VTE.RISK",
            null,
            null,
            now,
            "reviewer-1",
            now,
            "reviewer-1",
            "trace-sys04"
        );
    }

    private String newVersionId() {
        return "av-" + Ulid.newUlid();
    }
}
