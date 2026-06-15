package com.medkernel.migration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 不依赖 Docker 的 H2 Flyway smoke。
 *
 * <p>与 {@link FlywayMultiDialectSmokeTest} 区分：后者用 Testcontainers 起 postgres/oracle，必须有 Docker；
 * 本测试只跑 H2，本地或无 Docker 的 CI 也能跑，确保 baseline 迁移在 H2 方言下健全。
 */
class H2BaselineMigrationTest {

    private static final int LATEST_MIGRATION_VERSION = 132;

    @Test
    void h2AppliesCompleteAuthoritativeBaselineMigrations() {
        DataSource ds = new HikariDataSource(hikari());
        Flyway flyway = Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration/h2")
            .baselineOnMigrate(true)
            .load();

        var result = flyway.migrate();
        assertThat(result.success).as("H2 baseline migrations succeed").isTrue();
        assertThat(result.migrationsExecuted).as("当前全部基线迁移应用").isEqualTo(LATEST_MIGRATION_VERSION);

        var applied = flyway.info().applied();
        assertThat(applied).extracting(info -> info.getVersion().getVersion())
            .containsExactlyElementsOf(IntStream.rangeClosed(1, LATEST_MIGRATION_VERSION)
                .mapToObj(String::valueOf)
                .toList());

        var repeated = flyway.migrate();
        assertThat(repeated.success).as("H2 baseline repeated migrate succeeds").isTrue();
        assertThat(repeated.migrationsExecuted).as("H2 baseline repeated migrate is idempotent").isZero();

        JdbcTemplate jdbc = new JdbcTemplate(ds);
        Integer assignmentCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_role_assignment WHERE tenant_id = 't-1' AND active_flag = 'Y'",
            Integer.class);
        assertThat(assignmentCount).as("平台空间只初始化两类平台职责").isEqualTo(2);

        List<String> seededUsers = jdbc.queryForList("""
            SELECT user_id FROM user_role_assignment
            WHERE tenant_id = 't-1'
            ORDER BY user_id
            """, String.class);
        assertThat(seededUsers).containsExactly(
            "platform-governance-admin-1",
            "platform-knowledge-governor-1");

        Integer tenantUserCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM tenant_user WHERE tenant_id = 't-1'",
            Integer.class);
        assertThat(tenantUserCount).as("平台用户目录不混入客户机构职责").isEqualTo(2);

        Integer roleCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys_role WHERE tenant_id = 'SYSTEM' AND built_in_flag = 'Y'",
            Integer.class);
        assertThat(roleCount).as("系统内置 15 个全新职责角色").isEqualTo(15);

        List<String> dimensions = jdbc.queryForList("""
            SELECT DISTINCT dimension FROM sys_permission
            ORDER BY dimension
            """, String.class);
        assertThat(dimensions).as("五维权限点目录")
            .containsExactly("ACTION", "ASSET", "DATA", "ENVIRONMENT", "MENU");

        Integer readinessPermissionCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys_permission WHERE permission_code = 'workbench:readiness:view'",
            Integer.class);
        assertThat(readinessPermissionCount).as("WORKBENCH-02 动作权限目录").isEqualTo(1);

        Integer governancePermissionCount = jdbc.queryForObject("""
            SELECT COUNT(*) FROM sys_permission
            WHERE permission_code IN ('platform.publish', 'tenant.override')
            """, Integer.class);
        assertThat(governancePermissionCount).as("平台/租户发布治理权限目录").isEqualTo(2);

        Integer modelCapabilityCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM model_capability_definition WHERE enabled_flag = 'Y'",
            Integer.class);
        assertThat(modelCapabilityCount).as("模型能力关系库目录种子").isEqualTo(8);

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
    }

    @Test
    void knowledgeReviewMigrationBackfillsExistingActiveVersionDeadline() {
        DataSource ds = new HikariDataSource(hikari());
        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration/h2")
            .target("108")
            .load()
            .migrate();

        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.update("""
            INSERT INTO knowledge_identity (
                tenant_id, identity_code, domain, subject, status
            ) VALUES (?, ?, ?, ?, ?)
            """, "t-1", "plat:drug:review-backfill", "DRUG", "复审迁移测试", "ACTIVE");
        Long identityId = jdbc.queryForObject(
            "SELECT id FROM knowledge_identity WHERE identity_code = 'plat:drug:review-backfill'",
            Long.class);
        jdbc.update("""
            INSERT INTO knowledge_asset_version (
                tenant_id, identity_id, version_no, content_hash, status, risk_level,
                authority_level, grade_quality, organization_scope, applicable_scope,
                active_scope_key, reviewed_by, reviewed_at, activated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            "t-1", identityId, "2026.1", "a".repeat(64), "ACTIVE", "LOW",
            "B_GUIDELINE", "HIGH", "tenant:t-1", "ALL",
            identityId + "|tenant:t-1|ALL", "reviewer",
            LocalDateTime.of(2026, 6, 1, 0, 0),
            LocalDateTime.of(2026, 6, 1, 0, 0));

        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration/h2")
            .load()
            .migrate();

        LocalDateTime nextReviewAt = jdbc.queryForObject("""
            SELECT next_review_at FROM knowledge_asset_version
            WHERE identity_id = ?
            """, LocalDateTime.class, identityId);
        assertThat(nextReviewAt)
            .as("存量 ACTIVE 权威版本必须在 V109 后进入复审计划")
            .isEqualTo(LocalDateTime.of(2027, 6, 1, 0, 0));
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
