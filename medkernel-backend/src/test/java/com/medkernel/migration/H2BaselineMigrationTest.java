package com.medkernel.migration;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 不依赖 Docker 的 H2 Flyway smoke。
 *
 * <p>与 {@link FlywayMultiDialectSmokeTest} 区分：后者用 Testcontainers 起 postgres/oracle，必须有 Docker；
 * 本测试只跑 H2，本地或无 Docker 的 CI 也能跑，确保 baseline 迁移在 H2 方言下健全。
 */
class H2BaselineMigrationTest {

    @Test
    void h2AppliesSingleAuthoritativeBaseline() {
        DataSource ds = new HikariDataSource(hikari());
        Flyway flyway = Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration/h2")
            .baselineOnMigrate(true)
            .load();

        var result = flyway.migrate();
        assertThat(result.success).as("H2 baseline migrations succeed").isTrue();
        assertThat(result.migrationsExecuted).as("全新项目只应用单一 V1 基线").isEqualTo(1);

        var applied = flyway.info().applied();
        assertThat(applied).extracting(info -> info.getVersion().getVersion())
            .containsExactly("1");

        var repeated = flyway.migrate();
        assertThat(repeated.success).as("H2 baseline repeated migrate succeeds").isTrue();
        assertThat(repeated.migrationsExecuted).as("H2 baseline repeated migrate is idempotent").isZero();

        JdbcTemplate jdbc = new JdbcTemplate(ds);
        Integer removedLegacyTables = jdbc.queryForObject("""
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME IN ('ROLE_PERMISSION', 'SYS_ROLE', 'SYS_PERMISSION', 'RULE_SIGNOFF')
            """, Integer.class);
        assertThat(removedLegacyTables).as("旧动态角色目录与签核门阀不进入全新基线").isZero();

        Integer removedEvalReviewColumns = jdbc.queryForObject("""
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_NAME = 'MK_LLM_EVAL_RUN'
              AND COLUMN_NAME IN ('REVIEWER', 'SIGNED_AT', 'REVIEW_COMMENT')
            """, Integer.class);
        assertThat(removedEvalReviewColumns).as("模型评测不保留专家签核字段").isZero();

        Integer legacyProviderCredentialReferenceColumns = jdbc.queryForObject("""
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_NAME = 'MK_LLM_PROVIDER'
              AND COLUMN_NAME = 'CREDENTIAL_REF'
            """, Integer.class);
        assertThat(legacyProviderCredentialReferenceColumns)
            .as("旧环境变量凭据引用列不保留")
            .isZero();

        Integer modelVersionBundleColumns = jdbc.queryForObject("""
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_NAME = 'MK_LLM_MODEL_VERSION_BUNDLE'
              AND COLUMN_NAME IN ('PROMPT_VERSION', 'TOOL_VERSION', 'MODEL_VERSION', 'PROMPT_HASH', 'TOOL_HASH', 'MODEL_HASH')
            """, Integer.class);
        assertThat(modelVersionBundleColumns).as("LLM-04 提示词、工具和模型版本组合列").isEqualTo(6);

        Integer providerLockVersionColumns = jdbc.queryForObject("""
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_NAME = 'MK_LLM_PROVIDER'
              AND COLUMN_NAME = 'LOCK_VERSION'
              AND IS_NULLABLE = 'NO'
            """, Integer.class);
        assertThat(providerLockVersionColumns).as("模型服务乐观锁列").isEqualTo(1);

        Integer providerCredentialColumns = jdbc.queryForObject("""
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_NAME = 'MK_LLM_PROVIDER_CREDENTIAL'
              AND COLUMN_NAME IN (
                'TENANT_ID',
                'PROVIDER_CODE',
                'CREDENTIAL_CIPHERTEXT',
                'CREDENTIAL_FINGERPRINT',
                'CREDENTIAL_LAST4',
                'LOCK_VERSION',
                'CREATED_AT',
                'CREATED_BY',
                'UPDATED_AT',
                'UPDATED_BY',
                'TRACE_ID'
              )
            """, Integer.class);
        assertThat(providerCredentialColumns).as("模型凭据租户加密库字段").isEqualTo(11);

        Integer sandboxRuntimeTables = jdbc.queryForObject("""
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'MK_SANDBOX_RUN'
            """, Integer.class);
        assertThat(sandboxRuntimeTables).as("沙盘不可变运行账本").isEqualTo(1);

        Integer legacySandboxBindingTables = jdbc.queryForObject("""
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'MK_SANDBOX_RUNTIME_BINDING'
            """, Integer.class);
        assertThat(legacySandboxBindingTables).as("沙盘不再维护独立运行绑定").isZero();

        Integer sandboxRuntimeReleaseColumns = jdbc.queryForObject("""
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_NAME = 'MK_SANDBOX_RUN'
              AND COLUMN_NAME IN (
                'RUNTIME_RELEASE_REF',
                'RUNTIME_REVISION_NO',
                'PLATFORM_BASELINE_RELEASE_ID',
                'MANIFEST_SHA256',
                'ASSET_MANIFEST_JSON',
                'RESOLUTION_SOURCE'
              )
            """, Integer.class);
        assertThat(sandboxRuntimeReleaseColumns).as("沙盘机构生效版本冻结字段").isEqualTo(6);

        Integer sandboxReplayTables = jdbc.queryForObject("""
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME IN ('MK_SANDBOX_REPLAY_CASE', 'MK_SANDBOX_REPLAY_ASSET_BINDING')
            """, Integer.class);
        assertThat(sandboxReplayTables).as("沙盘历史原样重放清单与精确资产绑定").isEqualTo(2);

        Integer sandboxReplayRuntimeColumns = jdbc.queryForObject("""
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_NAME = 'MK_SANDBOX_REPLAY_CASE'
              AND COLUMN_NAME IN ('SOURCE_RUNTIME_RELEASE_REF', 'SOURCE_RUNTIME_REVISION_NO')
            """, Integer.class);
        assertThat(sandboxReplayRuntimeColumns).as("历史重放来源机构生效版本字段").isEqualTo(2);

        Integer knowledgeInitializationTables = jdbc.queryForObject("""
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME IN (
                'MK_KNOWLEDGE_INITIALIZATION_BATCH',
                'MK_KNOWLEDGE_INITIALIZATION_ITEM'
            )
            """, Integer.class);
        assertThat(knowledgeInitializationTables).as("知识初始化发行批次").isEqualTo(2);

        Integer removedSourceApprovalTables = jdbc.queryForObject("""
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'MK_KNOWLEDGE_SOURCE_VERSION_APPROVAL'
            """, Integer.class);
        assertThat(removedSourceApprovalTables).as("来源版本不再设置独立批准门阀").isZero();

        String hash = "a".repeat(64);
        int activeBundleInserted = jdbc.update("""
            INSERT INTO mk_llm_model_version_bundle (
                tenant_id, capability_code, prompt_version, prompt_hash,
                tool_version, tool_hash, model_version, model_hash, status, active_scope_key
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            "tenant-version", "knowledge.extract", "prompt:v1", hash,
            "tool:v1", hash, "model:v1", hash, "ACTIVE", "tenant-version|knowledge.extract");
        assertThat(activeBundleInserted).as("首个已生效模型版本组合可写入").isEqualTo(1);

        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO mk_llm_model_version_bundle (
                tenant_id, capability_code, prompt_version, prompt_hash,
                tool_version, tool_hash, model_version, model_hash, status, active_scope_key
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            "tenant-version", "knowledge.extract", "prompt:v2", hash,
            "tool:v2", hash, "model:v2", hash, "ACTIVE", "tenant-version|knowledge.extract"))
            .as("同服务机构同能力不得并存两个已生效模型版本组合")
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO mk_llm_model_version_bundle (
                tenant_id, capability_code, prompt_version, prompt_hash,
                tool_version, tool_hash, model_version, model_hash, status, active_scope_key
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)
            """,
            "tenant-version-2", "knowledge.extract", "prompt:v1", hash,
            "tool:v1", hash, "model:v1", hash, "ACTIVE"))
            .as("已生效模型版本组合不得绕过作用域唯一键")
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO mk_llm_model_version_bundle (
                tenant_id, capability_code, prompt_version, prompt_hash,
                tool_version, tool_hash, model_version, model_hash, status, active_scope_key
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            "tenant-version", "knowledge.extract", "prompt:v3", hash,
            "tool:v3", hash, "model:v3", hash, "ACTIVE", "forged-scope-key"))
            .as("ACTIVE 作用域键必须由租户与能力确定，禁止伪造键绕过唯一约束")
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        Integer taskToolVersionColumn = jdbc.queryForObject("""
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_NAME = 'MODEL_CAPABILITY_TASK'
              AND COLUMN_NAME = 'TOOL_VERSION'
            """, Integer.class);
        assertThat(taskToolVersionColumn).as("模型任务记录 tool_version").isEqualTo(1);

        Integer diffAndExpiryTables = jdbc.queryForObject("""
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME IN ('MK_KNOWLEDGE_DIFF', 'MK_KNOWLEDGE_EXPIRY_TASK')
            """, Integer.class);
        assertThat(diffAndExpiryTables).as("AIK-STD-08 差异与过期治理表").isEqualTo(2);

        int nullableConfigInserted = jdbc.update("""
            INSERT INTO mk_config_item (
                config_id, tenant_id, config_key, config_value, value_type, display_name,
                risk_level, owner, source, protected_flag, active_flag, version,
                created_by, updated_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            "cfg-null-h2",
            "SYSTEM",
            "medkernel.test.null-config.h2",
            null,
            "STRING",
            "空配置跨库测试",
            "LOW",
            "测试",
            "DB",
            "N",
            "Y",
            1,
            "test",
            "test");
        assertThat(nullableConfigInserted).as("H2 允许未配置值持久化为 NULL").isEqualTo(1);

        int nullableHistoryInserted = jdbc.update("""
            INSERT INTO mk_config_history (
                history_id, tenant_id, config_key, before_value, after_value, change_type,
                reason, version, created_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            "cfg-hist-null-h2",
            "SYSTEM",
            "medkernel.test.null-config.h2",
            "configured",
            null,
            "ROLLBACK",
            "回滚到未配置状态",
            2,
            "test");
        assertThat(nullableHistoryInserted).as("H2 允许回滚历史记录未配置值").isEqualTo(1);

        String largeAssets = String.join(",",
            java.util.Collections.nCopies(180, "\"ACTION_CARD.RUNTIME.RELEASE.SNAPSHOT\""));
        String largePayload = """
            {"deliveryKind":"CLINICAL_RUNTIME_RELEASE","signatureAlgorithm":"SM3_WITH_SM2","runtimeMutation":false,"assets":[%s]}
            """.formatted(largeAssets);
        assertThat(largePayload.length()).as("回归样本必须超过旧 VARCHAR(4000) 上限").isGreaterThan(4_000);
        int evidenceInserted = jdbc.update("""
            INSERT INTO evidence_snapshot (
                evidence_id, tenant_id, trace_id, evidence_type, action, subject_type,
                subject_id, evidence_summary, payload_snapshot, payload_hash, file_uri,
                file_digest, signature_algorithm, signature_value, signer_public_key,
                created_by, updated_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            "evd-long-runtime-delivery",
            "tenant-version",
            "trace-long-runtime-delivery",
            "RUNTIME_RELEASE_OFFLINE_DELIVERY",
            "EXPORT",
            "clinical_runtime_release",
            "runtime-release-long",
            "机构生效版本离线交付完整 JSON 快照",
            largePayload,
            "sm3:" + "a".repeat(64),
            "/api/v1/compliance/evidence/snapshots/evd-long-runtime-delivery/file",
            "sm3:" + "b".repeat(64),
            "SM3_WITH_SM2",
            "signature",
            "public-key",
            "engine-operator",
            "engine-operator");
        assertThat(evidenceInserted).as("H2 基线允许完整离线交付 JSON 存证").isEqualTo(1);
    }

    private HikariConfig hikari() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:h2:mem:flyway-h2-smoke-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        cfg.setUsername("sa");
        cfg.setPassword("");
        cfg.setDriverClassName("org.h2.Driver");
        cfg.setMaximumPoolSize(2);
        return cfg;
    }
}
