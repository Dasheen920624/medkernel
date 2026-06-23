package com.medkernel.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.medkernel.engine.workflow.WorkflowNotificationSettingsRequest;
import com.medkernel.engine.workflow.WorkflowNotificationSettingsResponse;
import com.medkernel.engine.workflow.WorkflowNotificationSettingsService;
import com.medkernel.engine.workflow.WorkflowNotificationSettingsSource;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 真实空 PostgreSQL 首部署冒烟：防「首次部署同族缺陷」再潜伏的系统性防护网。
 *
 * <p>背景：2026-06-10 知识生产服务器清库上线暴露一族 H2 结构上测不出的缺陷——
 * 其中最隐蔽的是只读事务播种 SQLSTATE 25006（H2 不强制只读事务，永远测不出）。
 * 本测试在**真实空 PostgreSQL（Testcontainers）** 上以 Flyway 迁移建库、启动全量 Spring 上下文
 * （连带所有 ApplicationRunner / 种子器的空库首动也一并验证），再实跑首部署关键路径：
 *
 * <ul>
 *   <li><b>只读事务播种（25006 网）</b>：以 {@code @Transactional(readOnly = true)} 的
 *       「每页必调」读取触发配置中心按需播种；若播种 INSERT 退化为加入外层只读事务，
 *       真实 PG 立即 25006，本测试变红。</li>
 *   <li><b>指派主键空表首写（INSERT 误判 UPDATE 网）</b>：业务指派 String 主键实体首次保存，
 *       若未声明新建语义会被 Spring Data JDBC 误判为 UPDATE 而静默丢失，真实空表暴露。</li>
 * </ul>
 *
 * <p>{@code @Tag("docker")} + {@code disabledWithoutDocker}：CI 有 Docker 必跑，本机无 Docker 跳过。
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("docker")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FirstDeployEmptyPostgresSmokeTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:15-alpine"))
        .withDatabaseName("medkernel")
        .withUsername("medkernel")
        .withPassword("medkernel");

    @DynamicPropertySource
    static void wireEmptyPostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/postgres");
        registry.add("medkernel.runtime.database-dialect", () -> "postgres");
        registry.add("medkernel.runtime.migration-location", () -> "classpath:db/migration/postgres");
    }

    @Autowired
    private WorkflowNotificationSettingsService notificationSettingsService;

    @Test
    void readOnlyPageLoadSeedsTenantDefaultsWithoutReadOnlyTransactionCrash() {
        // 真实 PG 只读事务下按需播种：REQUIRES_NEW 退化为 REQUIRED 时此处必 25006。
        WorkflowNotificationSettingsResponse response =
            notificationSettingsService.getSettingsForUser("t-smoke", "u-smoke");

        assertThat(response).isNotNull();
        assertThat(response.source()).isEqualTo(WorkflowNotificationSettingsSource.SYSTEM_DEFAULT);
        assertThat(response.systemVersion()).as("首读应已播种租户默认策略").isPositive();
    }

    @Test
    void repeatedReadOnlyPageLoadStaysIdempotent() {
        notificationSettingsService.getSettingsForUser("t-smoke-2", "u-smoke");
        // 再次读取：配置已存在，不再播种，仍返回系统默认，不得抛错。
        assertThatCode(() -> notificationSettingsService.getSettingsForUser("t-smoke-2", "u-smoke"))
            .doesNotThrowAnyException();
        assertThat(notificationSettingsService.getSettingsForUser("t-smoke-2", "u-smoke").source())
            .isEqualTo(WorkflowNotificationSettingsSource.SYSTEM_DEFAULT);
    }

    @Test
    void assignedKeyPreferenceFirstSaveSucceedsOnEmptyTable() throws Exception {
        RequestContext.Snapshot context = new RequestContext.Snapshot(
            "trace-smoke", OrgScope.tenant("t-smoke"), "u-smoke");

        WorkflowNotificationSettingsResponse saved = RequestContext.callWith(context, () ->
            notificationSettingsService.saveSettings(new WorkflowNotificationSettingsRequest(
                true, false, false, false, false, false, false, null, null, null, null)));

        assertThat(saved.source()).isEqualTo(WorkflowNotificationSettingsSource.PERSONAL);
        assertThat(saved.version())
            .as("空表首次保存应为真实 INSERT（version=1），而非被误判为 UPDATE 静默丢失")
            .isEqualTo(1L);
    }
}
