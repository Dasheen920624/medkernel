package com.medkernel.migration;

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

    private static final int LATEST_MIGRATION_VERSION = 104;

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
        assertThat(assignmentCount).as("初始化用户角色绑定数量").isEqualTo(14);

        List<String> seededUsers = jdbc.queryForList("""
            SELECT user_id FROM user_role_assignment
            WHERE tenant_id = 't-1'
            ORDER BY user_id
            """, String.class);
        assertThat(seededUsers)
            .contains("admin-1", "doctor-1", "implementation-1", "it-ops-1", "qa-manager-1",
                "system-superadmin-1");

        Integer tenantUserCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM tenant_user WHERE tenant_id = 't-1'",
            Integer.class);
        assertThat(tenantUserCount).as("角色种子已收敛为统一租户用户目录").isEqualTo(14);

        Integer roleCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys_role WHERE tenant_id = 'SYSTEM' AND built_in_flag = 'Y'",
            Integer.class);
        assertThat(roleCount).as("系统内置 14 角色目录").isEqualTo(14);

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

        Integer modelCapabilityCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM model_capability_definition WHERE enabled_flag = 'Y'",
            Integer.class);
        assertThat(modelCapabilityCount).as("模型能力关系库目录种子").isEqualTo(8);
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
