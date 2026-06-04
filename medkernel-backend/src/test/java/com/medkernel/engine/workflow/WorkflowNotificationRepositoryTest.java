package com.medkernel.engine.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

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
 * 统一通知仓储测试：默认查询只返回当前用户通知与组织范围通知，避免同租户个人通知串读。
 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:workflow-notification-repo-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class WorkflowNotificationRepositoryTest {

    @Autowired WorkflowNotificationRepository repository;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void wipe() {
        repository.deleteAll();
        jdbc.update("DELETE FROM org_closure");
        jdbc.update("DELETE FROM org_unit");
    }

    @Test
    void visibleRecipientScopeIncludesCurrentUserAndOrganizationRowsOnly() {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        repository.save(sample(
            "notify-own",
            "todo-own",
            "todo:todo-own:created",
            WorkflowNotificationLevel.HIGH,
            "doctor-1",
            now.plusSeconds(30)));
        repository.save(sample(
            "notify-org",
            "event-org",
            "clinical-event:event-org",
            WorkflowNotificationLevel.INFO,
            null,
            now.plusSeconds(20)));
        repository.save(sample(
            "notify-other",
            "todo-other",
            "todo:todo-other:created",
            WorkflowNotificationLevel.CRITICAL,
            "doctor-2",
            now.plusSeconds(10)));

        long total = repository.countByVisibleRecipientScope(
            "tenant-A",
            "UNREAD",
            null,
            null,
            "doctor-1",
            null);
        List<WorkflowNotification> page = repository.pageByVisibleRecipientScope(
            "tenant-A",
            "UNREAD",
            null,
            null,
            "doctor-1",
            null,
            0,
            10);

        assertThat(total).isEqualTo(2);
        assertThat(page).extracting(WorkflowNotification::notificationId)
            .containsExactly("notify-own", "notify-org");
    }

    @Test
    void visibleRecipientScopeUsesOrgClosureForOrganizationRows() {
        seedOrgTree();
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        repository.save(sample(
            "notify-own",
            "todo-own",
            "todo:todo-own:created",
            WorkflowNotificationLevel.HIGH,
            "doctor-1",
            "dept-b",
            now.plusSeconds(400)));
        repository.save(sample(
            "notify-dept",
            "event-dept",
            "clinical-event:event-dept",
            WorkflowNotificationLevel.INFO,
            null,
            "dept-a",
            now.plusSeconds(300)));
        repository.save(sample(
            "notify-specialty",
            "event-specialty",
            "clinical-event:event-specialty",
            WorkflowNotificationLevel.INFO,
            null,
            "spec-a1",
            now.plusSeconds(200)));
        repository.save(sample(
            "notify-tenant",
            "event-tenant",
            "clinical-event:event-tenant",
            WorkflowNotificationLevel.INFO,
            null,
            null,
            now.plusSeconds(100)));
        repository.save(sample(
            "notify-sibling",
            "event-sibling",
            "clinical-event:event-sibling",
            WorkflowNotificationLevel.INFO,
            null,
            "dept-b",
            now.plusSeconds(500)));

        long total = repository.countByVisibleRecipientScope(
            "tenant-A",
            "UNREAD",
            null,
            null,
            "doctor-1",
            "dept-a");
        List<WorkflowNotification> page = repository.pageByVisibleRecipientScope(
            "tenant-A",
            "UNREAD",
            null,
            null,
            "doctor-1",
            "dept-a",
            0,
            10);

        assertThat(total).isEqualTo(4);
        assertThat(page).extracting(WorkflowNotification::notificationId)
            .containsExactly("notify-own", "notify-dept", "notify-specialty", "notify-tenant");
    }

    @Test
    void explicitRecipientFilterStillNarrowsToRequestedRecipient() {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        repository.save(sample(
            "notify-own",
            "todo-own",
            "todo:todo-own:created",
            WorkflowNotificationLevel.HIGH,
            "doctor-1",
            now.plusSeconds(30)));
        repository.save(sample(
            "notify-other",
            "todo-other",
            "todo:todo-other:created",
            WorkflowNotificationLevel.CRITICAL,
            "doctor-2",
            now.plusSeconds(10)));

        List<WorkflowNotification> page = repository.pageByVisibleRecipientScope(
            "tenant-A",
            "UNREAD",
            null,
            "doctor-2",
            "doctor-1",
            null,
            0,
            10);

        assertThat(page).extracting(WorkflowNotification::notificationId)
            .containsExactly("notify-other");
    }

    private WorkflowNotification sample(
            String notificationId,
            String sourceId,
            String dedupeKey,
            WorkflowNotificationLevel level,
            String recipientId,
            Instant createdAt) {
        return sample(notificationId, sourceId, dedupeKey, level, recipientId, null, createdAt);
    }

    private WorkflowNotification sample(
            String notificationId,
            String sourceId,
            String dedupeKey,
            WorkflowNotificationLevel level,
            String recipientId,
            String orgUnitId,
            Instant createdAt) {
        return new WorkflowNotification(
            null,
            notificationId,
            "tenant-A",
            orgUnitId,
            WorkflowNotificationSourceType.WORKFLOW_TODO,
            sourceId,
            dedupeKey,
            "待办待处理",
            "真实协同通知",
            level,
            WorkflowNotificationStatus.UNREAD,
            recipientId,
            "DOCTOR",
            "patient-1",
            "enc-1",
            "/workflow/todos",
            null,
            null,
            "trace-workflow",
            createdAt,
            "tester",
            createdAt,
            "tester");
    }

    private void seedOrgTree() {
        jdbc.update("""
            INSERT INTO org_unit (id, parent_id, tenant_id, org_path, level_code, code, name, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
            """, "tenant-root", null, "tenant-A", "/TENANT-A", "TENANT", "TENANT-A", "租户");
        jdbc.update("""
            INSERT INTO org_unit (id, parent_id, tenant_id, org_path, level_code, code, name, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
            """, "dept-a", "tenant-root", "tenant-A", "/TENANT-A/DEPT-A", "DEPARTMENT", "DEPT-A", "A 科室");
        jdbc.update("""
            INSERT INTO org_unit (id, parent_id, tenant_id, org_path, level_code, code, name, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
            """, "spec-a1", "dept-a", "tenant-A", "/TENANT-A/DEPT-A/SPEC-A1", "SPECIALTY", "SPEC-A1", "A1 专病");
        jdbc.update("""
            INSERT INTO org_unit (id, parent_id, tenant_id, org_path, level_code, code, name, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
            """, "dept-b", "tenant-root", "tenant-A", "/TENANT-A/DEPT-B", "DEPARTMENT", "DEPT-B", "B 科室");
        jdbc.update("""
            INSERT INTO org_closure (tenant_id, ancestor_id, descendant_id, depth)
            VALUES
              ('tenant-A', 'tenant-root', 'tenant-root', 0),
              ('tenant-A', 'tenant-root', 'dept-a', 1),
              ('tenant-A', 'tenant-root', 'spec-a1', 2),
              ('tenant-A', 'tenant-root', 'dept-b', 1),
              ('tenant-A', 'dept-a', 'dept-a', 0),
              ('tenant-A', 'dept-a', 'spec-a1', 1),
              ('tenant-A', 'spec-a1', 'spec-a1', 0),
              ('tenant-A', 'dept-b', 'dept-b', 0)
            """);
    }
}
