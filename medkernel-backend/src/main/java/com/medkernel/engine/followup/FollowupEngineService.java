package com.medkernel.engine.followup;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.RequestContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 随访引擎服务 (GA-ENG-API-09)。
 *
 * <p>负责随访计划生成、问卷提交、异常回院上报与计划详情查询。
 * 所有操作均绑定当前请求上下文的租户与追踪 ID。
 */
@Service
public class FollowupEngineService {

    private final FollowupPlanRepository planRepository;
    private final FollowupTaskRepository taskRepository;
    private final FollowupQuestionnaireRepository questionnaireRepository;
    private final FollowupEventRepository eventRepository;

    public FollowupEngineService(
        FollowupPlanRepository planRepository,
        FollowupTaskRepository taskRepository,
        FollowupQuestionnaireRepository questionnaireRepository,
        FollowupEventRepository eventRepository
    ) {
        this.planRepository = planRepository;
        this.taskRepository = taskRepository;
        this.questionnaireRepository = questionnaireRepository;
        this.eventRepository = eventRepository;
    }

    /**
     * 根据出院事件、病种、风险分层生成随访计划（幂等）。
     *
     * @param request 计划生成请求
     * @return 计划详情响应（含下属任务列表）
     */
    @Transactional
    public FollowupPlanDetailResponse generatePlan(FollowupPlanGenerateRequest request) {
        RequestContext.Snapshot ctx = RequestContext.snapshot();
        String tenantId = ctx.orgScope().tenantId();
        String traceId = ctx.traceId();
        Optional<FollowupPlan> existingPlan = existingPlanByPathway(tenantId, request.pathwayId());
        if (existingPlan.isPresent()) {
            return toDetailResponse(existingPlan.get());
        }

        Instant now = Instant.now();
        FollowupPlan plan = new FollowupPlan(
            null,
            UUID.randomUUID().toString(),
            tenantId,
            request.patientId(),
            request.encounterId(),
            request.pathwayId(),
            request.diseaseCode(),
            request.riskLevel(),
            FollowupPlanStatus.ACTIVE,
            now,
            "system",
            now,
            "system",
            traceId
        );
        plan = planRepository.save(plan);

        List<FollowupTaskDetailResponse> taskResponses = new ArrayList<>();
        if (request.taskTypes() != null) {
            for (String typeStr : request.taskTypes()) {
                FollowupTaskType taskType = FollowupTaskType.valueOf(typeStr);
                Instant taskNow = Instant.now();
                FollowupTask task = new FollowupTask(
                    null,
                    UUID.randomUUID().toString(),
                    tenantId,
                    plan.planId(),
                    taskType,
                    taskNow.plusSeconds(86400 * 7),
                    FollowupTaskStatus.PENDING,
                    null,
                    null,
                    taskNow,
                    "system",
                    taskNow,
                    "system",
                    traceId
                );
                task = taskRepository.save(task);
                taskResponses.add(new FollowupTaskDetailResponse(
                    task.taskId(),
                    task.taskType(),
                    task.dueDate(),
                    task.status()
                ));
            }
        }

        return new FollowupPlanDetailResponse(
            plan.planId(),
            plan.tenantId(),
            plan.patientId(),
            plan.encounterId(),
            plan.diseaseCode(),
            plan.status(),
            taskResponses
        );
    }

    /**
     * 提交随访问卷数据，标记对应任务为已完成。
     *
     * @param taskId  任务业务 ID
     * @param request 问卷提交请求
     */
    @Transactional
    public void submitQuestionnaire(String taskId, FollowupQuestionnaireSubmitRequest request) {
        RequestContext.Snapshot ctx = RequestContext.snapshot();
        String tenantId = ctx.orgScope().tenantId();
        String traceId = ctx.traceId();
        
        FollowupTask task = taskRepository.findByTaskId(taskId)
            .orElseThrow(() -> ApiException.notFound("随访任务 " + taskId));
        
        if (tenantId != null && !tenantId.equals(task.tenantId())) {
            throw ApiException.forbidden("无权访问该租户数据");
        }

        FollowupQuestionnaire q = new FollowupQuestionnaire(
            null,
            UUID.randomUUID().toString(),
            tenantId,
            taskId,
            request.formData(),
            null,
            "COMPLETED",
            Instant.now(),
            request.executorId() != null ? request.executorId() : "system",
            Instant.now(),
            request.executorId() != null ? request.executorId() : "system",
            traceId
        );
        questionnaireRepository.save(q);

        FollowupTask updatedTask = new FollowupTask(
            task.id(),
            task.taskId(),
            task.tenantId(),
            task.planId(),
            task.taskType(),
            task.dueDate(),
            FollowupTaskStatus.COMPLETED,
            request.executorId(),
            request.executorType(),
            task.createdAt(),
            task.createdBy(),
            Instant.now(),
            request.executorId() != null ? request.executorId() : "system",
            traceId
        );
        taskRepository.save(updatedTask);
    }

