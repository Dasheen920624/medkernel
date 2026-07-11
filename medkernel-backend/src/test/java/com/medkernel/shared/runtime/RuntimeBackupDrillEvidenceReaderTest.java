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
            checksum_file=/private/runtime/backups/secret.dump.sha256
            drill_database=medkernel_restore_drill
            flyway_schema_history_rows=96
            rpo=24 小时
            rto=4 小时
            """);

        RuntimeOperationsSnapshot.RuntimeBackupDrillEvidence evidence =
            reader.read(evidenceFile.toString());

        assertThat(evidence.status()).isEqualTo("SUCCESS");
        assertThat(evidence.completedAt()).hasToString("2026-06-06T16:30:00Z");
        assertThat(evidence.migrationCount()).isEqualTo(96);
        assertThat(evidence.evidenceReference()).isEqualTo("latest-restore-drill.properties");
        assertThat(evidence.checksumEvidence()).isEqualTo("SHA-256 摘要已校验");
        assertThat(evidence.drillDatabaseIsIsolated()).isTrue();
        assertThat(evidence.rpo()).isEqualTo("24 小时");
        assertThat(evidence.rto()).isEqualTo("4 小时");
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

    @Test
    void successWithoutChecksumOrIsolatedDatabaseFailsClosed() throws Exception {
        Path missingChecksum = tempDir.resolve("missing-checksum.properties");
        Files.writeString(missingChecksum, """
            status=SUCCESS
            completed_at=2026-06-06T16:30:00Z
            backup_file=/private/runtime/backups/drill.dump
            drill_database=medkernel_restore_drill
            flyway_schema_history_rows=96
            rpo=24 小时
            rto=4 小时
            """);

        RuntimeOperationsSnapshot.RuntimeBackupDrillEvidence checksumEvidence =
            reader.read(missingChecksum.toString());

        assertThat(checksumEvidence.status()).isEqualTo("INVALID");

        Path productionDatabase = tempDir.resolve("production-database.properties");
        Files.writeString(productionDatabase, """
            status=SUCCESS
            completed_at=2026-06-06T16:30:00Z
            backup_file=/private/runtime/backups/drill.dump
            checksum_file=/private/runtime/backups/drill.dump.sha256
            drill_database=medkernel
            flyway_schema_history_rows=96
            rpo=24 小时
            rto=4 小时
            """);

        RuntimeOperationsSnapshot.RuntimeBackupDrillEvidence productionEvidence =
            reader.read(productionDatabase.toString());

        assertThat(productionEvidence.status()).isEqualTo("INVALID");
    }

    @Test
    void successWithoutRpoOrRtoFailsClosed() throws Exception {
        Path missingRpo = tempDir.resolve("missing-rpo.properties");
        Files.writeString(missingRpo, """
            status=SUCCESS
            completed_at=2026-06-06T16:30:00Z
            backup_file=/private/runtime/backups/drill.dump
            checksum_file=/private/runtime/backups/drill.dump.sha256
            drill_database=medkernel_restore_drill
            flyway_schema_history_rows=96
            rto=4 小时
            """);

        RuntimeOperationsSnapshot.RuntimeBackupDrillEvidence rpoEvidence =
            reader.read(missingRpo.toString());

        assertThat(rpoEvidence.status()).isEqualTo("INVALID");

        Path missingRto = tempDir.resolve("missing-rto.properties");
        Files.writeString(missingRto, """
            status=SUCCESS
            completed_at=2026-06-06T16:30:00Z
            backup_file=/private/runtime/backups/drill.dump
            checksum_file=/private/runtime/backups/drill.dump.sha256
            drill_database=medkernel_restore_drill
            flyway_schema_history_rows=96
            rpo=24 小时
            """);

        RuntimeOperationsSnapshot.RuntimeBackupDrillEvidence rtoEvidence =
            reader.read(missingRto.toString());

        assertThat(rtoEvidence.status()).isEqualTo("INVALID");
    }

    @Test
    void customProductionDatabaseNameCannotBeReportedAsIsolated() throws Exception {
        Path evidenceFile = tempDir.resolve("custom-production.properties");
        Files.writeString(evidenceFile, """
            status=SUCCESS
            completed_at=2026-06-06T16:30:00Z
            backup_file=/private/runtime/backups/drill.dump
            checksum_file=/private/runtime/backups/drill.dump.sha256
            drill_database=medkernel_prod
            flyway_schema_history_rows=96
            rpo=24 小时
            rto=4 小时
            """);

        RuntimeOperationsSnapshot.RuntimeBackupDrillEvidence evidence =
            reader.read(evidenceFile.toString(), "medkernel_prod");

        assertThat(evidence.status()).isEqualTo("INVALID");
    }
}
