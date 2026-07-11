package com.medkernel.shared.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeConfigurationContractTest {

    private final Path repoRoot = Path.of("..").toAbsolutePath().normalize();

    @Test
    void runtimeProfilesExposeOperationsContract() throws IOException {
        for (String profile : List.of(
            "application.yml",
            "application-dev.yml",
            "application-test.yml",
            "application-container.yml",
            "application-govcloud.yml"
        )) {
            Path file = backendResource(profile);
            assertThat(file).as(profile).exists();
            assertThat(Files.readString(file)).as(profile)
                .contains("medkernel:")
                .contains("runtime:")
                .doesNotContain("Phase-1")
                .doesNotContain("GA-CORE")
                .doesNotContain("W1-G");
        }

        String govcloud = Files.readString(backendResource("application-govcloud.yml"));
        assertThat(govcloud)
            .contains("deployment-mode: govcloud")
            .contains("database-dialect: ${MEDKERNEL_GOV_DATABASE_DIALECT:dm}")
            .contains("classpath:db/migration/${MEDKERNEL_GOV_DATABASE_DIALECT:dm}")
            .contains("target-os: 麒麟 / 统信 / openEuler")
            .contains("database-vendors:")
            .contains("达梦")
            .contains("人大金仓");
    }

    @Test
    void containerProfileKeepsRuntimeSwitchesInConfigurationCenterShape() throws IOException {
        String container = Files.readString(backendResource("application-container.yml"));

        assertThat(container)
            .contains("feature-flags:")
            .contains("graph-projection:")
            .contains("search-projection:")
            .contains("dify-workflow:")
            .contains("external-provider:")
            .doesNotContain("graph-enabled:")
            .doesNotContain("dify-enabled:");
    }

    @Test
    void backupRestoreScriptsRequireSha256Evidence() throws IOException {
        String backup = Files.readString(repoRoot.resolve("deploy/docker/scripts/backup.sh"));
        String restore = Files.readString(repoRoot.resolve("deploy/docker/scripts/restore.sh"));
        String drill = Files.readString(repoRoot.resolve("deploy/docker/scripts/backup-restore-drill.sh"));
        String validator = Files.readString(repoRoot.resolve("deploy/docker/tests/validate-deployment-assets.sh"));

        assertThat(backup)
            .contains("checksum_file")
            .contains(".sha256")
            .contains("PostgreSQL backup checksum created");
        assertThat(restore)
            .contains("verify_checksum")
            .contains(".sha256")
            .contains("PostgreSQL backup checksum verified");
        assertThat(drill)
            .contains("MEDKERNEL_BACKUP_DRILL_DB")
            .contains("pg_restore")
            .contains("restore drill evidence")
            .contains("status=SUCCESS")
            .contains("completed_at=")
            .contains("backup_file=")
            .contains("checksum_file=")
            .contains("drill_database=")
            .contains("flyway_schema_history_rows=")
            .contains("rpo=")
            .contains("rto=")
            .contains("latest-restore-drill.properties")
            .contains("LATEST_EVIDENCE_TMP")
            .contains("mv \"$LATEST_EVIDENCE_TMP\" \"$LATEST_EVIDENCE_FILE\"")
            .contains("flyway_schema_history")
            .doesNotContain(" -d \"$MEDKERNEL_DB_NAME\"");
        assertThat(validator)
            .contains("backup-restore-drill.sh")
            .contains("checksum_file")
            .contains("verify_checksum")
            .contains(".sha256");
    }

    @Test
    void govcloudSmokeScriptFailsClosedWithoutRealDomesticConnection() throws IOException {
        String smoke = Files.readString(repoRoot.resolve("deploy/docker/scripts/govcloud-smoke.sh"));
        String validator = Files.readString(repoRoot.resolve("deploy/docker/tests/validate-deployment-assets.sh"));

        assertThat(smoke)
            .contains("MEDKERNEL_GOV_DB_URL")
            .contains("MEDKERNEL_GOV_DB_DRIVER")
            .contains("MEDKERNEL_GOV_DATABASE_DIALECT")
            .contains("MEDKERNEL_GOV_EVIDENCE_DIR")
            .contains("govcloud smoke evidence")
            .contains("status=PASS")
            .contains("status=FAIL")
            .contains("jdbc_jar_sha256")
            .contains("dm|kingbase")
            .contains("Domestic crypto smoke passed")
            .contains("mvn -q -Dtest=SmCryptoServiceTest test")
            .doesNotContain("|| true");
        assertThat(validator)
            .contains("MEDKERNEL_GOV_EVIDENCE_DIR")
            .contains("govcloud smoke evidence")
            .contains("jdbc_jar_sha256");
    }

    private Path backendResource(String file) {
        return Path.of("src/main/resources").resolve(file);
    }
}
