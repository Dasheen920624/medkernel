package com.medkernel.shared.runtime;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Properties;

import org.springframework.stereotype.Component;

import com.medkernel.shared.runtime.RuntimeOperationsSnapshot.RuntimeBackupDrillEvidence;

/**
 * 只读解析部署侧隔离恢复演练证据，不执行备份或恢复操作。
 */
@Component
public class RuntimeBackupDrillEvidenceReader {

    private static final String STATUS_SUCCESS = "SUCCESS";

    /**
     * 读取演练证据并失败关闭。
     *
     * @param configuredPath 部署侧 latest 证据文件路径
     * @return 可安全展示的演练事实
     */
    public RuntimeBackupDrillEvidence read(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return RuntimeBackupDrillEvidence.notAvailable();
        }

        try {
            Path path = Path.of(configuredPath).normalize();
            if (!Files.isRegularFile(path)) {
                return RuntimeBackupDrillEvidence.notAvailable();
            }

            Properties evidence = new Properties();
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                evidence.load(reader);
            }
            String status = evidence.getProperty("status", "").trim();
            Instant completedAt = Instant.parse(evidence.getProperty("completed_at", "").trim());
            int migrationCount = Integer.parseInt(
                evidence.getProperty("flyway_schema_history_rows", "").trim());
            if (!STATUS_SUCCESS.equals(status) || migrationCount <= 0) {
                return RuntimeBackupDrillEvidence.invalid();
            }
            return new RuntimeBackupDrillEvidence(
                STATUS_SUCCESS,
                completedAt,
                migrationCount,
                path.getFileName().toString(),
                "隔离恢复演练通过，迁移历史校验正常"
            );
        } catch (IOException | InvalidPathException | DateTimeParseException | NumberFormatException ex) {
            return RuntimeBackupDrillEvidence.invalid();
        }
    }
}
