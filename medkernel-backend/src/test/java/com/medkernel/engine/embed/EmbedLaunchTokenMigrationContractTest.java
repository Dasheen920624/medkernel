package com.medkernel.engine.embed;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class EmbedLaunchTokenMigrationContractTest {

    @Test
    void allFiveDialectsPersistTokenBoundParentOriginWithChineseComment() throws Exception {
        for (String dialect : List.of("h2", "postgres", "kingbase", "oracle", "dm")) {
            Path migration = Path.of("src/main/resources/db/migration", dialect,
                "V119__embed_external_host_contract.sql");
            assertThat(migration).as(dialect + " 嵌入外部宿主迁移").exists();
            String ddl = Files.readString(migration);
            assertThat(ddl).contains("parent_origin");
            assertThat(ddl).contains("父系统Origin");
        }
    }
}
