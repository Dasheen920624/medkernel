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
import org.springframework.jdbc.core.JdbcTemplate;
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
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void wipe() {
        repository.deleteAll();
        followupEvents.deleteAll();
        followupTasks.deleteAll();
        followupPlans.deleteAll();
        jdbc.update("DELETE FROM user_role_assignment WHERE tenant_id = 'tenant-A'");
        jdbc.update("DELETE FROM org_closure");
        jdbc.update("DELETE FROM org_unit");
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
    void visibleAssigneeScopeNarrowsReportInterpretationTodoBySourceId() {
        seedRoleAssignment("doctor-1", "clinical-user");
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        for (int index = 0; index < 12; index++) {
            repository.save(sample(
                "todo-report-old-" + index,
                WorkflowTodoSourceType.REPORT_INTERPRETATION,
                "card-report-old-" + index,
                WorkflowPriority.HIGH,
                "patient-report-" + index,
                null,
                null));
        }
        repository.save(sample(
            "todo-report-current",
            WorkflowTodoSourceType.REPORT_INTERPRETATION,
            "card-regional-1",
            WorkflowPriority.HIGH,
            "patient-current",
            null,
            now.plusSeconds(3600)));

        long total = repository.countByVisibleAssigneeScope(
            "tenant-A",
            "PENDING",
            null,
            "REPORT_INTERPRETATION",
            null,
            "doctor-1",
            null,
            null,
            "card-regional-1");
        List<WorkflowTodo> page = repository.pageByVisibleAssigneeScope(
            "tenant-A",
            "PENDING",
            null,
            "REPORT_INTERPRETATION",
            null,
            "doctor-1",
            null,
            null,
            "card-regional-1",
            0,
            10);

        assertThat(total).isEqualTo(1);
        assertThat(page).extracting(WorkflowTodo::todoId)
            .containsExactly("todo-report-current");
    }

    @Test
    void visibleAssigneeScopeIncludesCurrentUserAndUnassignedRowsOnly() {
        seedRoleAssignment("doctor-1", "clinical-user");
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        repository.save(sample(
            "todo-own",
            WorkflowTodoSourceType.FOLLOWUP_TASK,
            "followup-own",
            WorkflowPriority.HIGH,
            "patient-1",
            "doctor-1",
            now.plusSeconds(1800)));
        repository.save(sample(
            "todo-org",
            WorkflowTodoSourceType.RECOMMENDATION_CARD,
            "card-org",
            WorkflowPriority.MEDIUM,
            "patient-2",
            null,
            now.plusSeconds(900)));
        repository.save(sample(
            "todo-other",
            WorkflowTodoSourceType.FOLLOWUP_TASK,
            "followup-other",
            WorkflowPriority.CRITICAL,
            "patient-3",
            "doctor-2",
            now.plusSeconds(300)));

        long total = repository.countByVisibleAssigneeScope(
            "tenant-A",
            "PENDING",
            null,
            null,
            null,
            "doctor-1",
            null,
            null);
        List<WorkflowTodo> page = repository.pageByVisibleAssigneeScope(
            "tenant-A",
            "PENDING",
            null,
            null,
            null,
            "doctor-1",
            null,
            null,
            0,
            10);

        assertThat(total).isEqualTo(2);
        assertThat(page).extracting(WorkflowTodo::todoId)
            .containsExactly("todo-own", "todo-org");
    }

    @Test
    void visibleAssigneeScopeOrdersReportInterpretationBeforeEarlierFollowupRows() {
        seedRoleAssignment("doctor-1", "clinical-user");
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        for (int index = 0; index < 12; index++) {
            repository.save(sample(
                "todo-followup-" + index,
                WorkflowTodoSourceType.FOLLOWUP_TASK,
                "followup-" + index,
                WorkflowPriority.HIGH,
                "patient-followup-" + index,
                null,
                now.plusSeconds(index + 1)));
        }
        repository.save(sample(
            "todo-report",
            WorkflowTodoSourceType.REPORT_INTERPRETATION,
            "card-report",
            WorkflowPriority.HIGH,
            "patient-report",
            null,
            null));

        List<WorkflowTodo> page = repository.pageByVisibleAssigneeScope(
            "tenant-A",
            "PENDING",
            null,
            null,
            null,
            "doctor-1",
            null,
            null,
            0,
            10);

        assertThat(page).extracting(WorkflowTodo::todoId)
            .startsWith("todo-report")
            .doesNotContain("todo-followup-10", "todo-followup-11");
    }

    @Test
    void visibleAssigneeScopeUsesOrgClosureForUnassignedRows() {
        seedOrgTree();
        seedRoleAssignment("doctor-1", "clinical-user");
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        repository.save(sample(
            "todo-own",
            WorkflowTodoSourceType.FOLLOWUP_TASK,
            "followup-own",
            WorkflowPriority.MEDIUM,
            "patient-1",
            "doctor-1",
            "facility-b",
            now.plusSeconds(100)));
        repository.save(sample(
            "todo-hospital",
            WorkflowTodoSourceType.RECOMMENDATION_CARD,
            "card-hospital",
            WorkflowPriority.MEDIUM,
            "patient-2",
            null,
            "facility-a",
            now.plusSeconds(200)));
        repository.save(sample(
            "todo-department",
            WorkflowTodoSourceType.NURSING_TASK,
            "card-department",
            WorkflowPriority.MEDIUM,
            "patient-3",
            null,
            "dept-a",
            now.plusSeconds(300)));
        repository.save(sample(
            "todo-tenant",
            WorkflowTodoSourceType.RECOMMENDATION_CARD,
            "card-tenant",
            WorkflowPriority.MEDIUM,
            "patient-4",
            null,
            null,
            now.plusSeconds(400)));
        repository.save(sample(
            "todo-sibling",
            WorkflowTodoSourceType.RECOMMENDATION_CARD,
            "card-sibling",
            WorkflowPriority.MEDIUM,
            "patient-5",
            null,
            "facility-b",
            now.plusSeconds(50)));

        long total = repository.countByVisibleAssigneeScope(
            "tenant-A",
            "PENDING",
            null,
            null,
            null,
            "doctor-1",
            "facility-a",
            null);
        List<WorkflowTodo> page = repository.pageByVisibleAssigneeScope(
            "tenant-A",
            "PENDING",
            null,
            null,
            null,
            "doctor-1",
            "facility-a",
            null,
            0,
            10);

        assertThat(total).isEqualTo(4);
        assertThat(page).extracting(WorkflowTodo::todoId)
            .containsExactly("todo-own", "todo-hospital", "todo-tenant", "todo-department");
    }

    @Test
    void visibleAssigneeScopeHonorsRoleScopedPathwayNodeTodos() {
        seedOrgTree();
        seedRoleAssignment("doctor-1", "clinical-user");
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        repository.save(sampleWithRole(
            "todo-pathway-clinical",
            WorkflowTodoSourceType.PATHWAY_NODE,
            "pp-1:ASSESS",
            "clinical-user",
            "dept-a",
            now.plusSeconds(300)));
        repository.save(sampleWithRole(
            "todo-pathway-operation",
            WorkflowTodoSourceType.PATHWAY_NODE,
            "pp-1:NURSING",
            "engine-operator",
            "dept-a",
            now.plusSeconds(200)));

        long total = repository.countByVisibleAssigneeScope(
            "tenant-A",
            "PENDING",
            null,
            null,
            null,
            "doctor-1",
            "dept-a",
            null);
        List<WorkflowTodo> page = repository.pageByVisibleAssigneeScope(
            "tenant-A",
            "PENDING",
            null,
            null,
            null,
            "doctor-1",
            "dept-a",
            null,
            0,
            10);

        assertThat(total).isEqualTo(1);
        assertThat(page).extracting(WorkflowTodo::todoId)
            .containsExactly("todo-pathway-clinical");
    }

    @Test
    void selectedOrganizationFilterOrdersReportInterpretationBeforeEarlierFollowupRows() {
        seedOrgTree();
        seedRoleAssignment("doctor-1", "clinical-user");
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        for (int index = 0; index < 12; index++) {
            repository.save(sample(
                "todo-selected-followup-" + index,
                WorkflowTodoSourceType.FOLLOWUP_TASK,
                "selected-followup-" + index,
                WorkflowPriority.HIGH,
                "patient-followup-" + index,
                null,
                "dept-a",
                now.plusSeconds(index + 1)));
        }
        repository.save(sample(
            "todo-selected-report",
            WorkflowTodoSourceType.REPORT_INTERPRETATION,
            "card-selected-report",
            WorkflowPriority.HIGH,
            "patient-report",
            null,
            "dept-a",
            null));

        List<WorkflowTodo> page = repository.pageByVisibleAssigneeScopeAndOrgUnitFilter(
            "tenant-A",
            "PENDING",
            null,
            null,
            null,
            "doctor-1",
            "dept-a",
            null,
            "facility-a",
            0,
            10);

        assertThat(page).extracting(WorkflowTodo::todoId)
            .startsWith("todo-selected-report")
            .doesNotContain("todo-selected-followup-10", "todo-selected-followup-11");
    }

    @Test
    void selectedOrganizationFilterNarrowsVisibleTodosToSelectedSubtree() {
        seedOrgTree();
        seedRoleAssignment("doctor-1", "clinical-user");
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        repository.save(sample(
            "todo-own-selected",
            WorkflowTodoSourceType.FOLLOWUP_TASK,
            "followup-own-selected",
            WorkflowPriority.HIGH,
            "patient-1",
            "doctor-1",
            "dept-a",
            now.plusSeconds(100)));
        repository.save(sample(
            "todo-selected-org",
            WorkflowTodoSourceType.RECOMMENDATION_CARD,
            "card-selected-org",
            WorkflowPriority.MEDIUM,
            "patient-2",
            null,
            "dept-a",
            now.plusSeconds(200)));
        repository.save(sample(
            "todo-selected-parent",
            WorkflowTodoSourceType.RECOMMENDATION_CARD,
            "card-selected-parent",
            WorkflowPriority.MEDIUM,
            "patient-3",
            null,
            "facility-a",
            now.plusSeconds(300)));
        repository.save(sample(
            "todo-tenant",
            WorkflowTodoSourceType.RECOMMENDATION_CARD,
            "card-tenant",
            WorkflowPriority.MEDIUM,
            "patient-4",
            null,
            null,
            now.plusSeconds(400)));
        repository.save(sample(
            "todo-sibling",
            WorkflowTodoSourceType.RECOMMENDATION_CARD,
            "card-sibling",
            WorkflowPriority.MEDIUM,
            "patient-5",
            null,
            "facility-b",
            now.plusSeconds(50)));

        long total = repository.countByVisibleAssigneeScopeAndOrgUnitFilter(
            "tenant-A",
            "PENDING",
            null,
            null,
            null,
            "doctor-1",
            "facility-a",
            null,
            "dept-a");
        List<WorkflowTodo> page = repository.pageByVisibleAssigneeScopeAndOrgUnitFilter(
            "tenant-A",
            "PENDING",
            null,
            null,
            null,
            "doctor-1",
            "facility-a",
            null,
            "dept-a",
            0,
            10);

        assertThat(total).isEqualTo(2);
        assertThat(page).extracting(WorkflowTodo::todoId)
            .containsExactly("todo-selected-org", "todo-own-selected");
    }

    @Test
    void selectedOrganizationFilterDoesNotExposeSiblingTodosOutsideCurrentClosure() {
        seedOrgTree();
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        repository.save(sample(
            "todo-sibling",
            WorkflowTodoSourceType.RECOMMENDATION_CARD,
            "card-sibling",
            WorkflowPriority.HIGH,
            "patient-1",
            null,
            "facility-b",
            now.plusSeconds(100)));

        long total = repository.countByVisibleAssigneeScopeAndOrgUnitFilter(
            "tenant-A",
            "PENDING",
            null,
            null,
            null,
            "doctor-1",
            "facility-a",
            null,
            "facility-b");
        List<WorkflowTodo> page = repository.pageByVisibleAssigneeScopeAndOrgUnitFilter(
            "tenant-A",
            "PENDING",
            null,
            null,
            null,
            "doctor-1",
            "facility-a",
            null,
            "facility-b",
            0,
            10);

        assertThat(total).isZero();
        assertThat(page).isEmpty();
    }

    @Test
    void explicitAssigneeFilterStillNarrowsToRequestedAssignee() {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        repository.save(sample(
            "todo-own",
            WorkflowTodoSourceType.FOLLOWUP_TASK,
            "followup-own",
            WorkflowPriority.HIGH,
            "patient-1",
            "doctor-1",
            now.plusSeconds(1800)));
        repository.save(sample(
            "todo-other",
            WorkflowTodoSourceType.FOLLOWUP_TASK,
            "followup-other",
            WorkflowPriority.CRITICAL,
            "patient-2",
            "doctor-2",
            now.plusSeconds(300)));

        List<WorkflowTodo> page = repository.pageByVisibleAssigneeScope(
            "tenant-A",
            "PENDING",
            null,
            null,
            "doctor-2",
            "doctor-1",
            null,
            null,
            0,
            10);

        assertThat(page).extracting(WorkflowTodo::todoId)
            .containsExactly("todo-other");
    }

    @Test
    void recommendationDerivedLookupPreventsDuplicateTypedTodoRows() {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        repository.save(sample(
            "todo-card-legacy",
            WorkflowTodoSourceType.RECOMMENDATION_CARD,
            "card-nursing-1",
            WorkflowPriority.HIGH,
            now.plusSeconds(1800)));
        repository.save(sample(
            "todo-followup-same-source",
            WorkflowTodoSourceType.FOLLOWUP_TASK,
            "card-nursing-1",
            WorkflowPriority.HIGH,
            now.plusSeconds(900)));

        var existing = repository.findRecommendationDerivedByTenantIdAndSourceId(
            "tenant-A",
            "card-nursing-1");

        assertThat(existing).isPresent();
        assertThat(existing.orElseThrow().todoId()).isEqualTo("todo-card-legacy");
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
        return sample(todoId, sourceType, sourceId, priority, patientId, "doctor-1", dueAt);
    }

    private WorkflowTodo sample(
            String todoId,
            WorkflowTodoSourceType sourceType,
            String sourceId,
            WorkflowPriority priority,
            String patientId,
            String assigneeId,
            Instant dueAt) {
        return sample(todoId, sourceType, sourceId, priority, patientId, assigneeId, null, dueAt);
    }

    private WorkflowTodo sample(
            String todoId,
            WorkflowTodoSourceType sourceType,
            String sourceId,
            WorkflowPriority priority,
            String patientId,
            String assigneeId,
            String orgUnitId,
            Instant dueAt) {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        return new WorkflowTodo(
            null,
            todoId,
            "tenant-A",
            orgUnitId,
            sourceType,
            sourceId,
            sourceType == WorkflowTodoSourceType.SAFETY_REVIEW ? "安全撤回复核任务" : "随访异常复核",
            "真实来源待办",
            priority,
            WorkflowTodoStatus.PENDING,
            assigneeId,
            "clinical-user",
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

    private WorkflowTodo sampleWithRole(
            String todoId,
            WorkflowTodoSourceType sourceType,
            String sourceId,
            String assigneeRole,
            String orgUnitId,
            Instant dueAt) {
        Instant now = Instant.parse("2026-06-04T08:00:00Z");
        return new WorkflowTodo(
            null,
            todoId,
            "tenant-A",
            orgUnitId,
            sourceType,
            sourceId,
            "路径节点待处理",
            "真实路径节点角色待办",
            WorkflowPriority.MEDIUM,
            WorkflowTodoStatus.PENDING,
            null,
            assigneeRole,
            "patient-1",
            "enc-1",
            dueAt,
            "/clinical/pathways?patientPathwayId=pp-1&nodeCode=ASSESS",
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

    private void seedRoleAssignment(String userId, String roleCode) {
        jdbc.update("""
            INSERT INTO user_role_assignment
                (tenant_id, user_id, role_code, scope_level, scope_code, active_flag, created_by, updated_by)
            VALUES (?, ?, ?, 'TENANT', 'tenant-A', 'Y', 'test', 'test')
            """, "tenant-A", userId, roleCode);
    }

    private void seedOrgTree() {
        jdbc.update("""
            INSERT INTO org_unit (id, parent_id, tenant_id, org_path, level_code, code, name, facility_type, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
            """, "tenant-root", null, "tenant-A", "/TENANT-A", "TENANT", "TENANT-A", "租户", null);
        jdbc.update("""
            INSERT INTO org_unit (id, parent_id, tenant_id, org_path, level_code, code, name, facility_type, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
            """, "facility-a", "tenant-root", "tenant-A", "/TENANT-A/FACILITY-A", "FACILITY", "FACILITY-A", "A 机构", "HOSPITAL");
        jdbc.update("""
            INSERT INTO org_unit (id, parent_id, tenant_id, org_path, level_code, code, name, facility_type, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
            """, "dept-a", "facility-a", "tenant-A", "/TENANT-A/FACILITY-A/DEPT-A", "DEPARTMENT", "DEPT-A", "A 科室", null);
        jdbc.update("""
            INSERT INTO org_unit (id, parent_id, tenant_id, org_path, level_code, code, name, facility_type, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
            """, "facility-b", "tenant-root", "tenant-A", "/TENANT-A/FACILITY-B", "FACILITY", "FACILITY-B", "B 机构", "HOSPITAL");
        jdbc.update("""
            INSERT INTO org_closure (tenant_id, ancestor_id, descendant_id, depth)
            VALUES
              ('tenant-A', 'tenant-root', 'tenant-root', 0),
              ('tenant-A', 'tenant-root', 'facility-a', 1),
              ('tenant-A', 'tenant-root', 'dept-a', 2),
              ('tenant-A', 'tenant-root', 'facility-b', 1),
              ('tenant-A', 'facility-a', 'facility-a', 0),
              ('tenant-A', 'facility-a', 'dept-a', 1),
              ('tenant-A', 'dept-a', 'dept-a', 0),
              ('tenant-A', 'facility-b', 'facility-b', 0)
            """);
    }
}
