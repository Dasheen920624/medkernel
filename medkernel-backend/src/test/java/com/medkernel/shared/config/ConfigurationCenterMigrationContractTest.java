package com.medkernel.shared.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证全新 V1 基线中的配置中心五方言合同。
 */
class ConfigurationCenterMigrationContractTest {

    private final Path migrationRoot = Path.of("src/main/resources/db/migration");

    @Test
    void configCenterMigrationExistsInEveryDialectWithChineseComments() throws IOException {
        for (String dialect : List.of("h2", "postgres", "oracle", "dm", "kingbase")) {
            Path migration = migrationRoot.resolve(dialect).resolve("V1__baseline.sql");
            assertThat(migration).as(dialect + " config migration").exists();
            String sql = Files.readString(migration);
            assertThat(sql)
                .contains("mk_config_item")
                .contains("mk_config_history")
                .contains("uk_config_item_tenant_key")
                .contains("idx_config_item_tenant_key")
                .contains("idx_config_history_tenant_key")
                .contains("配置中心");
            assertThat(sql)
                .contains("COMMENT ON TABLE mk_config_item")
                .contains("COMMENT ON TABLE mk_config_history");
        }
    }
}
