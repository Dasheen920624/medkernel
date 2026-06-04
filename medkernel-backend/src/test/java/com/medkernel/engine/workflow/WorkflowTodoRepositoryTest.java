package com.medkernel.engine.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import com.medkernel.engine.followup.FollowupEvent;
import com.medkernel.engine.followup.FollowupEventRepository;
import com.medkernel.engine.followup.FollowupEventType;
import com.medkernel.engine.followup.FollowupPlan;
import com.medkernel.engine.followup.FollowupPlanRepository;
import com.medkernel.engine.followup.FollowupPlanStatus;
import com.medkernel.engine.followup.FollowupTask;
import com.medkernel.engine.followup.FollowupTaskRepository;
import com.medkernel.engine.followup.FollowupTaskStatus;
import com.medkernel.engine.followup.FollowupTaskType;
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
 * 统一待办仓储测试：安全复核必须在分页层置顶，避免前端分页后漏看高风险任务。
 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:workflow-todo-repo-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class WorkflowTodoRepositoryTest {

    @Autowired WorkflowTodoRepository repository;
    @Autowired FollowupPlanRepository followupPlans;
    @Autowired FollowupTaskRepository followupTasks;
    @Autowired FollowupEventRepository followupEvents;

    @AfterEach
    void wipe() {
        repository.deleteAll();
        followupEvents.deleteAll();
        followupTasks.deleteAll();
        followupPlans.deleteAll();
    }

    @Test
    void safetyReviewTodosAreOrderedBeforeLowerRiskFollowupRows() {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        repository.save(sample(
            "todo-followup-early",
            WorkflowTodoSourceType.FOLLOWUP_TASK,
            "followup-1",
            WorkflowPriority.HIGH,
            now.plusSeconds(1800)));
        repository.save(sample(
            "todo-safety-later",
            WorkflowTodoSourceType.SAFETY_REVIEW,
            "withdrawal:patient-1",
            WorkflowPriority.CRITICAL,
            now.plusSeconds(7200)));

        List<WorkflowTodo> page = repository.pageByFilter(
            "tenant-A",
            "PENDING",
            null,
            null,
            null,
            null,
            0,
            10);

        assertThat(page).extracting(WorkflowTodo::todoId)
            .containsExactly("todo-safety-later", "todo-followup-early");
    }

    @Test
    void patientFilterNarrowsTodosAtRepositoryBoundary() {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        repository.save(sample(
            "todo-patient-1",
            WorkflowTodoSourceType.FOLLOWUP_TASK,
            "followup-1",
            WorkflowPriority.HIGH,
            "patient-1",
            now.plusSeconds(1800)));
        repository.save(sample(
            "todo-patient-2",
            WorkflowTodoSourceType.SAFETY_REVIEW,
            "withdrawal:patient-2",
            WorkflowPriority.CRITICAL,
            "patient-2",
            now.plusSeconds(900)));

        List<WorkflowTodo> page = repository.pageByFilter(
            "tenant-A",
            "PENDING",
            null,
            null,
            null,
            "patient-1",
            0,
            10);

        assertThat(page).extracting(WorkflowTodo::todoId)
            .containsExactly("todo-patient-1");
    }

    @Test
    void followupRowsProjectToWorkflowTodoAndNotificationSources() {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        followupPlans.save(new FollowupPlan(
            null,
            "plan-workflow-1",
            "tenant-A",
            "patient-1",
            "enc-1",
            "pathway-1",
            "D-RULE",
            "HIGH",
            FollowupPlanStatus.ACTIVE,
            now,
            "tester",
            now,
            "tester",
            "trace-plan"));
        followupTasks.save(new FollowupTask(
            null,
            "task-return-1",
            "tenant-A",
            "plan-workflow-1",
            FollowupTaskType.RETURN_VISIT,
            now.plusSeconds(3600),
            FollowupTaskStatus.ABNORMAL_RETURN,
            "doctor-1",
            "DOCTOR",
            now,
            "tester",
            now,
            "tester",
            "trace-task"));
        followupEvents.save(new FollowupEvent(
            null,
            "event-notify-1",
            "tenant-A",
            "plan-workflow-1",
            FollowupEventType.NOTIFICATION_REQUESTED,
            "{}",
            "followup",
            now,
            "tester",
            now,
            "tester",
            "trace-event"));

        List<FollowupWorkflowTodoRow> todoRows = followupTasks.pageOpenWorkflowRows("tenant-A", 0, 10);
        List<FollowupNotificationRow> notificationRows =
            followupEvents.pageNotificationRows("tenant-A", 0, 10);

        assertThat(todoRows).singleElement()
            .satisfies(row -> {
                assertThat(row.taskId()).isEqualTo("task-return-1");
                assertThat(row.taskType()).isEqualTo(FollowupTaskType.RETURN_VISIT);
                assertThat(row.status()).isEqualTo(FollowupTaskStatus.ABNORMAL_RETURN);
                assertThat(row.patientId()).isEqualTo("patient-1");
                assertThat(row.encounterId()).isEqualTo("enc-1");
                assertThat(row.executorId()).isEqualTo("doctor-1");
            });
        assertThat(notificationRows).singleElement()
            .satisfies(row -> {
                assertThat(row.eventId()).isEqualTo("event-notify-1");
                assertThat(row.taskId()).isEqualTo("task-return-1");
                assertThat(row.executorId()).isEqualTo("doctor-1");
                assertThat(row.executorType()).isEqualTo("DOCTOR");
                assertThat(row.patientId()).isEqualTo("patient-1");
                assertThat(row.title()).isEqualTo("随访异常回院通知");
                assertThat(row.traceId()).isEqualTo("trace-event");
            });
    }

    private WorkflowTodo sample(
            String todoId,
            WorkflowTodoSourceType sourceType,
            String sourceId,
            WorkflowPriority priority,
            Instant dueAt) {
        return sample(todoId, sourceType, sourceId, priority, "patient-1", dueAt);
    }

    private WorkflowTodo sample(
            String todoId,
            WorkflowTodoSourceType sourceType,
            String sourceId,
            WorkflowPriority priority,
            String patientId,
            Instant dueAt) {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        return new WorkflowTodo(
            null,
            todoId,
            "tenant-A",
            sourceType,
            sourceId,
            sourceType == WorkflowTodoSourceType.SAFETY_REVIEW ? "安全撤回复核任务" : "随访异常复核",
            "真实来源待办",
            priority,
            WorkflowTodoStatus.PENDING,
            "doctor-1",
            "DOCTOR",
            patientId,
            "enc-1",
            dueAt,
            "/clinical/followup",
            null,
            null,
            null,
            null,
            null,
            "trace-workflow",
            now,
            "tester",
            now,
            "tester");
    }
}
