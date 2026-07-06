package com.medkernel.engine.pathway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.authoring.AuthoringFeatureFlag;
import com.medkernel.engine.authoring.AuthoringFeatureGate;
import com.medkernel.engine.context.ClinicalEventContext;
import com.medkernel.engine.context.ContextFieldPathPolicy;
import com.medkernel.engine.context.ContextFactBridge;
import com.medkernel.engine.context.ContextSnapshotResources;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.context.ContextSnapshotStatus;
import com.medkernel.engine.context.canonical.CanonicalEncounter;
import com.medkernel.engine.evaluation.EvaluationIndicatorRepository;
import com.medkernel.engine.evaluation.EvaluationIndicatorStatus;
import com.medkernel.engine.event.ClockSlaBreachedEvent;
import com.medkernel.engine.event.EngineDomainEventPort;
import com.medkernel.engine.event.PathwayVarianceRecordedEvent;
import com.medkernel.engine.versioning.AssetReferenceConsistency;
import com.medkernel.engine.rule.ConditionEvaluation;
import com.medkernel.engine.rule.ConditionEvaluator;
import com.medkernel.engine.safety.ClinicalSafetyGuard;
import com.medkernel.engine.versioning.AssetTriggerBindingInput;
import com.medkernel.engine.versioning.AssetTriggerBindingService;
import com.medkernel.engine.versioning.AssetTriggerPurpose;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetDependencyDeclaration;
import com.medkernel.engine.versioning.AssetVersionNumbers;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.InheritanceResolveQuery;
import com.medkernel.engine.versioning.InheritanceResolver;
import com.medkernel.engine.versioning.ResolvedAssetVersion;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.config.SystemConfigService;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.observability.DiagnoseResponse;
import com.medkernel.shared.observability.DiagnoseResponseAssembler;
import com.medkernel.shared.observability.PayloadRef;
import com.medkernel.shared.observability.StateTransitionRecorder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

/**
 * 临床路径应用服务（临床路径 + 患者路径实例 + 确定性推进）。
 *
 * <p>聚合临床路径、节点、边、患者路径、变异、关键时钟和指标绑定，
 * 承担：
 * <ul>
 *   <li>专病路径资产的草稿创建、版本化查询和真实快照试运行；</li>
 *   <li>基于已发布临床路径创建患者路径实例并初始化节点关键时钟；</li>
 *   <li>按确定性推进器处理完成、变异和退出事件，并保存审计事实；</li>
 *   <li>输出试运行轨迹和诊断解释，支撑后续路径画布与临床嵌入式提醒。</li>
 * </ul>
 * 所有读写均按当前租户隔离，写动作发布审计事件并记录状态迁移。
 */
@Service
public class PathwayEngineService {

    private static final String TEMPLATE_ENTITY = "pathway_template";
    private static final String PATIENT_PATHWAY_ENTITY = "patient_pathway";
    private final PathwayTemplateRepository templates;
    private final PathwayNodeRepository nodes;
    private final PathwayMilestoneRepository milestones;
    private final PathwayEdgeRepository edges;
    private final PatientPathwayRepository patientPathways;
    private final PathwayVarianceRepository variances;
    private final ClinicalClockRepository clocks;
    private final SpecialtyMetricBindingRepository metricBindings;
    private final PathwayOutcomeBindingRepository outcomeBindings;
    private final EvaluationIndicatorRepository evaluationIndicators;
    private final ContextSnapshotService contextSnapshots;
    private final PathwayProgressor progressor;
    private final AuditRecorder auditRecorder;
    private final StateTransitionRecorder transitions;
    private final DiagnoseResponseAssembler diagnoseAssembler;
    private final ObjectMapper json;
    private final PathwayFollowupHandoffPort followupHandoff;
    private final PathwayWorklistPort worklist;
    private final EngineDomainEventPort domainEvents;
    private final ClinicalSafetyGuard safetyGuard;
    private final PathwayVersionedAssetAdapter versionedAssets;
    private final AssetVersionRepository assetVersions;
    private final AssetTriggerBindingService triggerBindings;
    private final InheritanceResolver inheritanceResolver;
    private final RuntimeReleasePathwaySelector runtimePathways;
    private final ConditionEvaluator conditionEvaluator;
    private final AuthoringFeatureGate authoringFeatureGate;

    /**
     * 注入临床路径闭环所需仓库、推进器、审计发布器、状态记录器、诊断装配器和 JSON 工具。
     */
    @Autowired
    public PathwayEngineService(PathwayTemplateRepository templates,
                                PathwayNodeRepository nodes,
                                PathwayMilestoneRepository milestones,
                                PathwayEdgeRepository edges,
                                PatientPathwayRepository patientPathways,
                                PathwayVarianceRepository variances,
                                ClinicalClockRepository clocks,
                                SpecialtyMetricBindingRepository metricBindings,
                                PathwayOutcomeBindingRepository outcomeBindings,
                                EvaluationIndicatorRepository evaluationIndicators,
                                ContextSnapshotService contextSnapshots,
                                PathwayProgressor progressor,
                                ConditionEvaluator conditionEvaluator,
                                AuthoringFeatureGate authoringFeatureGate,
                                AuditRecorder auditRecorder,
                                StateTransitionRecorder transitions,
                                DiagnoseResponseAssembler diagnoseAssembler,
                                ObjectMapper json,
                                ClinicalSafetyGuard safetyGuard,
                                ObjectProvider<PathwayFollowupHandoffPort> followupHandoffProvider,
                                ObjectProvider<PathwayWorklistPort> worklistProvider,
                                ObjectProvider<EngineDomainEventPort> domainEventProvider,
                                PathwayVersionedAssetAdapter versionedAssets,
                                AssetVersionRepository assetVersions,
                                AssetTriggerBindingService triggerBindings,
                                InheritanceResolver inheritanceResolver,
                                RuntimeReleasePathwaySelector runtimePathways) {
        this(templates, nodes, milestones, edges, patientPathways, variances, clocks,
            metricBindings, outcomeBindings, evaluationIndicators, contextSnapshots, progressor, conditionEvaluator,
            authoringFeatureGate, auditRecorder, transitions, diagnoseAssembler, json,
            followupHandoffProvider.getIfAvailable(PathwayFollowupHandoffPort::noop),
            worklistProvider.getIfAvailable(PathwayWorklistPort::noop),
            domainEventProvider.getIfAvailable(EngineDomainEventPort::noop), safetyGuard,
            versionedAssets, assetVersions, triggerBindings, inheritanceResolver, runtimePathways);
    }

    PathwayEngineService(PathwayTemplateRepository templates,
                         PathwayNodeRepository nodes,
                         PathwayMilestoneRepository milestones,
                         PathwayEdgeRepository edges,
                         PatientPathwayRepository patientPathways,
                         PathwayVarianceRepository variances,
                         ClinicalClockRepository clocks,
                         SpecialtyMetricBindingRepository metricBindings,
                         PathwayOutcomeBindingRepository outcomeBindings,
                         EvaluationIndicatorRepository evaluationIndicators,
                         ContextSnapshotService contextSnapshots,
                         PathwayProgressor progressor,
                         AuditRecorder auditRecorder,
                         StateTransitionRecorder transitions,
                         DiagnoseResponseAssembler diagnoseAssembler,
                         ObjectMapper json,
                         PathwayFollowupHandoffPort followupHandoff,
                         PathwayWorklistPort worklist,
                         EngineDomainEventPort domainEvents,
                         ClinicalSafetyGuard safetyGuard,
                         PathwayVersionedAssetAdapter versionedAssets,
                         AssetVersionRepository assetVersions,
                         InheritanceResolver inheritanceResolver,
                         RuntimeReleasePathwaySelector runtimePathways) {
        this(templates, nodes, milestones, edges, patientPathways, variances, clocks,
            metricBindings, outcomeBindings, evaluationIndicators, contextSnapshots, progressor,
            new ConditionEvaluator(json), AuthoringFeatureGate.alwaysEnabled(), auditRecorder, transitions,
            diagnoseAssembler, json, followupHandoff, worklist, domainEvents, safetyGuard,
            versionedAssets, assetVersions, null, inheritanceResolver, runtimePathways);
    }

    PathwayEngineService(PathwayTemplateRepository templates,
                         PathwayNodeRepository nodes,
                         PathwayMilestoneRepository milestones,
                         PathwayEdgeRepository edges,
                         PatientPathwayRepository patientPathways,
                         PathwayVarianceRepository variances,
                         ClinicalClockRepository clocks,
                         SpecialtyMetricBindingRepository metricBindings,
                         PathwayOutcomeBindingRepository outcomeBindings,
                         EvaluationIndicatorRepository evaluationIndicators,
                         ContextSnapshotService contextSnapshots,
                         PathwayProgressor progressor,
                         AuditRecorder auditRecorder,
                         StateTransitionRecorder transitions,
                         DiagnoseResponseAssembler diagnoseAssembler,
                         ObjectMapper json,
                         PathwayFollowupHandoffPort followupHandoff,
                         PathwayWorklistPort worklist,
                         EngineDomainEventPort domainEvents,
                         ClinicalSafetyGuard safetyGuard,
                         PathwayVersionedAssetAdapter versionedAssets,
                         AssetVersionRepository assetVersions,
                         AssetTriggerBindingService triggerBindings,
                         InheritanceResolver inheritanceResolver,
                         RuntimeReleasePathwaySelector runtimePathways) {
        this(templates, nodes, milestones, edges, patientPathways, variances, clocks,
            metricBindings, outcomeBindings, evaluationIndicators, contextSnapshots, progressor,
            new ConditionEvaluator(json), AuthoringFeatureGate.alwaysEnabled(), auditRecorder, transitions,
            diagnoseAssembler, json, followupHandoff, worklist, domainEvents, safetyGuard,
            versionedAssets, assetVersions, triggerBindings, inheritanceResolver, runtimePathways);
    }

    PathwayEngineService(PathwayTemplateRepository templates,
                         PathwayNodeRepository nodes,
                         PathwayMilestoneRepository milestones,
                         PathwayEdgeRepository edges,
                         PatientPathwayRepository patientPathways,
                         PathwayVarianceRepository variances,
                         ClinicalClockRepository clocks,
                         SpecialtyMetricBindingRepository metricBindings,
                         PathwayOutcomeBindingRepository outcomeBindings,
                         EvaluationIndicatorRepository evaluationIndicators,
                         ContextSnapshotService contextSnapshots,
                         PathwayProgressor progressor,
                         AuditRecorder auditRecorder,
                         StateTransitionRecorder transitions,
                         DiagnoseResponseAssembler diagnoseAssembler,
                         ObjectMapper json,
                         PathwayFollowupHandoffPort followupHandoff,
                         PathwayWorklistPort worklist,
                         ClinicalSafetyGuard safetyGuard,
                         PathwayVersionedAssetAdapter versionedAssets,
                         AssetVersionRepository assetVersions,
                         InheritanceResolver inheritanceResolver,
                         RuntimeReleasePathwaySelector runtimePathways) {
        this(templates, nodes, milestones, edges, patientPathways, variances, clocks,
            metricBindings, outcomeBindings, evaluationIndicators, contextSnapshots, progressor, auditRecorder,
            transitions, diagnoseAssembler, json, followupHandoff, worklist, EngineDomainEventPort.noop(),
            safetyGuard, versionedAssets, assetVersions, inheritanceResolver, runtimePathways);
    }

    private PathwayEngineService(PathwayTemplateRepository templates,
                                 PathwayNodeRepository nodes,
                                 PathwayMilestoneRepository milestones,
                                 PathwayEdgeRepository edges,
                                 PatientPathwayRepository patientPathways,
                                 PathwayVarianceRepository variances,
                                 ClinicalClockRepository clocks,
                                 SpecialtyMetricBindingRepository metricBindings,
                                 PathwayOutcomeBindingRepository outcomeBindings,
                                 EvaluationIndicatorRepository evaluationIndicators,
                                 ContextSnapshotService contextSnapshots,
                                 PathwayProgressor progressor,
                                 ConditionEvaluator conditionEvaluator,
                                 AuthoringFeatureGate authoringFeatureGate,
                                 AuditRecorder auditRecorder,
                                 StateTransitionRecorder transitions,
                                 DiagnoseResponseAssembler diagnoseAssembler,
                                 ObjectMapper json,
                                 PathwayFollowupHandoffPort followupHandoff,
                                 PathwayWorklistPort worklist,
                                 EngineDomainEventPort domainEvents,
                                 ClinicalSafetyGuard safetyGuard,
                                 PathwayVersionedAssetAdapter versionedAssets,
                                 AssetVersionRepository assetVersions,
                                 AssetTriggerBindingService triggerBindings,
                                 InheritanceResolver inheritanceResolver,
                                 RuntimeReleasePathwaySelector runtimePathways) {
        this.templates = templates;
        this.nodes = nodes;
        this.milestones = milestones;
        this.edges = edges;
        this.patientPathways = patientPathways;
        this.variances = variances;
        this.clocks = clocks;
        this.metricBindings = metricBindings;
        this.outcomeBindings = outcomeBindings;
        this.evaluationIndicators = evaluationIndicators;
        this.contextSnapshots = contextSnapshots;
        this.progressor = progressor;
        this.conditionEvaluator = conditionEvaluator == null ? new ConditionEvaluator(json) : conditionEvaluator;
        this.authoringFeatureGate = authoringFeatureGate == null
            ? AuthoringFeatureGate.alwaysEnabled()
            : authoringFeatureGate;
        this.auditRecorder = auditRecorder;
        this.transitions = transitions;
        this.diagnoseAssembler = diagnoseAssembler;
        this.json = json;
        this.followupHandoff = followupHandoff == null ? PathwayFollowupHandoffPort.noop() : followupHandoff;
        this.worklist = worklist == null ? PathwayWorklistPort.noop() : worklist;
        this.domainEvents = domainEvents == null ? EngineDomainEventPort.noop() : domainEvents;
        this.safetyGuard = safetyGuard;
        this.versionedAssets = Objects.requireNonNull(versionedAssets, "路径统一版本适配器不能为空");
        this.assetVersions = Objects.requireNonNull(assetVersions, "统一资产版本仓库不能为空");
        this.triggerBindings = triggerBindings;
        this.inheritanceResolver = Objects.requireNonNull(inheritanceResolver, "继承解析器不能为空");
        this.runtimePathways = Objects.requireNonNull(runtimePathways, "机构生效版本路径选择器不能为空");
    }

