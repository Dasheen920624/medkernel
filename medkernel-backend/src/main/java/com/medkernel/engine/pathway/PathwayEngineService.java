package com.medkernel.engine.pathway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ClinicalEventContext;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEventPublisher;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.observability.DiagnoseResponse;
import com.medkernel.shared.observability.DiagnoseResponseAssembler;
import com.medkernel.shared.observability.PayloadRef;
import com.medkernel.shared.observability.StateTransitionRecorder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 路径引擎应用服务（GA-ENG-API-06 专病包 + 路径模板 + 患者路径实例 + 确定性推进）。
 *
 * <p>聚合专病包、专病画像、路径模板、节点、边、患者路径、变异、关键时钟和指标绑定九类数据，
 * 承担：
 * <ul>
 *   <li>专病路径资产的草稿创建、模板发布门禁和版本化查询；</li>
 *   <li>基于已发布模板创建患者路径实例并初始化节点关键时钟；</li>
 *   <li>按确定性推进器处理完成、变异和退出事件，并保存审计事实；</li>
 *   <li>输出试运行轨迹和诊断解释，支撑后续路径画布与临床嵌入式提醒。</li>
 * </ul>
 * 所有读写均按当前租户隔离，写动作发布审计事件并记录状态迁移。
 */
@Service
public class PathwayEngineService {

    private static final String PACKAGE_ENTITY = "specialty_package";
    private static final String TEMPLATE_ENTITY = "pathway_template";
    private static final String PATIENT_PATHWAY_ENTITY = "patient_pathway";

    private final SpecialtyPackageRepository packages;
    private final SpecialtyProfileRepository profiles;
    private final PathwayTemplateRepository templates;
    private final PathwayNodeRepository nodes;
    private final PathwayEdgeRepository edges;
    private final PatientPathwayRepository patientPathways;
    private final PathwayVarianceRepository variances;
    private final ClinicalClockRepository clocks;
    private final SpecialtyMetricBindingRepository metricBindings;
    private final PathwayProgressor progressor;
    private final AuditEventPublisher auditPublisher;
    private final StateTransitionRecorder transitions;
    private final DiagnoseResponseAssembler diagnoseAssembler;
    private final ObjectMapper json;

    /**
     * 注入路径引擎闭环所需仓库、推进器、审计发布器、状态记录器、诊断装配器和 JSON 工具。
     */
    public PathwayEngineService(SpecialtyPackageRepository packages,
                                SpecialtyProfileRepository profiles,
                                PathwayTemplateRepository templates,
                                PathwayNodeRepository nodes,
                                PathwayEdgeRepository edges,
                                PatientPathwayRepository patientPathways,
                                PathwayVarianceRepository variances,
                                ClinicalClockRepository clocks,
                                SpecialtyMetricBindingRepository metricBindings,
                                PathwayProgressor progressor,
                                AuditEventPublisher auditPublisher,
                                StateTransitionRecorder transitions,
                                DiagnoseResponseAssembler diagnoseAssembler,
                                ObjectMapper json) {
        this.packages = packages;
        this.profiles = profiles;
        this.templates = templates;
        this.nodes = nodes;
        this.edges = edges;
        this.patientPathways = patientPathways;
        this.variances = variances;
        this.clocks = clocks;
        this.metricBindings = metricBindings;
        this.progressor = progressor;
        this.auditPublisher = auditPublisher;
        this.transitions = transitions;
        this.diagnoseAssembler = diagnoseAssembler;
        this.json = json;
    }

