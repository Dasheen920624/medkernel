package com.medkernel.engine.followup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.medkernel.engine.context.ContextSnapshotRequest;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.context.ContextSnapshotStatus;
import com.medkernel.engine.context.QualityStatus;
import com.medkernel.engine.pathway.ClinicalClock;
import com.medkernel.engine.pathway.ClinicalClockRepository;
import com.medkernel.engine.pathway.ClinicalClockStatus;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * 随访引擎 Service 单元测试。
 *
 * <p>使用 Mockito 隔离数据库依赖，验证业务逻辑：
 * <ul>
 *   <li>计划生成：验证返回的 planId 和任务列表</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class FollowupEngineServiceTest {

    @Mock
    private FollowupPlanRepository planRepository;
    @Mock
    private FollowupTaskRepository taskRepository;
    @Mock
    private FollowupQuestionnaireRepository questionnaireRepository;
    @Mock
    private FollowupEventRepository eventRepository;
    @Mock
    private ContextSnapshotService contextSnapshotService;
    @Mock
    private ClinicalClockRepository clinicalClockRepository;

    @InjectMocks
    private FollowupEngineService service;

    @BeforeEach
    void setUp() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-123", OrgScope.tenant("tenant-1"), "user-1"
        ));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void testGeneratePlan() {
        FollowupPlanGenerateRequest request = new FollowupPlanGenerateRequest(
            "PAT01", "ENC01", "PATH01", "D01", "HIGH", List.of("QUESTIONNAIRE")
        );
        
        FollowupPlan plan = new FollowupPlan(1L, "PLAN01", "tenant-1", "PAT01", "ENC01", "PATH01", "D01", "HIGH",
            FollowupPlanStatus.ACTIVE, Instant.now(), "sys", Instant.now(), "sys", "trace-123");
            
        when(planRepository.save(any(FollowupPlan.class))).thenReturn(plan);
        
        FollowupTask task = new FollowupTask(1L, "TASK01", "tenant-1", "PLAN01", FollowupTaskType.QUESTIONNAIRE,
            Instant.now(), FollowupTaskStatus.PENDING, null, null, Instant.now(), "sys", Instant.now(), "sys", "trace-123");
            
        when(taskRepository.save(any(FollowupTask.class))).thenReturn(task);

        FollowupPlanDetailResponse response = service.generatePlan(request);
        
        assertNotNull(response);
        assertEquals("PLAN01", response.planId());
        assertEquals(1, response.tasks().size());
        assertEquals(FollowupTaskType.QUESTIONNAIRE, response.tasks().get(0).taskType());
    }

    @Test
    void generatePlanReusesExistingPathwayPlan() {
        FollowupPlanGenerateRequest request = new FollowupPlanGenerateRequest(
            "PAT01", "ENC01", "PATH01", "D01", "HIGH", List.of("QUESTIONNAIRE")
        );
        FollowupPlan plan = new FollowupPlan(1L, "PLAN01", "tenant-1", "PAT01", "ENC01", "PATH01", "D01", "HIGH",
            FollowupPlanStatus.ACTIVE, Instant.now(), "sys", Instant.now(), "sys", "trace-123");
        FollowupTask task = new FollowupTask(1L, "TASK01", "tenant-1", "PLAN01", FollowupTaskType.QUESTIONNAIRE,
            Instant.now(), FollowupTaskStatus.PENDING, null, null, Instant.now(), "sys", Instant.now(), "sys", "trace-123");

        when(planRepository.findByTenantIdAndPathwayId("tenant-1", "PATH01"))
            .thenReturn(Optional.of(plan));
        when(taskRepository.findByTenantIdAndPlanId("tenant-1", "PLAN01"))
            .thenReturn(List.of(task));

        FollowupPlanDetailResponse response = service.generatePlan(request);

        assertEquals("PLAN01", response.planId());
        assertEquals(1, response.tasks().size());
        verify(planRepository, never()).save(any(FollowupPlan.class));
        verify(taskRepository, never()).save(any(FollowupTask.class));
    }

    @Test
    void generatePlanRejectsTaskTypesWithoutControlledFacts() {
        FollowupPlanGenerateRequest request = new FollowupPlanGenerateRequest(
            "PAT01", "ENC01", null, null, null, List.of("QUESTIONNAIRE"), "unsafe-task-only", false
        );

        ApiException exception = assertThrows(ApiException.class, () -> service.generatePlan(request));

        assertThat(exception.errorCode()).isEqualTo(ErrorCode.ENG_FOLLOW_004);
        verify(planRepository, never()).save(any(FollowupPlan.class));
        verify(taskRepository, never()).save(any(FollowupTask.class));
    }

    @Test
    void generatePlanRejectsIdempotencyReplayWithoutControlledFacts() {
        FollowupPlanGenerateRequest request = new FollowupPlanGenerateRequest(
            "PAT01", "ENC01", null, null, null, List.of("QUESTIONNAIRE"), "replay-without-fact", false
        );
        FollowupPlan existing = new FollowupPlan(1L, "PLAN01", "tenant-1", "PAT01", "ENC01", null, null, null,
            FollowupPlanStatus.ACTIVE, "replay-without-fact", Instant.now(), "sys", Instant.now(), "sys",
            "trace-123");
        FollowupTask task = new FollowupTask(1L, "TASK01", "tenant-1", "PLAN01", FollowupTaskType.QUESTIONNAIRE,
            Instant.now(), FollowupTaskStatus.PENDING, null, null, "task-key-1", Instant.now(), "sys",
            Instant.now(), "sys", "trace-123");
        lenient().when(planRepository.findByTenantIdAndIdempotencyKey("tenant-1", "replay-without-fact"))
            .thenReturn(Optional.of(existing));
        lenient().when(taskRepository.findByTenantIdAndPlanId("tenant-1", "PLAN01"))
            .thenReturn(List.of(task));

        ApiException exception = assertThrows(ApiException.class, () -> service.generatePlan(request));

        assertThat(exception.errorCode()).isEqualTo(ErrorCode.ENG_FOLLOW_004);
        verify(planRepository, never()).save(any(FollowupPlan.class));
        verify(taskRepository, never()).save(any(FollowupTask.class));
    }

    @Test
    void generatePlanDerivesTasksFromControlledFactsAndBindsClinicalClock() {
        Instant startedAt = Instant.parse("2026-06-01T00:00:00Z");
        Instant dueAt = Instant.parse("2026-06-08T00:00:00Z");
        ClinicalClock clock = new ClinicalClock(
            1L, "clock-followup-1", "tenant-1", "pp-1", "FOLLOWUP", "FOLLOWUP_7D",
            startedAt, dueAt, null, ClinicalClockStatus.RUNNING,
            startedAt, "pathway", startedAt, "pathway", "trace-pathway"
        );
        FollowupPlanGenerateRequest request = new FollowupPlanGenerateRequest(
            "PAT01", "ENC01", "pp-1", "D01", "HIGH", List.of(), "fact-plan-key-1", false
        );

        when(clinicalClockRepository.findByPatientPathwayIdAndTenantIdOrderByStartedAtAsc("pp-1", "tenant-1"))
            .thenReturn(List.of(clock));
        when(planRepository.save(any(FollowupPlan.class))).thenAnswer(inv -> {
            FollowupPlan p = inv.getArgument(0);
            return new FollowupPlan(
                1L, "PLAN01", p.tenantId(), p.patientId(), p.encounterId(), p.pathwayId(),
                p.diseaseCode(), p.riskLevel(), p.status(), p.idempotencyKey(),
                p.sourceFactType(), p.sourceFactId(), p.generationRuleCode(), p.generationExplanation(),
                p.createdAt(), p.createdBy(), p.updatedAt(), p.updatedBy(), p.traceId()
            );
        });
        when(taskRepository.save(any(FollowupTask.class))).thenAnswer(inv -> {
            FollowupTask t = inv.getArgument(0);
            return new FollowupTask(
                1L, "TASK-" + t.taskType().name(), t.tenantId(), t.planId(), t.taskType(),
                t.dueDate(), t.status(), t.executorId(), t.executorType(), t.idempotencyKey(),
                t.clinicalClockId(), t.createdAt(), t.createdBy(), t.updatedAt(), t.updatedBy(), t.traceId()
            );
        });

        FollowupPlanDetailResponse response = service.generatePlan(request);

        assertThat(response.sourceFactType()).isEqualTo("PATHWAY");
        assertThat(response.sourceFactId()).isEqualTo("pp-1");
        assertThat(response.generationRuleCode()).isEqualTo("CONTROLLED_FACT_PATHWAY_HIGH");
        assertThat(response.generationExplanation()).contains("clock-followup-1");
        assertThat(response.tasks())
            .extracting(FollowupTaskDetailResponse::taskType)
            .containsExactly(FollowupTaskType.QUESTIONNAIRE, FollowupTaskType.OUTPATIENT);
        assertThat(response.tasks())
            .extracting(FollowupTaskDetailResponse::dueDate)
            .containsExactly(dueAt, dueAt);
        assertThat(response.tasks())
            .extracting(FollowupTaskDetailResponse::clinicalClockId)
            .containsExactly("clock-followup-1", "clock-followup-1");
    }

    @Test
    void testListPlans() {
        FollowupPlan plan = new FollowupPlan(1L, "PLAN01", "tenant-1", "PAT01", "ENC01", "PATH01", "D01", "HIGH",
            FollowupPlanStatus.ACTIVE, Instant.now(), "sys", Instant.now(), "sys", "trace-123");
            
        Page<FollowupPlan> planPage = new PageImpl<>(List.of(plan));
        
        when(planRepository.findByTenantId(any(String.class), any(Pageable.class)))
            .thenReturn(planPage);
            
        FollowupTask task = new FollowupTask(1L, "TASK01", "tenant-1", "PLAN01", FollowupTaskType.QUESTIONNAIRE,
            Instant.now(), FollowupTaskStatus.PENDING, null, null, Instant.now(), "sys", Instant.now(), "sys", "trace-123");
            
        when(taskRepository.findByTenantIdAndPlanId(any(String.class), any(String.class)))
            .thenReturn(List.of(task));

        PageResponse<FollowupPlanDetailResponse> response = service.listPlans(null, new PageRequest(1, 10, null));
        
        assertNotNull(response);
        assertEquals(1, response.items().size());
        assertEquals("PLAN01", response.items().get(0).planId());
    }

    @Test
    void generatePlanReusesIdempotencyKeyAndReportsModelDisabled() {
        FollowupPlanGenerateRequest request = new FollowupPlanGenerateRequest(
            "PAT01", "ENC01", "PATH01", "D01", "HIGH", List.of("QUESTIONNAIRE"),
            "follow-plan-key-1", false
        );
        FollowupPlan plan = new FollowupPlan(1L, "PLAN01", "tenant-1", "PAT01", "ENC01", "PATH01", "D01", "HIGH",
            FollowupPlanStatus.ACTIVE, "follow-plan-key-1", Instant.now(), "sys", Instant.now(), "sys", "trace-123");
        FollowupTask task = new FollowupTask(1L, "TASK01", "tenant-1", "PLAN01", FollowupTaskType.QUESTIONNAIRE,
            Instant.now(), FollowupTaskStatus.PENDING, null, null, "task-key-1", Instant.now(), "sys", Instant.now(),
            "sys", "trace-123");

        when(planRepository.findByTenantIdAndIdempotencyKey("tenant-1", "follow-plan-key-1"))
            .thenReturn(Optional.of(plan));
        when(taskRepository.findByTenantIdAndPlanId("tenant-1", "PLAN01"))
            .thenReturn(List.of(task));

        FollowupPlanDetailResponse response = service.generatePlan(request);

        assertThat(response.planId()).isEqualTo("PLAN01");
        assertThat(response.modelStatus()).isEqualTo(FollowupModelStatus.MODEL_DISABLED);
        verify(planRepository, never()).save(any(FollowupPlan.class));
        verify(taskRepository, never()).save(any(FollowupTask.class));
    }

    @Test
    void listTasksUsesPatientScopedServerPagination() {
        FollowupTask task = new FollowupTask(1L, "TASK01", "tenant-1", "PLAN01", FollowupTaskType.QUESTIONNAIRE,
            Instant.parse("2026-06-04T00:00:00Z"), FollowupTaskStatus.PENDING, null, null, "task-key-1",
            Instant.now(), "sys", Instant.now(), "sys", "trace-123");

        when(taskRepository.countByTenantIdAndFilters("tenant-1", "PAT01", "PLAN01", "PENDING"))
            .thenReturn(1L);
        when(taskRepository.pageByTenantIdAndFilters("tenant-1", "PAT01", "PLAN01", "PENDING", 0, 10))
            .thenReturn(List.of(task));

        PageResponse<FollowupTaskDetailResponse> response = service.listTasks(
            new FollowupTaskFilter("PAT01", "PLAN01", FollowupTaskStatus.PENDING),
            new PageRequest(1, 10, null)
        );

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).taskId()).isEqualTo("TASK01");
        assertThat(response.items().get(0).planId()).isEqualTo("PLAN01");
    }

    @Test
    void dispatchQuestionnaireIsIdempotentAndMarksTaskInProgress() {
        FollowupTask task = new FollowupTask(1L, "TASK01", "tenant-1", "PLAN01", FollowupTaskType.QUESTIONNAIRE,
            Instant.now(), FollowupTaskStatus.PENDING, null, null, "task-key-1", Instant.now(), "sys", Instant.now(),
            "sys", "trace-123");
        FollowupQuestionnaire existing = new FollowupQuestionnaire(
            1L, "FQ01", "tenant-1", "PLAN01", "TASK01", "Q-TPL-1",
            "{\"title\":\"出院后症状随访\"}", null, null, "DISPATCHED", "questionnaire-key-1",
            null, "nurse-1", Instant.now(), "nurse-1", Instant.now(), "nurse-1", "trace-123"
        );

        when(questionnaireRepository.findByTenantIdAndIdempotencyKey("tenant-1", "questionnaire-key-1"))
            .thenReturn(Optional.of(existing));

        FollowupQuestionnaireResponse response = service.dispatchQuestionnaire(new FollowupQuestionnaireRequest(
            "TASK01", "Q-TPL-1", "{\"title\":\"出院后症状随访\"}", null, null,
            "questionnaire-key-1", "nurse-1", "FOLLOWUP_NURSE"
        ));

        assertThat(response.questionnaireId()).isEqualTo("FQ01");
        assertThat(response.status()).isEqualTo("DISPATCHED");
        verify(questionnaireRepository, never()).save(any(FollowupQuestionnaire.class));
        verify(taskRepository, never()).save(any(FollowupTask.class));
    }

    @Test
    void submitQuestionnaireRejectsNonJsonObjectAnswer() {
        ApiException exception = assertThrows(ApiException.class, () -> service.submitQuestionnaire(
            "TASK01",
            new FollowupQuestionnaireSubmitRequest("TASK01", "[\"非结构化答案\"]", "nurse-1", "FOLLOWUP_NURSE")
        ));

        assertThat(exception.errorCode()).isEqualTo(ErrorCode.ENG_FOLLOW_004);
        verify(questionnaireRepository, never()).save(any(FollowupQuestionnaire.class));
        verify(taskRepository, never()).save(any(FollowupTask.class));
    }

    @Test
    void reportAbnormalCreatesReturnVisitTaskAndNotificationEvent() {
        FollowupPlan plan = new FollowupPlan(1L, "PLAN01", "tenant-1", "PAT01", "ENC01", "PATH01", "D01", "HIGH",
            FollowupPlanStatus.ACTIVE, "follow-plan-key-1", Instant.now(), "sys", Instant.now(), "sys", "trace-123");

        when(planRepository.findByPlanId("PLAN01")).thenReturn(Optional.of(plan));
        when(taskRepository.save(any(FollowupTask.class))).thenAnswer(inv -> {
            FollowupTask t = inv.getArgument(0);
            return new FollowupTask(10L, "TASK-RETURN", t.tenantId(), t.planId(), t.taskType(), t.dueDate(),
                t.status(), t.executorId(), t.executorType(), t.idempotencyKey(), t.createdAt(), t.createdBy(),
                t.updatedAt(), t.updatedBy(), t.traceId());
        });
        when(eventRepository.save(any(FollowupEvent.class))).thenAnswer(inv -> {
            FollowupEvent e = inv.getArgument(0);
            return new FollowupEvent(10L, e.eventId(), e.tenantId(), e.planId(), e.eventType(), e.payload(),
                e.triggeredBy(), e.createdAt(), e.createdBy(), e.updatedAt(), e.updatedBy(), e.traceId());
        });

        FollowupAbnormalReportResponse response = service.reportAbnormal(new FollowupAbnormalReportRequest(
            "PLAN01", FollowupEventType.ABNORMAL_RETURN, "{\"reason\":\"异常症状加重\"}",
            "nurse-1", "abnormal-key-1"
        ));

        assertThat(response.returnTaskId()).isEqualTo("TASK-RETURN");
        ArgumentCaptor<FollowupTask> taskCaptor = ArgumentCaptor.forClass(FollowupTask.class);
        verify(taskRepository).save(taskCaptor.capture());
        assertThat(taskCaptor.getValue().taskType()).isEqualTo(FollowupTaskType.RETURN_VISIT);
        assertThat(taskCaptor.getValue().status()).isEqualTo(FollowupTaskStatus.ABNORMAL_RETURN);

        ArgumentCaptor<FollowupEvent> eventCaptor = ArgumentCaptor.forClass(FollowupEvent.class);
        verify(eventRepository, org.mockito.Mockito.times(2)).save(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
            .extracting(FollowupEvent::eventType)
            .containsExactly(FollowupEventType.ABNORMAL_RETURN, FollowupEventType.NOTIFICATION_REQUESTED);
    }

    @Test
    void reportAbnormalRejectsNonAbnormalEventType() {
        ApiException exception = assertThrows(ApiException.class, () -> service.reportAbnormal(
            new FollowupAbnormalReportRequest("PLAN01", FollowupEventType.RESULT_INFLOW, "{}", "nurse-1",
                "abnormal-key-1")
        ));

        assertThat(exception.errorCode()).isEqualTo(ErrorCode.ENG_FOLLOW_004);
        verify(planRepository, never()).findByPlanId(any(String.class));
        verify(taskRepository, never()).save(any(FollowupTask.class));
        verify(eventRepository, never()).save(any(FollowupEvent.class));
    }

    @Test
    void backflowResultCreatesFollowupContextSnapshot() {
        FollowupPlan plan = new FollowupPlan(1L, "PLAN01", "tenant-1", "PAT01", "ENC01", "PATH01", "D01", "HIGH",
            FollowupPlanStatus.ACTIVE, "follow-plan-key-1", Instant.now(), "sys", Instant.now(), "sys", "trace-123");
        FollowupTask task = new FollowupTask(1L, "TASK01", "tenant-1", "PLAN01", FollowupTaskType.QUESTIONNAIRE,
            Instant.now(), FollowupTaskStatus.COMPLETED, "nurse-1", "FOLLOWUP_NURSE", "task-key-1", Instant.now(),
            "sys", Instant.now(), "nurse-1", "trace-123");
        FollowupQuestionnaire questionnaire = new FollowupQuestionnaire(
            1L, "FQ01", "tenant-1", "PLAN01", "TASK01", "Q-TPL-1",
            "{\"title\":\"出院后症状随访\"}", "{\"painScore\":2}", new BigDecimal("2.00"), "COMPLETED",
            "questionnaire-key-1", Instant.now(), "nurse-1", Instant.now(), "nurse-1", Instant.now(), "nurse-1",
            "trace-123"
        );

        when(planRepository.findByPlanId("PLAN01")).thenReturn(Optional.of(plan));
        when(taskRepository.findByTaskId("TASK01")).thenReturn(Optional.of(task));
        when(questionnaireRepository.findByQuestionnaireId("FQ01")).thenReturn(Optional.of(questionnaire));
        when(contextSnapshotService.create(any(ContextSnapshotRequest.class), eq("result-key-1")))
            .thenReturn(new ContextSnapshotResponse(
                "ctx-follow-1", ContextSnapshotStatus.ACTIVE, null, null, null, null, null,
                QualityStatus.VALID, List.of(), Map.of(), Instant.now(), "trace-123"
            ));
        when(eventRepository.save(any(FollowupEvent.class))).thenAnswer(inv -> {
            FollowupEvent e = inv.getArgument(0);
            return new FollowupEvent(10L, "FE01", e.tenantId(), e.planId(), e.eventType(), e.payload(),
                e.triggeredBy(), e.createdAt(), e.createdBy(), e.updatedAt(), e.updatedBy(), e.traceId());
        });

        FollowupResultBackflowResponse response = service.backflowResult(new FollowupResultBackflowRequest(
            "PLAN01", "TASK01", "FQ01", "{\"painScore\":2}", "N",
            "pkg-2026.06", "result-key-1"
        ));

        assertThat(response.contextSnapshotId()).isEqualTo("ctx-follow-1");
        ArgumentCaptor<ContextSnapshotRequest> snapshotCaptor = ArgumentCaptor.forClass(ContextSnapshotRequest.class);
        verify(contextSnapshotService).create(snapshotCaptor.capture(), eq("result-key-1"));
        assertThat(snapshotCaptor.getValue().resources().patient().name()).isEqualTo("随访回流未提供患者姓名");
        assertThat(snapshotCaptor.getValue().resources().patient().qualityStatus()).isEqualTo(QualityStatus.PARTIAL);
        assertThat(snapshotCaptor.getValue().resources().followUps()).hasSize(1);
        assertThat(snapshotCaptor.getValue().resources().followUps().get(0).followUpId()).isEqualTo("FQ01");
        assertThat(snapshotCaptor.getValue().resources().followUps().get(0).abnormalFlag()).isEqualTo("N");
        verify(eventRepository).save(any(FollowupEvent.class));
    }

    @Test
    void backflowResultReusesExistingResultInflowEventByIdempotencyKey() {
        FollowupEvent existing = new FollowupEvent(
            10L,
            "FE-RESULT-EXISTING",
            "tenant-1",
            "PLAN01",
            FollowupEventType.RESULT_INFLOW,
            "{\"questionnaireId\":\"FQ01\",\"contextSnapshotId\":\"ctx-existing\",\"abnormalFlag\":\"Y\"}",
            "nurse-1",
            "result-key-1",
            Instant.now(),
            "nurse-1",
            Instant.now(),
            "nurse-1",
            "trace-old"
        );
        when(eventRepository.findByTenantIdAndEventTypeAndIdempotencyKey(
            "tenant-1", FollowupEventType.RESULT_INFLOW, "result-key-1"))
            .thenReturn(Optional.of(existing));

        FollowupResultBackflowResponse response = service.backflowResult(new FollowupResultBackflowRequest(
            "PLAN01", "TASK01", "FQ01", "{\"painScore\":8}", "Y",
            "pkg-2026.06", "result-key-1"
        ));

        assertThat(response.eventId()).isEqualTo("FE-RESULT-EXISTING");
        assertThat(response.contextSnapshotId()).isEqualTo("ctx-existing");
        verify(contextSnapshotService, never()).create(any(ContextSnapshotRequest.class), any(String.class));
        verify(eventRepository, never()).save(any(FollowupEvent.class));
    }
}
