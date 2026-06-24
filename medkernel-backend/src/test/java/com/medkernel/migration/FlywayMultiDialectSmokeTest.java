package com.medkernel.migration;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 五方言 Flyway 完整基线运行门禁。
 *
 * <p>验证 Flyway 能正确发现并应用由统一规范模型生成的单一 V1 基线：
 * <ul>
 *   <li>postgres + oracle 通过 Testcontainers 起真实容器跑（@Tag("docker")，CI 默认跑）
 *   <li>h2 通过内嵌进程跑（无 docker 依赖，CI 永远跑）
 *   <li>达梦、金仓在普通 CI 无公开镜像，由国产化环境矩阵执行运行烟测，静态合同测试负责结构门禁
 * </ul>
 */
class FlywayMultiDialectSmokeTest {

    @Test
    @Tag("docker")
    void postgresFlywayBaselineMigrates() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker 不可用，跳过 PostgreSQL 迁移烟测");
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
                DockerImageName.parse("postgres:15-alpine"))
                .withDatabaseName("medkernel")
                .withUsername("medkernel")
                .withPassword("medkernel")) {
            postgres.start();
            DataSource ds = buildHikari(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword(),
                "org.postgresql.Driver"
            );
            runFlyway(ds, "classpath:db/migration/postgres", "PostgreSQL");
            assertNullableConfigValue(ds, "PostgreSQL");
        }
    }

    @Test
    @Tag("docker")
    void oracleFlywayBaselineMigrates() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker 不可用，跳过 Oracle 迁移烟测");
        try (OracleContainer oracle = new OracleContainer(
                DockerImageName.parse("gvenzl/oracle-xe:21-slim-faststart"))
                .withDatabaseName("medkernel")
                .withUsername("medkernel")
                .withPassword("medkernel")) {
            oracle.start();
            DataSource ds = buildHikari(
                oracle.getJdbcUrl(),
                oracle.getUsername(),
                oracle.getPassword(),
                "oracle.jdbc.OracleDriver"
            );
            runFlyway(ds, "classpath:db/migration/oracle", "Oracle");
            assertNullableConfigValue(ds, "Oracle");

            JdbcTemplate jdbc = new JdbcTemplate(ds);
            Integer lowercaseHistory = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_tables WHERE table_name = 'flyway_schema_history'",
                Integer.class);
            Integer uppercaseHistory = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_tables WHERE table_name = 'FLYWAY_SCHEMA_HISTORY'",
                Integer.class);
            assertThat(lowercaseHistory).as("Oracle Flyway 小写 schema history 表").isEqualTo(1);
            assertThat(uppercaseHistory).as("Oracle 不应额外生成大写 schema history 表").isZero();
        }
    }

    @Test
    void h2FlywayBaselineMigrates() {
        DataSource ds = buildHikari(
                "jdbc:h2:mem:smoke;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "",
                "org.h2.Driver"
        );
        runFlyway(ds, "classpath:db/migration/h2", "H2");
        assertNullableConfigValue(ds, "H2");
    }

    private void runFlyway(DataSource ds, String location, String vendorName) {
        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .locations(location)
                .baselineOnMigrate(true)
                .load();

        var result = flyway.migrate();
        assertThat(result.success).as("%s migrate success", vendorName).isTrue();
        assertThat(result.migrationsExecuted).as("%s 单一 V1 基线执行", vendorName)
            .isEqualTo(1);

        MigrationInfo[] applied = flyway.info().applied();
        assertThat(applied).extracting(info -> info.getVersion().getVersion())
            .as("%s 只应用 V1", vendorName)
            .containsExactly("1");

        var repeated = flyway.migrate();
        assertThat(repeated.success).as("%s 重复执行 migrate 成功", vendorName).isTrue();
        assertThat(repeated.migrationsExecuted).as("%s 重复执行不产生新迁移", vendorName).isZero();
    }

    private void assertNullableConfigValue(DataSource ds, String vendorName) {
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        String suffix = vendorName.toLowerCase();
        int inserted = jdbc.update("""
            INSERT INTO mk_config_item (
                config_id, tenant_id, config_key, config_value, value_type, display_name,
                risk_level, owner, source, protected_flag, active_flag, version,
                created_by, updated_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            "cfg-null-" + suffix,
            "SYSTEM",
            "medkernel.test.null-config." + suffix,
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
        assertThat(inserted).as("%s 允许未配置值持久化为 NULL", vendorName).isEqualTo(1);

        int historyInserted = jdbc.update("""
            INSERT INTO mk_config_history (
                history_id, tenant_id, config_key, before_value, after_value, change_type,
                reason, version, created_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            "cfg-hist-null-" + suffix,
            "SYSTEM",
            "medkernel.test.null-config." + suffix,
            "configured",
            null,
            "ROLLBACK",
            "回滚到未配置状态",
            2,
            "test");
        assertThat(historyInserted).as("%s 允许回滚历史记录未配置值", vendorName).isEqualTo(1);
    }

    private DataSource buildHikari(String jdbcUrl, String username, String password, String driver) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername(username);
        cfg.setPassword(password);
        cfg.setDriverClassName(driver);
        cfg.setMaximumPoolSize(2);
        cfg.setMinimumIdle(0);
        cfg.setLeakDetectionThreshold(30_000);
        return new HikariDataSource(cfg);
    }
}