    /**
     * 创建专病包草稿，并保存请求中携带的专病画像。
     *
     * <p>成功后记录 {@code CREATE_SPECIALTY_PACKAGE} 状态迁移和创建审计事件。
     */
    @Transactional
    public SpecialtyPackageResponse createPackage(SpecialtyPackageCreateRequest request) {
        String tenantId = requireCurrentTenant();
        String traceId = RequestContext.currentTraceId();
        String actor = currentActor();
        Instant now = Instant.now();
        String packageId = "sp-" + UUID.randomUUID();

        packages.save(new SpecialtyPackage(
            null, packageId, tenantId, request.packageCode(), request.diseaseCode(),
            request.name(), request.packageVersion(), SpecialtyPackageStatus.DRAFT,
            request.sourceRef(), request.description(), null, null,
            now, actor, now, actor, traceId));
        for (SpecialtyProfileRequest profile : nullToEmpty(request.profiles())) {
            profiles.save(new SpecialtyProfile(
                null, "spr-" + UUID.randomUUID(), tenantId, packageId,
                profile.profileCode(), profile.name(), writeJson(profile.stratification()),
                writeJson(profile.entryCriteria()), writeJson(profile.exitCriteria()),
                writeJson(profile.followupPlan()), now, actor, now, actor, traceId));
        }
        transitions.record(PACKAGE_ENTITY, packageId, null, SpecialtyPackageStatus.DRAFT.name(),
            "CREATE_SPECIALTY_PACKAGE", null);
        auditPublisher.publish(AuditAction.CREATE, PACKAGE_ENTITY, packageId,
            "创建专病包 " + request.packageCode());
        return new SpecialtyPackageResponse(packageId, SpecialtyPackageStatus.DRAFT, traceId);
    }

    /**
     * 创建路径模板草稿，并一次性持久化模板节点、路径边和专病指标绑定。
     *
     * <p>前置：关联专病包必须存在于当前租户；失败抛出 {@code ENG-PATHWAY-007}。
     */
    @Transactional
    public PathwayTemplateDetailResponse createTemplate(PathwayTemplateCreateRequest request) {
        String tenantId = requireCurrentTenant();
        String traceId = RequestContext.currentTraceId();
        String actor = currentActor();
        Instant now = Instant.now();
        SpecialtyPackage specialtyPackage = packages.findByPackageIdAndTenantId(request.packageId(), tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_PATHWAY_007,
                "专病包不存在: " + request.packageId()));
        String templateId = "pt-" + UUID.randomUUID();
        PathwayTemplate template = templates.save(new PathwayTemplate(
            null, templateId, tenantId, specialtyPackage.packageId(), request.templateCode(),
            request.name(), request.diseaseCode(), request.templateVersion(), request.templateLevel(),
            PathwayTemplateStatus.DRAFT, request.startNodeCode(), request.sourceRef(),
            request.description(), writeJson(request.entryCriteria()), writeJson(request.exitCriteria()),
            now, actor, now, actor, traceId));
        List<PathwayNode> savedNodes = nullToEmpty(request.nodes()).stream()
            .map(node -> nodes.save(new PathwayNode(
                null, "pn-" + UUID.randomUUID(), tenantId, templateId, node.nodeCode(),
                node.name(), node.nodeType(), safeInt(node.sortOrder()),
                node.responsibleRole(), writeJson(node.dependency()), node.timeWindowMinutes(),
                Boolean.TRUE.equals(node.terminal()), writeJson(node.config()),
                now, actor, now, actor, traceId)))
            .toList();
        List<PathwayEdge> savedEdges = nullToEmpty(request.edges()).stream()
            .map(edge -> edges.save(new PathwayEdge(
                null, "pe-" + UUID.randomUUID(), tenantId, templateId, edge.edgeCode(),
                edge.fromNodeCode(), edge.toNodeCode(), edge.edgeType(),
                writeJson(edge.condition()), safeInt(edge.priority()),
                now, actor, now, actor, traceId)))
            .toList();
        List<SpecialtyMetricBinding> savedBindings = nullToEmpty(request.metricBindings()).stream()
            .map(binding -> metricBindings.save(new SpecialtyMetricBinding(
                null, "smb-" + UUID.randomUUID(), tenantId, specialtyPackage.packageId(),
                templateId, binding.nodeCode(), binding.metricCode(),
                Boolean.TRUE.equals(binding.required()), now, actor, now, actor, traceId)))
            .toList();

