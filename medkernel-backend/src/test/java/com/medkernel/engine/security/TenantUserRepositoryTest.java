package com.medkernel.engine.security;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 租户用户目录仓储集成测试，验证真实 SQL 的租户隔离、搜索和分页行为。
 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:tenant-user-repo-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class TenantUserRepositoryTest {

    @Autowired
    TenantUserRepository repository;

    @AfterEach
    void wipe() {
        repository.deleteAll();
    }

    @Test
    void activeDirectorySearchIsTenantScopedAndPaginated() {
        repository.save(user("t-directory", "u-cardio", "心内科医生", "ACTIVE"));
        repository.save(user("t-directory", "u-nurse", "心内科护士", "ACTIVE"));
        repository.save(user("t-directory", "u-disabled", "心内科停用账号", "DISABLED"));
        repository.save(user("t-other", "u-other", "心内科外租户", "ACTIVE"));

        assertThat(repository.countActiveDirectory("t-directory", "心内科")).isEqualTo(2);
        assertThat(repository.pageActiveDirectory("t-directory", "心内科", 0, 1))
            .hasSize(1)
            .allMatch(TenantUser::active);
        assertThat(repository.pageActiveDirectory("t-directory", "u-nurse", 0, 10))
            .extracting(TenantUser::userId)
            .containsExactly("u-nurse");
        assertThat(repository.pageActiveDirectory("t-directory", null, 1, 10))
            .extracting(TenantUser::userId)
            .containsExactly("u-nurse");
    }

    private TenantUser user(String tenantId, String userId, String displayName, String status) {
        Instant now = Instant.now();
        return new TenantUser(
            null, tenantId, userId, displayName, status, 1L,
            now, "test", now, "test", "trace-test"
        );
    }
}
