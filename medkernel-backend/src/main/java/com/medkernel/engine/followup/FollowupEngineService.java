package com.medkernel.engine.followup;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ContextSnapshotRequest;
import com.medkernel.engine.context.ContextSnapshotRepository;
import com.medkernel.engine.context.ContextSnapshotResources;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.context.ContextSnapshotStatus;
import com.medkernel.engine.context.QualityStatus;
import com.medkernel.engine.context.canonical.CanonicalFollowUp;
import com.medkernel.engine.context.canonical.CanonicalPatient;
import com.medkernel.engine.pathway.ClinicalClock;
import com.medkernel.engine.pathway.ClinicalClockRepository;
import com.medkernel.engine.pathway.ClinicalClockStatus;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 随访引擎服务 (GA-ENG-API-09)。
 *
 * <p>负责随访计划生成、任务分页、问卷下发 / 作答、异常回院触发和结果回流。
 * 所有操作均绑定当前请求上下文的租户与追踪 ID；模型未接入时显式返回 {@code MODEL_DISABLED}。
 */
@Service
public class FollowupEngineService {

    private static final String SYSTEM = "system";
    private static final String FOLLOWUP_BACKFLOW_UNKNOWN_NAME = "随访回流未提供患者姓名";
    private static final String MODEL_DOWNGRADE_REASON = "MODEL_DISABLED_DETERMINISTIC_RULES";
    private static final long DEFAULT_TASK_DELAY_SECONDS = 86_400L * 7;

    private final FollowupPlanRepository planRepository;
    private final FollowupTaskRepository taskRepository;
    private final FollowupQuestionnaireRepository questionnaireRepository;
    private final FollowupEventRepository eventRepository;
    private final ContextSnapshotService contextSnapshotService;
    private final ContextSnapshotRepository contextSnapshots;
    private final ClinicalClockRepository clinicalClockRepository;
    private final FollowupTemplateService templateService;
    private final ObjectMapper json = new ObjectMapper();

    public FollowupEngineService(
        FollowupPlanRepository planRepository,
        FollowupTaskRepository taskRepository,
        FollowupQuestionnaireRepository questionnaireRepository,
        FollowupEventRepository eventRepository,
        ContextSnapshotService contextSnapshotService,
        ContextSnapshotRepository contextSnapshots,
        ClinicalClockRepository clinicalClockRepository,
        FollowupTemplateService templateService
    ) {
        this.planRepository = planRepository;
        this.taskRepository = taskRepository;
        this.questionnaireRepository = questionnaireRepository;
        this.eventRepository = eventRepository;
        this.contextSnapshotService = contextSnapshotService;
        this.contextSnapshots = contextSnapshots;
        this.clinicalClockRepository = clinicalClockRepository;
        this.templateService = templateService;
    }

