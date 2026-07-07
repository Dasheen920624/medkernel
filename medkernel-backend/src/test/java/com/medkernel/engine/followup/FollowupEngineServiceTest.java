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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.medkernel.engine.context.ContextSnapshotRequest;
import com.medkernel.engine.context.ContextSnapshot;
import com.medkernel.engine.context.ContextSnapshotRepository;
import com.medkernel.engine.context.ContextSnapshotResources;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.context.ContextSnapshotStatus;
import com.medkernel.engine.context.QualityStatus;
import com.medkernel.engine.context.canonical.CanonicalCarePlan;
import com.medkernel.engine.context.canonical.CanonicalCondition;
import com.medkernel.engine.context.canonical.CanonicalNursingAssessment;
import com.medkernel.engine.pathway.ClinicalClock;
import com.medkernel.engine.pathway.ClinicalClockEscalationLevel;
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
    private ContextSnapshotRepository contextSnapshots;
    @Mock
    private ClinicalClockRepository clinicalClockRepository;
    @Mock
    private FollowupTemplateService templateService;
    @Mock
    private RuntimeReleaseFollowupTemplateSelector runtimeTemplates;

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
        stubActiveSnapshot("ctx-plan-1", "PAT01", "ENC01", "D01");
        FollowupPlanGenerateRequest request = new FollowupPlanGenerateRequest(
            "ctx-plan-1", "HIGH", List.of("QUESTIONNAIRE")
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
    void generatePlanUsesRuntimeReleasePinnedTemplateTasksAndQuestionnaireBinding() {
        stubActiveSnapshot("ctx-template-1", "PAT01", "ENC01", "D01");
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        FollowupTemplate template = followupTemplate("ftpl-1", 3);
        when(runtimeTemplates.requireByTemplateId("tenant-1", "runtime-release-test", "ftpl-1"))
            .thenReturn(template);
        when(templateService.tasks(template)).thenReturn(List.of(
            new FollowupTemplateTaskInput(
                FollowupTaskType.QUESTIONNAIRE,
                3,
                "QUESTIONNAIRE.COPD.03"
            ),
            new FollowupTemplateTaskInput(FollowupTaskType.OUTPATIENT, 14, null)
        ));
        when(planRepository.save(any(FollowupPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.save(any(FollowupTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FollowupPlanDetailResponse response = service.generatePlan(new FollowupPlanGenerateRequest(
            "ctx-template-1",
            "HIGH",
            List.of(),
            "template-plan-key-1",
            false,
            "ftpl-1"
        ));

        assertThat(response.runtimeReleaseId()).isEqualTo("runtime-release-test");
        assertThat(response.templateId()).isEqualTo("ftpl-1");
        assertThat(response.templateVersion()).isEqualTo(3);
        assertThat(response.templateCode()).isEqualTo("FUP.COPD");
        assertThat(response.templateName()).isEqualTo("慢阻肺出院随访");
        assertThat(response.tasks()).extracting(FollowupTaskDetailResponse::taskType)
            .containsExactly(FollowupTaskType.QUESTIONNAIRE, FollowupTaskType.OUTPATIENT);
        assertThat(response.tasks().get(0).questionnaireTemplateId())
            .isEqualTo("QUESTIONNAIRE.COPD.03");
        assertThat(response.tasks().get(1).questionnaireTemplateId()).isNull();
        ArgumentCaptor<FollowupTask> taskCaptor = ArgumentCaptor.forClass(FollowupTask.class);
        verify(taskRepository, org.mockito.Mockito.times(2)).save(taskCaptor.capture());
        assertThat(taskCaptor.getAllValues().get(0).dueDate())
            .isBefore(taskCaptor.getAllValues().get(1).dueDate());
        ArgumentCaptor<FollowupPlan> planCaptor = ArgumentCaptor.forClass(FollowupPlan.class);
        verify(planRepository).save(planCaptor.capture());
        assertThat(planCaptor.getValue().runtimeReleaseId()).isEqualTo("runtime-release-test");
        verify(templateService, never()).requirePublished("ftpl-1");
    }

    @Test
    void generatePlanExplanationConsumesNursingAssessmentAndCarePlanFacts() {
        Instant now = Instant.parse("2026-07-07T08:00:00Z");
        stubActiveSnapshot("ctx-nursing-1", "PAT-NURSE", "ENC-NURSE", "NURSING_CONTINUITY");
        ContextSnapshotResources resources = new ContextSnapshotResources(
            null,
            List.of(),
            List.of(),
            List.of(new CanonicalCondition(
                "cond-nursing",
                "NURSING_CONTINUITY",
                "ICD-10",
                "护理连续照护",
                null,
                "HIGH",
                "MEDKERNEL_FRONTDESK",
                "cond-source",
                "FRONTDESK_CONTEXT_V1",
                now,
                now,
                QualityStatus.VALID)),
            List.of(new CanonicalNursingAssessment(
                "na-fall-high",
                "跌倒风险评估",
                "HIGH",
                "CONFIRMED",
                "MEDKERNEL_FRONTDESK",
                "nursing-source",
                "FRONTDESK_CONTEXT_V1",
                now,
                now,
                QualityStatus.VALID)),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(new CanonicalCarePlan(
                "care-fall-plan",
                "PATHWAY.NURSING.FALL",
                "REHAB_EDUCATION",
                null,
                Instant.parse("2026-07-20T08:00:00Z"),
                "MEDKERNEL_FRONTDESK",
                "care-source",
                "FRONTDESK_CONTEXT_V1",
                now,
                now,
                QualityStatus.VALID)),
            List.of(),
            List.of(),
            ContextSnapshotResources.emptyExtensions());
        when(contextSnapshotService.findById("ctx-nursing-1")).thenReturn(new ContextSnapshotResponse(
            "ctx-nursing-1",
            ContextSnapshotStatus.ACTIVE,
            resources,
            "runtime-release-test",
            QualityStatus.VALID,
            List.of(),
            Map.of(),
            now,
            "trace-123"));
        FollowupTemplate template = followupTemplate("ftpl-nursing", 1);
        when(runtimeTemplates.requireByTemplateId("tenant-1", "runtime-release-test", "ftpl-nursing"))
            .thenReturn(template);
        when(templateService.tasks(template)).thenReturn(List.of(
            new FollowupTemplateTaskInput(FollowupTaskType.QUESTIONNAIRE, 1, "Q-NURSING-FALL"),
            new FollowupTemplateTaskInput(FollowupTaskType.RETURN_VISIT, 3, null)
        ));
        when(planRepository.save(any(FollowupPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.save(any(FollowupTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FollowupPlanDetailResponse response = service.generatePlan(new FollowupPlanGenerateRequest(
            "ctx-nursing-1",
            "HIGH",
            List.of(),
            "nursing-continuity-plan-key",
            false,
            "ftpl-nursing"
        ));

        assertThat(response.generationExplanation()).contains("nursingAssessmentEvidence");
        assertThat(response.generationExplanation()).contains("na-fall-high");
        assertThat(response.generationExplanation()).contains("carePlanEvidence");
        assertThat(response.generationExplanation()).contains("care-fall-plan");
        assertThat(response.generationExplanation()).contains("runtimeAssetEvidence");
        assertThat(response.generationExplanation()).contains("av-followup-1");
    }

    @Test
    void generatePlanRejectsSupersededSnapshot() {
        stubSnapshotEntity(
            "ctx-superseded-1",
            "PAT01",
            "ENC01",
            ContextSnapshotStatus.SUPERSEDED
        );

        ApiException exception = assertThrows(ApiException.class, () -> service.generatePlan(
            new FollowupPlanGenerateRequest("ctx-superseded-1", "HIGH", List.of("QUESTIONNAIRE"))
        ));

        assertThat(exception.errorCode()).isEqualTo(ErrorCode.ENG_FOLLOW_004);
        verify(contextSnapshotService, never()).findById(any(String.class));
        verify(planRepository, never()).save(any(FollowupPlan.class));
    }

    @Test
    void generatePlanUsesRiskFactWhenSnapshotHasNoResourceBody() {
        stubSnapshotEntity("ctx-empty-resources-1", "PAT01", "ENC01", ContextSnapshotStatus.ACTIVE);
        when(contextSnapshotService.findById("ctx-empty-resources-1")).thenReturn(new ContextSnapshotResponse(
            "ctx-empty-resources-1",
            ContextSnapshotStatus.ACTIVE,
            null,
            "runtime-release-test",
            QualityStatus.PARTIAL,
            List.of(),
            Map.of(),
            Instant.now(),
            "trace-123"
        ));
        FollowupPlan existing = new FollowupPlan(
            1L,
            "PLAN01",
            "tenant-1",
            "PAT01",
            "ENC01",
            null,
            null,
            "HIGH",
            FollowupPlanStatus.ACTIVE,
            "risk-plan-key-1",
            Instant.now(),
            "sys",
            Instant.now(),
            "sys",
            "trace-123"
        );
        when(planRepository.findByTenantIdAndIdempotencyKey("tenant-1", "risk-plan-key-1"))
            .thenReturn(Optional.of(existing));
        when(taskRepository.findByTenantIdAndPlanId("tenant-1", "PLAN01")).thenReturn(List.of());

        FollowupPlanDetailResponse response = service.generatePlan(new FollowupPlanGenerateRequest(
            "ctx-empty-resources-1",
            "HIGH",
            List.of("QUESTIONNAIRE"),
            "risk-plan-key-1",
            false
        ));

        assertThat(response.planId()).isEqualTo("PLAN01");
        verify(planRepository, never()).save(any(FollowupPlan.class));
    }

    @Test
    void generatePlanReusesExistingPathwayPlan() {
        FollowupPlan plan = new FollowupPlan(1L, "PLAN01", "tenant-1", "PAT01", "ENC01", "PATH01", "D01", "HIGH",
            FollowupPlanStatus.ACTIVE, Instant.now(), "sys", Instant.now(), "sys", "trace-123");
        FollowupTask task = new FollowupTask(1L, "TASK01", "tenant-1", "PLAN01", FollowupTaskType.QUESTIONNAIRE,
            Instant.now(), FollowupTaskStatus.PENDING, null, null, Instant.now(), "sys", Instant.now(), "sys", "trace-123");

        when(planRepository.findByTenantIdAndPathwayId("tenant-1", "PATH01"))
            .thenReturn(Optional.of(plan));
        when(taskRepository.findByTenantIdAndPlanId("tenant-1", "PLAN01"))
            .thenReturn(List.of(task));

        FollowupPlanDetailResponse response = service.generatePlanFromPathway(
            "PAT01", "ENC01", "PATH01", "D01", "HIGH", List.of("QUESTIONNAIRE"));

        assertEquals("PLAN01", response.planId());
        assertEquals(1, response.tasks().size());
        verify(planRepository, never()).save(any(FollowupPlan.class));
        verify(taskRepository, never()).save(any(FollowupTask.class));
    }

    @Test
    void generatePlanRejectsTaskTypesWithoutControlledFacts() {
        stubActiveSnapshot("ctx-no-fact-1", "PAT01", "ENC01", null);
        FollowupPlanGenerateRequest request = new FollowupPlanGenerateRequest(
            "ctx-no-fact-1", null, List.of("QUESTIONNAIRE"), "unsafe-task-only", false
        );

        ApiException exception = assertThrows(ApiException.class, () -> service.generatePlan(request));

        assertThat(exception.errorCode()).isEqualTo(ErrorCode.ENG_FOLLOW_004);
        verify(planRepository, never()).save(any(FollowupPlan.class));
        verify(taskRepository, never()).save(any(FollowupTask.class));
    }

    @Test
    void generatePlanRejectsIdempotencyReplayWithoutControlledFacts() {
        stubActiveSnapshot("ctx-no-fact-2", "PAT01", "ENC01", null);
        FollowupPlanGenerateRequest request = new FollowupPlanGenerateRequest(
            "ctx-no-fact-2", null, List.of("QUESTIONNAIRE"), "replay-without-fact", false
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
            null, null, null, null, null, ClinicalClockEscalationLevel.NONE, null,
            startedAt, "pathway", startedAt, "pathway", "trace-pathway"
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

        FollowupPlanDetailResponse response = service.generatePlanFromPathway(
            "PAT01", "ENC01", "pp-1", "D01", "HIGH", List.of(), "fact-plan-key-1", false);

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
        FollowupPlan plan = new FollowupPlan(
            1L, "PLAN01", "tenant-1", "PAT01", "ENC01", "PATH01", "D01", "HIGH",
            "runtime-release-test", FollowupPlanStatus.ACTIVE, null, "DIAGNOSIS", "D01",
            "FOLLOWUP_TEMPLATE_FUP.COPD_V3", "{}", "ftpl-1", 3,
            Instant.now(), "sys", Instant.now(), "sys", "trace-123");
            
        Page<FollowupPlan> planPage = new PageImpl<>(List.of(plan));
        
        when(planRepository.findByTenantId(any(String.class), any(Pageable.class)))
            .thenReturn(planPage);
            
        FollowupTask task = new FollowupTask(1L, "TASK01", "tenant-1", "PLAN01", FollowupTaskType.QUESTIONNAIRE,
            Instant.now(), FollowupTaskStatus.PENDING, null, null, Instant.now(), "sys", Instant.now(), "sys", "trace-123");
            
        when(taskRepository.findByTenantIdAndPlanId(any(String.class), any(String.class)))
            .thenReturn(List.of(task));
        when(templateService.findById("tenant-1", "ftpl-1"))
            .thenReturn(Optional.of(followupTemplate("ftpl-1", 3)));

        PageResponse<FollowupPlanDetailResponse> response = service.listPlans(null, new PageRequest(1, 10, null));
        
        assertNotNull(response);
        assertEquals(1, response.items().size());
        assertEquals("PLAN01", response.items().get(0).planId());
        assertThat(response.items().get(0).templateCode()).isEqualTo("FUP.COPD");
        assertThat(response.items().get(0).templateName()).isEqualTo("慢阻肺出院随访");
    }

    @Test
    void generatePlanReusesIdempotencyKeyAndReportsModelDisabled() {
        stubActiveSnapshot("ctx-idempotent-1", "PAT01", "ENC01", "D01");
        FollowupPlanGenerateRequest request = new FollowupPlanGenerateRequest(
            "ctx-idempotent-1", "HIGH", List.of("QUESTIONNAIRE"), "follow-plan-key-1", false
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
    void b0ReplayRunsFollowupLoopAndRecordsModelDisabledDowngrade() {
        List<FollowupPlan> plans = new ArrayList<>();
        List<FollowupTask> tasks = new ArrayList<>();
        List<FollowupQuestionnaire> questionnaires = new ArrayList<>();
        List<FollowupEvent> events = new ArrayList<>();

        when(clinicalClockRepository.findByPatientPathwayIdAndTenantIdOrderByStartedAtAsc("path-b0", "tenant-1"))
            .thenReturn(List.of());
        when(planRepository.findByTenantIdAndIdempotencyKey("tenant-1", "b0-plan-key"))
            .thenReturn(Optional.empty());
        when(planRepository.findByTenantIdAndPathwayId("tenant-1", "path-b0"))
            .thenReturn(Optional.empty());
        when(planRepository.findByPlanId(any(String.class))).thenAnswer(inv -> {
            String planId = inv.getArgument(0);
            return plans.stream().filter(plan -> plan.planId().equals(planId)).findFirst();
        });
        when(planRepository.save(any(FollowupPlan.class))).thenAnswer(inv -> {
            FollowupPlan p = inv.getArgument(0);
            FollowupPlan saved = new FollowupPlan(
                1L, "PLAN-B0", p.tenantId(), p.patientId(), p.encounterId(), p.pathwayId(),
                p.diseaseCode(), p.riskLevel(), p.status(), p.idempotencyKey(),
                p.sourceFactType(), p.sourceFactId(), p.generationRuleCode(), p.generationExplanation(),
                p.createdAt(), p.createdBy(), p.updatedAt(), p.updatedBy(), p.traceId()
            );
            plans.add(saved);
            return saved;
        });
        when(taskRepository.findByTaskId(any(String.class))).thenAnswer(inv -> {
            String taskId = inv.getArgument(0);
            return tasks.stream().filter(task -> task.taskId().equals(taskId)).findFirst();
        });
        when(taskRepository.findByTenantIdAndIdempotencyKey(eq("tenant-1"), any(String.class))).thenAnswer(inv -> {
            String idempotencyKey = inv.getArgument(1);
            return tasks.stream().filter(task -> idempotencyKey.equals(task.idempotencyKey())).findFirst();
        });
        when(taskRepository.save(any(FollowupTask.class))).thenAnswer(inv -> {
            FollowupTask t = inv.getArgument(0);
            String taskId = t.taskType() == FollowupTaskType.RETURN_VISIT ? "TASK-B0-RETURN" : "TASK-B0-Q";
            FollowupTask saved = new FollowupTask(
                t.id() == null ? (long) tasks.size() + 1 : t.id(),
                taskId,
                t.tenantId(),
                t.planId(),
                t.taskType(),
                t.dueDate(),
                t.status(),
                t.executorId(),
                t.executorType(),
                t.idempotencyKey(),
                t.clinicalClockId(),
                t.createdAt(),
                t.createdBy(),
                t.updatedAt(),
                t.updatedBy(),
                t.traceId()
            );
            tasks.removeIf(task -> task.taskId().equals(saved.taskId()));
            tasks.add(saved);
            return saved;
        });
        when(questionnaireRepository.findByTenantIdAndIdempotencyKey("tenant-1", "b0-questionnaire-key"))
            .thenReturn(Optional.empty());
        when(questionnaireRepository.findByQuestionnaireId(any(String.class))).thenAnswer(inv -> {
            String questionnaireId = inv.getArgument(0);
            return questionnaires.stream()
                .filter(questionnaire -> questionnaire.questionnaireId().equals(questionnaireId))
                .findFirst();
        });
        when(questionnaireRepository.save(any(FollowupQuestionnaire.class))).thenAnswer(inv -> {
            FollowupQuestionnaire q = inv.getArgument(0);
            FollowupQuestionnaire saved = new FollowupQuestionnaire(
                1L, "FQ-B0", q.tenantId(), q.planId(), q.taskId(), q.questionnaireTemplateId(),
                q.formData(), q.answerData(), q.score(), q.status(), q.idempotencyKey(),
                q.submittedAt(), q.executorId(), q.createdAt(), q.createdBy(), q.updatedAt(),
                q.updatedBy(), q.traceId()
            );
            questionnaires.add(saved);
            return saved;
        });
        when(eventRepository.findByTenantIdAndEventTypeAndIdempotencyKey(
            eq("tenant-1"), any(FollowupEventType.class), any(String.class))).thenAnswer(inv -> {
                FollowupEventType eventType = inv.getArgument(1);
                String idempotencyKey = inv.getArgument(2);
                return events.stream()
                    .filter(event -> event.eventType() == eventType)
                    .filter(event -> idempotencyKey.equals(event.idempotencyKey()))
                    .findFirst();
            });
        when(eventRepository.save(any(FollowupEvent.class))).thenAnswer(inv -> {
            FollowupEvent e = inv.getArgument(0);
            FollowupEvent saved = new FollowupEvent(
                (long) events.size() + 1,
                "FE-B0-" + (events.size() + 1),
                e.tenantId(),
                e.planId(),
                e.eventType(),
                e.payload(),
                e.triggeredBy(),
                e.idempotencyKey(),
                e.createdAt(),
                e.createdBy(),
                e.updatedAt(),
                e.updatedBy(),
                e.traceId()
            );
            events.add(saved);
            return saved;
        });
        when(contextSnapshotService.create(any(ContextSnapshotRequest.class), eq("b0-result-key")))
            .thenReturn(new ContextSnapshotResponse(
                "ctx-b0", ContextSnapshotStatus.ACTIVE, null, "runtime-release-test",
                QualityStatus.VALID, List.of(), Map.of(), Instant.now(), "trace-123"
            ));

        FollowupPlanDetailResponse plan = service.generatePlanFromPathway(
            "PAT-B0", "ENC-B0", "path-b0", "D-B0", "STANDARD", List.of(), "b0-plan-key", true);
        FollowupQuestionnaireResponse questionnaire = service.dispatchQuestionnaire(new FollowupQuestionnaireRequest(
            plan.tasks().get(0).taskId(),
            "TPL-B0",
            "{\"title\":\"B0随访\"}",
            "{\"painScore\":4}",
            new BigDecimal("4.00"),
            "b0-questionnaire-key",
            "nurse-1",
            "FOLLOWUP_NURSE"
        ));
        FollowupAbnormalReportResponse abnormal = service.reportAbnormal(new FollowupAbnormalReportRequest(
            plan.planId(),
            FollowupEventType.ABNORMAL_RETURN,
            "{\"reason\":\"painScore上升\",\"score\":4}",
            "nurse-1",
            "b0-abnormal-key"
        ));
        FollowupResultBackflowResponse backflow = service.backflowResult(new FollowupResultBackflowRequest(
            plan.planId(),
            plan.tasks().get(0).taskId(),
            questionnaire.questionnaireId(),
            "{\"painScore\":4,\"returnTaskId\":\"" + abnormal.returnTaskId() + "\"}",
            "Y",
            "b0-result-key"
        ));

        assertThat(plan.modelStatus()).isEqualTo(FollowupModelStatus.MODEL_DISABLED);
        assertThat(plan.generationExplanation())
            .contains("\"requestedModelEnabled\":true")
            .contains("\"modelDowngradeReason\":\"MODEL_DISABLED_DETERMINISTIC_RULES\"");
        assertThat(plan.tasks())
            .extracting(FollowupTaskDetailResponse::taskType)
            .containsExactly(FollowupTaskType.QUESTIONNAIRE);
        assertThat(questionnaire.status()).isEqualTo("COMPLETED");
        assertThat(abnormal.returnTaskId()).isEqualTo("TASK-B0-RETURN");
        assertThat(backflow.contextSnapshotId()).isEqualTo("ctx-b0");
        assertThat(events)
            .extracting(FollowupEvent::eventType)
            .containsExactly(
                FollowupEventType.ABNORMAL_RETURN,
                FollowupEventType.NOTIFICATION_REQUESTED,
                FollowupEventType.RESULT_INFLOW
            );
        assertThat(events.get(1).payload()).contains("\"abnormalPayload\":{\"reason\":\"painScore上升\",\"score\":4}");
        verify(contextSnapshotService).create(any(ContextSnapshotRequest.class), eq("b0-result-key"));
    }

    private void stubActiveSnapshot(
            String snapshotId,
            String patientId,
            String encounterId,
            String diseaseCode) {
        Instant now = Instant.now();
        stubSnapshotEntity(snapshotId, patientId, encounterId, ContextSnapshotStatus.ACTIVE);
        List<CanonicalCondition> conditions = diseaseCode == null
            ? List.of()
            : List.of(new CanonicalCondition(
                "condition-1",
                diseaseCode,
                "ICD-10",
                diseaseCode,
                null,
                null,
                "HIS",
                "source-1",
                "2026.06",
                now,
                now,
                QualityStatus.VALID));
        ContextSnapshotResources resources = new ContextSnapshotResources(
            null,
            List.of(),
            List.of(),
            conditions,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            ContextSnapshotResources.emptyExtensions());
        when(contextSnapshotService.findById(snapshotId)).thenReturn(new ContextSnapshotResponse(
            snapshotId,
            ContextSnapshotStatus.ACTIVE,
            resources,
            "runtime-release-test",
            QualityStatus.VALID,
            List.of(),
            Map.of(),
            now,
            "trace-123"));
    }

    private FollowupTemplate followupTemplate(String templateId, int versionNo) {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        return new FollowupTemplate(
            null,
            templateId,
            "tenant-1",
            "FUP.COPD",
            versionNo,
            "慢阻肺出院随访",
            null,
            "tenant:tenant-1",
            "riskLevel=HIGH",
            "[]",
            "{}",
            "{}",
            "hospital://followup/copd",
            "av-followup-" + versionNo,
            now,
            "user-1",
            now,
            "user-1",
            "trace-123"
        );
    }

    private void stubSnapshotEntity(
            String snapshotId,
            String patientId,
            String encounterId,
            ContextSnapshotStatus status) {
        Instant now = Instant.now();
        when(contextSnapshots.findBySnapshotIdAndTenantId(snapshotId, "tenant-1"))
            .thenReturn(Optional.of(new ContextSnapshot(
                1L,
                snapshotId,
                "tenant-1",
                "dept-1",
                "request-1",
                "/tenant-1/dept-1",
                "runtime-release-test",
                patientId,
                encounterId,
                status,
                "[]",
                "{}",
            "{}",
                QualityStatus.VALID,
                "trace-123",
                "signature",
                now,
                "system")));
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
    void statsUsesTenantAndPatientFiltersForGlobalProgress() {
        when(planRepository.countByTenantIdAndOptionalPatient("tenant-1", "PAT01"))
            .thenReturn(4L);
        when(planRepository.countByTenantIdAndOptionalPatientAndStatus(
            "tenant-1", "PAT01", FollowupPlanStatus.ACTIVE.name()))
            .thenReturn(2L);
        when(taskRepository.countByTenantIdAndPatientAndOptionalStatus("tenant-1", "PAT01", null))
            .thenReturn(10L);
        when(taskRepository.countByTenantIdAndPatientAndOptionalStatus(
            "tenant-1", "PAT01", FollowupTaskStatus.COMPLETED.name()))
            .thenReturn(7L);
        when(taskRepository.countByTenantIdAndPatientAndOptionalStatus(
            "tenant-1", "PAT01", FollowupTaskStatus.ABNORMAL_RETURN.name()))
            .thenReturn(2L);

        FollowupStatsResponse response = service.stats("PAT01");

        assertThat(response.totalPlans()).isEqualTo(4L);
        assertThat(response.activePlans()).isEqualTo(2L);
        assertThat(response.totalTasks()).isEqualTo(10L);
        assertThat(response.completedTasks()).isEqualTo(7L);
        assertThat(response.abnormalReturnTasks()).isEqualTo(2L);
        assertThat(response.taskCompletionRatePercent()).isEqualTo(70.0);
        assertThat(response.abnormalReturnRatePercent()).isEqualTo(20.0);
        assertThat(response.traceId()).isEqualTo("trace-123");
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
    void dispatchQuestionnaireRejectsNonJsonObjectAnswer() {
        ApiException exception = assertThrows(ApiException.class, () -> service.dispatchQuestionnaire(
            new FollowupQuestionnaireRequest(
                "TASK01", "FOLLOWUP_QUESTIONNAIRE_DEFAULT", "{\"title\":\"随访问卷\"}",
                "[\"非结构化答案\"]", null, "questionnaire-key-invalid", "nurse-1", "FOLLOWUP_NURSE"
            )
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
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-123",
            new OrgScope("tenant-1", "group-1", "hospital-1", "campus-1", "site-1", "dept-1", null, "specialty-1"),
            "user-1"
        ));
        FollowupPlan plan = new FollowupPlan(
            1L, "PLAN01", "tenant-1", "PAT01", "ENC01", "PATH01", "D01", "HIGH",
            "runtime-followup-plan", FollowupPlanStatus.ACTIVE, "follow-plan-key-1",
            null, null, null, null, null, null,
            Instant.now(), "sys", Instant.now(), "sys", "trace-123");
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
        when(contextSnapshotService.createBound(
            any(ContextSnapshotRequest.class), eq("result-key-1"), eq("runtime-followup-plan")))
            .thenReturn(new ContextSnapshotResponse(
                "ctx-follow-1", ContextSnapshotStatus.ACTIVE, null, "runtime-followup-plan",
                QualityStatus.VALID, List.of(), Map.of(), Instant.now(), "trace-123"
            ));
        when(eventRepository.save(any(FollowupEvent.class))).thenAnswer(inv -> {
            FollowupEvent e = inv.getArgument(0);
            return new FollowupEvent(10L, "FE01", e.tenantId(), e.planId(), e.eventType(), e.payload(),
                e.triggeredBy(), e.createdAt(), e.createdBy(), e.updatedAt(), e.updatedBy(), e.traceId());
        });

        FollowupResultBackflowResponse response = service.backflowResult(new FollowupResultBackflowRequest(
            "PLAN01", "TASK01", "FQ01", "{\"painScore\":2}", "N",
            "result-key-1"
        ));

        assertThat(response.contextSnapshotId()).isEqualTo("ctx-follow-1");
        ArgumentCaptor<ContextSnapshotRequest> snapshotCaptor = ArgumentCaptor.forClass(ContextSnapshotRequest.class);
        verify(contextSnapshotService).createBound(
            snapshotCaptor.capture(), eq("result-key-1"), eq("runtime-followup-plan"));
        assertThat(snapshotCaptor.getValue().resources().patient().name()).isEqualTo("随访回流未提供患者姓名");
        assertThat(snapshotCaptor.getValue().resources().patient().qualityStatus()).isEqualTo(QualityStatus.PARTIAL);
        assertThat(snapshotCaptor.getValue().resources().patient().mappedVersion()).isEqualTo("FOLLOWUP_RESULT");
        assertThat(snapshotCaptor.getValue().resources().followUps()).hasSize(1);
        assertThat(snapshotCaptor.getValue().resources().followUps().get(0).followUpId()).isEqualTo("FQ01");
        assertThat(snapshotCaptor.getValue().resources().followUps().get(0).abnormalFlag()).isEqualTo("N");
        assertThat(snapshotCaptor.getValue().resources().followUps().get(0).mappedVersion()).isEqualTo("FOLLOWUP_RESULT");
        assertThat(snapshotCaptor.getValue().orgUnitId()).isEqualTo("dept-1");
        verify(eventRepository).save(any(FollowupEvent.class));
    }

    @Test
    void backflowResultRejectsIncompleteQuestionnaireTask() {
        FollowupPlan plan = new FollowupPlan(
            1L, "PLAN01", "tenant-1", "PAT01", "ENC01", "PATH01", "D01", "HIGH",
            "runtime-followup-plan", FollowupPlanStatus.ACTIVE, "follow-plan-key-1",
            null, null, null, null, null, null,
            Instant.now(), "sys", Instant.now(), "sys", "trace-123");
        FollowupTask task = new FollowupTask(1L, "TASK01", "tenant-1", "PLAN01", FollowupTaskType.QUESTIONNAIRE,
            Instant.now(), FollowupTaskStatus.IN_PROGRESS, "nurse-1", "FOLLOWUP_NURSE", "task-key-1", Instant.now(),
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

        ApiException exception = assertThrows(ApiException.class, () -> service.backflowResult(
            new FollowupResultBackflowRequest("PLAN01", "TASK01", "FQ01", "{\"painScore\":2}", "N",
                "result-key-incomplete-task")
        ));

        assertThat(exception.errorCode()).isEqualTo(ErrorCode.ENG_FOLLOW_004);
        assertThat(exception.getMessage()).contains("随访问卷任务未完成");
        verify(contextSnapshotService, never()).create(any(ContextSnapshotRequest.class), any(String.class));
        verify(contextSnapshotService, never()).createBound(
            any(ContextSnapshotRequest.class), any(String.class), any(String.class));
        verify(eventRepository, never()).save(any(FollowupEvent.class));
    }

    @Test
    void backflowResultRejectsIncompleteQuestionnaireRecord() {
        FollowupPlan plan = new FollowupPlan(
            1L, "PLAN01", "tenant-1", "PAT01", "ENC01", "PATH01", "D01", "HIGH",
            "runtime-followup-plan", FollowupPlanStatus.ACTIVE, "follow-plan-key-1",
            null, null, null, null, null, null,
            Instant.now(), "sys", Instant.now(), "sys", "trace-123");
        FollowupTask task = new FollowupTask(1L, "TASK01", "tenant-1", "PLAN01", FollowupTaskType.QUESTIONNAIRE,
            Instant.now(), FollowupTaskStatus.COMPLETED, "nurse-1", "FOLLOWUP_NURSE", "task-key-1", Instant.now(),
            "sys", Instant.now(), "nurse-1", "trace-123");
        FollowupQuestionnaire questionnaire = new FollowupQuestionnaire(
            1L, "FQ01", "tenant-1", "PLAN01", "TASK01", "Q-TPL-1",
            "{\"title\":\"出院后症状随访\"}", null, null, "DISPATCHED",
            "questionnaire-key-1", null, "nurse-1", Instant.now(), "nurse-1", Instant.now(), "nurse-1",
            "trace-123"
        );

        when(planRepository.findByPlanId("PLAN01")).thenReturn(Optional.of(plan));
        when(taskRepository.findByTaskId("TASK01")).thenReturn(Optional.of(task));
        when(questionnaireRepository.findByQuestionnaireId("FQ01")).thenReturn(Optional.of(questionnaire));

        ApiException exception = assertThrows(ApiException.class, () -> service.backflowResult(
            new FollowupResultBackflowRequest("PLAN01", "TASK01", "FQ01", "{\"painScore\":2}", "N",
                "result-key-incomplete-questionnaire")
        ));

        assertThat(exception.errorCode()).isEqualTo(ErrorCode.ENG_FOLLOW_004);
        assertThat(exception.getMessage()).contains("随访问卷未完成");
        verify(contextSnapshotService, never()).create(any(ContextSnapshotRequest.class), any(String.class));
        verify(contextSnapshotService, never()).createBound(
            any(ContextSnapshotRequest.class), any(String.class), any(String.class));
        verify(eventRepository, never()).save(any(FollowupEvent.class));
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
            "result-key-1"
        ));

        assertThat(response.eventId()).isEqualTo("FE-RESULT-EXISTING");
        assertThat(response.contextSnapshotId()).isEqualTo("ctx-existing");
        verify(contextSnapshotService, never()).create(any(ContextSnapshotRequest.class), any(String.class));
        verify(eventRepository, never()).save(any(FollowupEvent.class));
    }
}
