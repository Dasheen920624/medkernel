package com.medkernel.engine.embed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.test.context.TestPropertySource;

/**
 * 嵌入启动令牌仓储测试。
 *
 * <p>覆盖一次性消费 SQL 的真实参数绑定，避免兑换接口到现场才暴露 500。
 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:embed-token-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class EmbedLaunchTokenRepositoryTest {

    @Autowired EmbedLaunchTokenRepository repository;

    @AfterEach
    void wipe() {
        repository.deleteAll();
    }

    @Test
    void consumeUnusedTokenMarksTokenUsedAndRecordsConsumerFields() {
        Instant createdAt = Instant.parse("2026-06-11T01:00:00Z");
        Instant consumedAt = Instant.parse("2026-06-11T01:01:00Z");
        repository.save(new EmbedLaunchToken(
            null,
            "tkn-sql-bind",
            "tenant-A",
            "doctor-1",
            "clinical-decision-user",
            "P100",
            "E200",
            "patient-view",
            EmbedLaunchTokenStatus.UNUSED.name(),
            createdAt.plusSeconds(600),
            createdAt,
            "issuer-1",
            createdAt,
            "issuer-1",
            "trace-embed-repo",
            EmbedIntegrationMode.IFRAME.name(),
            "patient-view",
            "hook-instance-001",
            null
        ));

        int rows = repository.consumeUnusedToken(
            "tkn-sql-bind",
            "tenant-A",
            consumedAt,
            consumedAt,
            "doctor-1");

        assertThat(rows).isEqualTo(1);
        assertThat(repository.findByToken("tkn-sql-bind"))
            .hasValueSatisfying(token -> {
                assertThat(token.status()).isEqualTo(EmbedLaunchTokenStatus.USED.name());
                assertThat(token.consumedAt()).isEqualTo(consumedAt);
                assertThat(token.updatedAt()).isEqualTo(consumedAt);
                assertThat(token.updatedBy()).isEqualTo("doctor-1");
            });
    }
}
