package com.medkernel.migration;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 五方言 Flyway 完整基线运行门禁。
 *
 * <p>验证 Flyway 能正确发现并应用当前全部权威迁移：
 * <ul>
 *   <li>postgres + oracle 通过 Testcontainers 起真实容器跑（@Tag("docker")，CI 默认跑）
 *   <li>h2 通过内嵌进程跑（无 docker 依赖，CI 永远跑）
 *   <li>达梦、金仓在普通 CI 无公开镜像，由国产化环境矩阵执行运行烟测，静态合同测试负责结构门禁
 * </ul>
 */
@Testcontainers(disabledWithoutDocker = true)
class FlywayMultiDialectSmokeTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("medkernel")
            .withUsername("medkernel")
            .withPassword("medkernel");

    @Container
    static OracleContainer oracle = new OracleContainer(
            DockerImageName.parse("gvenzl/oracle-xe:21-slim-faststart"))
            .withDatabaseName("medkernel")
            .withUsername("medkernel")
            .withPassword("medkernel");

    @Test
    @Tag("docker")
    void postgresFlywayBaselineMigrates() {
        runFlyway(buildHikari(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword(),
                "org.postgresql.Driver"
        ), "classpath:db/migration/postgres", "PostgreSQL");
    }

    @Test
    @Tag("docker")
    void oracleFlywayBaselineMigrates() {
        runFlyway(buildHikari(
                oracle.getJdbcUrl(),
                oracle.getUsername(),
                oracle.getPassword(),
                "oracle.jdbc.OracleDriver"
        ), "classpath:db/migration/oracle", "Oracle");
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
    }

    private void runFlyway(DataSource ds, String location, String vendorName) {
        Flyway flyway = Flyway.configure()
                .dataSource(ds)
                .locations(location)
                .baselineOnMigrate(true)
                .load();

        var result = flyway.migrate();
        assertThat(result.success).as("%s migrate success", vendorName).isTrue();
        assertThat(result.migrationsExecuted).as("%s 二十八个基线迁移执行", vendorName).isEqualTo(28);

        MigrationInfo[] applied = flyway.info().applied();
        assertThat(applied).extracting(info -> info.getVersion().getVersion())
            .as("%s 完整迁移版本序列", vendorName)
            .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13",
                "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27",
                "28");
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