        transitions.record(TEMPLATE_ENTITY, templateId, null, PathwayTemplateStatus.DRAFT.name(),
            "CREATE_PATHWAY_TEMPLATE", null);
        auditPublisher.publish(AuditAction.CREATE, TEMPLATE_ENTITY, templateId,
            "创建路径模板 " + request.templateCode());
        return new PathwayTemplateDetailResponse(template, savedNodes, savedEdges, savedBindings, traceId);
    }

    /**
     * 对路径模板执行发布门禁并将草稿发布为 {@code PUBLISHED}。
     *
     * <p>门禁校验起始节点、终止节点、节点编码唯一性、边端点存在性和时间窗合法性。
     */
    @Transactional
    public PathwayTemplatePublishResponse publishTemplate(String templateId) {
        String tenantId = requireCurrentTenant();
        PathwayTemplate template = findTemplate(templateId, tenantId);
        ensureTemplateDraft(template);
        List<PathwayNode> graphNodes = nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc(templateId, tenantId);
        List<PathwayEdge> graphEdges = edges.findByTemplateIdAndTenantIdOrderByPriorityAsc(templateId, tenantId);
        validatePublishGate(template, graphNodes, graphEdges);

        Instant now = Instant.now();
        String actor = currentActor();
        PathwayTemplate published = copyTemplate(
            template, PathwayTemplateStatus.PUBLISHED, now, actor, RequestContext.currentTraceId());
        templates.save(published);
        transitions.record(TEMPLATE_ENTITY, templateId, template.status().name(),
            PathwayTemplateStatus.PUBLISHED.name(), "PUBLISH_PATHWAY_TEMPLATE", null);
        auditPublisher.publish(AuditAction.PUBLISH, TEMPLATE_ENTITY, templateId,
            "发布路径模板 " + template.templateCode());
        return new PathwayTemplatePublishResponse(
            templateId, PathwayTemplateStatus.PUBLISHED, RequestContext.currentTraceId());
    }

    /**
     * 分页查询当前租户下的专病包。
     *
     * <p>当调用方未传分页参数时使用 {@link PageRequest#defaults()}，结果按更新时间倒序返回。
     */
    @Transactional(readOnly = true)
    public PageResponse<SpecialtyPackage> listPackages(PageRequest page) {
        PageRequest safePage = page == null ? PageRequest.defaults() : page;
        String tenantId = requireCurrentTenant();
        long total = packages.countByTenantId(tenantId);
        List<SpecialtyPackage> rows = total == 0 ? List.of()
            : packages.pageByTenantId(tenantId, safePage.offset(), safePage.safeSize());
        return PageResponse.of(rows, safePage, total);
    }

    /**
     * 按状态、病种和专病包过滤分页查询路径模板。
     *
     * <p>过滤条件为 {@code null} 时不进入 SQL；分页总数与行集分别由仓库 count/page 查询提供。
     */
    @Transactional(readOnly = true)
    public PageResponse<PathwayTemplate> listTemplates(PathwayTemplateFilter filter, PageRequest page) {
        PageRequest safePage = page == null ? PageRequest.defaults() : page;
        String tenantId = requireCurrentTenant();
        String status = filter == null || filter.status() == null ? null : filter.status().name();
        String diseaseCode = filter == null ? null : filter.diseaseCode();
        String packageId = filter == null ? null : filter.packageId();
        long total = templates.countByFilter(tenantId, status, diseaseCode, packageId);
        List<PathwayTemplate> rows = total == 0 ? List.of()
            : templates.pageByFilter(tenantId, status, diseaseCode, packageId,
                safePage.offset(), safePage.safeSize());
        return PageResponse.of(rows, safePage, total);
    }

    /**
     * 装配路径模板详情。
     *
     * <p>返回模板主表、按顺序排列的节点、按优先级排列的边和按节点排列的指标绑定。
     */
    @Transactional(readOnly = true)
    public PathwayTemplateDetailResponse templateDetail(String templateId) {
        String tenantId = requireCurrentTenant();
        PathwayTemplate template = findTemplate(templateId, tenantId);
        return new PathwayTemplateDetailResponse(
            template,
            nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc(templateId, tenantId),
            edges.findByTemplateIdAndTenantIdOrderByPriorityAsc(templateId, tenantId),
            metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc(templateId, tenantId),
            RequestContext.currentTraceId());
    }

    /**
     * 为患者创建路径实例并进入模板起始节点或请求指定起点。
     *
     * <p>仅允许基于 {@code PUBLISHED} 模板入径；成功后创建首个 {@link ClinicalClock} 关键时钟。
     */
    @Transactional
    public PatientPathwayDetailResponse enterPatientPathway(PatientPathwayEnterRequest request) {
        String tenantId = requireCurrentTenant();
        PathwayTemplate template = findTemplate(request.templateId(), tenantId);
        if (template.status() != PathwayTemplateStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_005, "路径模板未发布，不能入径");
        }
        String startNodeCode = isBlank(request.startNodeCode()) ? template.startNodeCode() : request.startNodeCode();
        PathwayNode startNode = nodes.findByTemplateIdAndTenantIdAndNodeCode(template.templateId(), tenantId, startNodeCode)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_PATHWAY_006,
                "入径起始节点不存在: " + startNodeCode));
        String traceId = RequestContext.currentTraceId();
        String actor = currentActor();
        Instant now = Instant.now();
        String patientPathwayId = "pp-" + UUID.randomUUID();
        PatientPathway runtime = patientPathways.save(new PatientPathway(
            null, patientPathwayId, tenantId, request.patientId(), request.encounterId(),
            template.templateId(), startNode.nodeCode(), PatientPathwayStatus.NODE_EXECUTING,
            now, null, null, null, null, now, actor, now, actor, traceId));
        ClinicalClock startClock = clocks.save(newClock(
            tenantId, patientPathwayId, startNode, now, actor, traceId));
        transitions.record(PATIENT_PATHWAY_ENTITY, patientPathwayId, null,
            PatientPathwayStatus.NODE_EXECUTING.name(), "ENTER_PATHWAY", null);
        auditPublisher.publish(AuditAction.CREATE, PATIENT_PATHWAY_ENTITY, patientPathwayId,
            "患者入径 " + template.templateCode());
        return new PatientPathwayDetailResponse(runtime, List.of(), List.of(startClock), traceId);
    }

    /**
     * 查看患者路径实例详情。
     *
     * <p>返回路径运行时状态、按创建时间排列的变异记录和按启动时间排列的关键时钟。
     */
    @Transactional(readOnly = true)
    public PatientPathwayDetailResponse patientDetail(String patientPathwayId) {
        String tenantId = requireCurrentTenant();
        PatientPathway runtime = findPatientPathway(patientPathwayId, tenantId);
        return new PatientPathwayDetailResponse(
            runtime,
            variances.findByPatientPathwayIdAndTenantIdOrderByCreatedAtAsc(patientPathwayId, tenantId),
            clocks.findByPatientPathwayIdAndTenantIdOrderByStartedAtAsc(patientPathwayId, tenantId),
            RequestContext.currentTraceId());
    }

    /**
     * 查询指定患者路径实例的变异事实。
     *
     * <p>先校验患者路径属于当前租户，再按创建时间返回变异记录，避免跨租户直接枚举变异。
     */
    @Transactional(readOnly = true)
    public List<PathwayVariance> variances(String patientPathwayId) {
        String tenantId = requireCurrentTenant();
        findPatientPathway(patientPathwayId, tenantId);
        return variances.findByPatientPathwayIdAndTenantIdOrderByCreatedAtAsc(patientPathwayId, tenantId);
    }

    /**
     * 查询指定患者路径实例的关键时钟。
     *
     * <p>先校验患者路径属于当前租户，再按启动时间返回节点时钟事实。
     */
    @Transactional(readOnly = true)
    public List<ClinicalClock> clocks(String patientPathwayId) {
        String tenantId = requireCurrentTenant();
        findPatientPathway(patientPathwayId, tenantId);
        return clocks.findByPatientPathwayIdAndTenantIdOrderByStartedAtAsc(patientPathwayId, tenantId);
    }

    /**
     * 接收临床事件统一上下文，作为路径引擎后续入径/推进监听的稳定入口。
     *
     * <p>D0 只建立上下文入口，不在这里自动创建患者路径实例；D3 路径业务卡会基于该入口补充匹配规则。
     */
    @Transactional(readOnly = true)
    public PathwayEventDispatchResponse dispatchClinicalEvent(ClinicalEventContext context) {
        if (context == null || isBlank(context.patientId())) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_001, "临床事件上下文缺少患者标识");
        }
        String tenantId = requireCurrentTenant();
        if (!tenantId.equals(context.tenantId())) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_001, "临床事件上下文租户不匹配");
        }
        return new PathwayEventDispatchResponse(
            context.eventId(), context.patientId(), context.encounterId(), context.traceId());
    }

    /**
     * 基于模板图和可选目标节点序列试运行路径推进。
     *
     * <p>试运行只返回节点轨迹与最终状态，不创建患者路径、不写变异、不创建关键时钟。
     */
    @Transactional(readOnly = true)
    public PathwaySimulationResponse simulate(String templateId, PathwaySimulateRequest request) {
        String tenantId = requireCurrentTenant();
        PathwayTemplate template = findTemplate(templateId, tenantId);
        List<PathwayNode> graphNodes = nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc(templateId, tenantId);
        List<PathwayEdge> graphEdges = edges.findByTemplateIdAndTenantIdOrderByPriorityAsc(templateId, tenantId);
        String currentNode = request == null || isBlank(request.startNodeCode())
            ? template.startNodeCode() : request.startNodeCode();
        List<String> requestedTargets = request == null ? List.of() : request.requestedNextNodeCodes();
        java.util.ArrayList<String> trajectory = new java.util.ArrayList<>();
        trajectory.add(currentNode);
        PatientPathwayStatus finalStatus = PatientPathwayStatus.NODE_EXECUTING;
        for (int i = 0; i <= graphNodes.size(); i++) {
            String requestedTarget = i < requestedTargets.size() ? requestedTargets.get(i) : null;
            PathwayProgressDecision decision = progressor.advance(new PathwayProgressCommand(
                new PathwayGraph(graphNodes, graphEdges), currentNode,
                PathwayAdvanceEventType.COMPLETE, requestedTarget));
            finalStatus = decision.status();
            if (decision.nextNodeCode() == null) {
                break;
            }
            currentNode = decision.nextNodeCode();
            trajectory.add(currentNode);
        }
        return new PathwaySimulationResponse(templateId, trajectory, finalStatus, RequestContext.currentTraceId());
    }

    /**
     * 推进患者路径节点，或登记变异、退出路径。
     *
     * <p>方法会校验运行时状态、调用确定性推进器、保存变异事实、关闭当前时钟、创建下一节点时钟，
     * 并同步患者路径状态、审计事件和状态迁移。
     */
    @Transactional
    public PathwayAdvanceResponse advance(PathwayAdvanceRequest request) {
        String tenantId = requireCurrentTenant();
        PatientPathway runtime = findPatientPathway(request.patientPathwayId(), tenantId);
        ensureRuntimeMutable(runtime);
        String currentNodeCode = isBlank(request.currentNodeCode())
            ? runtime.currentNodeCode() : request.currentNodeCode();
        validateVarianceRequest(request);
        List<PathwayNode> graphNodes = nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc(runtime.templateId(), tenantId);
        List<PathwayEdge> graphEdges = edges.findByTemplateIdAndTenantIdOrderByPriorityAsc(runtime.templateId(), tenantId);
        PathwayProgressDecision decision = progressor.advance(new PathwayProgressCommand(
            new PathwayGraph(graphNodes, graphEdges), currentNodeCode,
            request.eventType(), request.requestedNextNodeCode()));

        String traceId = RequestContext.currentTraceId();
        String actor = currentActor();
        Instant now = Instant.now();
        String varianceId = null;
        if (request.eventType() == PathwayAdvanceEventType.VARIANCE) {
            varianceId = "pv-" + UUID.randomUUID();
            variances.save(new PathwayVariance(
                null, varianceId, tenantId, runtime.patientPathwayId(), currentNodeCode,
                request.varianceType(), request.varianceReason(), request.resolutionAction(),
                request.requestedNextNodeCode(), now, actor, now, actor, traceId));
        }
        closeCurrentClocks(runtime.patientPathwayId(), tenantId, currentNodeCode, request.eventType(), now, actor, traceId);
        ClinicalClock nextClock = null;
        PathwayNode nextNode = findNode(graphNodes, decision.nextNodeCode());
        if (decision.status() == PatientPathwayStatus.NODE_EXECUTING && nextNode != null) {
            nextClock = clocks.save(newClock(tenantId, runtime.patientPathwayId(), nextNode, now, actor, traceId));
        }

        PatientPathway updated = copyRuntime(runtime, decision, request, now, actor, traceId);
        patientPathways.save(updated);
        transitions.record(PATIENT_PATHWAY_ENTITY, runtime.patientPathwayId(), runtime.status().name(),
            updated.status().name(), "ADVANCE_PATHWAY", null);
        auditPublisher.publish(AuditAction.EXECUTE, PATIENT_PATHWAY_ENTITY, runtime.patientPathwayId(),
            "推进患者路径 " + runtime.patientPathwayId());
        return new PathwayAdvanceResponse(
            runtime.patientPathwayId(), decision.previousNodeCode(), decision.nextNodeCode(),
            decision.status(), varianceId, traceId);
    }

    /**
     * 生成患者路径实例的诊断解释响应。
     *
     * <p>诊断响应包含路径实例当前状态、模板引用、内联证据摘要和 traceId，用于排查路径推进结果。
     */
    @Transactional(readOnly = true)
    public DiagnoseResponse diagnose(String patientPathwayId) {
        String tenantId = requireCurrentTenant();
        PatientPathway runtime = findPatientPathway(patientPathwayId, tenantId);
        PayloadRef payloadRef = new PayloadRef(
            PayloadRef.STORAGE_INLINE, digest(runtime.patientPathwayId() + ":" + runtime.status()),
            "db://patient_pathway/" + runtime.patientPathwayId(), 0L, "application/json");
        return diagnoseAssembler.assemble(
            PATIENT_PATHWAY_ENTITY, runtime.patientPathwayId(), tenantId, runtime.status().name(),
            runtime, List.of(), Map.of("template", List.of(runtime.templateId())),
            payloadRef, runtime.traceId());
    }

    private void validatePublishGate(PathwayTemplate template, List<PathwayNode> graphNodes,
                                     List<PathwayEdge> graphEdges) {
        Set<String> nodeCodes = new HashSet<>();
        boolean hasTerminal = false;
        for (PathwayNode node : graphNodes) {
            if (isBlank(node.nodeCode()) || !nodeCodes.add(node.nodeCode())) {
                throw new ApiException(ErrorCode.ENG_PATHWAY_004, "路径节点编码重复或为空");
            }
            if (node.timeWindowMinutes() != null && node.timeWindowMinutes() < 0) {
                throw new ApiException(ErrorCode.ENG_PATHWAY_004, "路径节点时间窗不能为负数");
            }
            hasTerminal = hasTerminal || Boolean.TRUE.equals(node.terminalFlag());
        }
        if (isBlank(template.startNodeCode()) || !nodeCodes.contains(template.startNodeCode())) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004, "路径模板缺少有效起始节点");
        }
        if (!hasTerminal) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004, "路径模板缺少终止节点");
        }
        Set<String> nodesWithOutgoing = new HashSet<>();
        for (PathwayEdge edge : graphEdges) {
            if (!nodeCodes.contains(edge.fromNodeCode()) || !nodeCodes.contains(edge.toNodeCode())) {
                throw new ApiException(ErrorCode.ENG_PATHWAY_004, "路径边引用了不存在的节点");
            }
            nodesWithOutgoing.add(edge.fromNodeCode());
        }
        for (PathwayNode node : graphNodes) {
            if (!Boolean.TRUE.equals(node.terminalFlag()) && !nodesWithOutgoing.contains(node.nodeCode())) {
                throw new ApiException(ErrorCode.ENG_PATHWAY_004, "非终止节点缺少出边: " + node.nodeCode());
            }
        }
    }

    private void closeCurrentClocks(String patientPathwayId, String tenantId, String nodeCode,
                                    PathwayAdvanceEventType eventType, Instant now,
                                    String actor, String traceId) {
        ClinicalClockStatus status = switch (eventType) {
            case VARIANCE -> ClinicalClockStatus.VARIANCE;
            case COMPLETE, EXIT -> ClinicalClockStatus.COMPLETED;
        };
        clocks.findByPatientPathwayIdAndTenantIdOrderByStartedAtAsc(patientPathwayId, tenantId).stream()
            .filter(clock -> Objects.equals(clock.nodeCode(), nodeCode))
            .filter(clock -> clock.status() == ClinicalClockStatus.RUNNING)
            .forEach(clock -> clocks.save(copyClock(clock, status, now, actor, traceId)));
    }

    private PatientPathway copyRuntime(PatientPathway runtime, PathwayProgressDecision decision,
                                       PathwayAdvanceRequest request, Instant now,
                                       String actor, String traceId) {
        String currentNode = switch (decision.status()) {
            case NODE_EXECUTING, VARIANCE -> decision.nextNodeCode();
            case COMPLETED, EXITED -> null;
            case ENTERED -> runtime.currentNodeCode();
        };
        return new PatientPathway(
            runtime.id(), runtime.patientPathwayId(), runtime.tenantId(), runtime.patientId(),
            runtime.encounterId(), runtime.templateId(), currentNode, decision.status(),
            runtime.enteredAt(), decision.status() == PatientPathwayStatus.COMPLETED ? now : runtime.completedAt(),
            decision.status() == PatientPathwayStatus.EXITED ? now : runtime.exitedAt(),
            decision.status() == PatientPathwayStatus.EXITED ? request.exitReason() : runtime.exitReason(),
            request.eventId(), runtime.createdAt(), runtime.createdBy(), now, actor, traceId);
    }

    private PathwayTemplate copyTemplate(PathwayTemplate template, PathwayTemplateStatus status,
                                         Instant now, String actor, String traceId) {
        return new PathwayTemplate(
            template.id(), template.templateId(), template.tenantId(), template.packageId(),
            template.templateCode(), template.name(), template.diseaseCode(),
            template.templateVersion(), template.templateLevel(), status, template.startNodeCode(),
            template.sourceRef(), template.description(), template.entryCriteriaJson(),
            template.exitCriteriaJson(), template.createdAt(), template.createdBy(), now, actor, traceId);
    }

    private ClinicalClock newClock(String tenantId, String patientPathwayId,
                                   PathwayNode node, Instant now, String actor, String traceId) {
        Instant dueAt = node.timeWindowMinutes() == null ? null
            : now.plusSeconds(node.timeWindowMinutes().longValue() * 60L);
        return new ClinicalClock(
            null, "cc-" + UUID.randomUUID(), tenantId, patientPathwayId,
            node.nodeCode(), null, now, dueAt, null, ClinicalClockStatus.RUNNING,
            now, actor, now, actor, traceId);
    }

    private ClinicalClock copyClock(ClinicalClock clock, ClinicalClockStatus status,
                                    Instant now, String actor, String traceId) {
        return new ClinicalClock(
            clock.id(), clock.clockId(), clock.tenantId(), clock.patientPathwayId(),
            clock.nodeCode(), clock.metricCode(), clock.startedAt(), clock.dueAt(),
            now, status, clock.createdAt(), clock.createdBy(), now, actor, traceId);
    }

    private PathwayTemplate findTemplate(String templateId, String tenantId) {
        return templates.findByTemplateIdAndTenantId(templateId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_PATHWAY_002,
                "路径模板不存在: " + templateId));
    }

    private PatientPathway findPatientPathway(String patientPathwayId, String tenantId) {
        return patientPathways.findByPatientPathwayIdAndTenantId(patientPathwayId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_PATHWAY_003,
                "患者路径不存在: " + patientPathwayId));
    }

    private PathwayNode findNode(List<PathwayNode> graphNodes, String nodeCode) {
        if (isBlank(nodeCode)) {
            return null;
        }
        return graphNodes.stream()
            .filter(node -> Objects.equals(node.nodeCode(), nodeCode))
            .findFirst()
            .orElse(null);
    }

    private void ensureTemplateDraft(PathwayTemplate template) {
        if (template.status() != PathwayTemplateStatus.DRAFT) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_005, "当前路径模板状态不允许发布");
        }
    }

    private void ensureRuntimeMutable(PatientPathway runtime) {
        if (runtime.status() == PatientPathwayStatus.COMPLETED || runtime.status() == PatientPathwayStatus.EXITED) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_005, "当前患者路径状态不允许推进");
        }
    }

    private void validateVarianceRequest(PathwayAdvanceRequest request) {
        if (request.eventType() != PathwayAdvanceEventType.VARIANCE) {
            return;
        }
        if (request.varianceType() == null || isBlank(request.varianceReason())) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_006, "变异事件必须包含变异类型和原因");
        }
    }

    private String requireCurrentTenant() {
        var scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private String currentActor() {
        return RequestContext.currentUserId().orElse("system");
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private <T> List<T> nullToEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }

    private String writeJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return json.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_001, "路径 JSON 字段无法序列化", exception);
        }
    }

    private String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "无法计算路径诊断摘要", exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