    /**
     * 根据当前租户的 ACTIVE 标准上下文快照生成随访计划（幂等）。
     */
    @Transactional
    public FollowupPlanDetailResponse generatePlan(FollowupPlanGenerateRequest request) {
        if (request == null || !hasText(request.contextSnapshotId())) {
            throw new ApiException(ErrorCode.ENG_FOLLOW_004, "随访计划必须选择 ACTIVE 标准上下文快照");
        }
        RequestContext.Snapshot ctx = requireContext();
        String tenantId = ctx.orgScope().tenantId();
        var snapshot = contextSnapshots
            .findBySnapshotIdAndTenantId(request.contextSnapshotId(), tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_FOLLOW_004, "标准上下文快照不存在"));
        if (snapshot.status() != ContextSnapshotStatus.ACTIVE) {
            throw new ApiException(ErrorCode.ENG_FOLLOW_004, "随访计划仅允许使用 ACTIVE 标准上下文快照");
        }
        ContextSnapshotResponse detail = contextSnapshotService.findById(request.contextSnapshotId());
        ContextSnapshotResources resources = detail.resources();
        String diseaseCode = resources == null ? null : resources.conditions().stream()
            .filter(condition -> hasText(condition.code()))
            .map(condition -> condition.code().trim())
            .findFirst()
            .orElse(null);
        return generatePlan(new FollowupPlanCommand(
            snapshot.patientId(),
            snapshot.encounterId(),
            null,
            diseaseCode,
            request.riskLevel(),
            request.taskTypes(),
            request.idempotencyKey(),
            request.modelEnabled(),
            request.templateId()));
    }

    @Transactional
    public FollowupPlanDetailResponse generatePlanFromPathway(
            String patientId,
            String encounterId,
            String pathwayId,
            String diseaseCode,
            String riskLevel,
            List<String> taskTypes) {
        return generatePlanFromPathway(
            patientId,
            encounterId,
            pathwayId,
            diseaseCode,
            riskLevel,
            taskTypes,
            null,
            null);
    }

    FollowupPlanDetailResponse generatePlanFromPathway(
            String patientId,
            String encounterId,
            String pathwayId,
            String diseaseCode,
            String riskLevel,
            List<String> taskTypes,
            String idempotencyKey,
            Boolean modelEnabled) {
        return generatePlan(new FollowupPlanCommand(
            patientId,
            encounterId,
            pathwayId,
            diseaseCode,
            riskLevel,
            taskTypes,
            idempotencyKey,
            modelEnabled,
            null));
    }

    private FollowupPlanDetailResponse generatePlan(FollowupPlanCommand request) {
        RequestContext.Snapshot ctx = requireContext();
        String tenantId = ctx.orgScope().tenantId();
        String traceId = ctx.traceId();
        String actor = actor(ctx);
        requireControlledFacts(request);

        Optional<FollowupPlan> existingPlan = existingPlanByIdempotency(tenantId, request.idempotencyKey())
            .or(() -> existingPlanByPathway(tenantId, request.pathwayId()));
        if (existingPlan.isPresent()) {
            return toDetailResponse(existingPlan.get());
        }

        Instant now = Instant.now();
        ControlledPlan controlledPlan = resolveControlledPlan(request, tenantId);
        FollowupPlan plan = new FollowupPlan(
            null,
            "fp-" + UUID.randomUUID(),
            tenantId,
            request.patientId(),
            request.encounterId(),
            request.pathwayId(),
            request.diseaseCode(),
            request.riskLevel(),
            FollowupPlanStatus.ACTIVE,
            blankToNull(request.idempotencyKey()),
            controlledPlan.sourceFactType(),
            controlledPlan.sourceFactId(),
            controlledPlan.ruleCode(),
            controlledPlan.explanation(),
            controlledPlan.templateId(),
            controlledPlan.templateVersion(),
            now,
            actor,
            now,
            actor,
            traceId
        );
        plan = planRepository.save(plan);

        int index = 0;
        List<FollowupTaskDetailResponse> taskResponses = new java.util.ArrayList<>();
        for (ResolvedTask resolvedTask : controlledPlan.tasks()) {
            Instant taskNow = Instant.now();
            Instant dueDate = dueDate(controlledPlan, resolvedTask, taskNow);
            FollowupTask task = new FollowupTask(
                null,
                "ft-" + UUID.randomUUID(),
                tenantId,
                plan.planId(),
                resolvedTask.taskType(),
                dueDate,
                FollowupTaskStatus.PENDING,
                null,
                null,
                taskIdempotencyKey(request.idempotencyKey(), index++),
                controlledPlan.clinicalClockId(),
                resolvedTask.questionnaireTemplateId(),
                taskNow,
                actor,
                taskNow,
                actor,
                traceId
            );
            task = taskRepository.save(task);
            taskResponses.add(toTaskResponse(task));
        }

        return new FollowupPlanDetailResponse(
            plan.planId(),
            plan.tenantId(),
            plan.patientId(),
            plan.encounterId(),
            plan.diseaseCode(),
            plan.status(),
            taskResponses,
            FollowupModelStatus.MODEL_DISABLED,
            plan.sourceFactType(),
            plan.sourceFactId(),
            plan.generationRuleCode(),
            plan.generationExplanation(),
            plan.templateId(),
            plan.templateVersion()
        );
    }

    /**
     * 分页查询随访任务列表。
     */
    @Transactional(readOnly = true)
    public PageResponse<FollowupTaskDetailResponse> listTasks(FollowupTaskFilter filter, PageRequest pageRequest) {
        RequestContext.Snapshot ctx = requireContext();
        PageRequest req = pageRequest == null ? PageRequest.defaults() : pageRequest;
        FollowupTaskFilter f = filter == null ? new FollowupTaskFilter(null, null, null) : filter;
        String status = f.status() == null ? null : f.status().name();
        long total = taskRepository.countByTenantIdAndFilters(
            ctx.orgScope().tenantId(), blankToNull(f.patientId()), blankToNull(f.planId()), status);
        List<FollowupTaskDetailResponse> rows = taskRepository.pageByTenantIdAndFilters(
                ctx.orgScope().tenantId(), blankToNull(f.patientId()), blankToNull(f.planId()), status,
                req.offset(), req.safeSize())
            .stream()
            .map(this::toTaskResponse)
            .toList();
        return PageResponse.of(rows, req, total);
    }

    /**
     * 读取当前租户作用域下的随访全局进度统计。
     */
    @Transactional(readOnly = true)
    public FollowupStatsResponse stats(String patientId) {
        RequestContext.Snapshot ctx = requireContext();
        String tenantId = ctx.orgScope().tenantId();
        String normalizedPatientId = blankToNull(patientId);
        long totalPlans = planRepository.countByTenantIdAndOptionalPatient(tenantId, normalizedPatientId);
        long activePlans = planRepository.countByTenantIdAndOptionalPatientAndStatus(
            tenantId, normalizedPatientId, FollowupPlanStatus.ACTIVE.name());
        long totalTasks = taskRepository.countByTenantIdAndPatientAndOptionalStatus(
            tenantId, normalizedPatientId, null);
        long completedTasks = taskRepository.countByTenantIdAndPatientAndOptionalStatus(
            tenantId, normalizedPatientId, FollowupTaskStatus.COMPLETED.name());
        long abnormalReturnTasks = taskRepository.countByTenantIdAndPatientAndOptionalStatus(
            tenantId, normalizedPatientId, FollowupTaskStatus.ABNORMAL_RETURN.name());
        return new FollowupStatsResponse(
            totalPlans,
            activePlans,
            totalTasks,
            completedTasks,
            abnormalReturnTasks,
            percent(completedTasks, totalTasks),
            percent(abnormalReturnTasks, totalTasks),
            ctx.traceId()
        );
    }

    /**
     * 顶层问卷下发 / 作答入口，按幂等键复用已有问卷事实。
     */
    @Transactional
    public FollowupQuestionnaireResponse dispatchQuestionnaire(FollowupQuestionnaireRequest request) {
        RequestContext.Snapshot ctx = requireContext();
        String tenantId = ctx.orgScope().tenantId();
        String formData = normalizeJsonObject(request.formData(), "问卷模板载荷");
        String answerData = normalizeOptionalJsonObject(request.answerData(), "问卷作答载荷");
        Optional<FollowupQuestionnaire> existing =
            existingQuestionnaireByIdempotency(tenantId, request.idempotencyKey());
        if (existing.isPresent()) {
            return toQuestionnaireResponse(existing.get());
        }

        FollowupTask task = findTask(request.taskId(), tenantId);
        Instant now = Instant.now();
        String actor = firstNonBlank(request.executorId(), actor(ctx));
        boolean completed = hasText(answerData);
        FollowupQuestionnaire questionnaire = questionnaireRepository.save(new FollowupQuestionnaire(
            null,
            "fq-" + UUID.randomUUID(),
            tenantId,
            task.planId(),
            task.taskId(),
            request.questionnaireTemplateId(),
            formData,
            answerData,
            request.score(),
            completed ? "COMPLETED" : "DISPATCHED",
            request.idempotencyKey(),
            completed ? now : null,
            actor,
            now,
            actor,
            now,
            actor,
            ctx.traceId()
        ));

        taskRepository.save(rewriteTask(
            task,
            completed ? FollowupTaskStatus.COMPLETED : FollowupTaskStatus.IN_PROGRESS,
            actor,
            request.executorType(),
            now,
            ctx.traceId()
        ));
        return toQuestionnaireResponse(questionnaire);
    }

    /**
     * 上报异常回院事件，同时生成返院任务和站内通知请求事件。
     */
    @Transactional
    public FollowupAbnormalReportResponse reportAbnormal(FollowupAbnormalReportRequest request) {
        RequestContext.Snapshot ctx = requireContext();
        String tenantId = ctx.orgScope().tenantId();
        String traceId = ctx.traceId();
        String actor = firstNonBlank(request.triggeredBy(), actor(ctx));
        String idempotencyKey = blankToNull(request.idempotencyKey());
        ensureAbnormalReportType(request.eventType());
        JsonNode abnormalPayload = requireJsonObjectPayload(request.payload(), "异常回院事件载荷");

        if (idempotencyKey != null) {
            Optional<FollowupEvent> existingEvent = eventRepository.findByTenantIdAndEventTypeAndIdempotencyKey(
                tenantId, FollowupEventType.ABNORMAL_RETURN, idempotencyKey);
            Optional<FollowupTask> existingTask = taskRepository.findByTenantIdAndIdempotencyKey(
                tenantId, abnormalTaskKey(idempotencyKey));
            Optional<FollowupEvent> existingNotification = eventRepository.findByTenantIdAndEventTypeAndIdempotencyKey(
                tenantId, FollowupEventType.NOTIFICATION_REQUESTED, notificationKey(idempotencyKey));
            if (existingEvent.isPresent() && existingTask.isPresent()) {
                return new FollowupAbnormalReportResponse(
                    existingEvent.get().eventId(),
                    existingTask.get().taskId(),
                    existingNotification.map(FollowupEvent::eventId).orElse(null),
                    traceId
                );
            }
        }

        FollowupPlan plan = findPlan(request.planId(), tenantId);
        Instant now = Instant.now();
        FollowupEvent abnormalEvent = eventRepository.save(new FollowupEvent(
            null,
            "fe-" + UUID.randomUUID(),
            tenantId,
            plan.planId(),
            FollowupEventType.ABNORMAL_RETURN,
            writeJson(abnormalPayload),
            actor,
            idempotencyKey,
            now,
            actor,
            now,
            actor,
            traceId
        ));

        FollowupTask returnTask = taskRepository.save(new FollowupTask(
            null,
            "ft-" + UUID.randomUUID(),
            tenantId,
            plan.planId(),
            FollowupTaskType.RETURN_VISIT,
            now,
            FollowupTaskStatus.ABNORMAL_RETURN,
            null,
            null,
            idempotencyKey == null ? null : abnormalTaskKey(idempotencyKey),
            null,
            now,
            actor,
            now,
            actor,
            traceId
        ));

        FollowupEvent notification = eventRepository.save(new FollowupEvent(
            null,
            "fe-" + UUID.randomUUID(),
            tenantId,
            plan.planId(),
            FollowupEventType.NOTIFICATION_REQUESTED,
            notificationPayload(returnTask, plan, abnormalEvent, abnormalPayload),
            actor,
            idempotencyKey == null ? null : notificationKey(idempotencyKey),
            now,
            actor,
            now,
            actor,
            traceId
        ));
        return new FollowupAbnormalReportResponse(
            abnormalEvent.eventId(), returnTask.taskId(), notification.eventId(), traceId);
    }

    /**
     * 随访结果回流到标准临床上下文。
     */
    @Transactional
    public FollowupResultBackflowResponse backflowResult(FollowupResultBackflowRequest request) {
        RequestContext.Snapshot ctx = requireContext();
        String tenantId = ctx.orgScope().tenantId();
        String traceId = ctx.traceId();
        String actor = actor(ctx);
        JsonNode resultPayload = requireJsonObjectPayload(request.resultPayload(), "随访结果回流载荷");
        String idempotencyKey = blankToNull(request.idempotencyKey());
        if (idempotencyKey != null) {
            Optional<FollowupEvent> existingEvent = eventRepository.findByTenantIdAndEventTypeAndIdempotencyKey(
                tenantId, FollowupEventType.RESULT_INFLOW, idempotencyKey);
            if (existingEvent.isPresent()) {
                return new FollowupResultBackflowResponse(
                    existingEvent.get().eventId(), contextSnapshotIdFromPayload(existingEvent.get().payload()), traceId);
            }
        }
        FollowupPlan plan = findPlan(request.planId(), tenantId);
        FollowupTask task = findTask(request.taskId(), tenantId);
        FollowupQuestionnaire questionnaire = questionnaireRepository.findByQuestionnaireId(request.questionnaireId())
            .orElseThrow(() -> ApiException.notFound("随访问卷 " + request.questionnaireId()));
        ensureTenant(tenantId, questionnaire.tenantId());
        if (!plan.planId().equals(task.planId()) || !task.taskId().equals(questionnaire.taskId())) {
            throw new ApiException(ErrorCode.ENG_FOLLOW_004, "随访结果引用关系不一致");
        }

        ContextSnapshotResponse snapshot = contextSnapshotService.create(
            contextBackflowRequest(plan, task, questionnaire, request, ctx),
            request.idempotencyKey()
        );
        Instant now = Instant.now();
        FollowupEvent event = eventRepository.save(new FollowupEvent(
            null,
            "fe-" + UUID.randomUUID(),
            tenantId,
            plan.planId(),
            FollowupEventType.RESULT_INFLOW,
            resultPayload(request, snapshot.snapshotId(), resultPayload),
            actor,
            request.idempotencyKey(),
            now,
            actor,
            now,
            actor,
            traceId
        ));
        return new FollowupResultBackflowResponse(event.eventId(), snapshot.snapshotId(), traceId);
    }

    /**
     * 获取随访计划详情，包含下属任务列表。
     */
    @Transactional(readOnly = true)
    public FollowupPlanDetailResponse getPlanDetail(String planId) {
        RequestContext.Snapshot ctx = requireContext();
        return toDetailResponse(findPlan(planId, ctx.orgScope().tenantId()));
    }

    /**
     * 分页查询随访计划列表。
     */
    @Transactional(readOnly = true)
    public PageResponse<FollowupPlanDetailResponse> listPlans(String patientId, PageRequest pageRequest) {
        RequestContext.Snapshot ctx = requireContext();
        String tenantId = ctx.orgScope().tenantId();

        PageRequest req = pageRequest == null ? PageRequest.defaults() : pageRequest;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
            req.safePage() - 1,
            req.safeSize()
        );

        org.springframework.data.domain.Page<FollowupPlan> pageResult;
        if (hasText(patientId)) {
            pageResult = planRepository.findByTenantIdAndPatientId(tenantId, patientId, pageable);
        } else {
            pageResult = planRepository.findByTenantId(tenantId, pageable);
        }

        List<FollowupPlanDetailResponse> list = pageResult.getContent().stream()
            .map(this::toDetailResponse)
            .toList();
        return PageResponse.of(list, req, pageResult.getTotalElements());
    }

    private Optional<FollowupPlan> existingPlanByIdempotency(String tenantId, String idempotencyKey) {
        if (!hasText(idempotencyKey)) {
            return Optional.empty();
        }
        return planRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
    }

    private Optional<FollowupPlan> existingPlanByPathway(String tenantId, String pathwayId) {
        if (!hasText(pathwayId)) {
            return Optional.empty();
        }
        return planRepository.findByTenantIdAndPathwayId(tenantId, pathwayId);
    }

    private Optional<FollowupQuestionnaire> existingQuestionnaireByIdempotency(
            String tenantId, String idempotencyKey) {
        if (!hasText(idempotencyKey)) {
            return Optional.empty();
        }
        return questionnaireRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
    }

    private FollowupPlan findPlan(String planId, String tenantId) {
        FollowupPlan plan = planRepository.findByPlanId(planId)
            .orElseThrow(() -> ApiException.notFound("随访计划 " + planId));
        ensureTenant(tenantId, plan.tenantId());
        return plan;
    }

    private FollowupTask findTask(String taskId, String tenantId) {
        FollowupTask task = taskRepository.findByTaskId(taskId)
            .orElseThrow(() -> ApiException.notFound("随访任务 " + taskId));
        ensureTenant(tenantId, task.tenantId());
        return task;
    }

    private FollowupTask rewriteTask(
            FollowupTask task,
            FollowupTaskStatus status,
            String executorId,
            String executorType,
            Instant now,
            String traceId) {
        return new FollowupTask(
            task.id(),
            task.taskId(),
            task.tenantId(),
            task.planId(),
            task.taskType(),
            task.dueDate(),
            status,
            executorId,
            executorType,
            task.idempotencyKey(),
            task.clinicalClockId(),
            task.createdAt(),
            task.createdBy(),
            now,
            firstNonBlank(executorId, SYSTEM),
            traceId
        );
    }

    private void ensureAbnormalReportType(FollowupEventType eventType) {
        if (eventType != FollowupEventType.ABNORMAL_RETURN) {
            throw new ApiException(ErrorCode.ENG_FOLLOW_004, "异常回院上报仅允许 ABNORMAL_RETURN 事件");
        }
    }

    private double percent(long numerator, long denominator) {
        if (denominator <= 0L) {
            return 0.0;
        }
        return Math.round((numerator * 1000.0) / denominator) / 10.0;
    }

    private ControlledPlan resolveControlledPlan(FollowupPlanCommand request, String tenantId) {
        requireControlledFacts(request);
        String sourceFactType;
        String sourceFactId;
        if (hasText(request.pathwayId())) {
            sourceFactType = "PATHWAY";
            sourceFactId = request.pathwayId();
        } else if (hasText(request.diseaseCode())) {
            sourceFactType = "DIAGNOSIS";
            sourceFactId = request.diseaseCode();
        } else {
            sourceFactType = "RISK";
            sourceFactId = request.riskLevel();
        }

        Optional<ClinicalClock> clock = controlledClock(request.pathwayId(), tenantId);
        FollowupTemplate template = hasText(request.templateId())
            ? templateService.requirePublished(request.templateId())
            : null;
        List<ResolvedTask> tasks = resolveTasks(request, template);
        String riskBucket = "HIGH".equalsIgnoreCase(blankToNull(request.riskLevel())) ? "HIGH" : "STANDARD";
        String ruleCode = template == null
            ? "CONTROLLED_FACT_" + sourceFactType + "_" + riskBucket
            : "FOLLOWUP_TEMPLATE_" + template.templateCode() + "_V" + template.versionNo();
        String explanation = controlledExplanation(
            request,
            sourceFactType,
            sourceFactId,
            ruleCode,
            tasks,
            clock,
            template
        );
        return new ControlledPlan(
            sourceFactType,
            sourceFactId,
            ruleCode,
            explanation,
            tasks,
            clock.map(ClinicalClock::dueAt).orElse(null),
            clock.map(ClinicalClock::clockId).orElse(null),
            template == null ? null : template.templateId(),
            template == null ? null : template.versionNo()
        );
    }

    private void requireControlledFacts(FollowupPlanCommand request) {
        boolean hasPathway = hasText(request.pathwayId());
        boolean hasDiagnosis = hasText(request.diseaseCode());
        boolean hasRisk = hasText(request.riskLevel());
        if (!hasPathway && !hasDiagnosis && !hasRisk) {
            throw new ApiException(ErrorCode.ENG_FOLLOW_004, "随访计划生成缺少路径、诊断或风险分层受控事实");
        }
    }

    private Optional<ClinicalClock> controlledClock(String pathwayId, String tenantId) {
        if (!hasText(pathwayId)) {
            return Optional.empty();
        }
        List<ClinicalClock> clocks = clinicalClockRepository
            .findByPatientPathwayIdAndTenantIdOrderByStartedAtAsc(pathwayId, tenantId);
        Optional<ClinicalClock> runningClock = clocks.stream()
            .filter(clock -> clock.status() == ClinicalClockStatus.RUNNING)
            .filter(clock -> clock.dueAt() != null)
            .findFirst();
        return runningClock.or(() -> clocks.stream().filter(clock -> clock.dueAt() != null).findFirst());
    }

    private List<FollowupTaskType> resolveTaskTypes(FollowupPlanCommand request) {
        List<FollowupTaskType> explicit = request.taskTypes().stream()
            .filter(FollowupEngineService::hasText)
            .map(this::parseTaskType)
            .distinct()
            .toList();
        if (!explicit.isEmpty()) {
            return explicit;
        }
        List<FollowupTaskType> derived = new java.util.ArrayList<>();
        derived.add(FollowupTaskType.QUESTIONNAIRE);
        if ("HIGH".equalsIgnoreCase(blankToNull(request.riskLevel()))) {
            derived.add(FollowupTaskType.OUTPATIENT);
        }
        return List.copyOf(derived);
    }

    private List<ResolvedTask> resolveTasks(FollowupPlanCommand request, FollowupTemplate template) {
        if (template != null) {
            return templateService.tasks(template).stream()
                .map(task -> new ResolvedTask(
                    task.taskType(),
                    task.delayDays(),
                    blankToNull(task.questionnaireTemplateId())
                ))
                .toList();
        }
        return resolveTaskTypes(request).stream()
            .map(type -> new ResolvedTask(
                type,
                null,
                type == FollowupTaskType.QUESTIONNAIRE ? "FOLLOWUP_QUESTIONNAIRE_DEFAULT" : null
            ))
            .toList();
    }

    private Instant dueDate(ControlledPlan controlledPlan, ResolvedTask task, Instant taskNow) {
        if (task.delayDays() != null) {
            Instant base = controlledPlan.dueAt() == null ? taskNow : controlledPlan.dueAt();
            return base.plusSeconds(task.delayDays() * 86_400L);
        }
        return controlledPlan.dueAt() == null
            ? taskNow.plusSeconds(DEFAULT_TASK_DELAY_SECONDS)
            : controlledPlan.dueAt();
    }

    private FollowupTaskType parseTaskType(String type) {
        try {
            return FollowupTaskType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.ENG_FOLLOW_004, "不支持的随访任务类型: " + type);
        }
    }

    private String controlledExplanation(
            FollowupPlanCommand request,
            String sourceFactType,
            String sourceFactId,
            String ruleCode,
            List<ResolvedTask> tasks,
            Optional<ClinicalClock> clock,
            FollowupTemplate template) {
        Map<String, Object> explanation = new LinkedHashMap<>();
        explanation.put("sourceFactType", sourceFactType);
        explanation.put("sourceFactId", sourceFactId);
        explanation.put("generationRuleCode", ruleCode);
        explanation.put("modelStatus", FollowupModelStatus.MODEL_DISABLED.name());
        if (request.modelEnabled() != null) {
            explanation.put("requestedModelEnabled", request.modelEnabled());
        }
        if (Boolean.TRUE.equals(request.modelEnabled())) {
            explanation.put("modelDowngradeReason", MODEL_DOWNGRADE_REASON);
        }
        if (hasText(request.diseaseCode())) {
            explanation.put("diseaseCode", request.diseaseCode());
        }
        if (hasText(request.riskLevel())) {
            explanation.put("riskLevel", request.riskLevel());
        }
        if (!request.taskTypes().isEmpty()) {
            explanation.put("requestedTaskTypes", request.taskTypes());
        }
        explanation.put("generatedTaskTypes", tasks.stream()
            .map(task -> task.taskType().name())
            .toList());
        if (template != null) {
            explanation.put("templateId", template.templateId());
            explanation.put("templateVersion", template.versionNo());
            explanation.put("templateCode", template.templateCode());
        }
        clock.ifPresent(value -> {
            explanation.put("clinicalClockId", value.clockId());
            explanation.put("clinicalClockDueAt", value.dueAt().toString());
        });
        return writeJson(explanation);
    }

    private FollowupPlanDetailResponse toDetailResponse(FollowupPlan plan) {
        List<FollowupTaskDetailResponse> taskResponses = taskRepository
            .findByTenantIdAndPlanId(plan.tenantId(), plan.planId())
            .stream()
            .map(this::toTaskResponse)
            .toList();

        return new FollowupPlanDetailResponse(
            plan.planId(),
            plan.tenantId(),
            plan.patientId(),
            plan.encounterId(),
            plan.diseaseCode(),
            plan.status(),
            taskResponses,
            FollowupModelStatus.MODEL_DISABLED,
            plan.sourceFactType(),
            plan.sourceFactId(),
            plan.generationRuleCode(),
            plan.generationExplanation(),
            plan.templateId(),
            plan.templateVersion()
        );
    }

    private FollowupTaskDetailResponse toTaskResponse(FollowupTask task) {
        return new FollowupTaskDetailResponse(
            task.taskId(),
            task.planId(),
            task.taskType(),
            task.dueDate(),
            task.status(),
            task.executorId(),
            task.executorType(),
            task.clinicalClockId(),
            questionnaireTemplateId(task)
        );
    }

    private String questionnaireTemplateId(FollowupTask task) {
        if (hasText(task.questionnaireTemplateId())) {
            return task.questionnaireTemplateId();
        }
        return task.taskType() == FollowupTaskType.QUESTIONNAIRE
            ? "FOLLOWUP_QUESTIONNAIRE_DEFAULT"
            : null;
    }

    private FollowupQuestionnaireResponse toQuestionnaireResponse(FollowupQuestionnaire questionnaire) {
        return new FollowupQuestionnaireResponse(
            questionnaire.questionnaireId(),
            questionnaire.taskId(),
            questionnaire.questionnaireTemplateId(),
            questionnaire.status(),
            questionnaire.traceId()
        );
    }

    private ContextSnapshotRequest contextBackflowRequest(
            FollowupPlan plan,
            FollowupTask task,
            FollowupQuestionnaire questionnaire,
            FollowupResultBackflowRequest request,
            RequestContext.Snapshot ctx) {
        Instant now = Instant.now();
        CanonicalPatient patient = new CanonicalPatient(
            plan.patientId(),
            FOLLOWUP_BACKFLOW_UNKNOWN_NAME,
            null,
            null,
            List.of(),
            "FOLLOWUP",
            plan.patientId(),
            request.packageVersion(),
            now,
            now,
            QualityStatus.PARTIAL
        );
        CanonicalFollowUp followUp = new CanonicalFollowUp(
            questionnaire.questionnaireId(),
            plan.diseaseCode() == null ? "FOLLOWUP_RESULT" : plan.diseaseCode(),
            task.dueDate() == null ? now : task.dueDate(),
            questionnaire.questionnaireTemplateId(),
            firstNonBlank(request.abnormalFlag(), "N"),
            "FOLLOWUP",
            questionnaire.questionnaireId(),
            request.packageVersion(),
            now,
            now,
            QualityStatus.VALID
        );
        ContextSnapshotResources resources = new ContextSnapshotResources(
            patient,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(followUp),
            List.of()
        );
        OrgScope scope = ctx.orgScope();
        return new ContextSnapshotRequest(
            request.idempotencyKey(),
            ctx.traceId(),
            scope.tenantId(),
            scope.groupId(),
            scope.hospitalId(),
            scope.campusId(),
            scope.siteId(),
            scope.departmentId(),
            scope.specialtyId(),
            ctx.userId(),
            List.of(),
            plan.patientId(),
            plan.encounterId(),
            scope.nearestOrgUnitIdOrTenant(scope.tenantId()),
            request.packageVersion(),
            resources
        );
    }

    private String resultPayload(FollowupResultBackflowRequest request, String snapshotId, JsonNode resultPayload) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("questionnaireId", request.questionnaireId());
        payload.put("contextSnapshotId", snapshotId);
        payload.put("abnormalFlag", firstNonBlank(request.abnormalFlag(), "N"));
        payload.put("resultPayload", resultPayload);
        return writeJson(payload);
    }

    private String notificationPayload(
            FollowupTask returnTask,
            FollowupPlan plan,
            FollowupEvent abnormalEvent,
            JsonNode abnormalPayload) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("returnTaskId", returnTask.taskId());
        payload.put("patientId", plan.patientId());
        payload.put("encounterId", plan.encounterId());
        payload.put("sourceEventId", abnormalEvent.eventId());
        payload.put("abnormalPayload", abnormalPayload);
        return writeJson(payload);
    }

    private String contextSnapshotIdFromPayload(String payload) {
        JsonNode node = requireJsonObjectPayload(payload, "随访回流事件载荷");
        JsonNode snapshotId = node.get("contextSnapshotId");
        if (snapshotId == null || !snapshotId.isTextual() || !hasText(snapshotId.asText())) {
            throw new ApiException(ErrorCode.ENG_FOLLOW_004, "已有随访回流事件缺少上下文快照 ID");
        }
        return snapshotId.asText();
    }

    private String normalizeOptionalJsonObject(String payload, String label) {
        if (!hasText(payload)) {
            return null;
        }
        return normalizeJsonObject(payload, label);
    }

    private String normalizeJsonObject(String payload, String label) {
        return writeJson(requireJsonObjectPayload(payload, label));
    }

    private JsonNode requireJsonObjectPayload(String payload, String label) {
        if (!hasText(payload)) {
            throw new ApiException(ErrorCode.ENG_FOLLOW_004, label + "必须是 JSON 对象");
        }
        try {
            JsonNode node = json.readTree(payload);
            if (node == null || !node.isObject()) {
                throw new ApiException(ErrorCode.ENG_FOLLOW_004, label + "必须是 JSON 对象");
            }
            return node;
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.ENG_FOLLOW_004, label + "必须是 JSON 对象");
        }
    }

    private RequestContext.Snapshot requireContext() {
        RequestContext.Snapshot ctx = RequestContext.snapshot();
        if (ctx.orgScope() == null || !ctx.orgScope().hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return ctx;
    }

    private void ensureTenant(String expectedTenantId, String actualTenantId) {
        if (expectedTenantId != null && !expectedTenantId.equals(actualTenantId)) {
            throw ApiException.forbidden("无权访问该租户数据");
        }
    }

    private String actor(RequestContext.Snapshot ctx) {
        return firstNonBlank(ctx.userId(), SYSTEM);
    }

    private String taskIdempotencyKey(String idempotencyKey, int index) {
        return hasText(idempotencyKey) ? "task:" + idempotencyKey + ":" + index : null;
    }

    private String abnormalTaskKey(String idempotencyKey) {
        return "abnormal-task:" + idempotencyKey;
    }

    private String notificationKey(String idempotencyKey) {
        return "notification:" + idempotencyKey;
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("随访事件载荷序列化失败", e);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return hasText(value) ? value : null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private record ControlledPlan(
        String sourceFactType,
        String sourceFactId,
        String ruleCode,
        String explanation,
        List<ResolvedTask> tasks,
        Instant dueAt,
        String clinicalClockId,
        String templateId,
        Integer templateVersion
    ) {}

    private record ResolvedTask(
        FollowupTaskType taskType,
        Integer delayDays,
        String questionnaireTemplateId
    ) {}
}
