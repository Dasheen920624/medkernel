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
 * 保存视图仓储测试：覆盖空库首次插入路径。
 *
 * <p>回归 2026-06-10 首次部署缺陷族：业务指派主键实体未声明新建语义时，
 * Spring Data JDBC 把首次保存误判为 UPDATE，空库保存视图即失败（同 UserPreference）。
 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:saved-view-repo-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true"
})
class SavedViewRepositoryTest {

    @Autowired
    private SavedViewRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM mk_experience_saved_view");
    }

    @Test
    void save_insertsBrandNewViewIntoEmptyTable() {
        Instant now = Instant.parse("2026-06-10T00:00:00Z");
        SavedView fresh = SavedView.create(
            "sv-fresh-001", "t-1", "medkernel",
            new SavedViewRequest("mpi", "默认筛选", "{\"filters\":{}}", false), now);

        SavedView saved = repository.save(fresh);

        assertThat(saved.savedViewId()).isEqualTo("sv-fresh-001");
        assertThat(repository.findByTenantIdAndUserIdAndPageKeyAndViewName(
                "t-1", "medkernel", "mpi", "默认筛选"))
            .isPresent();
    }

    @Test
    void save_updatesExistingViewLoadedFromDatabase() {
        Instant now = Instant.parse("2026-06-10T00:00:00Z");
        repository.save(SavedView.create(
            "sv-update-001", "t-1", "medkernel",
            new SavedViewRequest("mpi", "默认筛选", "{\"filters\":{}}", false), now));

        SavedView loaded = repository.findByTenantIdAndUserIdAndPageKeyAndViewName(
                "t-1", "medkernel", "mpi", "默认筛选").orElseThrow();
        repository.save(loaded.updatedBy(
            new SavedViewRequest("mpi", "默认筛选", "{\"filters\":{\"status\":\"ACTIVE\"}}", true),
            "medkernel", now));

        assertThat(repository.findByTenantIdAndUserIdAndPageKeyAndViewName(
                "t-1", "medkernel", "mpi", "默认筛选"))
            .get()
            .extracting(SavedView::version, SavedView::isDefaultView)
            .containsExactly(2L, true);
    }
}
