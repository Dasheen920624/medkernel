package com.medkernel.engine.experience;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * 用户体验偏好仓储测试：覆盖空库首次插入路径。
 *
 * <p>回归 2026-06-10 真实 PostgreSQL 首次部署缺陷：{@link UserPreference} 主键为业务指派的
 * 非空字符串，Spring Data JDBC 默认据此判定为已存在实体而发出 UPDATE，空库无行时
 * 抛 {@code IncorrectUpdateSemanticsDataAccessException}，导致主题 / 通知偏好首次保存失败。
 * 既有服务测试 mock 仓库，掩盖了真实新建路径，故补此真实仓库集成测试。
 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:user-pref-repo-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true"
})
class UserPreferenceRepositoryTest {

    @Autowired
    private UserPreferenceRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM mk_experience_user_pref");
    }

    @Test
    void save_insertsBrandNewPreferenceIntoEmptyTable() {
        Instant now = Instant.parse("2026-06-10T00:00:00Z");
        UserPreference fresh = UserPreference.create(
            "up-fresh-001", "t-1", "medkernel", "theme.mode", "default", now);

        UserPreference saved = repository.save(fresh);

        assertThat(saved.userPrefId()).isEqualTo("up-fresh-001");
        assertThat(repository.findByTenantIdAndUserIdAndPrefKeyAndStatus(
                "t-1", "medkernel", "theme.mode", "ACTIVE"))
            .isPresent()
            .get()
            .extracting(UserPreference::prefValue)
            .isEqualTo("default");
    }

    @Test
    void save_updatesExistingPreferenceLoadedFromDatabase() {
        Instant now = Instant.parse("2026-06-10T00:00:00Z");
        repository.save(UserPreference.create(
            "up-update-001", "t-1", "medkernel", "theme.mode", "default", now));

        UserPreference loaded = repository.findByTenantIdAndUserIdAndPrefKeyAndStatus(
                "t-1", "medkernel", "theme.mode", "ACTIVE").orElseThrow();
        repository.save(loaded.updateValue("dark", "medkernel", now));

        assertThat(repository.findByTenantIdAndUserIdAndPrefKeyAndStatus(
                "t-1", "medkernel", "theme.mode", "ACTIVE"))
            .get()
            .extracting(UserPreference::prefValue, UserPreference::version)
            .containsExactly("dark", 2L);
    }
}