    PathwayEngineService(PathwayTemplateRepository templates,
                         PathwayNodeRepository nodes,
                         PathwayMilestoneRepository milestones,
                         PathwayEdgeRepository edges,
                         PatientPathwayRepository patientPathways,
                         PathwayVarianceRepository variances,
                         ClinicalClockRepository clocks,
                         SpecialtyMetricBindingRepository metricBindings,
                         PathwayOutcomeBindingRepository outcomeBindings,
                         EvaluationIndicatorRepository evaluationIndicators,
                         ContextSnapshotService contextSnapshots,
                         PathwayProgressor progressor,
                         AuditRecorder auditRecorder,
                         StateTransitionRecorder transitions,
                         DiagnoseResponseAssembler diagnoseAssembler,
                         ObjectMapper json,
                         PathwayFollowupHandoffPort followupHandoff,
                         ClinicalSafetyGuard safetyGuard,
                         PathwayVersionedAssetAdapter versionedAssets,
                         AssetVersionRepository assetVersions,
                         InheritanceResolver inheritanceResolver,
                         RuntimeReleasePathwaySelector runtimePathways) {
        this(templates, nodes, milestones, edges, patientPathways, variances, clocks,
            metricBindings, outcomeBindings, evaluationIndicators, contextSnapshots, progressor, auditRecorder, transitions, diagnoseAssembler, json,
            followupHandoff, PathwayWorklistPort.noop(), EngineDomainEventPort.noop(), safetyGuard,
            versionedAssets, assetVersions, inheritanceResolver, runtimePathways);
    }

    /**
     * 创建临床路径草稿，并一次性持久化临床节点、路径边和专病指标绑定。
     *
     * <p>路径是独立版本资产，不依附旧容器；版本号由稳定路径编码自动递增。
     */
    @Transactional
    public PathwayTemplateDetailResponse createTemplate(PathwayTemplateCreateRequest request) {
        String tenantId = requireCurrentTenant();
        String traceId = RequestContext.currentTraceId();
        String actor = currentActor();
        Instant now = Instant.now();
        int templateVersion = allocateNextTemplateVersion(tenantId, request.templateCode());
        String templateId = "pt-" + UUID.randomUUID();
        PathwayTemplate template;
        try {
            template = templates.save(new PathwayTemplate(
                null, templateId, tenantId, request.templateCode(),
                request.name(), request.diseaseCode(), templateVersion, request.templateLevel(),
                PathwayTemplateStatus.DRAFT, request.entryMode(), request.startNodeCode(), request.sourceRef(),
                request.description(), writeJson(request.entryCriteria()), writeJson(request.exitCriteria()),
                now, actor, now, actor, traceId));
        } catch (DuplicateKeyException exception) {
            throw new ApiException(
                ErrorCode.CONFLICT,
                "路径版本并发创建冲突，请刷新后重试: "
                    + request.templateCode() + "@" + templateVersion,
                exception
            );
        }
        validateMilestoneBindings(request.milestones(), request.nodes());
        validateOutcomeBindings(request.outcomeBindings(), request.milestones(), tenantId);
        List<PathwayMilestone> savedMilestones = nullToEmpty(request.milestones()).stream()
            .map(milestone -> milestones.save(new PathwayMilestone(
                null, "pm-" + UUID.randomUUID(), tenantId, templateId,
                milestone.phaseCode(), milestone.phaseName(), milestone.milestoneCode(),
                milestone.name(), milestone.dayOffset(),
                milestone.expectedOffsetMinutes(), writeJson(milestone.achievementCriteria()),
                safeInt(milestone.sortOrder()), now, actor, now, actor, traceId)))
            .toList();
        List<PathwayNode> savedNodes = nullToEmpty(request.nodes()).stream()
            .map(node -> nodes.save(new PathwayNode(
                null, "pn-" + UUID.randomUUID(), tenantId, templateId, node.nodeCode(),
                node.name(), node.nodeType(), node.milestoneCode(), safeInt(node.sortOrder()),
                node.responsibleRole(), node.accountableRole(), writeJson(node.consultedRoles()),
                writeJson(node.informedRoles()), writeJson(node.dependency()), node.timeWindowMinutes(),
                Boolean.TRUE.equals(node.terminal()), Boolean.TRUE.equals(node.disabled()), writeJson(node.config()),
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
                null, "smb-" + UUID.randomUUID(), tenantId, templateId,
                binding.nodeCode(), binding.metricCode(),
                Boolean.TRUE.equals(binding.required()), now, actor, now, actor, traceId)))
            .toList();
        List<PathwayOutcomeBinding> savedOutcomeBindings = nullToEmpty(request.outcomeBindings()).stream()
            .map(binding -> outcomeBindings.save(new PathwayOutcomeBinding(
                null,
                "pob-" + UUID.randomUUID(),
                tenantId,
                templateId,
                binding.scope(),
                normalizedOutcomeRefCode(binding.scope(), binding.refCode()),
                binding.indicatorCode().trim(),
                now,
                actor,
                now,
                actor,
                traceId)))
            .toList();

        validatePathwayDraftGraph(
            template, savedMilestones, savedNodes, savedEdges, savedBindings, savedOutcomeBindings);
        String assetContent = pathwayContent(
            template, savedMilestones, savedNodes, savedEdges, savedBindings, savedOutcomeBindings);
        AssetVersion assetVersion = versionedAssets.registerDraft(new AssetVersionRegisterCommand(
            template.tenantId(),
            VersionedAssetType.PATHWAY,
            template.templateCode(),
            releaseOrgScope(template),
            releaseApplicableScope(template),
            assetContent,
            null,
            template.sourceRef(),
            actor,
            traceId,
            AssetVersionSafetyPolicy.NORMAL,
            null,
            pathwayDependencyDeclarations(
                template, savedMilestones, savedNodes, savedEdges, savedOutcomeBindings)
        ));
        registerDefaultPathwayTriggerBindings(assetVersion, actor, traceId);
        transitions.record(TEMPLATE_ENTITY, templateId, null, PathwayTemplateStatus.DRAFT.name(),
            "CREATE_PATHWAY_TEMPLATE", null);
        auditRecorder.record(AuditAction.CREATE, TEMPLATE_ENTITY, templateId,
            "创建临床路径 " + request.templateCode());
        return new PathwayTemplateDetailResponse(
            template, savedMilestones, savedNodes, savedEdges, savedBindings, savedOutcomeBindings,
            nextTemplateVersionNo(template), assetVersion.status(), traceId);
    }

    private void registerDefaultPathwayTriggerBindings(
            AssetVersion assetVersion,
            String actor,
            String traceId) {
        if (triggerBindings == null) {
            return;
        }
        triggerBindings.replaceBindings(
            assetVersion,
            List.of(
                new AssetTriggerBindingInput(
                    "patient-view",
                    AssetTriggerPurpose.PATHWAY_ENTRY_CANDIDATE,
                    List.of("patient.mpi", "encounters[].encounterId")),
                new AssetTriggerBindingInput(
                    "patient-view",
                    AssetTriggerPurpose.PATHWAY_PROGRESS,
                    List.of("patientPathwayId"))
            ),
            actor,
            traceId
        );
    }