    /**
     * 上报异常回院事件。
     *
     * @param request 异常上报请求
     */
    @Transactional
    public void reportAbnormal(FollowupAbnormalReportRequest request) {
        RequestContext.Snapshot ctx = RequestContext.snapshot();
        String tenantId = ctx.orgScope().tenantId();
        String traceId = ctx.traceId();
        
        FollowupEvent event = new FollowupEvent(
            null,
            UUID.randomUUID().toString(),
            tenantId,
            request.planId(),
            request.eventType(),
            request.payload(),
            request.triggeredBy(),
            Instant.now(),
            "system",
            Instant.now(),
            "system",
            traceId
        );
        eventRepository.save(event);
    }

    /**
     * 获取随访计划详情，包含下属任务列表。
     *
     * @param planId 计划业务 ID
     * @return 计划详情响应
     */
    @Transactional(readOnly = true)
    public FollowupPlanDetailResponse getPlanDetail(String planId) {
        RequestContext.Snapshot ctx = RequestContext.snapshot();
        String tenantId = ctx.orgScope().tenantId();
        FollowupPlan plan = planRepository.findByPlanId(planId)
            .orElseThrow(() -> ApiException.notFound("随访计划 " + planId));

        if (tenantId != null && !tenantId.equals(plan.tenantId())) {
            throw ApiException.forbidden("无权访问该租户数据");
        }

        return toDetailResponse(plan);
    }

    /**
     * 分页查询随访计划列表。
     *
     * @param patientId   患者 ID（可选）
     * @param pageRequest 分页参数
     * @return 分页计划详情列表
     */
    @Transactional(readOnly = true)
    public PageResponse<FollowupPlanDetailResponse> listPlans(String patientId, PageRequest pageRequest) {
        RequestContext.Snapshot ctx = RequestContext.snapshot();
        String tenantId = ctx.orgScope().tenantId();
        
        PageRequest req = pageRequest == null ? PageRequest.defaults() : pageRequest;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
            req.page() - 1,
            req.safeSize()
        );

        org.springframework.data.domain.Page<FollowupPlan> pageResult;
        if (patientId != null && !patientId.isBlank()) {
            pageResult = planRepository.findByTenantIdAndPatientId(tenantId, patientId, pageable);
        } else {
            pageResult = planRepository.findByTenantId(tenantId, pageable);
        }

        List<FollowupPlanDetailResponse> list = pageResult.getContent().stream()
            .map(this::toDetailResponse)
            .collect(Collectors.toList());

        return PageResponse.of(list, req, pageResult.getTotalElements());
    }

    private Optional<FollowupPlan> existingPlanByPathway(String tenantId, String pathwayId) {
        if (pathwayId == null || pathwayId.isBlank()) {
            return Optional.empty();
        }
        Optional<FollowupPlan> result = planRepository.findByTenantIdAndPathwayId(tenantId, pathwayId);
        return result == null ? Optional.empty() : result;
    }

    private FollowupPlanDetailResponse toDetailResponse(FollowupPlan plan) {
        List<FollowupTask> tasks = taskRepository.findByTenantIdAndPlanId(plan.tenantId(), plan.planId());
        List<FollowupTaskDetailResponse> taskResponses = tasks.stream()
            .map(t -> new FollowupTaskDetailResponse(t.taskId(), t.taskType(), t.dueDate(), t.status()))
            .collect(Collectors.toList());

        return new FollowupPlanDetailResponse(
            plan.planId(),
            plan.tenantId(),
            plan.patientId(),
            plan.encounterId(),
            plan.diseaseCode(),
            plan.status(),
            taskResponses
        );
    }
}
