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

    @AfterEach
    void wipe() {
        repository.deleteAll();
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
            "doctor-1");
        List<WorkflowNotification> page = repository.pageByVisibleRecipientScope(
            "tenant-A",
            "UNREAD",
            null,
            null,
            "doctor-1",
            0,
            10);

        assertThat(total).isEqualTo(2);
        assertThat(page).extracting(WorkflowNotification::notificationId)
            .containsExactly("notify-own", "notify-org");
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
        return new WorkflowNotification(
            null,
            notificationId,
            "tenant-A",
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
}
