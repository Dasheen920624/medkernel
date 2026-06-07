package com.medkernel.shared.runtime;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeBackupDrillEvidenceReaderTest {

    @TempDir
    Path tempDir;

    private final RuntimeBackupDrillEvidenceReader reader = new RuntimeBackupDrillEvidenceReader();

    @Test
    void missingEvidenceIsReportedHonestly() {
        RuntimeOperationsSnapshot.RuntimeBackupDrillEvidence evidence =
            reader.read(tempDir.resolve("missing.properties").toString());

        assertThat(evidence.status()).isEqualTo("NOT_AVAILABLE");
        assertThat(evidence.completedAt()).isNull();
        assertThat(evidence.migrationCount()).isNull();
        assertThat(evidence.evidenceReference()).isNull();
        assertThat(evidence.detail()).contains("尚未提供");
    }

    @Test
    void successfulEvidenceExposesOnlySafeVerificationFacts() throws Exception {
        Path evidenceFile = tempDir.resolve("latest-restore-drill.properties");
        Files.writeString(evidenceFile, """
            status=SUCCESS
            completed_at=2026-06-06T16:30:00Z
            backup_file=/private/runtime/backups/secret.dump
            drill_database=medkernel_restore_drill
            flyway_schema_history_rows=96
            """);

        RuntimeOperationsSnapshot.RuntimeBackupDrillEvidence evidence =
            reader.read(evidenceFile.toString());

        assertThat(evidence.status()).isEqualTo("SUCCESS");
        assertThat(evidence.completedAt()).hasToString("2026-06-06T16:30:00Z");
        assertThat(evidence.migrationCount()).isEqualTo(96);
        assertThat(evidence.evidenceReference()).isEqualTo("latest-restore-drill.properties");
        assertThat(evidence.detail()).contains("隔离恢复演练通过");
        assertThat(evidence.toString()).doesNotContain("secret.dump", "medkernel_restore_drill");
    }

    @Test
    void malformedEvidenceFailsClosed() throws Exception {
        Path evidenceFile = tempDir.resolve("latest-restore-drill.properties");
        Files.writeString(evidenceFile, """
            status=SUCCESS
            completed_at=not-a-time
            flyway_schema_history_rows=0
            """);

        RuntimeOperationsSnapshot.RuntimeBackupDrillEvidence evidence =
            reader.read(evidenceFile.toString());

        assertThat(evidence.status()).isEqualTo("INVALID");
        assertThat(evidence.completedAt()).isNull();
        assertThat(evidence.migrationCount()).isNull();
        assertThat(evidence.detail()).contains("格式无效");
    }
}