    private AssetVersion requirePathwayAssetVersion(PathwayTemplate template) {
        return findPathwayAssetVersion(template)
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_PATHWAY_004,
                "路径缺少统一资产版本，禁止发布: "
                    + template.templateCode() + "@" + template.templateVersion()
            ));
    }

    private Optional<AssetVersion> findPathwayAssetVersion(PathwayTemplate template) {
        return assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            template.tenantId(),
            VersionedAssetType.PATHWAY,
            template.templateCode(),
            AssetVersionNumbers.canonical(template.templateVersion())
        );
    }

    private AssetVersion requireRuntimePathwayAssetVersion(PathwayTemplate template, String patientId) {
        AssetVersion assetVersion = findPathwayAssetVersion(template)
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_PATHWAY_005,
                "临床路径缺少统一资产版本，不能入径: "
                    + template.templateCode() + "@" + template.templateVersion()
            ));
        if (assetVersion.status() == AssetVersionStatus.PUBLISHED) {
            return assetVersion;
        }
        throw new ApiException(
            ErrorCode.ENG_PATHWAY_005,
            "临床路径尚未进入当前机构生效版本，不能入径: "
                + template.templateCode() + "@" + template.templateVersion()
        );
    }

    private boolean hasActivePathwayAssetVersion(PathwayTemplate template) {
        return findPathwayAssetVersion(template)
            .filter(assetVersion -> assetVersion.status() == AssetVersionStatus.PUBLISHED)
            .isPresent();
    }

    private static String releaseOrgScope(PathwayTemplate template) {
        return null;
    }

    private static String releaseApplicableScope(PathwayTemplate template) {
        return "disease:" + notBlank(template.diseaseCode(), "ALL");
    }

    private String pathwayContent(PathwayTemplate template) {
        return pathwayContent(
            template,
            milestones.findByTemplateIdAndTenantIdOrderBySortOrderAsc(
                template.templateId(), template.tenantId()),
            nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc(template.templateId(), template.tenantId()),
            edges.findByTemplateIdAndTenantIdOrderByPriorityAsc(template.templateId(), template.tenantId()),
            metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc(
                template.templateId(), template.tenantId()),
            outcomeBindings.findByTemplateIdAndTenantIdOrderByScopeAscRefCodeAscIndicatorCodeAsc(
                template.templateId(), template.tenantId())
        );
    }

    private String pathwayContent(
            PathwayTemplate template,
            List<PathwayMilestone> graphMilestones,
            List<PathwayNode> graphNodes,
            List<PathwayEdge> graphEdges,
            List<SpecialtyMetricBinding> graphBindings,
            List<PathwayOutcomeBinding> graphOutcomeBindings) {
        return writeObject(new PathwayAssetContent(
            template.templateCode(),
            template.name(),
            template.diseaseCode(),
            template.templateVersion(),
            template.templateLevel(),
            template.entryMode(),
            template.startNodeCode(),
            template.sourceRef(),
            template.description(),
            template.entryCriteriaJson(),
            template.exitCriteriaJson(),
            nullToEmpty(graphMilestones).stream().map(PathwayMilestoneAssetContent::from).toList(),
            nullToEmpty(graphNodes).stream().map(PathwayNodeAssetContent::from).toList(),
            nullToEmpty(graphEdges).stream().map(PathwayEdgeAssetContent::from).toList(),
            nullToEmpty(graphBindings).stream().map(PathwayMetricAssetContent::from).toList(),
            nullToEmpty(graphOutcomeBindings).stream().map(PathwayOutcomeAssetContent::from).toList()
        ));
    }

    private List<AssetDependencyDeclaration> pathwayDependencyDeclarations(
            PathwayTemplate template,
            List<PathwayMilestone> graphMilestones,
            List<PathwayNode> graphNodes,
            List<PathwayEdge> graphEdges,
            List<PathwayOutcomeBinding> graphOutcomeBindings) {
        LinkedHashSet<AssetDependencyDeclaration> declarations = new LinkedHashSet<>();
        addDependencyDeclarations(
            declarations, template.entryCriteriaJson(), "临床路径入径条件 " + template.templateCode());
        addDependencyDeclarations(
            declarations, template.exitCriteriaJson(), "临床路径出径条件 " + template.templateCode());
        for (PathwayMilestone milestone : nullToEmpty(graphMilestones)) {
            addDependencyDeclarations(
                declarations,
                milestone.achievementCriteriaJson(),
                "路径里程碑达成条件 " + milestone.milestoneCode());
        }
        for (PathwayNode node : nullToEmpty(graphNodes)) {
            addDependencyDeclarations(
                declarations, node.dependencyJson(), "路径节点依赖 " + node.nodeCode());
            addDependencyDeclarations(
                declarations, node.configJson(), "路径节点配置 " + node.nodeCode());
        }
        for (PathwayEdge edge : nullToEmpty(graphEdges)) {
            addDependencyDeclarations(
                declarations, edge.conditionJson(), "路径边条件 " + edge.edgeCode());
        }
        for (PathwayOutcomeBinding binding : nullToEmpty(graphOutcomeBindings)) {
            declarations.add(new AssetDependencyDeclaration(
                VersionedAssetType.EVALUATION,
                binding.indicatorCode(),
                null,
                null,
                com.medkernel.engine.versioning.AssetDependencyKind.EVALUATION
            ));
        }
        return List.copyOf(declarations);
    }

    private void addDependencyDeclarations(
            Set<AssetDependencyDeclaration> target,
            String jsonText,
            String ownerLabel) {
        target.addAll(AssetReferenceConsistency.dependencyDeclarations(
            readJsonOrEmpty(jsonText, ownerLabel, ErrorCode.ENG_PATHWAY_004)));
    }

    private EffectivePathwayGraph effectiveGraphFor(PathwayTemplate template, String tenantId) {
        List<PathwayNode> graphNodes = activeNodes(
            nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc(template.templateId(), tenantId));
        Set<String> activeNodeCodes = pathwayNodeCodeSet(graphNodes);
        List<PathwayMilestone> graphMilestones =
            milestones.findByTemplateIdAndTenantIdOrderBySortOrderAsc(template.templateId(), tenantId);
        return new EffectivePathwayGraph(
            graphMilestones,
            graphNodes,
            activeEdges(
                edges.findByTemplateIdAndTenantIdOrderByPriorityAsc(template.templateId(), tenantId),
                activeNodeCodes),
            metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc(template.templateId(), tenantId)
                .stream()
                .filter(binding -> activeNodeCodes.contains(binding.nodeCode()))
                .toList(),
            outcomeBindings.findByTemplateIdAndTenantIdOrderByScopeAscRefCodeAscIndicatorCodeAsc(
                template.templateId(), tenantId));
    }

    /**
     * 按状态、病种和路径编码过滤分页查询临床路径。
     *
     * <p>过滤条件为 {@code null} 时不进入 SQL；分页总数与行集分别由仓库 count/page 查询提供。
     */
    @Transactional(readOnly = true)
    public PageResponse<PathwayTemplate> listTemplates(PathwayTemplateFilter filter, PageRequest page) {
        PageRequest safePage = page == null ? PageRequest.defaults() : page;
        String tenantId = requireCurrentTenant();
        String status = filter == null || filter.status() == null ? null : filter.status().name();
        String diseaseCode = filter == null ? null : filter.diseaseCode();
        String templateCode = filter == null ? null : filter.templateCode();
        String keyword = filter == null ? null : keywordLike(filter.keyword());
        if (requiresEffectiveTemplateMerge(tenantId, status)) {
            String platformStatus = PathwayTemplateStatus.PUBLISHED.name();
            long total = templates.countEffectiveByFilter(
                tenantId, PlatformTenant.ID, status, platformStatus,
                diseaseCode, templateCode, keyword);
            if (total == 0) {
                return PageResponse.empty(safePage);
            }
            List<PathwayTemplate> rows = templates.pageEffectiveByFilter(
                tenantId, PlatformTenant.ID, status, platformStatus,
                diseaseCode, templateCode, keyword,
                safePage.offset(), safePage.safeSize());
            return PageResponse.of(rows, safePage, total);
        }
        long total = templates.countByFilter(tenantId, status, diseaseCode, templateCode, keyword);
        if (total == 0) {
            return PageResponse.empty(safePage);
        }
        List<PathwayTemplate> rows = templates.pageByFilter(
            tenantId, status, diseaseCode, templateCode, keyword,
            safePage.offset(), safePage.safeSize());
        return PageResponse.of(rows, safePage, total);
    }

    /**
     * 服务端分页查询患者路径运行实例。
     *
     * <p>列表只读取当前租户真实运行事实，支持按患者和状态过滤，供患者路径页展示刷新后仍存在的在径实例。
     */
    @Transactional(readOnly = true)
    public PageResponse<PatientPathway> listPatientPathways(String patientId,
                                                            PatientPathwayStatus status,
                                                            PageRequest page) {
        PageRequest safePage = page == null ? PageRequest.defaults() : page;
        String tenantId = requireCurrentTenant();
        String statusName = status == null ? null : status.name();
        long total = patientPathways.countByTenantIdAndFilters(tenantId, patientId, statusName);
        List<PatientPathway> rows = total == 0 ? List.of()
            : patientPathways.pageByTenantIdAndFilters(
                tenantId, patientId, statusName, safePage.offset(), safePage.safeSize());
        return PageResponse.of(rows, safePage, total);
    }

    /**
     * 装配临床路径详情。
     *
     * <p>返回路径主表、按阶段顺序排列的里程碑、按顺序排列的节点、按优先级排列的边和按节点排列的指标绑定。
     */
    @Transactional(readOnly = true)
    public PathwayTemplateDetailResponse templateDetail(String templateId) {
        String tenantId = requireCurrentTenant();
        EffectivePathwayTemplate effective = findEffectiveTemplate(templateId, tenantId);
        PathwayTemplate template = effective.template();
        AssetVersion assetVersion = requirePathwayAssetVersion(template);
        EffectivePathwayGraph graph = effectiveGraphFor(template, effective.sourceTenantId());
        return new PathwayTemplateDetailResponse(
            template,
            graph.milestones(),
            graph.nodes(),
            graph.edges(),
            graph.metricBindings(),
            graph.outcomeBindings(),
            nextTemplateVersionNo(template),
            assetVersion.status(),
            RequestContext.currentTraceId());
    }

    private int nextTemplateVersionNo(PathwayTemplate template) {
        return templates.findTopByTenantIdAndTemplateCodeOrderByTemplateVersionDesc(
                template.tenantId(), template.templateCode())
            .map(PathwayTemplate::templateVersion)
            .filter(Objects::nonNull)
            .map(version -> version + 1)
            .orElse(template.templateVersion() + 1);
    }

    private int allocateNextTemplateVersion(String tenantId, String templateCode) {
        return templates.findTopByTenantIdAndTemplateCodeOrderByTemplateVersionDesc(
                tenantId, templateCode)
            .map(PathwayTemplate::templateVersion)
            .filter(Objects::nonNull)
            .map(version -> version + 1)
            .orElse(1);
    }

    /**
     * 查询当前患者快照和临床触发点下可供医师确认的入径候选。
     *
     * <p>候选只来自快照锁定的机构生效版本；响应不提供机构生效版本或版本选择参数，
     * 入径时会重新执行同一选择校验，避免页面缓存绕过运行真相。
     */
    public PathwayEntryCandidateResponse entryCandidates(
            String contextSnapshotId,
            String triggerPoint) {
        String tenantId = requireCurrentTenant();
        ContextSnapshotResponse snapshot = contextSnapshots.findById(contextSnapshotId);
        if (snapshot.status() != ContextSnapshotStatus.ACTIVE
                || snapshot.resources() == null
                || snapshot.resources().patient() == null) {
            throw new ApiException(
                ErrorCode.ENG_PATHWAY_001,
                "路径候选只能基于包含标准患者资源的已生效上下文"
            );
        }
        if (isBlank(snapshot.runtimeReleaseId())) {
            throw new ApiException(
                ErrorCode.ENG_PATHWAY_001,
                "路径候选快照缺少机构生效版本，不能确定路径版本"
            );
        }
        RuntimePathwaySelection selection = runtimePathways.selectEntryCandidates(
            tenantId,
            snapshot.runtimeReleaseId(),
            triggerPoint
        );
        List<PathwayEntryCandidate> candidates = selection.pathways().stream()
            .map(pathway -> new PathwayEntryCandidate(
                pathway.templateId(),
                pathway.templateCode(),
                pathway.name(),
                pathway.diseaseCode()
            ))
            .toList();
        return new PathwayEntryCandidateResponse(
            snapshot.snapshotId(),
            triggerPoint,
            candidates
        );
    }

    /**
     * 为患者创建路径实例并进入临床路径起始节点或请求指定起点。
     *
     * <p>仅允许基于统一版本状态为 {@code ACTIVE} 的临床路径入径；成功后创建首个
     * {@link ClinicalClock} 关键时钟。
     */
    @Transactional
    public PatientPathwayDetailResponse enterPatientPathway(PatientPathwayEnterRequest request) {
        String tenantId = requireCurrentTenant();
        ContextSnapshotResponse snapshot = contextSnapshots.findById(request.contextSnapshotId());
        if (snapshot.status() != ContextSnapshotStatus.ACTIVE || snapshot.resources() == null
                || snapshot.resources().patient() == null) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_001,
                "患者入径只能使用包含标准患者资源的已生效上下文");
        }
        String patientId = snapshot.resources().patient().mpi();
        String encounterId = snapshot.resources().encounters().isEmpty()
            ? null
            : snapshot.resources().encounters().getFirst().encounterId();
        if (isBlank(snapshot.runtimeReleaseId())) {
            throw new ApiException(
                ErrorCode.ENG_PATHWAY_001,
                "患者入径快照缺少机构生效版本，不能确定路径版本"
            );
        }
        RuntimePathwayReference selected = runtimePathways.requireEntryCandidate(
            tenantId,
            snapshot.runtimeReleaseId(),
            request.triggerPoint(),
            request.templateId()
        );
        EffectivePathwayTemplate effective = runtimeTemplate(selected);
        PathwayTemplate template = effective.template();
        safetyGuard.assertPathwayTemplateAllowed(template);
        validateEntryCriteria(template, snapshot.resources());
        EffectivePathwayGraph graph = effectiveGraphFor(template, effective.sourceTenantId());
        String startNodeCode = isBlank(request.startNodeCode()) ? template.startNodeCode() : request.startNodeCode();
        PathwayNode startNode = findEntryStartNode(template, effective.sourceTenantId(), graph, startNodeCode)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_PATHWAY_006,
                "入径起始节点不存在: " + startNodeCode));
        List<SpecialtyMetricBinding> entryMetricBindings =
            entryMetricBindings(template, effective.sourceTenantId(), graph);
        String traceId = RequestContext.currentTraceId();
        String actor = currentActor();
        Instant now = Instant.now();
        String patientPathwayId = "pp-" + UUID.randomUUID();
        PatientPathway runtime = patientPathways.save(new PatientPathway(
            null, patientPathwayId, tenantId, patientId, encounterId,
            template.templateId(), snapshot.runtimeReleaseId(), selected.pathwayVersionId(),
            startNode.nodeCode(), PatientPathwayStatus.NODE_EXECUTING,
            now, null, null, null, null, now, actor, now, actor, traceId));
        List<PathwayCoordinationWarning> coordinationWarnings =
            coordinationWarningsForPatient(patientId, tenantId, patientPathwayId, template, graph);
        ClinicalClock startClock = clocks.save(newClock(
            tenantId, patientPathwayId, startNode, metricCodeForNode(entryMetricBindings, startNode.nodeCode()),
            snapshot.resources(), now, now, actor, traceId));
        openNodeWorklist(runtime, startNode, startClock, actor, traceId);
        transitions.record(PATIENT_PATHWAY_ENTITY, patientPathwayId, null,
            PatientPathwayStatus.NODE_EXECUTING.name(), "ENTER_PATHWAY", null);
        auditRecorder.record(AuditAction.CREATE, PATIENT_PATHWAY_ENTITY, patientPathwayId,
            "患者入径 " + template.templateCode());
        List<ClinicalClock> runtimeClocks = List.of(startClock);
        return new PatientPathwayDetailResponse(
            runtime,
            milestoneStatuses(runtime, graph.milestones(), graph.nodes(), runtimeClocks),
            List.of(),
            runtimeClocks,
            graph.outcomeBindings(),
            coordinationWarnings,
            traceId);
    }

    private void validateEntryCriteria(PathwayTemplate template, ContextSnapshotResources resources) {
        JsonNode criteria = readCriteriaJson(template.entryCriteriaJson(), "入径");
        if (criteria == null || criteria.isNull() || criteria.isMissingNode() || criteria.isEmpty()) {
            return;
        }
        JsonNode context = criteriaContext(resources);
        JsonNode include = criteria.path("include");
        boolean includeMatched = !isConfiguredCondition(include) || evaluateCriteria(include, context, "纳入").matched();
        if (!includeMatched && template.entryMode() != PathwayEntryMode.MANUAL_CONFIRM) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_001,
                "患者上下文不符合路径入径纳入标准: " + template.templateCode());
        }
        JsonNode exclude = criteria.path("exclude");
        if (isConfiguredCondition(exclude) && evaluateCriteria(exclude, context, "排除").matched()) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_001,
                "患者上下文命中路径入径排除标准: " + template.templateCode());
        }
    }

    private Optional<PathwayNode> findEntryStartNode(
            PathwayTemplate template,
            String sourceTenantId,
            EffectivePathwayGraph graph,
            String startNodeCode) {
        Optional<PathwayNode> effectiveStart = graph.nodes().stream()
            .filter(node -> Objects.equals(node.nodeCode(), startNodeCode))
            .findFirst();
        if (effectiveStart.isPresent() || !graph.nodes().isEmpty()) {
            return effectiveStart;
        }
        return nodes.findByTemplateIdAndTenantIdAndNodeCode(
            template.templateId(), sourceTenantId, startNodeCode);
    }

    private List<SpecialtyMetricBinding> entryMetricBindings(
            PathwayTemplate template,
            String sourceTenantId,
            EffectivePathwayGraph graph) {
        if (!graph.metricBindings().isEmpty() || !graph.nodes().isEmpty()) {
            return graph.metricBindings();
        }
        return metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc(
            template.templateId(), sourceTenantId);
    }

    private List<PathwayCoordinationWarning> coordinationWarningsForPatient(
            String patientId,
            String tenantId,
            String currentPatientPathwayId,
            PathwayTemplate currentTemplate,
            EffectivePathwayGraph currentGraph) {
        if (isBlank(patientId)) {
            return List.of();
        }
        List<PathwayNode> currentOrderSetNodes = orderSetNodes(currentGraph.nodes());
        if (currentOrderSetNodes.isEmpty()) {
            return List.of();
        }
        List<PathwayCoordinationWarning> warnings = new ArrayList<>();
        for (PatientPathway activeRuntime
                : patientPathways.findActiveByTenantIdAndPatientIdOrderByEnteredAtDesc(tenantId, patientId, 0, 20)) {
            if (Objects.equals(activeRuntime.patientPathwayId(), currentPatientPathwayId)) {
                continue;
            }
            EffectivePathwayTemplate otherEffective =
                findPinnedRuntimeTemplate(activeRuntime.templateId(), tenantId);
            EffectivePathwayGraph otherGraph =
                effectiveGraphFor(otherEffective.template(), otherEffective.sourceTenantId());
            appendOrderSetCoordinationWarnings(
                warnings,
                currentPatientPathwayId,
                currentTemplate,
                currentOrderSetNodes,
                activeRuntime,
                otherEffective.template(),
                orderSetNodes(otherGraph.nodes()));
        }
        return warnings;
    }

    private void appendOrderSetCoordinationWarnings(
            List<PathwayCoordinationWarning> warnings,
            String currentPatientPathwayId,
            PathwayTemplate currentTemplate,
            List<PathwayNode> currentNodes,
            PatientPathway activeRuntime,
            PathwayTemplate otherTemplate,
            List<PathwayNode> otherNodes) {
        for (PathwayNode currentNode : currentNodes) {
            String currentOrderSetRef = nodeConfigText(currentNode, "orderSetRef");
            if (isBlank(currentOrderSetRef)) {
                continue;
            }
            for (PathwayNode otherNode : otherNodes) {
                String otherOrderSetRef = nodeConfigText(otherNode, "orderSetRef");
                if (!Objects.equals(currentOrderSetRef, otherOrderSetRef)) {
                    continue;
                }
                warnings.add(new PathwayCoordinationWarning(
                    PathwayCoordinationWarningType.ORDER_SET_CONFLICT,
                    "WARN",
                    currentPatientPathwayId,
                    currentTemplate.templateId(),
                    currentNode.nodeCode(),
                    activeRuntime.patientPathwayId(),
                    otherTemplate.templateId(),
                    otherNode.nodeCode(),
                    currentOrderSetRef,
                    "患者存在并行路径共享医嘱套餐 " + currentOrderSetRef + "，仅提示协调，不自动改医嘱"));
            }
        }
    }

    private List<PathwayNode> orderSetNodes(List<PathwayNode> graphNodes) {
        return nullToEmpty(graphNodes).stream()
            .filter(node -> node.nodeType() == PathwayNodeType.ORDER_SET)
            .toList();
    }

    private void validateExitCriteria(PathwayTemplate template, ContextSnapshotResources resources) {
        JsonNode criteria = readCriteriaJson(template.exitCriteriaJson(), "出径");
        if (criteria == null || criteria.isNull() || criteria.isMissingNode() || criteria.isEmpty()) {
            return;
        }
        JsonNode context = criteriaContext(resources);
        JsonNode include = criteria.path("include");
        if (isConfiguredCondition(include) && !evaluateCriteria(include, context, "出径纳入").matched()) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_001,
                "患者上下文不符合路径出径纳入标准: " + template.templateCode());
        }
        JsonNode exclude = criteria.path("exclude");
        if (isConfiguredCondition(exclude) && evaluateCriteria(exclude, context, "出径排除").matched()) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_001,
                "患者上下文命中路径出径排除标准: " + template.templateCode());
        }
    }

    private JsonNode readCriteriaJson(String criteriaJson, String label) {
        if (isBlank(criteriaJson)) {
            return null;
        }
        try {
            return json.readTree(criteriaJson);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_001,
                "路径" + label + "条件 JSON 无法解析", exception);
        }
    }

    private boolean isConfiguredCondition(JsonNode condition) {
        if (condition == null || condition.isMissingNode() || condition.isNull()) {
            return false;
        }
        if (condition.has("all") && condition.get("all").isArray()) {
            return !condition.get("all").isEmpty();
        }
        if (condition.has("any") && condition.get("any").isArray()) {
            return !condition.get("any").isEmpty();
        }
        if (condition.has("not")) {
            return isConfiguredCondition(condition.get("not"));
        }
        return condition.has("fact") || condition.has("expr");
    }

    private ConditionEvaluation evaluateCriteria(JsonNode condition, JsonNode context, String label) {
        try {
            return conditionEvaluator.evaluate(condition, context);
        } catch (ApiException exception) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_001,
                "路径" + label + "条件无法求值: " + exception.getMessage(), exception);
        }
    }

    private JsonNode criteriaContext(ContextSnapshotResources resources) {
        return ContextFactBridge.conditionContext(json, resources);
    }

    /**
     * 查看患者路径实例详情。
     *
     * <p>返回路径运行时状态、里程碑达成判定、按创建时间排列的变异记录和按启动时间排列的关键时钟。
     */
    @Transactional
    public PatientPathwayDetailResponse patientDetail(String patientPathwayId) {
        String tenantId = requireCurrentTenant();
        PatientPathway runtime = findPatientPathway(patientPathwayId, tenantId);
        EffectivePathwayTemplate effective = findPinnedRuntimeTemplate(runtime.templateId(), tenantId);
        EffectivePathwayGraph graph = effectiveGraphFor(effective.template(), effective.sourceTenantId());
        List<ClinicalClock> runtimeClocks =
            projectClockSla(
                runtime,
                clocks.findByPatientPathwayIdAndTenantIdOrderByStartedAtAsc(patientPathwayId, tenantId),
                Instant.now());
        return new PatientPathwayDetailResponse(
            runtime,
            milestoneStatuses(runtime, graph.milestones(), graph.nodes(), runtimeClocks),
            variances.findByPatientPathwayIdAndTenantIdOrderByCreatedAtAsc(patientPathwayId, tenantId),
            runtimeClocks,
            graph.outcomeBindings(),
            coordinationWarningsForPatient(
                runtime.patientId(), tenantId, runtime.patientPathwayId(), effective.template(), graph),
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
    @Transactional
    public List<ClinicalClock> clocks(String patientPathwayId) {
        String tenantId = requireCurrentTenant();
        PatientPathway runtime = findPatientPathway(patientPathwayId, tenantId);
        return projectClockSla(
            runtime,
            clocks.findByPatientPathwayIdAndTenantIdOrderByStartedAtAsc(patientPathwayId, tenantId),
            Instant.now());
    }

    /**
     * 接收临床事件统一上下文，作为临床路径后续入径/推进监听的稳定入口。
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
     * 基于临床路径图和可选目标节点序列试运行路径推进。
     *
     * <p>试运行只返回节点轨迹与最终状态，不创建患者路径、不写变异、不创建关键时钟。
     */
    @Transactional(readOnly = true)
    public PathwaySimulationResponse simulate(String templateId, PathwaySimulateRequest request) {
        String tenantId = requireCurrentTenant();
        EffectivePathwayTemplate effective = findEffectiveTemplate(templateId, tenantId);
        PathwayTemplate template = effective.template();
        EffectivePathwayGraph graph = effectiveGraphFor(template, effective.sourceTenantId());
        String startNodeCode = request == null || isBlank(request.startNodeCode())
            ? template.startNodeCode() : request.startNodeCode();
        List<String> requestedTargets = request == null ? List.of() : request.requestedNextNodeCodes();
        PathwaySimulationMode mode = request == null
            ? PathwaySimulationMode.SINGLE_SNAPSHOT : request.simulationMode();
        if (mode == PathwaySimulationMode.QUEUE_REPLAY) {
            List<String> replayIds = replaySnapshotIds(request);
            if (replayIds.isEmpty()) {
                throw new ApiException(ErrorCode.ENG_PATHWAY_001, "路径队列回放必须提供至少一个上下文快照");
            }
            List<PathwaySimulationReplayStep> replaySteps = replayIds.stream()
                .map(snapshotId -> {
                    ContextSnapshotResponse snapshot = simulationSnapshot(snapshotId);
                    validateEntryCriteria(template, snapshot.resources());
                    return simulateStep(tenantId, graph, startNodeCode, requestedTargets, snapshot);
                })
                .toList();
            PathwaySimulationReplayStep first = replaySteps.getFirst();
            return new PathwaySimulationResponse(
                templateId,
                first.snapshotId(),
                first.nodeTrajectory(),
                first.finalStatus(),
                first.contextQualityStatus(),
                first.missingFields(),
                first.mappingStatus(),
                first.contextResourceCounts(),
                mode,
                replaySteps,
                request.timeMachineAt(),
                RequestContext.currentTraceId());
        }
        ContextSnapshotResponse snapshot = request == null || isBlank(request.snapshotId())
            ? null : simulationSnapshot(request.snapshotId());
        if (snapshot != null) {
            validateEntryCriteria(template, snapshot.resources());
        }
        PathwaySimulationReplayStep step = simulateStep(tenantId, graph, startNodeCode, requestedTargets, snapshot);
        return new PathwaySimulationResponse(
            templateId,
            step.snapshotId(),
            step.nodeTrajectory(),
            step.finalStatus(),
            step.contextQualityStatus(),
            step.missingFields(),
            step.mappingStatus(),
            step.contextResourceCounts(),
            mode,
            mode == PathwaySimulationMode.TIME_MACHINE ? List.of(step) : List.of(),
            request == null ? null : request.timeMachineAt(),
            RequestContext.currentTraceId());
    }

    private PathwaySimulationReplayStep simulateStep(
            String tenantId,
            EffectivePathwayGraph graph,
            String startNodeCode,
            List<String> requestedTargets,
            ContextSnapshotResponse snapshot) {
        Map<String, Object> facts = snapshot == null ? Map.of() : contextFacts(snapshot.resources());
        List<String> safeRequestedTargets = nullToEmpty(requestedTargets);
        ArrayList<String> trajectory = new ArrayList<>();
        String currentNode = startNodeCode;
        trajectory.add(currentNode);
        PatientPathwayStatus finalStatus = PatientPathwayStatus.NODE_EXECUTING;
        int maxSteps = Math.max(graph.nodes().size(), 1);
        for (int index = 0; index < maxSteps; index += 1) {
            String requestedTarget = index < safeRequestedTargets.size()
                ? safeRequestedTargets.get(index)
                : null;
            PathwayProgressDecision decision = progressor.advance(new PathwayProgressCommand(
                new PathwayGraph(graph.nodes(), graph.edges()), currentNode,
                PathwayAdvanceEventType.COMPLETE, requestedTarget, facts,
                snapshot == null ? null : snapshot.runtimeReleaseId(),
                tenantId));
            finalStatus = decision.status();
            if (decision.nextNodeCode() == null) {
                return new PathwaySimulationReplayStep(
                    snapshot == null ? null : snapshot.snapshotId(),
                    trajectory,
                    finalStatus,
                    snapshot == null ? null : snapshot.qualityStatus(),
                    snapshot == null ? List.of() : snapshot.missingFields(),
                    snapshot == null ? Map.of() : snapshot.mappingStatus(),
                    snapshot == null ? Map.of() : contextResourceCounts(snapshot.resources()));
            }
            currentNode = decision.nextNodeCode();
            trajectory.add(currentNode);
        }
        throw new ApiException(ErrorCode.ENG_PATHWAY_004,
            "路径试运行超过最大推进步数 " + maxSteps + "，请检查路径图是否成环");
    }

    private List<String> replaySnapshotIds(PathwaySimulateRequest request) {
        if (request == null) {
            return List.of();
        }
        LinkedHashMap<String, String> ids = new LinkedHashMap<>();
        for (String snapshotId : nullToEmpty(request.replaySnapshotIds())) {
            if (!isBlank(snapshotId)) {
                ids.put(snapshotId, snapshotId);
            }
        }
        if (!isBlank(request.snapshotId())) {
            ids.put(request.snapshotId(), request.snapshotId());
        }
        return List.copyOf(ids.values());
    }

    private ContextSnapshotResponse simulationSnapshot(String snapshotId) {
        return runtimeSnapshot(snapshotId);
    }

    private ContextSnapshotResponse runtimeSnapshot(String snapshotId) {
        return contextSnapshots.findById(snapshotId);
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
        RuntimePathwayReference selected = runtimePathways.requireProgressPathway(
            tenantId,
            runtime.runtimeReleaseId(),
            runtime.pathwayVersionId(),
            request.triggerPoint()
        );
        if (!runtime.templateId().equals(selected.templateId())) {
            throw new ApiException(
                ErrorCode.ENG_PATHWAY_006,
                "患者临床路径与固定路径版本不一致：" + runtime.patientPathwayId()
            );
        }
        EffectivePathwayTemplate effective = runtimeTemplate(selected);
        EffectivePathwayGraph graph = effectiveGraphFor(effective.template(), effective.sourceTenantId());
        ContextSnapshotResponse snapshot = isBlank(request.snapshotId())
            ? null : runtimeSnapshot(request.snapshotId());
        if (request.eventType() == PathwayAdvanceEventType.EXIT) {
            validateExitCriteria(effective.template(), snapshot == null ? null : snapshot.resources());
        }
        Map<String, Object> facts = snapshot == null ? Map.of() : contextFacts(snapshot.resources());
        PathwayAdvanceEventType progressEventType = request.eventType() == PathwayAdvanceEventType.VARIANCE
            && request.resolutionDecision() == VarianceResolutionDecision.TERMINATE
            ? PathwayAdvanceEventType.EXIT : request.eventType();
        String requestedNextNodeCode = progressEventType == PathwayAdvanceEventType.EXIT
            ? null : request.requestedNextNodeCode();
        PathwayProgressDecision decision = progressor.advance(new PathwayProgressCommand(
            new PathwayGraph(graph.nodes(), graph.edges()), currentNodeCode,
            progressEventType, requestedNextNodeCode, facts,
            runtime.runtimeReleaseId(),
            tenantId));

        String traceId = RequestContext.currentTraceId();
        String actor = currentActor();
        Instant now = Instant.now();
        String varianceId = null;
        ClinicalClock currentClock = runningClockForNode(runtime.patientPathwayId(), tenantId, currentNodeCode);
        if (request.eventType() == PathwayAdvanceEventType.VARIANCE) {
            varianceId = "pv-" + UUID.randomUUID();
            PathwayVariance savedVariance = variances.save(new PathwayVariance(
                null, varianceId, tenantId, runtime.patientPathwayId(), currentNodeCode,
                request.varianceType(), request.varianceReasonCode(), request.varianceReason(),
                request.responsibleRole(), request.resolutionDecision(), request.resolutionAction(),
                request.requestedNextNodeCode(), now, actor, now, actor, traceId));
            domainEvents.pathwayVarianceRecorded(new PathwayVarianceRecordedEvent(
                savedVariance.tenantId(),
                savedVariance.traceId(),
                runtime.runtimeReleaseId(),
                runtime.patientPathwayId(),
                runtime.patientId(),
                runtime.encounterId(),
                savedVariance.varianceId(),
                savedVariance.nodeCode(),
                savedVariance.varianceType().name(),
                savedVariance.reasonCode(),
                savedVariance.responsibleRole(),
                savedVariance.resolutionDecision().name(),
                savedVariance.createdAt()));
        }
        if (decision.status() != PatientPathwayStatus.VARIANCE) {
            completeNodeWorklist(runtime, currentNodeCode, currentClock, decision, now, actor, traceId);
        }
        closeCurrentClocks(runtime.patientPathwayId(), tenantId, currentNodeCode, request.eventType(), now, actor, traceId);
        ClinicalClock nextClock = null;
        PathwayNode nextNode = findNode(graph.nodes(), decision.nextNodeCode());
        if (decision.status() == PatientPathwayStatus.NODE_EXECUTING && nextNode != null) {
            nextClock = clocks.save(newClock(
                tenantId, runtime.patientPathwayId(), nextNode,
                metricCodeForNode(graph.metricBindings(), nextNode.nodeCode()),
                snapshot == null ? null : snapshot.resources(), runtime.enteredAt(), now, actor, traceId));
            openNodeWorklist(runtime, nextNode, nextClock, actor, traceId);
        }

        PatientPathway updated = copyRuntime(runtime, decision, request, now, actor, traceId);
        patientPathways.save(updated);
        transitions.record(PATIENT_PATHWAY_ENTITY, runtime.patientPathwayId(), runtime.status().name(),
            updated.status().name(), "ADVANCE_PATHWAY", null);
        auditRecorder.record(AuditAction.EXECUTE, PATIENT_PATHWAY_ENTITY, runtime.patientPathwayId(),
            "推进患者路径 " + runtime.patientPathwayId());
        PathwayFollowupHandoffResult followup = handoffFollowupIfCompleted(updated, effective.template());
        List<PathwayCoordinationWarning> coordinationWarnings =
            coordinationWarningsForPatient(
                updated.patientId(), tenantId, updated.patientPathwayId(), effective.template(), graph);
        return new PathwayAdvanceResponse(
            runtime.patientPathwayId(), decision.previousNodeCode(), decision.nextNodeCode(),
            decision.status(), varianceId,
            decision.edgeCode(), decision.edgeType(),
            snapshot == null ? null : snapshot.snapshotId(),
            snapshot == null ? null : snapshot.qualityStatus(),
            snapshot == null ? List.of() : snapshot.missingFields(),
            snapshot == null ? Map.of() : snapshot.mappingStatus(),
            snapshot == null ? Map.of() : contextResourceCounts(snapshot.resources()),
            decision.evidence(),
            followup == null ? null : followup.planId(),
            followup == null ? 0 : followup.taskCount(),
            followup == null ? null : followup.status(),
            graph.outcomeBindings(),
            coordinationWarnings,
            traceId);
    }

    /**
     * 生成患者路径实例的诊断解释响应。
     *
     * <p>诊断响应包含路径实例当前状态、路径版本引用、内联证据摘要和 traceId，用于排查路径推进结果。
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

    private JsonNode readJsonOrEmpty(String jsonText, String ownerLabel, ErrorCode errorCode) {
        if (isBlank(jsonText)) {
            return json.createObjectNode();
        }
        try {
            return json.readTree(jsonText);
        } catch (JsonProcessingException exception) {
            throw new ApiException(errorCode, ownerLabel + " JSON 解析失败", exception);
        }
    }

    private PathwayFollowupHandoffResult handoffFollowupIfCompleted(PatientPathway runtime,
                                                                    PathwayTemplate template) {
        if (runtime.status() != PatientPathwayStatus.COMPLETED) {
            return null;
        }
        return followupHandoff.handoff(new PathwayFollowupHandoffCommand(
            runtime.patientPathwayId(),
            runtime.patientId(),
            runtime.encounterId(),
            runtime.templateId(),
            template.diseaseCode(),
            "PATHWAY_COMPLETED",
            runtime.runtimeReleaseId(),
            List.of("QUESTIONNAIRE")));
    }

    private void validateMilestoneBindings(List<PathwayMilestoneRequest> milestoneRequests,
                                           List<PathwayNodeRequest> nodeRequests) {
        Set<String> milestoneCodes = new HashSet<>();
        for (PathwayMilestoneRequest milestone : nullToEmpty(milestoneRequests)) {
            if (isBlank(milestone.phaseCode()) || isBlank(milestone.phaseName())
                    || isBlank(milestone.milestoneCode()) || isBlank(milestone.name())) {
                throw new ApiException(ErrorCode.ENG_PATHWAY_004, "路径阶段里程碑缺少阶段、编码或名称");
            }
            if (!milestoneCodes.add(milestone.milestoneCode())) {
                throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                    "路径阶段里程碑编码重复: " + milestone.milestoneCode());
            }
            if (milestone.dayOffset() != null && milestone.dayOffset() < 0) {
                throw new ApiException(ErrorCode.ENG_PATHWAY_004, "路径里程碑天序不能为负数");
            }
            if (milestone.expectedOffsetMinutes() != null && milestone.expectedOffsetMinutes() < 0) {
                throw new ApiException(ErrorCode.ENG_PATHWAY_004, "路径里程碑预期完成点不能为负数");
            }
        }
        for (PathwayNodeRequest node : nullToEmpty(nodeRequests)) {
            if (!isBlank(node.milestoneCode()) && !milestoneCodes.contains(node.milestoneCode())) {
                throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                    "路径节点引用不存在的里程碑: " + node.nodeCode() + " -> " + node.milestoneCode());
            }
        }
    }

    private void validateOutcomeBindings(
            List<PathwayOutcomeBindingRequest> bindingRequests,
            List<PathwayMilestoneRequest> milestoneRequests,
            String tenantId) {
        Set<String> phaseCodes = new HashSet<>();
        Set<String> milestoneCodes = new HashSet<>();
        for (PathwayMilestoneRequest milestone : nullToEmpty(milestoneRequests)) {
            if (!isBlank(milestone.phaseCode())) {
                phaseCodes.add(milestone.phaseCode());
            }
            if (!isBlank(milestone.milestoneCode())) {
                milestoneCodes.add(milestone.milestoneCode());
            }
        }
        for (PathwayOutcomeBindingRequest binding : nullToEmpty(bindingRequests)) {
            validateOutcomeBindingRef(binding.scope(), binding.refCode(), phaseCodes, milestoneCodes);
            ensureActiveOutcomeIndicator(tenantId, binding.indicatorCode());
        }
    }

    private void validateEffectiveOutcomeBindings(
            List<PathwayOutcomeBinding> bindings,
            List<PathwayMilestone> graphMilestones) {
        Set<String> phaseCodes = new HashSet<>();
        Set<String> milestoneCodes = new HashSet<>();
        for (PathwayMilestone milestone : nullToEmpty(graphMilestones)) {
            if (!isBlank(milestone.phaseCode())) {
                phaseCodes.add(milestone.phaseCode());
            }
            if (!isBlank(milestone.milestoneCode())) {
                milestoneCodes.add(milestone.milestoneCode());
            }
        }
        for (PathwayOutcomeBinding binding : nullToEmpty(bindings)) {
            validateOutcomeBindingRef(binding.scope(), binding.refCode(), phaseCodes, milestoneCodes);
            ensureActiveOutcomeIndicator(binding.tenantId(), binding.indicatorCode());
        }
    }

    private void validateOutcomeBindingRef(
            PathwayOutcomeScope scope,
            String refCode,
            Set<String> phaseCodes,
            Set<String> milestoneCodes) {
        if (scope == null) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004, "结局指标绑定缺少作用域");
        }
        switch (scope) {
            case TEMPLATE -> {
                // 全路径级绑定使用固定 ref，便于唯一约束与资产快照稳定。
            }
            case PHASE -> {
                if (isBlank(refCode) || !phaseCodes.contains(refCode)) {
                    throw new ApiException(ErrorCode.ENG_PATHWAY_004, "结局指标绑定引用不存在的阶段: " + refCode);
                }
            }
            case MILESTONE -> {
                if (isBlank(refCode) || !milestoneCodes.contains(refCode)) {
                    throw new ApiException(ErrorCode.ENG_PATHWAY_004, "结局指标绑定引用不存在的里程碑: " + refCode);
                }
            }
        }
    }

    private void ensureActiveOutcomeIndicator(String tenantId, String indicatorCode) {
        if (isBlank(indicatorCode)) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004, "结局指标绑定缺少指标编码");
        }
        if (evaluationIndicators.findByTenantIdAndIndicatorCodeAndStatus(
                tenantId, indicatorCode.trim(), EvaluationIndicatorStatus.ACTIVE).isEmpty()) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "结局指标未激活或不存在: " + indicatorCode.trim());
        }
    }

    private List<PathwayMilestoneRuntimeStatus> milestoneStatuses(
            PatientPathway runtime,
            List<PathwayMilestone> graphMilestones,
            List<PathwayNode> graphNodes,
            List<ClinicalClock> runtimeClocks) {
        List<PathwayMilestone> safeMilestones = nullToEmpty(graphMilestones);
        if (safeMilestones.isEmpty()) {
            return List.of();
        }
        Map<String, ClinicalClock> completedClockByNode = completedClockByNode(runtimeClocks);
        Instant now = Instant.now();
        List<PathwayMilestoneRuntimeStatus> statuses = new ArrayList<>();
        for (PathwayMilestone milestone : safeMilestones) {
            List<PathwayNode> linkedNodes = nullToEmpty(graphNodes).stream()
                .filter(node -> Objects.equals(node.milestoneCode(), milestone.milestoneCode()))
                .toList();
            List<String> nodeCodes = linkedNodes.stream().map(PathwayNode::nodeCode).toList();
            Instant achievedAt = null;
            boolean allCompleted = !nodeCodes.isEmpty();
            for (String nodeCode : nodeCodes) {
                ClinicalClock completedClock = completedClockByNode.get(nodeCode);
                if (completedClock == null) {
                    allCompleted = false;
                    continue;
                }
                if (achievedAt == null || completedClock.completedAt().isAfter(achievedAt)) {
                    achievedAt = completedClock.completedAt();
                }
            }
            Instant expectedAt = milestoneExpectedAt(runtime.enteredAt(), milestone);
            boolean current = nodeCodes.stream()
                .anyMatch(nodeCode -> Objects.equals(nodeCode, runtime.currentNodeCode()));
            PathwayMilestoneStatus status;
            if (allCompleted) {
                status = PathwayMilestoneStatus.ACHIEVED;
            } else if (current) {
                status = PathwayMilestoneStatus.CURRENT;
            } else if (expectedAt != null && expectedAt.isBefore(now)) {
                status = PathwayMilestoneStatus.OVERDUE;
            } else {
                status = PathwayMilestoneStatus.PENDING;
            }
            statuses.add(new PathwayMilestoneRuntimeStatus(
                milestone.milestoneId(), milestone.phaseCode(), milestone.phaseName(),
                milestone.milestoneCode(), milestone.name(), milestone.dayOffset(),
                milestone.expectedOffsetMinutes(), nodeCodes, status, expectedAt, achievedAt));
        }
        return statuses;
    }

    private Map<String, ClinicalClock> completedClockByNode(List<ClinicalClock> runtimeClocks) {
        Map<String, ClinicalClock> byNode = new LinkedHashMap<>();
        for (ClinicalClock clock : nullToEmpty(runtimeClocks)) {
            if (clock.completedAt() == null || isBlank(clock.nodeCode())) {
                continue;
            }
            ClinicalClock existing = byNode.get(clock.nodeCode());
            if (existing == null || clock.completedAt().isAfter(existing.completedAt())) {
                byNode.put(clock.nodeCode(), clock);
            }
        }
        return byNode;
    }

    private Instant milestoneExpectedAt(Instant enteredAt, PathwayMilestone milestone) {
        if (enteredAt == null) {
            return null;
        }
        if (milestone.expectedOffsetMinutes() != null) {
            return enteredAt.plusSeconds(milestone.expectedOffsetMinutes().longValue() * 60L);
        }
        if (milestone.dayOffset() != null) {
            return enteredAt.plusSeconds(milestone.dayOffset().longValue() * 24L * 60L * 60L);
        }
        return null;
    }

    private void validatePathwayDraftGraph(PathwayTemplate template,
                                           List<PathwayMilestone> graphMilestones,
                                           List<PathwayNode> graphNodes,
                                           List<PathwayEdge> graphEdges,
                                           List<SpecialtyMetricBinding> graphBindings,
                                           List<PathwayOutcomeBinding> graphOutcomeBindings) {
        List<PathwayNode> executableNodes = activeNodes(graphNodes);
        Set<String> executableNodeCodes = executableNodes.stream()
            .map(PathwayNode::nodeCode)
            .filter(code -> !isBlank(code))
            .collect(java.util.stream.Collectors.toSet());
        List<PathwayEdge> executableEdges = activeEdges(graphEdges, executableNodeCodes);
        validateMilestoneBindings(
            nullToEmpty(graphMilestones).stream()
                .map(milestone -> new PathwayMilestoneRequest(
                    milestone.phaseCode(), milestone.phaseName(), milestone.milestoneCode(),
                    milestone.name(), milestone.dayOffset(), milestone.expectedOffsetMinutes(),
                    null, milestone.sortOrder()))
                .toList(),
            executableNodes.stream()
                .map(node -> new PathwayNodeRequest(
                    node.nodeCode(), node.name(), node.nodeType(), node.milestoneCode(),
                    node.sortOrder(), node.responsibleRole(), node.accountableRole(),
                    readRoleList(node.consultedRolesJson()), readRoleList(node.informedRolesJson()),
                    null, node.timeWindowMinutes(),
                    node.terminalFlag(), false, null))
                .toList());
        Set<String> nodeCodes = new HashSet<>();
        boolean hasTerminal = false;
        for (PathwayNode node : executableNodes) {
            if (isBlank(node.nodeCode()) || !nodeCodes.add(node.nodeCode())) {
                throw new ApiException(ErrorCode.ENG_PATHWAY_004, "路径节点编码重复或为空");
            }
            if (isBlank(node.responsibleRole()) || isBlank(node.accountableRole())) {
                throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                    "路径节点 " + node.nodeCode() + " 缺少 Responsible 或 Accountable 角色");
            }
            if (node.timeWindowMinutes() != null && node.timeWindowMinutes() < 0) {
                throw new ApiException(ErrorCode.ENG_PATHWAY_004, "路径节点时间窗不能为负数");
            }
            hasTerminal = hasTerminal || Boolean.TRUE.equals(node.terminalFlag());
        }
        if (isBlank(template.startNodeCode()) || !nodeCodes.contains(template.startNodeCode())) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004, "临床路径缺少有效起始节点");
        }
        if (!hasTerminal) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004, "临床路径缺少终止节点");
        }
        Set<String> nodesWithOutgoing = new HashSet<>();
        for (PathwayEdge edge : executableEdges) {
            if (!nodeCodes.contains(edge.fromNodeCode()) || !nodeCodes.contains(edge.toNodeCode())) {
                throw new ApiException(ErrorCode.ENG_PATHWAY_004, "路径边引用了不存在的节点");
            }
            nodesWithOutgoing.add(edge.fromNodeCode());
        }
        for (PathwayNode node : executableNodes) {
            if (!Boolean.TRUE.equals(node.terminalFlag()) && !nodesWithOutgoing.contains(node.nodeCode())) {
                throw new ApiException(ErrorCode.ENG_PATHWAY_004, "非终止节点缺少出边: " + node.nodeCode());
            }
        }
        validateRichNodeContracts(executableNodes, executableEdges);
        validatePathwayGraphAcyclic(executableNodes, executableEdges);
        validatePathwayStableAssetReferences(
            template, executableNodes, executableEdges, graphOutcomeBindings);
        validateClockBindings(executableNodes, graphBindings);
        validateEffectiveOutcomeBindings(graphOutcomeBindings, graphMilestones);
    }

    private List<PathwayNode> activeNodes(List<PathwayNode> graphNodes) {
        return nullToEmpty(graphNodes).stream()
            .filter(node -> !Boolean.TRUE.equals(node.disabledFlag()))
            .toList();
    }

    private List<PathwayEdge> activeEdges(List<PathwayEdge> graphEdges, Set<String> activeNodeCodes) {
        return nullToEmpty(graphEdges).stream()
            .filter(edge -> edgeEndpointsActive(edge, activeNodeCodes))
            .toList();
    }

    private boolean edgeEndpointsActive(PathwayEdge edge, Set<String> activeNodeCodes) {
        return edge != null
            && activeNodeCodes.contains(edge.fromNodeCode())
            && activeNodeCodes.contains(edge.toNodeCode());
    }

    private void validateRichNodeContracts(List<PathwayNode> graphNodes, List<PathwayEdge> graphEdges) {
        Map<String, List<PathwayEdge>> outgoingByNode = new LinkedHashMap<>();
        for (PathwayEdge edge : nullToEmpty(graphEdges)) {
            outgoingByNode.computeIfAbsent(edge.fromNodeCode(), ignored -> new ArrayList<>()).add(edge);
        }
        for (PathwayNode node : nullToEmpty(graphNodes)) {
            List<PathwayEdge> outgoing = outgoingByNode.getOrDefault(node.nodeCode(), List.of());
            ensureRichNodeFeatureEnabledForTemplate(node);
            switch (node.nodeType()) {
                case DECISION -> validateDecisionNode(node, outgoing);
                case PARALLEL -> validateParallelNode(node, outgoing);
                case WAIT_TIMER -> validateWaitTimerNode(node, outgoing);
                case MANUAL_GATE -> {
                    if (isBlank(node.responsibleRole())) {
                        throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                            "人工闸门节点 " + node.nodeCode() + " 缺少责任角色");
                    }
                }
                case ORDER_SET -> requireNodeConfigText(node, "orderSetRef", "医嘱套餐节点 " + node.nodeCode() + " 缺少引用");
                default -> {
                    // 临床活动节点仅使用通用拓扑与时钟校验。
                }
            }
        }
    }

    private void validatePathwayGraphAcyclic(List<PathwayNode> graphNodes, List<PathwayEdge> graphEdges) {
        Map<String, List<String>> outgoingByNode = new LinkedHashMap<>();
        for (PathwayNode node : nullToEmpty(graphNodes)) {
            outgoingByNode.putIfAbsent(node.nodeCode(), new ArrayList<>());
        }
        for (PathwayEdge edge : nullToEmpty(graphEdges)) {
            outgoingByNode.computeIfAbsent(edge.fromNodeCode(), ignored -> new ArrayList<>())
                .add(edge.toNodeCode());
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        ArrayList<String> stack = new ArrayList<>();
        for (PathwayNode node : nullToEmpty(graphNodes)) {
            detectPathwayCycle(node.nodeCode(), outgoingByNode, visiting, visited, stack);
        }
    }

    private void detectPathwayCycle(String nodeCode,
                                    Map<String, List<String>> outgoingByNode,
                                    Set<String> visiting,
                                    Set<String> visited,
                                    ArrayList<String> stack) {
        if (isBlank(nodeCode) || visited.contains(nodeCode)) {
            return;
        }
        visiting.add(nodeCode);
        stack.add(nodeCode);
        for (String nextNodeCode : outgoingByNode.getOrDefault(nodeCode, List.of())) {
            if (visiting.contains(nextNodeCode)) {
                int cycleStart = stack.indexOf(nextNodeCode);
                List<String> cycle = new ArrayList<>(stack.subList(Math.max(cycleStart, 0), stack.size()));
                cycle.add(nextNodeCode);
                throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                    "路径图存在环: " + String.join(" -> ", cycle));
            }
            detectPathwayCycle(nextNodeCode, outgoingByNode, visiting, visited, stack);
        }
        stack.remove(stack.size() - 1);
        visiting.remove(nodeCode);
        visited.add(nodeCode);
    }

    private void validatePathwayStableAssetReferences(
            PathwayTemplate template,
            List<PathwayNode> graphNodes,
            List<PathwayEdge> graphEdges,
            List<PathwayOutcomeBinding> graphOutcomeBindings) {
        JsonNode entryCriteria = readJsonOrEmpty(
            template.entryCriteriaJson(), "临床路径入径条件 " + template.templateCode(),
            ErrorCode.ENG_PATHWAY_004);
        AssetReferenceConsistency.requireStableAssetReferences(
            entryCriteria, ErrorCode.ENG_PATHWAY_004, "临床路径 " + template.templateCode() + " 入径条件");
        validateContextFieldReferences(entryCriteria, "临床路径 " + template.templateCode() + " 入径条件");
        JsonNode exitCriteria = readJsonOrEmpty(
            template.exitCriteriaJson(), "临床路径出径条件 " + template.templateCode(),
            ErrorCode.ENG_PATHWAY_004);
        AssetReferenceConsistency.requireStableAssetReferences(
            exitCriteria, ErrorCode.ENG_PATHWAY_004, "临床路径 " + template.templateCode() + " 出径条件");
        validateContextFieldReferences(exitCriteria, "临床路径 " + template.templateCode() + " 出径条件");
        for (PathwayNode node : nullToEmpty(graphNodes)) {
            JsonNode dependency = readJsonOrEmpty(
                node.dependencyJson(), "路径节点依赖 " + node.nodeCode(), ErrorCode.ENG_PATHWAY_004);
            AssetReferenceConsistency.requireStableAssetReferences(
                dependency, ErrorCode.ENG_PATHWAY_004, "路径节点依赖 " + node.nodeCode());
            validateContextFieldReferences(dependency, "路径节点依赖 " + node.nodeCode());
            JsonNode config = readJsonOrEmpty(
                node.configJson(), "路径节点配置 " + node.nodeCode(), ErrorCode.ENG_PATHWAY_004);
            AssetReferenceConsistency.requireStableAssetReferences(
                config, ErrorCode.ENG_PATHWAY_004, "路径节点 " + node.nodeCode());
            validateContextFieldReferences(config, "路径节点配置 " + node.nodeCode());
        }
        for (PathwayEdge edge : nullToEmpty(graphEdges)) {
            JsonNode condition = readJsonOrEmpty(
                edge.conditionJson(),
                "路径边条件 " + edge.edgeCode(),
                ErrorCode.ENG_PATHWAY_004);
            AssetReferenceConsistency.requireStableAssetReferences(
                condition, ErrorCode.ENG_PATHWAY_004, "路径边 " + edge.edgeCode());
            validateContextFieldReferences(condition, "路径边 " + edge.edgeCode());
        }
    }

    private void validateContextFieldReferences(JsonNode jsonNode, String ownerLabel) {
        List<String> unknown = ContextFieldPathPolicy.unknownFields(
            ContextFieldPathPolicy.ruleDslFields(jsonNode));
        if (!unknown.isEmpty()) {
            throw new ApiException(
                ErrorCode.ENG_PATHWAY_004,
                ownerLabel + " 字段目录不存在：" + String.join(", ", unknown)
            );
        }
    }

    private void ensureRichNodeFeatureEnabledForTemplate(PathwayNode node) {
        if (!isRichPathwayNode(node.nodeType())
                || authoringFeatureGate.enabled(AuthoringFeatureFlag.PATHWAY_RICH_NODES)) {
            return;
        }
        throw new ApiException(
            ErrorCode.ENG_PATHWAY_004,
            AuthoringFeatureFlag.PATHWAY_RICH_NODES.displayName()
                + "能力开关未启用: "
                + SystemConfigService.runtimeFeatureFlagConfigKey(AuthoringFeatureFlag.PATHWAY_RICH_NODES.key())
                + "，节点 " + node.nodeCode() + " 类型 " + node.nodeType());
    }

    private boolean isRichPathwayNode(PathwayNodeType nodeType) {
        return switch (nodeType) {
            case DECISION, PARALLEL, WAIT_TIMER, MANUAL_GATE, ORDER_SET -> true;
            default -> false;
        };
    }

    private void validateDecisionNode(PathwayNode node, List<PathwayEdge> outgoing) {
        List<PathwayEdge> guardedEdges = outgoing.stream()
            .filter(edge -> edge.edgeType() == PathwayEdgeType.CONDITION)
            .toList();
        if (outgoing.size() < 2 || guardedEdges.isEmpty()) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "决策节点 " + node.nodeCode() + " 至少需要一个条件分支和一个兜底分支");
        }
        boolean hasDefaultFallback = outgoing.stream().anyMatch(edge -> edge.edgeType() == PathwayEdgeType.DEFAULT);
        if (!hasDefaultFallback) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "决策节点 " + node.nodeCode() + " 必须配置默认兜底分支");
        }
        boolean hasBlankGuard = guardedEdges.stream().anyMatch(edge -> isBlank(edge.conditionJson()));
        if (hasBlankGuard) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "决策节点 " + node.nodeCode() + " 的条件分支必须配置守卫条件");
        }
    }

    private void validateParallelNode(PathwayNode node, List<PathwayEdge> outgoing) {
        boolean hasFork = outgoing.size() >= 2;
        boolean hasJoin = outgoing.stream().anyMatch(edge -> edge.edgeType() == PathwayEdgeType.JOIN);
        if (!hasFork && !hasJoin) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "并行节点 " + node.nodeCode() + " 缺少并行分支或 JOIN 汇合边");
        }
    }

    private void validateWaitTimerNode(PathwayNode node, List<PathwayEdge> outgoing) {
        String clock = nodeConfigText(node, "clock");
        if (isBlank(clock) && node.timeWindowMinutes() == null) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "等待计时节点 " + node.nodeCode() + " 缺少计时规则或时窗分钟");
        }
        boolean hasTimerGuard = outgoing.stream().anyMatch(edge -> edge.edgeType() == PathwayEdgeType.CONDITION);
        if (!hasTimerGuard) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "等待计时节点 " + node.nodeCode() + " 缺少计时条件边");
        }
    }

    private String requireNodeConfigText(PathwayNode node, String field, String message) {
        String value = nodeConfigText(node, field);
        if (isBlank(value)) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004, message);
        }
        return value;
    }

    private String nodeConfigText(PathwayNode node, String field) {
        if (isBlank(node.configJson())) {
            return null;
        }
        try {
            JsonNode value = json.readTree(node.configJson()).get(field);
            return value == null || value.isNull() ? null : value.asText();
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "路径节点配置 JSON 解析失败：" + node.nodeCode(), exception);
        }
    }

    private void validateClockBindings(List<PathwayNode> graphNodes, List<SpecialtyMetricBinding> graphBindings) {
        Set<String> boundNodes = new HashSet<>();
        for (SpecialtyMetricBinding binding : nullToEmpty(graphBindings)) {
            if (!isBlank(binding.nodeCode()) && !isBlank(binding.metricCode())) {
                boundNodes.add(binding.nodeCode());
            }
        }
        for (PathwayNode node : graphNodes) {
            if (node.timeWindowMinutes() != null && node.timeWindowMinutes() > 0
                    && !boundNodes.contains(node.nodeCode())) {
                throw new ApiException(ErrorCode.PATHWAY_CLOCK_MISSING,
                    "节点 " + node.nodeCode() + " 设置了关键时限但未绑定评价指标");
            }
            if (node.timeWindowMinutes() != null && node.timeWindowMinutes() > 0) {
                requireClockSlaConfig(node);
            }
        }
    }

    private ClinicalClockSlaConfig requireClockSlaConfig(PathwayNode node) {
        ClinicalClockSlaConfig config = optionalClockSlaConfig(node);
        if (config == null) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "关键时钟节点 " + node.nodeCode() + " 缺少时窗校验配置");
        }
        return config;
    }

    private ClinicalClockSlaConfig optionalClockSlaConfig(PathwayNode node) {
        JsonNode clockSla = nodeConfigNode(node, "clockSla");
        if (clockSla == null || clockSla.isNull() || clockSla.isMissingNode()) {
            return null;
        }
        if (!clockSla.isObject()) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "关键时钟节点 " + node.nodeCode() + " 的时窗校验配置必须是结构化对象");
        }
        String baselineEvent = requiredText(clockSla, "baselineEvent", node, "时窗校验基准");
        validateBaselineEvent(baselineEvent, node);
        Integer minMinutes = requiredNonNegativeInt(clockSla, "minMinutes", node, "最早分钟");
        Integer targetMinutes = requiredNonNegativeInt(clockSla, "targetMinutes", node, "目标分钟");
        Integer maxMinutes = requiredNonNegativeInt(clockSla, "maxMinutes", node, "最晚分钟");
        if (targetMinutes <= 0) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "关键时钟节点 " + node.nodeCode() + " 的目标分钟必须大于 0");
        }
        if (minMinutes > targetMinutes || targetMinutes > maxMinutes) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "关键时钟节点 " + node.nodeCode() + " 的时窗校验分钟必须满足最早 <= 目标 <= 最晚");
        }
        List<ClockEscalationThreshold> escalations = escalationThresholds(clockSla.path("escalations"), node);
        return new ClinicalClockSlaConfig(baselineEvent, minMinutes, targetMinutes, maxMinutes, escalations);
    }

    private JsonNode nodeConfigNode(PathwayNode node, String field) {
        if (isBlank(node.configJson())) {
            return null;
        }
        try {
            return json.readTree(node.configJson()).get(field);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "路径节点配置 JSON 解析失败：" + node.nodeCode(), exception);
        }
    }

    private String requiredText(JsonNode source, String field, PathwayNode node, String label) {
        JsonNode value = source.get(field);
        if (value == null || value.isNull() || isBlank(value.asText())) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "关键时钟节点 " + node.nodeCode() + " 缺少 " + label);
        }
        return value.asText().trim();
    }

    private Integer requiredNonNegativeInt(JsonNode source, String field, PathwayNode node, String label) {
        JsonNode value = source.get(field);
        if (value == null || value.isNull() || !value.canConvertToInt() || value.asInt() < 0) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "关键时钟节点 " + node.nodeCode() + " 的 " + label + " 必须是非负整数");
        }
        return value.asInt();
    }

    private void validateBaselineEvent(String baselineEvent, PathwayNode node) {
        if (!Set.of("NODE_START", "PATHWAY_ENTRY", "ADMISSION").contains(baselineEvent)) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "关键时钟节点 " + node.nodeCode() + " 不支持时窗校验基准: " + baselineEvent);
        }
    }

    private List<ClockEscalationThreshold> escalationThresholds(JsonNode source, PathwayNode node) {
        if (source == null || !source.isArray() || source.size() == 0) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "关键时钟节点 " + node.nodeCode() + " 缺少超时升级策略");
        }
        LinkedHashMap<ClinicalClockEscalationLevel, ClockEscalationThreshold> thresholds = new LinkedHashMap<>();
        for (JsonNode item : source) {
            if (!item.isObject()) {
                throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                    "关键时钟节点 " + node.nodeCode() + " 的超时升级策略必须是对象数组");
            }
            String levelText = requiredText(item, "level", node, "超时升级级别");
            ClinicalClockEscalationLevel level;
            try {
                level = ClinicalClockEscalationLevel.valueOf(levelText);
            } catch (IllegalArgumentException exception) {
                throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                    "关键时钟节点 " + node.nodeCode() + " 不支持超时升级级别: " + levelText, exception);
            }
            if (level == ClinicalClockEscalationLevel.NONE) {
                throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                    "关键时钟节点 " + node.nodeCode() + " 的超时升级策略不能配置 NONE");
            }
            Integer afterMinutes = requiredNonNegativeInt(item, "afterMinutes", node, "升级等待分钟");
            thresholds.put(level, new ClockEscalationThreshold(level, afterMinutes));
        }
        for (ClinicalClockEscalationLevel required : List.of(
                ClinicalClockEscalationLevel.REMINDER,
                ClinicalClockEscalationLevel.REPORT,
                ClinicalClockEscalationLevel.QUALITY_RECORD)) {
            if (!thresholds.containsKey(required)) {
                throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                    "关键时钟节点 " + node.nodeCode() + " 缺少 " + required + " 超时升级级别");
            }
        }
        return List.copyOf(thresholds.values());
    }

    private Instant baselineAt(ClinicalClockSlaConfig sla, ContextSnapshotResources resources,
                               Instant pathwayEnteredAt, Instant now, PathwayNode node) {
        return switch (sla.baselineEvent()) {
            case "NODE_START" -> now;
            case "PATHWAY_ENTRY" -> pathwayEnteredAt == null ? now : pathwayEnteredAt;
            case "ADMISSION" -> admissionTime(resources, node);
            default -> throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "关键时钟节点 " + node.nodeCode() + " 不支持时窗校验基准: " + sla.baselineEvent());
        };
    }

    private Instant admissionTime(ContextSnapshotResources resources, PathwayNode node) {
        if (resources == null) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "关键时钟节点 " + node.nodeCode() + " 使用 ADMISSION 基准但缺少上下文快照");
        }
        return resources.encounters().stream()
            .map(CanonicalEncounter::admissionTime)
            .filter(Objects::nonNull)
            .findFirst()
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_PATHWAY_004,
                "关键时钟节点 " + node.nodeCode() + " 使用 ADMISSION 基准但缺少入院时间"));
    }

    private ClinicalClockEscalationLevel escalationLevel(List<ClockEscalationThreshold> thresholds,
                                                         Instant baselineAt,
                                                         Instant now) {
        ClinicalClockEscalationLevel result = ClinicalClockEscalationLevel.NONE;
        for (ClockEscalationThreshold threshold : nullToEmpty(thresholds)) {
            Instant reachedAt = baselineAt.plusSeconds(threshold.afterMinutes().longValue() * 60L);
            if (!reachedAt.isAfter(now) && threshold.level().ordinal() > result.ordinal()) {
                result = threshold.level();
            }
        }
        return result;
    }

    private String escalationPolicyJson(ClinicalClockSlaConfig sla) {
        try {
            return json.writeValueAsString(sla.escalations());
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004, "关键时钟超时升级策略无法序列化", exception);
        }
    }

    private List<ClinicalClock> projectClockSla(
            PatientPathway runtime,
            List<ClinicalClock> source,
            Instant now) {
        return nullToEmpty(source).stream()
            .map(clock -> {
                ClinicalClock projected = projectClockSla(clock, now);
                if (projected != clock && projected.status() == ClinicalClockStatus.TIMEOUT) {
                    domainEvents.clockSlaBreached(new ClockSlaBreachedEvent(
                        runtime.tenantId(),
                        projected.traceId(),
                        runtime.runtimeReleaseId(),
                        runtime.patientPathwayId(),
                        runtime.patientId(),
                        runtime.encounterId(),
                        projected.clockId(),
                        projected.nodeCode(),
                        projected.metricCode(),
                        projected.escalationLevel().name(),
                        projected.dueAt(),
                        now));
                }
                return projected;
            })
            .toList();
    }

    private ClinicalClock projectClockSla(ClinicalClock clock, Instant now) {
        if (clock.status() != ClinicalClockStatus.RUNNING || clock.baselineAt() == null
                || isBlank(clock.escalationPolicyJson())) {
            return clock;
        }
        ClinicalClockEscalationLevel current =
            clock.escalationLevel() == null ? ClinicalClockEscalationLevel.NONE : clock.escalationLevel();
        ClinicalClockEscalationLevel projected =
            escalationLevel(readClockEscalationPolicy(clock.escalationPolicyJson()), clock.baselineAt(), now);
        if (projected.ordinal() <= current.ordinal()) {
            return clock;
        }
        return new ClinicalClock(
            clock.id(), clock.clockId(), clock.tenantId(), clock.patientPathwayId(),
            clock.nodeCode(), clock.metricCode(), clock.startedAt(), clock.dueAt(), clock.completedAt(),
            ClinicalClockStatus.TIMEOUT,
            clock.baselineEvent(), clock.baselineAt(), clock.minDueAt(), clock.targetDueAt(), clock.maxDueAt(),
            projected, clock.escalationPolicyJson(),
            clock.createdAt(), clock.createdBy(), now, clock.updatedBy(), clock.traceId());
    }

    private List<ClockEscalationThreshold> readClockEscalationPolicy(String source) {
        try {
            JsonNode root = json.readTree(source);
            if (!root.isArray()) {
                return List.of();
            }
            List<ClockEscalationThreshold> thresholds = new ArrayList<>();
            for (JsonNode item : root) {
                ClinicalClockEscalationLevel level =
                    ClinicalClockEscalationLevel.valueOf(item.path("level").asText());
                thresholds.add(new ClockEscalationThreshold(level, item.path("afterMinutes").asInt()));
            }
            return thresholds;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004, "关键时钟超时升级策略无法解析", exception);
        }
    }

    private ClinicalClock runningClockForNode(String patientPathwayId, String tenantId, String nodeCode) {
        return clocks.findByPatientPathwayIdAndTenantIdOrderByStartedAtAsc(patientPathwayId, tenantId).stream()
            .filter(clock -> Objects.equals(clock.nodeCode(), nodeCode))
            .filter(clock -> clock.status() == ClinicalClockStatus.RUNNING)
            .findFirst()
            .orElse(null);
    }

    private void openNodeWorklist(
            PatientPathway runtime,
            PathwayNode node,
            ClinicalClock clock,
            String actor,
            String traceId) {
        worklist.openNodeTodo(new PathwayNodeWorklistCommand(
            runtime.tenantId(),
            targetOrgUnitId(),
            runtime.patientPathwayId(),
            runtime.patientId(),
            runtime.encounterId(),
            node.nodeCode(),
            node.name(),
            node.nodeType(),
            clock == null ? null : clock.clockId(),
            node.responsibleRole(),
            node.accountableRole(),
            readRoleList(node.consultedRolesJson()),
            readRoleList(node.informedRolesJson()),
            clock == null ? null : clock.dueAt(),
            "/clinical/pathways?patientPathwayId=" + runtime.patientPathwayId() + "&nodeCode=" + node.nodeCode(),
            traceId,
            actor));
    }

    private void completeNodeWorklist(
            PatientPathway runtime,
            String nodeCode,
            ClinicalClock clock,
            PathwayProgressDecision decision,
            Instant now,
            String actor,
            String traceId) {
        String reason = decision.status() == PatientPathwayStatus.COMPLETED
            ? "路径已完成，节点工作清单自动闭环"
            : decision.status() == PatientPathwayStatus.EXITED
                ? "路径已退出，节点工作清单自动闭环"
                : "路径已推进至 " + decision.nextNodeCode() + "，节点工作清单自动闭环";
        worklist.completeNodeTodo(new PathwayNodeWorklistCompletionCommand(
            runtime.tenantId(),
            runtime.patientPathwayId(),
            nodeCode,
            clock == null ? null : clock.clockId(),
            reason,
            now,
            traceId,
            actor));
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
            runtime.encounterId(), runtime.templateId(), runtime.runtimeReleaseId(),
            runtime.pathwayVersionId(), currentNode, decision.status(),
            runtime.enteredAt(), decision.status() == PatientPathwayStatus.COMPLETED ? now : runtime.completedAt(),
            decision.status() == PatientPathwayStatus.EXITED ? now : runtime.exitedAt(),
            decision.status() == PatientPathwayStatus.EXITED ? exitReason(request) : runtime.exitReason(),
            request.eventId(), runtime.createdAt(), runtime.createdBy(), now, actor, traceId);
    }

    private PathwayTemplate copyTemplate(PathwayTemplate template, PathwayTemplateStatus status,
                                         Instant now, String actor, String traceId) {
        return new PathwayTemplate(
            template.id(), template.templateId(), template.tenantId(),
            template.templateCode(), template.name(), template.diseaseCode(),
            template.templateVersion(), template.templateLevel(), status,
            template.entryMode(), template.startNodeCode(),
            template.sourceRef(), template.description(), template.entryCriteriaJson(),
            template.exitCriteriaJson(), template.createdAt(), template.createdBy(), now, actor, traceId);
    }

    private ClinicalClock newClock(String tenantId, String patientPathwayId,
                                   PathwayNode node, String metricCode,
                                   ContextSnapshotResources resources,
                                   Instant pathwayEnteredAt,
                                   Instant now, String actor, String traceId) {
        ClinicalClockSlaConfig sla = optionalClockSlaConfig(node);
        if (sla == null) {
            Instant dueAt = node.timeWindowMinutes() == null ? null
                : now.plusSeconds(node.timeWindowMinutes().longValue() * 60L);
            return new ClinicalClock(
                null, "cc-" + UUID.randomUUID(), tenantId, patientPathwayId,
                node.nodeCode(), metricCode, now, dueAt, null, ClinicalClockStatus.RUNNING,
                null, null, null, null, null, ClinicalClockEscalationLevel.NONE, null,
                now, actor, now, actor, traceId);
        }
        Instant baselineAt = baselineAt(sla, resources, pathwayEnteredAt, now, node);
        Instant minDueAt = baselineAt.plusSeconds(sla.minMinutes().longValue() * 60L);
        Instant targetDueAt = baselineAt.plusSeconds(sla.targetMinutes().longValue() * 60L);
        Instant maxDueAt = baselineAt.plusSeconds(sla.maxMinutes().longValue() * 60L);
        ClinicalClockEscalationLevel escalationLevel = escalationLevel(sla.escalations(), baselineAt, now);
        ClinicalClockStatus status = escalationLevel == ClinicalClockEscalationLevel.NONE
            ? ClinicalClockStatus.RUNNING : ClinicalClockStatus.TIMEOUT;
        return new ClinicalClock(
            null, "cc-" + UUID.randomUUID(), tenantId, patientPathwayId,
            node.nodeCode(), metricCode, now, targetDueAt, null, status,
            sla.baselineEvent(), baselineAt, minDueAt, targetDueAt, maxDueAt,
            escalationLevel, escalationPolicyJson(sla),
            now, actor, now, actor, traceId);
    }

    private String metricCodeForNode(List<SpecialtyMetricBinding> graphBindings, String nodeCode) {
        return nullToEmpty(graphBindings).stream()
            .filter(binding -> Objects.equals(binding.nodeCode(), nodeCode))
            .filter(binding -> !isBlank(binding.metricCode()))
            .map(SpecialtyMetricBinding::metricCode)
            .findFirst()
            .orElse(null);
    }

    private ClinicalClock copyClock(ClinicalClock clock, ClinicalClockStatus status,
                                    Instant now, String actor, String traceId) {
        return new ClinicalClock(
            clock.id(), clock.clockId(), clock.tenantId(), clock.patientPathwayId(),
            clock.nodeCode(), clock.metricCode(), clock.startedAt(), clock.dueAt(),
            now, status,
            clock.baselineEvent(), clock.baselineAt(), clock.minDueAt(), clock.targetDueAt(), clock.maxDueAt(),
            clock.escalationLevel(), clock.escalationPolicyJson(),
            clock.createdAt(), clock.createdBy(), now, actor, traceId);
    }

    private Map<String, Object> contextFacts(ContextSnapshotResources resources) {
        return ContextFactBridge.facts(resources);
    }

    private Map<String, Integer> contextResourceCounts(ContextSnapshotResources resources) {
        if (resources == null) {
            return Map.of();
        }
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        counts.put("patient", resources.patient() == null ? 0 : 1);
        counts.put("encounters", resources.encounters().size());
        counts.put("conditions", resources.conditions().size());
        counts.put("nursingAssessments", resources.nursingAssessments().size());
        counts.put("observations", resources.observations().size());
        counts.put("diagnosticReports", resources.diagnosticReports().size());
        counts.put("medications", resources.medications().size());
        counts.put("procedures", resources.procedures().size());
        counts.put("documents", resources.documents().size());
        counts.put("carePlans", resources.carePlans().size());
        counts.put("followUps", resources.followUps().size());
        counts.put("claims", resources.claims().size());
        return counts;
    }

    private PathwayTemplate findTemplate(String templateId, String tenantId) {
        return templates.findByTemplateIdAndTenantId(templateId, tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_PATHWAY_002,
                "临床路径不存在: " + templateId));
    }

    private Set<String> pathwayNodeCodeSet(List<PathwayNode> graphNodes) {
        Set<String> nodeCodes = new HashSet<>();
        for (PathwayNode node : nullToEmpty(graphNodes)) {
            if (!isBlank(node.nodeCode())) {
                nodeCodes.add(node.nodeCode());
            }
        }
        return nodeCodes;
    }

    private EffectivePathwayTemplate findEffectiveTemplate(String templateId, String tenantId) {
        Optional<PathwayTemplate> local = templates.findByTemplateIdAndTenantId(templateId, tenantId);
        Optional<PathwayTemplate> candidate = local;
        if (candidate.isEmpty() && !PlatformTenant.isPlatformTenant(tenantId)) {
            candidate = templates.findByTemplateIdAndTenantId(templateId, PlatformTenant.ID);
        }
        if (candidate.isPresent()) {
            Optional<EffectivePathwayTemplate> resolved =
                resolveEffectiveTemplateForCurrentOrg(candidate.get(), tenantId);
            if (resolved.isPresent()) {
                return resolved.get();
            }
        }
        if (local.isPresent()) {
            return new EffectivePathwayTemplate(local.get(), tenantId);
        }
        if (PlatformTenant.isPlatformTenant(tenantId)) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_002, "临床路径不存在: " + templateId);
        }
        PathwayTemplate platform = templates.findByTemplateIdAndTenantId(templateId, PlatformTenant.ID)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_PATHWAY_002,
                "临床路径不存在: " + templateId));
        return templates.findByTenantIdAndTemplateCodeAndTemplateVersion(
                tenantId, platform.templateCode(), platform.templateVersion())
            .filter(this::hasActivePathwayAssetVersion)
            .map(override -> new EffectivePathwayTemplate(override, tenantId))
            .orElseGet(() -> new EffectivePathwayTemplate(platform, PlatformTenant.ID));
    }

    /**
     * 按患者入径时保存的路径 ID 读取固定版本，禁止在运行中重新解析到后续激活版本。
     */
    private EffectivePathwayTemplate findPinnedRuntimeTemplate(String templateId, String tenantId) {
        Optional<PathwayTemplate> local = templates.findByTemplateIdAndTenantId(templateId, tenantId);
        if (local.isPresent()) {
            return new EffectivePathwayTemplate(local.get(), tenantId);
        }
        if (!PlatformTenant.isPlatformTenant(tenantId)) {
            Optional<PathwayTemplate> platform =
                templates.findByTemplateIdAndTenantId(templateId, PlatformTenant.ID);
            if (platform.isPresent()) {
                return new EffectivePathwayTemplate(platform.get(), PlatformTenant.ID);
            }
        }
        throw new ApiException(ErrorCode.ENG_PATHWAY_002, "患者路径绑定的临床路径版本不存在: " + templateId);
    }

    private EffectivePathwayTemplate runtimeTemplate(RuntimePathwayReference selected) {
        PathwayTemplate template = templates
            .findByTemplateIdAndTenantId(selected.templateId(), selected.sourceTenantId())
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_PATHWAY_002,
                "机构生效版本锁定的临床路径不存在: " + selected.templateId()
            ));
        if (!selected.templateCode().equals(template.templateCode())
                || selected.versionNo() != template.templateVersion()) {
            throw new ApiException(
                ErrorCode.ENG_PATHWAY_006,
                "机构生效版本路径版本与路径正文不一致: " + selected.pathwayVersionId()
            );
        }
        return new EffectivePathwayTemplate(template, selected.sourceTenantId());
    }

    private Optional<EffectivePathwayTemplate> resolveEffectiveTemplateForCurrentOrg(
            PathwayTemplate candidate, String tenantId) {
        if (candidate.status() != PathwayTemplateStatus.PUBLISHED) {
            return Optional.empty();
        }
        String targetOrgUnitId = targetOrgUnitId();
        if (targetOrgUnitId == null) {
            return Optional.empty();
        }
        ResolvedAssetVersion resolved;
        try {
            resolved = inheritanceResolver.resolve(new InheritanceResolveQuery(
                tenantId,
                VersionedAssetType.PATHWAY,
                candidate.templateCode(),
                releaseApplicableScope(candidate),
                targetOrgUnitId
            ));
        } catch (ApiException exception) {
            // 草稿临床路径在当前组织闭包内尚无任何 PUBLISHED 有效版本时，解析器按契约抛 NOT_FOUND。
            // 此处回退本地临床路径（含未发布草稿），由调用方按本地版本投影详情/影响/试运行预览，
            // 避免把 NOT_FOUND 透传给前台导致路径编排流被堵死。
            if (exception.errorCode() == ErrorCode.NOT_FOUND) {
                return Optional.empty();
            }
            throw exception;
        }
        if (resolved.disabled()) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_002, "临床路径已在当前组织停用");
        }
        if (resolved.version() == null) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_002, "当前组织未解析到有效路径版本");
        }
        AssetVersion assetVersion = resolved.version();
        int templateVersion = AssetVersionNumbers.intSequence(
            assetVersion.versionNo(), "路径统一版本号");
        return templates.findByTenantIdAndTemplateCodeAndTemplateVersion(
                assetVersion.tenantId(), candidate.templateCode(), templateVersion)
            .map(template -> new EffectivePathwayTemplate(template, assetVersion.tenantId()));
    }

    private boolean requiresEffectiveTemplateMerge(String tenantId, String status) {
        if (PlatformTenant.isPlatformTenant(tenantId)) {
            return false;
        }
        return status == null || PathwayTemplateStatus.PUBLISHED.name().equals(status);
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
            throw new ApiException(ErrorCode.ENG_PATHWAY_005, "当前临床路径状态不允许发布");
        }
    }

    private void ensureTemplatePublished(PathwayTemplate template, String message) {
        if (template.status() != PathwayTemplateStatus.PUBLISHED) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_005, message);
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
        if (request.varianceType() == null || isBlank(request.varianceReasonCode())
                || isBlank(request.varianceReason()) || isBlank(request.responsibleRole())
                || request.resolutionDecision() == null) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_006, "变异事件必须包含分类、原因码、原因说明、责任角色和处置决策");
        }
        if (request.resolutionDecision() == VarianceResolutionDecision.REENTER
                && isBlank(request.requestedNextNodeCode())) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_006, "变异再入径必须选择继续节点");
        }
        if (request.resolutionDecision() == VarianceResolutionDecision.HOLD
                && !isBlank(request.requestedNextNodeCode())) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_006, "变异暂停观察不能同时指定继续节点");
        }
        if (request.resolutionDecision() == VarianceResolutionDecision.TERMINATE
                && !isBlank(request.requestedNextNodeCode())) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_006, "变异终止路径不能指定继续节点");
        }
    }

    private String exitReason(PathwayAdvanceRequest request) {
        if (!isBlank(request.exitReason())) {
            return request.exitReason();
        }
        if (request.eventType() == PathwayAdvanceEventType.VARIANCE) {
            return request.varianceReason();
        }
        return null;
    }

    private String requireCurrentTenant() {
        var scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private static String targetOrgUnitId() {
        var scope = RequestContext.currentOrgScope();
        if (scope == null) {
            return null;
        }
        return scope.nearestOrgUnitId();
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
        return writeObject(node);
    }

    private String writeJson(List<String> values) {
        return writeObject(normalizedRoles(values));
    }

    private List<String> readRoleList(String source) {
        if (isBlank(source)) {
            return List.of();
        }
        try {
            JsonNode root = json.readTree(source);
            if (!root.isArray()) {
                return List.of();
            }
            List<String> roles = new ArrayList<>();
            for (JsonNode item : root) {
                if (item != null && !item.isNull()) {
                    roles.add(item.asText());
                }
            }
            return normalizedRoles(roles);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004, "路径节点责任分工角色无法解析", exception);
        }
    }

    private List<String> normalizedRoles(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
            .filter(value -> !isBlank(value))
            .map(String::trim)
            .distinct()
            .toList();
    }

    private String writeObject(Object value) {
        try {
            return json.writeValueAsString(value);
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

    private static String notBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String keywordLike(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : "%" + normalized.toLowerCase(Locale.ROOT) + "%";
    }

    private String normalizedOutcomeRefCode(PathwayOutcomeScope scope, String refCode) {
        if (scope == PathwayOutcomeScope.TEMPLATE) {
            return "TEMPLATE";
        }
        return blankToNull(refCode);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record EffectivePathwayGraph(
        List<PathwayMilestone> milestones,
        List<PathwayNode> nodes,
        List<PathwayEdge> edges,
        List<SpecialtyMetricBinding> metricBindings,
        List<PathwayOutcomeBinding> outcomeBindings
    ) {
        private EffectivePathwayGraph {
            milestones = milestones == null ? List.of() : List.copyOf(milestones);
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            edges = edges == null ? List.of() : List.copyOf(edges);
            metricBindings = metricBindings == null ? List.of() : List.copyOf(metricBindings);
            outcomeBindings = outcomeBindings == null ? List.of() : List.copyOf(outcomeBindings);
        }
    }

    private record PathwayAssetContent(
        String templateCode,
        String name,
        String diseaseCode,
        Integer templateVersion,
        PathwayTemplateLevel templateLevel,
        PathwayEntryMode entryMode,
        String startNodeCode,
        String sourceRef,
        String description,
        String entryCriteriaJson,
        String exitCriteriaJson,
        List<PathwayMilestoneAssetContent> milestones,
        List<PathwayNodeAssetContent> nodes,
        List<PathwayEdgeAssetContent> edges,
        List<PathwayMetricAssetContent> metricBindings,
        List<PathwayOutcomeAssetContent> outcomeBindings
    ) {}

    private record PathwayMilestoneAssetContent(
        String phaseCode,
        String phaseName,
        String milestoneCode,
        String name,
        Integer dayOffset,
        Integer expectedOffsetMinutes,
        String achievementCriteriaJson,
        Integer sortOrder
    ) {
        private static PathwayMilestoneAssetContent from(PathwayMilestone milestone) {
            return new PathwayMilestoneAssetContent(
                milestone.phaseCode(),
                milestone.phaseName(),
                milestone.milestoneCode(),
                milestone.name(),
                milestone.dayOffset(),
                milestone.expectedOffsetMinutes(),
                milestone.achievementCriteriaJson(),
                milestone.sortOrder()
            );
        }
    }

    private record PathwayNodeAssetContent(
        String nodeCode,
        String name,
        PathwayNodeType nodeType,
        String milestoneCode,
        Integer sortOrder,
        String responsibleRole,
        String accountableRole,
        String consultedRolesJson,
        String informedRolesJson,
        String dependencyJson,
        Integer timeWindowMinutes,
        boolean terminal,
        boolean disabled,
        String configJson
    ) {
        private static PathwayNodeAssetContent from(PathwayNode node) {
            return new PathwayNodeAssetContent(
                node.nodeCode(),
                node.name(),
                node.nodeType(),
                node.milestoneCode(),
                node.sortOrder(),
                node.responsibleRole(),
                node.accountableRole(),
                node.consultedRolesJson(),
                node.informedRolesJson(),
                node.dependencyJson(),
                node.timeWindowMinutes(),
                Boolean.TRUE.equals(node.terminalFlag()),
                Boolean.TRUE.equals(node.disabledFlag()),
                node.configJson()
            );
        }
    }

    private record PathwayEdgeAssetContent(
        String edgeCode,
        String fromNodeCode,
        String toNodeCode,
        PathwayEdgeType edgeType,
        String conditionJson,
        Integer priority
    ) {
        private static PathwayEdgeAssetContent from(PathwayEdge edge) {
            return new PathwayEdgeAssetContent(
                edge.edgeCode(),
                edge.fromNodeCode(),
                edge.toNodeCode(),
                edge.edgeType(),
                edge.conditionJson(),
                edge.priority()
            );
        }
    }

    private record PathwayMetricAssetContent(
        String nodeCode,
        String metricCode,
        boolean required
    ) {
        private static PathwayMetricAssetContent from(SpecialtyMetricBinding binding) {
            return new PathwayMetricAssetContent(
                binding.nodeCode(), binding.metricCode(), binding.requiredFlag());
        }
    }

    private record PathwayOutcomeAssetContent(
        PathwayOutcomeScope scope,
        String refCode,
        String indicatorCode
    ) {
        private static PathwayOutcomeAssetContent from(PathwayOutcomeBinding binding) {
            return new PathwayOutcomeAssetContent(
                binding.scope(), binding.refCode(), binding.indicatorCode());
        }
    }

    private record ClinicalClockSlaConfig(
        String baselineEvent,
        Integer minMinutes,
        Integer targetMinutes,
        Integer maxMinutes,
        List<ClockEscalationThreshold> escalations
    ) {
    }

    private record ClockEscalationThreshold(
        ClinicalClockEscalationLevel level,
        Integer afterMinutes
    ) {
    }

    private record EffectivePathwayTemplate(PathwayTemplate template, String sourceTenantId) {
    }
}
