package com.medkernel.shared.runtime.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 全新 V1 基线中的运行任务框架合同。
 */
class RuntimeTaskMigrationContractTest {

    private final Path migrationRoot = Path.of("src/main/resources/db/migration");

    @Test
    void runtimeTaskMigrationExistsInEveryDialectWithChineseComments() throws IOException {
        for (String dialect : List.of("h2", "postgres", "oracle", "dm", "kingbase")) {
            Path migration = migrationRoot.resolve(dialect).resolve("V1__baseline.sql");
            assertThat(migration).as(dialect + " runtime task migration").exists();
            String sql = Files.readString(migration);
            assertThat(sql)
                .contains("sys_task")
                .contains("uk_sys_task_tenant_task")
                .contains("idx_sys_task_status_ts")
                .contains("idx_sys_task_mode_ts")
                .contains("idx_sys_task_org_ts")
                .contains("任务运行框架");
            assertThat(sql)
                .contains("COMMENT ON TABLE sys_task")
                .contains("COMMENT ON COLUMN sys_task.task_id")
                .contains("COMMENT ON COLUMN sys_task.status");
        }
    }

    @Test
    void retryDeadLetterMigrationExistsInEveryDialectWithHonestStatusAndChineseComments() throws IOException {
        for (String dialect : List.of("h2", "postgres", "oracle", "dm", "kingbase")) {
            Path migration = migrationRoot.resolve(dialect).resolve("V1__baseline.sql");
            assertThat(migration).as(dialect + " retry dead letter migration").exists();
            String sql = Files.readString(migration);
            assertThat(sql)
                .contains("sys_task_dead_letter")
                .contains("retry_count")
                .contains("max_retries")
                .contains("dead_letter_id")
                .contains("OFFLINE")
                .contains("NOT_CONNECTED")
                .contains("DEAD_LETTER")
                .contains("任务死信");
            assertThat(sql)
                .contains("COMMENT ON TABLE sys_task_dead_letter")
                .contains("COMMENT ON COLUMN sys_task_dead_letter.dead_letter_id")
                .contains("COMMENT ON COLUMN sys_task.retry_count");
        }
    }
}
