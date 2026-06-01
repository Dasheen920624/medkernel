package com.medkernel.shared.runtime.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * SYS-05 PR1 运行任务框架迁移合同。
 */
class RuntimeTaskMigrationContractTest {

    private final Path migrationRoot = Path.of("src/main/resources/db/migration");

    @Test
    void runtimeTaskMigrationExistsInEveryDialectWithChineseComments() throws IOException {
        for (String dialect : List.of("h2", "postgres", "oracle", "dm", "kingbase")) {
            Path migration = migrationRoot.resolve(dialect).resolve("V41__runtime_task_framework.sql");
            assertThat(migration).as(dialect + " runtime task migration").exists();
            String sql = Files.readString(migration);
            assertThat(sql)
                .contains("sys_task")
                .contains("uk_sys_task_tenant_task")
                .contains("idx_sys_task_status_ts")
                .contains("idx_sys_task_mode_ts")
                .contains("idx_sys_task_org_ts")
                .contains("任务运行框架");
            if (List.of("postgres", "oracle", "dm", "kingbase").contains(dialect)) {
                assertThat(sql)
                    .contains("COMMENT ON TABLE sys_task")
                    .contains("COMMENT ON COLUMN sys_task.task_id")
                    .contains("COMMENT ON COLUMN sys_task.status");
            }
        }
    }
}
