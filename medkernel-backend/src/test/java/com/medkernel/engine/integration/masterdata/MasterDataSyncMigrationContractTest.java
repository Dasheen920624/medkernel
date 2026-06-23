package com.medkernel.engine.integration.masterdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 院内主数据同步台账五方言迁移合同。
 */
class MasterDataSyncMigrationContractTest {

    @Test
    void allFiveDialectsPersistBatchCursorAndRecordVersionWithoutRawPayload() throws Exception {
        for (String dialect : List.of("h2", "postgres", "oracle", "dm", "kingbase")) {
            Path migration = Path.of(
                "src/main/resources/db/migration",
                dialect,
                "V1__baseline.sql");
            assertThat(migration).as(dialect + " 主数据同步迁移").exists();

            String ddl = Files.readString(migration);
            assertThat(ddl)
                .contains("mk_integration_master_data_sync_batch")
                .contains("mk_integration_master_data_sync_record")
                .contains("uk_mk_integration_master_data_sync_batch")
                .contains("uk_mk_integration_master_data_sync_record")
                .contains("idx_mk_integration_master_data_sync_batch_latest")
                .contains("idx_mk_integration_master_data_sync_record_status")
                .contains("ck_mk_integration_master_data_sync_batch_status")
                .contains("ck_mk_integration_master_data_sync_record_status")
                .contains("payload_hash")
                .contains("source_version")
                .contains("cursor_value")
                .contains("主数据同步批次")
                .contains("来源主数据记录版本映射")
                .doesNotContain("payload_json")
                .doesNotContain("raw_payload");
        }
    }
}
