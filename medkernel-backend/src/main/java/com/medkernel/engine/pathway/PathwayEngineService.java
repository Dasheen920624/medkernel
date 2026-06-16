package com.medkernel.engine.pathway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.authoring.AuthoringFeatureFlag;
import com.medkernel.engine.authoring.AuthoringFeatureGate;
import com.medkernel.engine.context.ClinicalEventContext;
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
import com.medkernel.engine.pkg.KnowledgePackage;
import com.medkernel.engine.pkg.KnowledgePackageRepository;
import com.medkernel.engine.pkg.PackageItem;
import com.medkernel.engine.pkg.PackageItemRepository;
import com.medkernel.engine.pkg.PackageReferenceConsistency;
import com.medkernel.engine.rule.ConditionEvaluation;
import com.medkernel.engine.rule.ConditionEvaluator;
import com.medkernel.engine.safety.ClinicalSafetyGuard;
import com.medkernel.engine.security.AuthenticatedRoleGuard;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.terminology.TerminologyCoverageGate;
import com.medkernel.engine.terminology.TerminologyCoverageIssue;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.InheritanceResolveQuery;
import com.medkernel.engine.versioning.InheritanceResolver;
import com.medkernel.engine.versioning.ReleasePort;
import com.medkernel.engine.versioning.ResolvedAssetVersion;
import com.medkernel.engine.versioning.RolloutPolicy;
import com.medkernel.engine.versioning.VersionReleaseCommand;
import com.medkernel.engine.versioning.VersionReleasePlan;
import com.medkernel.engine.versioning.VersionReleaseScopeType;
import com.medkernel.engine.versioning.VersionRollbackCommand;
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

/**
 * 路径引擎应用服务（GA-ENG-API-06 路径知识包 + 路径模板 + 患者路径实例 + 确定性推进）。
 *
 * <p>聚合路径知识包、专病画像、路径模板、节点、边、患者路径、变异、关键时钟和指标绑定九类数据，
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

    private static final String TEMPLATE_ENTITY = "pathway_template";
    private static final String PATIENT_PATHWAY_ENTITY = "patient_pathway";
    private static final int DEFAULT_CANARY_PERCENT = 10;
    private static final String RELEASE_STEP_CANARY = "canary_release";
    private static final String RELEASE_STEP_FULL = "full_rollout";
    private static final String RELEASE_STEP_ROLLBACK = "evidence_rollback";

    private final KnowledgePackageRepository packages;
    private final SpecialtyProfileRepository profiles;
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
    private final TerminologyCoverageGate terminologyCoverageGate;
    private final PathwayVersionedAssetAdapter versionedAssets;
    private final AssetVersionRepository assetVersions;
    private final ReleasePort releasePort;
    private final PackageItemRepository packageItems;
    private final InheritanceResolver inheritanceResolver;
    private final ConditionEvaluator conditionEvaluator;
    private final AuthoringFeatureGate authoringFeatureGate;

    /**
     * 注入路径引擎闭环所需仓库、推进器、审计发布器、状态记录器、诊断装配器和 JSON 工具。
     */
    @Autowired
    public PathwayEngineService(KnowledgePackageRepository packages,
                                SpecialtyProfileRepository profiles,
                                PathwayTemplateRepository templates,
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
                                ObjectProvider<TerminologyCoverageGate> terminologyCoverageGateProvider,
                                PathwayVersionedAssetAdapter versionedAssets,
                                AssetVersionRepository assetVersions,
                                ReleasePort releasePort,
                                PackageItemRepository packageItems,
                                InheritanceResolver inheritanceResolver) {
        this(packages, profiles, templates, nodes, milestones, edges, patientPathways, variances, clocks,
            metricBindings, outcomeBindings, evaluationIndicators, contextSnapshots, progressor, conditionEvaluator,
            authoringFeatureGate, auditRecorder, transitions, diagnoseAssembler, json,
            followupHandoffProvider.getIfAvailable(PathwayFollowupHandoffPort::noop),
            worklistProvider.getIfAvailable(PathwayWorklistPort::noop),
            domainEventProvider.getIfAvailable(EngineDomainEventPort::noop), safetyGuard,
            terminologyCoverageGateProvider.getIfAvailable(TerminologyCoverageGate::noop),
            versionedAssets, assetVersions, releasePort, packageItems, inheritanceResolver);
    }

    PathwayEngineService(KnowledgePackageRepository packages,
                         SpecialtyProfileRepository profiles,
                         PathwayTemplateRepository templates,
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
                         TerminologyCoverageGate terminologyCoverageGate,
                         PathwayVersionedAssetAdapter versionedAssets,
                         AssetVersionRepository assetVersions,
                         ReleasePort releasePort,
                         PackageItemRepository packageItems,
                         InheritanceResolver inheritanceResolver) {
        this(packages, profiles, templates, nodes, milestones, edges, patientPathways, variances, clocks,
            metricBindings, outcomeBindings, evaluationIndicators, contextSnapshots, progressor,
            new ConditionEvaluator(json), AuthoringFeatureGate.alwaysEnabled(), auditRecorder, transitions,
            diagnoseAssembler, json, followupHandoff, worklist, domainEvents, safetyGuard, terminologyCoverageGate,
            versionedAssets, assetVersions, releasePort, packageItems, inheritanceResolver);
    }

    PathwayEngineService(KnowledgePackageRepository packages,
                         SpecialtyProfileRepository profiles,
                         PathwayTemplateRepository templates,
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
                         TerminologyCoverageGate terminologyCoverageGate,
                         PathwayVersionedAssetAdapter versionedAssets,
                         AssetVersionRepository assetVersions,
                         ReleasePort releasePort,
                         PackageItemRepository packageItems,
                         InheritanceResolver inheritanceResolver) {
        this(packages, profiles, templates, nodes, milestones, edges, patientPathways, variances, clocks,
            metricBindings, outcomeBindings, evaluationIndicators, contextSnapshots, progressor, auditRecorder,
            transitions, diagnoseAssembler, json, followupHandoff, worklist, EngineDomainEventPort.noop(),
            safetyGuard, terminologyCoverageGate, versionedAssets, assetVersions, releasePort, packageItems,
            inheritanceResolver);
    }

    private PathwayEngineService(KnowledgePackageRepository packages,
                                 SpecialtyProfileRepository profiles,
                                 PathwayTemplateRepository templates,
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
                                 TerminologyCoverageGate terminologyCoverageGate,
                                 PathwayVersionedAssetAdapter versionedAssets,
                                 AssetVersionRepository assetVersions,
                                 ReleasePort releasePort,
                                 PackageItemRepository packageItems,
                                 InheritanceResolver inheritanceResolver) {
        this.packages = packages;
        this.profiles = profiles;
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
        this.terminologyCoverageGate = terminologyCoverageGate == null
            ? TerminologyCoverageGate.noop()
            : terminologyCoverageGate;
        this.versionedAssets = Objects.requireNonNull(versionedAssets, "路径统一版本适配器不能为空");
        this.assetVersions = Objects.requireNonNull(assetVersions, "统一资产版本仓库不能为空");
        this.releasePort = Objects.requireNonNull(releasePort, "统一发布端口不能为空");
        this.packageItems = Objects.requireNonNull(packageItems, "知识包条目仓库不能为空");
        this.inheritanceResolver = Objects.requireNonNull(inheritanceResolver, "继承解析器不能为空");
    }

    PathwayEngineService(KnowledgePackageRepository packages,
                         SpecialtyProfileRepository profiles,
                         PathwayTemplateRepository templates,
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
                         TerminologyCoverageGate terminologyCoverageGate,
                         PathwayVersionedAssetAdapter versionedAssets,
                         AssetVersionRepository assetVersions,
                         ReleasePort releasePort,
                         PackageItemRepository packageItems,
                         InheritanceResolver inheritanceResolver) {
        this(packages, profiles, templates, nodes, milestones, edges, patientPathways, variances, clocks,
            metricBindings, outcomeBindings, evaluationIndicators, contextSnapshots, progressor, auditRecorder, transitions, diagnoseAssembler, json,
            followupHandoff, PathwayWorklistPort.noop(), EngineDomainEventPort.noop(), safetyGuard,
            terminologyCoverageGate, versionedAssets, assetVersions, releasePort, packageItems,
            inheritanceResolver);
    }

    /**
     * 创建路径模板草稿，并一次性持久化模板节点、路径边和专病指标绑定。
     *
     * <p>前置：关联路径知识包必须存在于当前租户；失败抛出 {@code ENG-PATHWAY-007}。
     */
    @Transactional
    public PathwayTemplateDetailResponse createTemplate(PathwayTemplateCreateRequest request) {
        String tenantId = requireCurrentTenant();
        String traceId = RequestContext.currentTraceId();
        String actor = currentActor();
        Instant now = Instant.now();
        KnowledgePackage knowledgePackage = packages.findByPackageIdAndTenantId(request.packageId(), tenantId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_PATHWAY_007,
                "路径知识包不存在: " + request.packageId()));
        validateParentTemplate(request, tenantId);
        String templateId = "pt-" + UUID.randomUUID();
        PathwayTemplate template = templates.save(new PathwayTemplate(
            null, templateId, tenantId, knowledgePackage.packageId(), request.templateCode(),
            request.name(), request.diseaseCode(), request.templateVersion(), request.templateLevel(),
            blankToNull(request.parentTemplateId()), PathwayTemplateStatus.DRAFT,
            request.entryMode(), request.startNodeCode(), request.sourceRef(),
            request.description(), writeJson(request.entryCriteria()), writeJson(request.exitCriteria()),
            now, actor, now, actor, traceId));
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
                null, "smb-" + UUID.randomUUID(), tenantId, knowledgePackage.packageId(),
                templateId, binding.nodeCode(), binding.metricCode(),
                Boolean.TRUE.equals(binding.required()), now, actor, now, actor, traceId)))
            .toList();
        String effectivePackageVersion = notBlank(request.packageVersion(), knowledgePackage.packageVersion());
        List<PathwayOutcomeBinding> savedOutcomeBindings = nullToEmpty(request.outcomeBindings()).stream()
            .map(binding -> outcomeBindings.save(new PathwayOutcomeBinding(
                null,
                "pob-" + UUID.randomUUID(),
                tenantId,
                templateId,
                binding.scope(),
                normalizedOutcomeRefCode(binding.scope(), binding.refCode()),
                binding.indicatorCode().trim(),
                notBlank(binding.packageVersion(), effectivePackageVersion),
                now,
                actor,
                now,
                actor,
                traceId)))
            .toList();

        ensurePathwayPackageItem(template, actor, now, traceId);
        AssetVersion assetVersion = versionedAssets.registerDraft(new AssetVersionRegisterCommand(
            template.tenantId(),
            VersionedAssetType.PATHWAY,
            template.templateCode(),
            String.valueOf(template.templateVersion()),
            releaseOrgScope(template),
            releaseApplicableScope(template),
            pathwayContent(template, savedMilestones, savedNodes, savedEdges, savedBindings, savedOutcomeBindings),
            null,
            template.sourceRef(),
            actor,
            traceId,
            AssetVersionSafetyPolicy.NORMAL,
            null
        ));
        transitions.record(TEMPLATE_ENTITY, templateId, null, PathwayTemplateStatus.DRAFT.name(),
            "CREATE_PATHWAY_TEMPLATE", null);
        auditRecorder.record(AuditAction.CREATE, TEMPLATE_ENTITY, templateId,
            "创建路径模板 " + request.templateCode());
        return new PathwayTemplateDetailResponse(
            template, savedMilestones, savedNodes, savedEdges, savedBindings, savedOutcomeBindings,
            assetVersion.status(), traceId);
    }

    private void ensurePathwayPackageItem(
            PathwayTemplate template,
            String actor,
            Instant now,
            String traceId) {
        packageItems.findByTenantIdAndPackageIdAndAssetTypeAndAssetId(
                template.tenantId(), template.packageId(), VersionedAssetType.PATHWAY, template.templateId())
            .orElseGet(() -> packageItems.save(new PackageItem(
                null,
                "pi-" + UUID.randomUUID(),
                template.tenantId(),
                template.packageId(),
                VersionedAssetType.PATHWAY,
                template.templateId(),
                String.valueOf(template.templateVersion()),
                now,
                actor,
                now,
                actor,
                traceId
            )));
    }

    /**
     * 对路径模板执行发布门禁并将草稿发布为 {@code PUBLISHED}。
     *
     * <p>门禁校验起始节点、终止节点、节点编码唯一性、边端点存在性和时间窗合法性。
     */
    @Transactional
    public PathwayTemplatePublishResponse publishTemplate(String templateId) {
        return publishTemplate(templateId, null);
    }

    /**
     * 对路径模板执行发布门禁并将草稿发布为 {@code PUBLISHED}。
     *
     * <p>发布必须携带当前影响摘要和审核说明，成功后进入 7 步流的灰度发布步骤。
     */
    @Transactional
    public PathwayTemplatePublishResponse publishTemplate(String templateId, PathwayOperationRequest request) {
        String tenantId = requireCurrentTenant();
        PathwayTemplate template = findTemplate(templateId, tenantId);
        ensureTemplateDraft(template);
        EffectivePathwayGraph graph = effectiveGraphFor(template, tenantId);
        validatePublishGate(
            template, graph.milestones(), graph.nodes(), graph.edges(), graph.metricBindings(),
            graph.outcomeBindings());
        PathwayTemplateImpactResponse impact = templateImpactFor(
            template, graph.milestones(), graph.nodes(), graph.edges(), graph.metricBindings(),
            graph.outcomeBindings(),
            patientPathways.findByTemplateIdAndTenantIdOrderByEnteredAtDesc(templateId, tenantId));
        validateReleaseGate(template, request, impact);
        ensureTerminologyCoverage(graph.edges());

        Instant now = Instant.now();
        String actor = currentActor();
        PathwayTemplate published = copyTemplate(
            template, PathwayTemplateStatus.PUBLISHED, now, actor, RequestContext.currentTraceId());
        templates.save(published);
        List<String> releaseEvidence = mergedReleaseEvidence(
            impact.releaseEvidence(),
            coordinateCanaryRelease(template, impact, request, actor)
        );
        transitions.record(TEMPLATE_ENTITY, templateId, template.status().name(),
            PathwayTemplateStatus.PUBLISHED.name(), "PUBLISH_PATHWAY_TEMPLATE", null);
        auditRecorder.record(AuditAction.PUBLISH, TEMPLATE_ENTITY, templateId,
            "发布路径模板 " + template.templateCode());
        return new PathwayTemplatePublishResponse(
            templateId, PathwayTemplateStatus.PUBLISHED, RELEASE_STEP_CANARY, impact.canaryPercent(),
            impact.impactDigest(), impact.analysisStatus(), releaseEvidence,
            RequestContext.currentTraceId());
    }

    private List<String> coordinateCanaryRelease(
            PathwayTemplate template,
            PathwayTemplateImpactResponse impact,
            PathwayOperationRequest request,
            String actor) {
        AssetVersion assetVersion = requirePathwayAssetVersion(template);
        VersionReleaseCommand command = new VersionReleaseCommand(
            template.tenantId(),
            VersionedAssetType.PATHWAY,
            template.templateCode(),
            assetVersion.versionId(),
            releaseOrgScope(template),
            releaseApplicableScope(template),
            null,
            null,
            RolloutPolicy.canaryBedPercent(impact.canaryPercent()),
            impact.impactDigest(),
            releaseReason(request, "路径发布门禁通过"),
            actor,
            RequestContext.currentTraceId(),
            request == null ? null : request.publishEvidence().electronicSignature(),
            request == null ? null : request.publishEvidence().qualityGate()
        );
        return advanceCanaryRelease(assetVersion, command);
    }

    private List<String> coordinateFullRelease(
            PathwayTemplate template,
            PathwayTemplateImpactResponse impact,
            PathwayOperationRequest request,
            String actor) {
        AssetVersion assetVersion = requirePathwayAssetVersion(template);
        VersionReleaseCommand command = new VersionReleaseCommand(
            template.tenantId(),
            VersionedAssetType.PATHWAY,
            template.templateCode(),
            assetVersion.versionId(),
            releaseOrgScope(template),
            releaseApplicableScope(template),
            VersionReleaseScopeType.ALL,
            null,
            RolloutPolicy.all(),
            impact.impactDigest(),
            releaseReason(request, "路径全量发布门禁通过"),
            actor,
            RequestContext.currentTraceId(),
            request == null ? null : request.publishEvidence().electronicSignature(),
            request == null ? null : request.publishEvidence().qualityGate()
        );
        return advanceFullRelease(assetVersion, command);
    }

    private List<String> coordinateRollback(
            PathwayTemplate current,
            PathwayTemplate target,
            PathwayOperationRequest request,
            String actor) {
        AssetVersion currentAsset = findPathwayAssetVersion(current)
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_PATHWAY_004,
                "当前路径版本缺少统一资产映射，禁止回滚"
            ));
        AssetVersion targetAsset = findPathwayAssetVersion(target)
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_PATHWAY_004,
                "目标路径版本缺少统一资产映射，禁止回滚"
            ));
        VersionRollbackCommand command = new VersionRollbackCommand(
            current.tenantId(),
            VersionedAssetType.PATHWAY,
            current.templateCode(),
            currentAsset.versionId(),
            targetAsset.versionId(),
            String.valueOf(current.templateVersion()),
            String.valueOf(target.templateVersion()),
            releaseReason(request, "路径回滚门禁通过"),
            true,
            actor,
            RequestContext.currentTraceId()
        );
        List<String> evidence = new ArrayList<>();
        appendEvidence(evidence, releasePort.rollback(command));
        return evidence;
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
            String.valueOf(template.templateVersion())
        );
    }

    private AssetVersion requireRuntimePathwayAssetVersion(PathwayTemplate template, String patientId) {
        AssetVersion assetVersion = findPathwayAssetVersion(template)
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_PATHWAY_005,
                "路径模板缺少统一资产版本，不能入径: "
                    + template.templateCode() + "@" + template.templateVersion()
            ));
        if (assetVersion.status() == AssetVersionStatus.PUBLISHED) {
            return assetVersion;
        }
        if (assetVersion.status() == AssetVersionStatus.APPROVED) {
            if (isCanaryEligible(patientId, template.templateCode())) {
                return assetVersion;
            }
            throw new ApiException(
                ErrorCode.ENG_PATHWAY_005,
                "患者不在路径灰度范围，不能入径: "
                    + template.templateCode() + "@" + template.templateVersion()
            );
        }
        throw new ApiException(
            ErrorCode.ENG_PATHWAY_005,
            "路径模板统一版本尚未发布，不能入径: "
                + template.templateCode() + "@" + template.templateVersion()
        );
    }

    private boolean hasActivePathwayAssetVersion(PathwayTemplate template) {
        return findPathwayAssetVersion(template)
            .filter(assetVersion -> assetVersion.status() == AssetVersionStatus.PUBLISHED)
            .isPresent();
    }

    private List<String> advanceCanaryRelease(AssetVersion assetVersion, VersionReleaseCommand command) {
        List<String> evidence = new ArrayList<>();
        AssetVersionStatus status = assetVersion.status();
        if (status == AssetVersionStatus.DRAFT) {
            appendEvidence(evidence, releasePort.submitForReview(command));
            appendEvidence(evidence, releasePort.approveReview(command));
            appendEvidence(evidence, releasePort.releaseGray(command));
            return evidence;
        }
        if (status == AssetVersionStatus.IN_REVIEW) {
            appendEvidence(evidence, releasePort.approveReview(command));
            appendEvidence(evidence, releasePort.releaseGray(command));
            return evidence;
        }
        if (status == AssetVersionStatus.APPROVED || status == AssetVersionStatus.PUBLISHED) {
            appendEvidence(evidence, releasePort.releaseGray(command));
        }
        return evidence;
    }

    private List<String> advanceFullRelease(AssetVersion assetVersion, VersionReleaseCommand command) {
        List<String> evidence = new ArrayList<>();
        AssetVersionStatus status = assetVersion.status();
        if (status == AssetVersionStatus.DRAFT) {
            appendEvidence(evidence, releasePort.submitForReview(command));
            appendEvidence(evidence, releasePort.approveReview(command));
            appendEvidence(evidence, releasePort.publish(command));
            return evidence;
        }
        if (status == AssetVersionStatus.IN_REVIEW) {
            appendEvidence(evidence, releasePort.approveReview(command));
            appendEvidence(evidence, releasePort.publish(command));
            return evidence;
        }
        if (status == AssetVersionStatus.APPROVED || status == AssetVersionStatus.PUBLISHED) {
            appendEvidence(evidence, releasePort.publish(command));
        }
        return evidence;
    }

    private static List<String> mergedReleaseEvidence(List<String> base, List<String> unified) {
        List<String> evidence = new ArrayList<>();
        if (base != null) {
            evidence.addAll(base);
        }
        if (unified != null) {
            evidence.addAll(unified);
        }
        return evidence;
    }

    private static void appendEvidence(List<String> evidence, VersionReleasePlan plan) {
        if (plan != null && plan.evidenceSummary() != null && !plan.evidenceSummary().isBlank()) {
            evidence.add(plan.evidenceSummary());
        }
    }

    private static String releaseOrgScope(PathwayTemplate template) {
        return "tenant:" + template.tenantId();
    }

    private static String releaseApplicableScope(PathwayTemplate template) {
        return "disease:" + notBlank(template.diseaseCode(), "ALL");
    }

    private static String releaseReason(PathwayOperationRequest request, String fallback) {
        return request == null ? fallback : notBlank(request.reason(), fallback);
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
            template.parentTemplateId(),
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

    /**
     * 计算路径发布前影响摘要。
     *
     * <p>摘要只来自模板拓扑、关键时钟绑定和当前患者路径实例事实，用于发布 7 步流留痕。
     */
    @Transactional(readOnly = true)
    public PathwayTemplateImpactResponse templateImpact(String templateId) {
        String tenantId = requireCurrentTenant();
        PathwayTemplate template = findTemplate(templateId, tenantId);
        EffectivePathwayGraph graph = effectiveGraphFor(template, tenantId);
        validatePublishGate(
            template, graph.milestones(), graph.nodes(), graph.edges(), graph.metricBindings(),
            graph.outcomeBindings());
        return templateImpactFor(
            template, graph.milestones(), graph.nodes(), graph.edges(), graph.metricBindings(),
            graph.outcomeBindings(),
            patientPathways.findByTemplateIdAndTenantIdOrderByEnteredAtDesc(templateId, tenantId));
    }

    /**
     * 读取路径模板相对父级模板链的差异，并返回有效合并后的节点和边。
     */
    @Transactional(readOnly = true)
    public PathwayTemplateInheritanceDiffResponse templateInheritanceDiff(String templateId) {
        String tenantId = requireCurrentTenant();
        PathwayTemplate template = findTemplate(templateId, tenantId);
        MergedPathwayGraph graph = resolveTemplateInheritanceGraph(template, tenantId, new HashSet<>());
        return new PathwayTemplateInheritanceDiffResponse(
            template.templateId(),
            template.parentTemplateId(),
            graph.diffItems(),
            graph.nodes(),
            graph.edges(),
            RequestContext.currentTraceId());
    }

    private EffectivePathwayGraph effectiveGraphFor(PathwayTemplate template, String tenantId) {
        MergedPathwayGraph inheritanceGraph = resolveTemplateInheritanceGraph(template, tenantId, new HashSet<>());
        List<PathwayNode> graphNodes = inheritanceGraph.nodes().stream()
            .map(node -> toEffectivePathwayNode(template, tenantId, node))
            .toList();
        Set<String> activeNodeCodes = pathwayNodeCodeSet(graphNodes);
        List<PathwayMilestone> graphMilestones = resolveEffectiveMilestones(template, tenantId, new HashSet<>());
        return new EffectivePathwayGraph(
            graphMilestones,
            graphNodes,
            inheritanceGraph.edges(),
            resolveEffectiveMetricBindings(template, tenantId, new HashSet<>(), activeNodeCodes),
            resolveEffectiveOutcomeBindings(template, tenantId, new HashSet<>(), graphMilestones));
    }

    private PathwayNode toEffectivePathwayNode(
            PathwayTemplate template,
            String tenantId,
            PathwayMergedNode node) {
        Instant now = Instant.now();
        return new PathwayNode(
            null,
            "effective-" + template.templateId() + "-" + node.nodeCode(),
            tenantId,
            template.templateId(),
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
            node.terminalFlag(),
            false,
            node.configJson(),
            now,
            "inheritance-resolver",
            now,
            "inheritance-resolver",
            RequestContext.currentTraceId());
    }

    /**
     * 对已经灰度发布的路径模板执行院级全量确认。
     */
    @Transactional
    public PathwayTemplatePublishResponse fullRolloutTemplate(String templateId, PathwayOperationRequest request) {
        String tenantId = requireCurrentTenant();
        PathwayTemplate template = findTemplate(templateId, tenantId);
        ensureTemplatePublished(template, "当前路径模板状态不允许全量发布");
        PathwayTemplateImpactResponse impact = templateImpact(templateId);
        validateReleaseGate(template, request, impact);
        requireReleaseCoordinator(template);

        Instant now = Instant.now();
        String actor = currentActor();
        templates.save(copyTemplate(
            template, PathwayTemplateStatus.PUBLISHED, now, actor, RequestContext.currentTraceId()));
        List<String> releaseEvidence = mergedReleaseEvidence(
            impact.releaseEvidence(),
            coordinateFullRelease(template, impact, request, actor)
        );
        transitions.record(TEMPLATE_ENTITY, templateId, template.status().name(),
            PathwayTemplateStatus.PUBLISHED.name(), "FULL_ROLLOUT_PATHWAY_TEMPLATE", null);
        auditRecorder.record(AuditAction.PUBLISH, TEMPLATE_ENTITY, templateId,
            "全量发布路径模板 " + template.templateCode());
        return new PathwayTemplatePublishResponse(
            templateId, PathwayTemplateStatus.PUBLISHED, RELEASE_STEP_FULL, 100,
            impact.impactDigest(), impact.analysisStatus(), releaseEvidence,
            RequestContext.currentTraceId());
    }

    /**
     * 将当前已发布路径模板回滚到同编码历史版本。
     */
    @Transactional
    public PathwayTemplatePublishResponse rollbackTemplate(String templateId, PathwayOperationRequest request) {
        String tenantId = requireCurrentTenant();
        PathwayTemplate current = findTemplate(templateId, tenantId);
        ensureTemplatePublished(current, "当前路径模板状态不允许回滚");
        PathwayTemplateImpactResponse impact = templateImpact(templateId);
        validateReleaseGate(current, request, impact);
        requireReleaseCoordinator(current);
        if (request == null || isBlank(request.rollbackTargetTemplateId())) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004, "路径回滚必须指定目标模板版本");
        }
        if (Objects.equals(templateId, request.rollbackTargetTemplateId())) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004, "路径回滚目标不能是当前模板");
        }
        PathwayTemplate target = findTemplate(request.rollbackTargetTemplateId(), tenantId);
        if (!Objects.equals(current.templateCode(), target.templateCode())) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004, "路径回滚目标必须属于同一模板编码");
        }
        if (target.status() != PathwayTemplateStatus.OFFLINE) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004, "路径回滚目标必须是已下线历史版本");
        }

        Instant now = Instant.now();
        String actor = currentActor();
        List<String> unifiedReleaseEvidence = coordinateRollback(current, target, request, actor);
        templates.save(copyTemplate(
            current, PathwayTemplateStatus.OFFLINE, now, actor, RequestContext.currentTraceId()));
        templates.save(copyTemplate(
            target, PathwayTemplateStatus.PUBLISHED, now, actor, RequestContext.currentTraceId()));
        List<String> releaseEvidence = mergedReleaseEvidence(
            impact.releaseEvidence(),
            unifiedReleaseEvidence
        );
        transitions.record(TEMPLATE_ENTITY, current.templateId(), current.status().name(),
            PathwayTemplateStatus.OFFLINE.name(), "ROLLBACK_PATHWAY_TEMPLATE_CURRENT", null);
        transitions.record(TEMPLATE_ENTITY, target.templateId(), target.status().name(),
            PathwayTemplateStatus.PUBLISHED.name(), "ROLLBACK_PATHWAY_TEMPLATE_TARGET", null);
        auditRecorder.record(AuditAction.ROLLBACK, TEMPLATE_ENTITY, target.templateId(),
            "回滚路径模板 " + target.templateCode());
        return new PathwayTemplatePublishResponse(
            target.templateId(), PathwayTemplateStatus.PUBLISHED, RELEASE_STEP_ROLLBACK, 0,
            impact.impactDigest(), impact.analysisStatus(), releaseEvidence,
            RequestContext.currentTraceId());
    }

    /**
     * 按状态、病种、路径知识包和模板编码过滤分页查询路径模板。
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
        String templateCode = filter == null ? null : filter.templateCode();
        String keyword = filter == null ? null : keywordLike(filter.keyword());
        if (requiresEffectiveTemplateMerge(tenantId, status)) {
            String platformStatus = PathwayTemplateStatus.PUBLISHED.name();
            long total = templates.countEffectiveByFilter(
                tenantId, PlatformTenant.ID, status, platformStatus,
                diseaseCode, packageId, templateCode, keyword);
            if (total == 0) {
                return PageResponse.empty(safePage);
            }
            List<PathwayTemplate> rows = templates.pageEffectiveByFilter(
                tenantId, PlatformTenant.ID, status, platformStatus,
                diseaseCode, packageId, templateCode, keyword,
                safePage.offset(), safePage.safeSize());
            return PageResponse.of(rows, safePage, total);
        }
        long total = templates.countByFilter(tenantId, status, diseaseCode, packageId, templateCode, keyword);
        if (total == 0) {
            return PageResponse.empty(safePage);
        }
        List<PathwayTemplate> rows = templates.pageByFilter(
            tenantId, status, diseaseCode, packageId, templateCode, keyword,
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
     * 装配路径模板详情。
     *
     * <p>返回模板主表、按阶段顺序排列的里程碑、按顺序排列的节点、按优先级排列的边和按节点排列的指标绑定。
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
            assetVersion.status(),
            RequestContext.currentTraceId());
    }

    /**
     * 为患者创建路径实例并进入模板起始节点或请求指定起点。
     *
     * <p>仅允许基于统一版本状态为 {@code ACTIVE} 的模板入径；成功后创建首个
     * {@link ClinicalClock} 关键时钟。
     */
    @Transactional
    public PatientPathwayDetailResponse enterPatientPathway(PatientPathwayEnterRequest request) {
        String tenantId = requireCurrentTenant();
        ContextSnapshotResponse snapshot = contextSnapshots.findById(request.contextSnapshotId());
        if (snapshot.status() != ContextSnapshotStatus.ACTIVE || snapshot.resources() == null
                || snapshot.resources().patient() == null) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_001,
                "患者入径只能使用包含标准患者资源的 ACTIVE 上下文快照");
        }
        String patientId = snapshot.resources().patient().mpi();
        String encounterId = snapshot.resources().encounters().isEmpty()
            ? null
            : snapshot.resources().encounters().getFirst().encounterId();
        EffectivePathwayTemplate effective = findEffectiveTemplate(request.templateId(), tenantId);
        PathwayTemplate template = effective.template();
        ensurePathwayRuntimePackageConsistency(
            template,
            effective.sourceTenantId(),
            request.packageVersion(),
            ErrorCode.ENG_PATHWAY_001,
            "患者入径包版本必须与路径模板所属包版本一致");
        requireRuntimePathwayAssetVersion(template, patientId);
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
            template.templateId(), startNode.nodeCode(), PatientPathwayStatus.NODE_EXECUTING,
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
        if (effectiveStart.isPresent()
                || !isBlank(template.parentTemplateId())
                || !graph.nodes().isEmpty()) {
            return effectiveStart;
        }
        return nodes.findByTemplateIdAndTenantIdAndNodeCode(
            template.templateId(), sourceTenantId, startNodeCode);
    }

    private List<SpecialtyMetricBinding> entryMetricBindings(
            PathwayTemplate template,
            String sourceTenantId,
            EffectivePathwayGraph graph) {
        if (!graph.metricBindings().isEmpty()
                || !isBlank(template.parentTemplateId())
                || !graph.nodes().isEmpty()) {
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
                    "患者存在并行路径共享医嘱集 " + currentOrderSetRef + "，仅提示协调，不自动改医嘱"));
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
                () -> packageVersionForTemplate(effective.template(), effective.sourceTenantId()),
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
            () -> {
                EffectivePathwayTemplate effective = findPinnedRuntimeTemplate(runtime.templateId(), tenantId);
                return packageVersionForTemplate(effective.template(), effective.sourceTenantId());
            },
            clocks.findByPatientPathwayIdAndTenantIdOrderByStartedAtAsc(patientPathwayId, tenantId),
            Instant.now());
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
                    ContextSnapshotResponse snapshot = simulationSnapshot(
                        template, effective.sourceTenantId(), snapshotId, request.packageVersion());
                    validateEntryCriteria(template, snapshot.resources());
                    return simulateStep(graph, startNodeCode, requestedTargets, snapshot);
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
            ? null : simulationSnapshot(
                template, effective.sourceTenantId(), request.snapshotId(), request.packageVersion());
        if (snapshot != null) {
            validateEntryCriteria(template, snapshot.resources());
        }
        PathwaySimulationReplayStep step = simulateStep(graph, startNodeCode, requestedTargets, snapshot);
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
                PathwayAdvanceEventType.COMPLETE, requestedTarget, facts));
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

    private ContextSnapshotResponse simulationSnapshot(
            PathwayTemplate template,
            String sourceTenantId,
            String snapshotId,
            String requestPackageVersion) {
        return runtimeSnapshot(
            template,
            sourceTenantId,
            snapshotId,
            requestPackageVersion,
            ErrorCode.ENG_PATHWAY_001,
            "路径仿真包版本必须与路径模板所属包版本一致");
    }

    private ContextSnapshotResponse runtimeSnapshot(
            PathwayTemplate template,
            String sourceTenantId,
            String snapshotId,
            String requestPackageVersion,
            ErrorCode errorCode,
            String message) {
        ContextSnapshotResponse snapshot = contextSnapshots.findById(snapshotId);
        String effectivePackageVersion = notBlank(requestPackageVersion, snapshot.packageVersion());
        ensurePathwayRuntimePackageConsistency(
            template,
            sourceTenantId,
            effectivePackageVersion,
            errorCode,
            message);
        return snapshot;
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
        EffectivePathwayTemplate effective = findPinnedRuntimeTemplate(runtime.templateId(), tenantId);
        EffectivePathwayGraph graph = effectiveGraphFor(effective.template(), effective.sourceTenantId());
        ContextSnapshotResponse snapshot = isBlank(request.snapshotId())
            ? null : runtimeSnapshot(
                effective.template(),
                effective.sourceTenantId(),
                request.snapshotId(),
                request.packageVersion(),
                ErrorCode.ENG_PATHWAY_001,
                "路径推进包版本必须与路径模板所属包版本一致");
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
            progressEventType, requestedNextNodeCode, facts));

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
                packageVersionForTemplate(effective.template(), effective.sourceTenantId()),
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

    private PathwayTemplateImpactResponse templateImpactFor(PathwayTemplate template,
                                                            List<PathwayMilestone> graphMilestones,
                                                            List<PathwayNode> graphNodes,
                                                            List<PathwayEdge> graphEdges,
                                                            List<SpecialtyMetricBinding> graphBindings,
                                                            List<PathwayOutcomeBinding> graphOutcomeBindings,
                                                            List<PatientPathway> runtimes) {
        List<PathwayMilestone> safeMilestones = nullToEmpty(graphMilestones);
        List<PathwayNode> safeNodes = nullToEmpty(graphNodes);
        List<PathwayEdge> safeEdges = nullToEmpty(graphEdges);
        List<SpecialtyMetricBinding> safeBindings = nullToEmpty(graphBindings);
        List<PathwayOutcomeBinding> safeOutcomeBindings = nullToEmpty(graphOutcomeBindings);
        List<PatientPathway> safeRuntimes = nullToEmpty(runtimes);
        int timedNodeCount = (int) safeNodes.stream()
            .filter(node -> node.timeWindowMinutes() != null && node.timeWindowMinutes() > 0)
            .count();
        int terminalNodeCount = (int) safeNodes.stream()
            .filter(node -> Boolean.TRUE.equals(node.terminalFlag()))
            .count();
        List<String> referencedAssets =
            pathwayReferenceSummaries(template, safeNodes, safeEdges, safeOutcomeBindings);
        List<String> releaseEvidence = List.of(
            "阶段里程碑 " + safeMilestones.size() + " 个，拓扑节点 " + safeNodes.size()
                + " 个，边 " + safeEdges.size() + " 条，终止节点 " + terminalNodeCount + " 个",
            "关键时钟节点 " + timedNodeCount + " 个，当前关联患者路径实例 "
                + safeRuntimes.size() + " 条",
            "结局指标绑定 " + safeOutcomeBindings.size() + " 个，发布后用于 LOS、再入院、并发症和成本等质控闭环",
            "引用资产 " + referencedAssets.size() + " 个"
                + (referencedAssets.isEmpty() ? "" : "：" + String.join("、", referencedAssets)),
            "灰度发布默认 10%，全量前必须保留本次 impactDigest，可按审计记录回滚到上一版本"
        );
        String impactDigest = digest(String.join("|",
            template.templateId(),
            template.templateCode(),
            String.valueOf(template.templateVersion()),
            template.startNodeCode(),
            safeMilestones.stream().map(this::milestoneImpactSignature).sorted().toList().toString(),
            safeNodes.stream().map(this::nodeImpactSignature).sorted().toList().toString(),
            safeEdges.stream().map(this::edgeImpactSignature).sorted().toList().toString(),
            safeBindings.stream().map(this::bindingImpactSignature).sorted().toList().toString(),
            safeOutcomeBindings.stream().map(this::outcomeBindingImpactSignature).sorted().toList().toString(),
            referencedAssets.toString(),
            safeRuntimes.stream().map(PatientPathway::patientPathwayId).sorted().toList().toString()
        ));
        return new PathwayTemplateImpactResponse(
            template.templateId(), "COMPLETE", safeRuntimes.size(), safeNodes.size(), safeEdges.size(),
            timedNodeCount, terminalNodeCount, safeOutcomeBindings.size(), DEFAULT_CANARY_PERCENT, impactDigest,
            releaseEvidence, RequestContext.currentTraceId());
    }

    private List<String> pathwayReferenceSummaries(
            PathwayTemplate template,
            List<PathwayNode> graphNodes,
            List<PathwayEdge> graphEdges,
            List<PathwayOutcomeBinding> graphOutcomeBindings) {
        ArrayList<String> summaries = new ArrayList<>();
        appendReferenceSummaries(summaries, template.entryCriteriaJson(), "路径模板入径条件 " + template.templateCode());
        appendReferenceSummaries(summaries, template.exitCriteriaJson(), "路径模板出径条件 " + template.templateCode());
        for (PathwayNode node : nullToEmpty(graphNodes)) {
            appendReferenceSummaries(summaries, node.configJson(), "路径节点配置 " + node.nodeCode());
        }
        for (PathwayEdge edge : nullToEmpty(graphEdges)) {
            appendReferenceSummaries(summaries, edge.conditionJson(), "路径边条件 " + edge.edgeCode());
        }
        for (PathwayOutcomeBinding binding : nullToEmpty(graphOutcomeBindings)) {
            if (!isBlank(binding.indicatorCode())) {
                summaries.add("EVALUATION:" + binding.indicatorCode().trim()
                    + (isBlank(binding.packageVersion()) ? "" : "@" + binding.packageVersion().trim()));
            }
        }
        return summaries.stream().distinct().sorted().toList();
    }

    private void appendReferenceSummaries(List<String> target, String jsonText, String ownerLabel) {
        target.addAll(PackageReferenceConsistency.referenceSummaries(
            readJsonOrEmpty(jsonText, ownerLabel, ErrorCode.ENG_PATHWAY_004)));
    }

    private void validateReleaseGate(
            PathwayTemplate template,
            PathwayOperationRequest request,
            PathwayTemplateImpactResponse impact) {
        if (request == null || isBlank(request.impactDigest())
                || !Objects.equals(request.impactDigest(), impact.impactDigest())) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004, "路径发布前必须提交当前影响分析摘要");
        }
        if (isBlank(request.reason())) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004, "路径发布前必须填写审核说明");
        }
        if (Boolean.TRUE.equals(request.directFullRollout())) {
            requireReleaseCoordinator(template);
        }
    }

    private void ensureTerminologyCoverage(List<PathwayEdge> graphEdges) {
        List<TerminologyCoverageIssue> issues = new ArrayList<>();
        for (PathwayEdge edge : nullToEmpty(graphEdges)) {
            if (isBlank(edge.conditionJson())) {
                continue;
            }
            issues.addAll(terminologyCoverageGate.checkConditionCoverage(readConditionJson(edge)));
        }
        if (!issues.isEmpty()) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "路径发布存在未覆盖编码对照，禁止上线：" + TerminologyCoverageGate.describeIssues(issues));
        }
    }

    private JsonNode readConditionJson(PathwayEdge edge) {
        try {
            return json.readTree(edge.conditionJson());
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "路径边条件 JSON 解析失败：" + edge.edgeCode(), exception);
        }
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

    private void requireReleaseCoordinator(PathwayTemplate template) {
        boolean platformTemplate = PlatformTenant.isPlatformTenant(template.tenantId());
        boolean allowed = platformTemplate
            ? AuthenticatedRoleGuard.has(RoleCode.PLATFORM_KNOWLEDGE_GOVERNOR)
                || AuthenticatedRoleGuard.has(RoleCode.PLATFORM_GOVERNANCE_ADMIN)
            : AuthenticatedRoleGuard.has(RoleCode.CLINICAL_GOVERNOR)
                || AuthenticatedRoleGuard.has(RoleCode.ORGANIZATION_ADMIN);
        if (!allowed) {
            String roleName = platformTemplate ? "平台知识治理员" : "临床治理负责人或机构管理员";
            throw new ApiException(
                ErrorCode.ENG_PATHWAY_004,
                "路径全量或回滚必须由" + roleName + "确认"
            );
        }
    }

    private boolean isCanaryEligible(String patientId, String templateCode) {
        if (isBlank(patientId) || isBlank(templateCode)) {
            return false;
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest((patientId + "|" + templateCode).getBytes(StandardCharsets.UTF_8));
            int prefix = (hash[0] & 0xff) << 24
                | (hash[1] & 0xff) << 16
                | (hash[2] & 0xff) << 8
                | (hash[3] & 0xff);
            return Integer.remainderUnsigned(prefix, 100) < DEFAULT_CANARY_PERCENT;
        } catch (NoSuchAlgorithmException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "无法计算路径灰度分桶", exception);
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
            List.of("QUESTIONNAIRE")));
    }

    private String nodeImpactSignature(PathwayNode node) {
        return String.join(":",
            node.nodeCode(),
            node.nodeType().name(),
            String.valueOf(node.milestoneCode()),
            String.valueOf(node.sortOrder()),
            String.valueOf(node.timeWindowMinutes()),
            String.valueOf(node.terminalFlag()));
    }

    private String milestoneImpactSignature(PathwayMilestone milestone) {
        return String.join(":",
            String.valueOf(milestone.phaseCode()),
            String.valueOf(milestone.phaseName()),
            String.valueOf(milestone.milestoneCode()),
            String.valueOf(milestone.name()),
            String.valueOf(milestone.dayOffset()),
            String.valueOf(milestone.expectedOffsetMinutes()),
            String.valueOf(milestone.sortOrder()));
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
                // 模板级绑定使用固定 ref，便于唯一约束与资产快照稳定。
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

    private void validateParentTemplate(PathwayTemplateCreateRequest request, String tenantId) {
        if (request == null || isBlank(request.parentTemplateId())) {
            return;
        }
        PathwayTemplate parent = findTemplate(request.parentTemplateId(), tenantId);
        if (!Objects.equals(parent.diseaseCode(), request.diseaseCode())) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004, "父级路径模板病种必须与当前模板一致");
        }
        if (templateLevelRank(parent.templateLevel()) >= templateLevelRank(request.templateLevel())) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "父级路径模板层级必须高于当前模板: " + parent.templateLevel() + " -> " + request.templateLevel());
        }
        assertParentChainAcyclic(parent, tenantId, new HashSet<>());
    }

    private void assertParentChainAcyclic(PathwayTemplate template, String tenantId, Set<String> visited) {
        if (!visited.add(template.templateId())) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004, "路径模板继承链存在环: " + template.templateId());
        }
        if (isBlank(template.parentTemplateId())) {
            return;
        }
        assertParentChainAcyclic(findTemplate(template.parentTemplateId(), tenantId), tenantId, visited);
    }

    private int templateLevelRank(PathwayTemplateLevel level) {
        return switch (level) {
            case STANDARD -> 0;
            case GROUP -> 1;
            case HOSPITAL -> 2;
            case DEPARTMENT -> 3;
            case SPECIALTY -> 4;
        };
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

    private String edgeImpactSignature(PathwayEdge edge) {
        return String.join(":",
            edge.edgeCode(),
            edge.fromNodeCode(),
            edge.toNodeCode(),
            edge.edgeType().name(),
            String.valueOf(edge.conditionJson()),
            String.valueOf(edge.priority()));
    }

    private String bindingImpactSignature(SpecialtyMetricBinding binding) {
        return String.join(":",
            binding.nodeCode(),
            binding.metricCode(),
            String.valueOf(binding.requiredFlag()));
    }

    private String outcomeBindingImpactSignature(PathwayOutcomeBinding binding) {
        return String.join(":",
            binding.scope().name(),
            notBlank(binding.refCode(), "-"),
            binding.indicatorCode(),
            notBlank(binding.packageVersion(), "-"));
    }

    private void validatePublishGate(PathwayTemplate template,
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
            throw new ApiException(ErrorCode.ENG_PATHWAY_004, "路径模板缺少有效起始节点");
        }
        if (!hasTerminal) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004, "路径模板缺少终止节点");
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
        validateSubPathwayCycleBoundary(template, executableNodes);
        validatePathwayReferencePackageConsistency(
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
                case SUBPATHWAY -> requireNodeConfigText(node, "subPathwayRef", "子路径节点 " + node.nodeCode() + " 缺少 subPathwayRef");
                case MANUAL_GATE -> {
                    if (isBlank(node.responsibleRole())) {
                        throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                            "人工闸门节点 " + node.nodeCode() + " 缺少责任角色");
                    }
                }
                case ORDER_SET -> requireNodeConfigText(node, "orderSetRef", "医嘱集节点 " + node.nodeCode() + " 缺少 orderSetRef");
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

    private void validateSubPathwayCycleBoundary(PathwayTemplate template, List<PathwayNode> graphNodes) {
        for (PathwayNode node : nullToEmpty(graphNodes)) {
            if (node.nodeType() != PathwayNodeType.SUBPATHWAY) {
                continue;
            }
            String ref = nodeConfigText(node, "subPathwayRef");
            if (samePathwayTemplateRef(template, ref)) {
                throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                    "子路径节点 " + node.nodeCode() + " 不能引用当前路径模板");
            }
        }
    }

    private boolean samePathwayTemplateRef(PathwayTemplate template, String ref) {
        if (isBlank(ref)) {
            return false;
        }
        return Objects.equals(ref, template.templateId())
            || Objects.equals(ref, template.templateCode());
    }

    private void validatePathwayReferencePackageConsistency(
            PathwayTemplate template,
            List<PathwayNode> graphNodes,
            List<PathwayEdge> graphEdges,
            List<PathwayOutcomeBinding> graphOutcomeBindings) {
        String packageVersion = templatePackageVersion(
            template, template.tenantId(), ErrorCode.ENG_PATHWAY_004);
        PackageReferenceConsistency.requireReferencesSamePackage(
            packageVersion,
            readJsonOrEmpty(template.entryCriteriaJson(), "路径模板入径条件 " + template.templateCode(),
                ErrorCode.ENG_PATHWAY_004),
            ErrorCode.ENG_PATHWAY_004,
            "路径模板 " + template.templateCode() + " 入径条件");
        PackageReferenceConsistency.requireReferencesSamePackage(
            packageVersion,
            readJsonOrEmpty(template.exitCriteriaJson(), "路径模板出径条件 " + template.templateCode(),
                ErrorCode.ENG_PATHWAY_004),
            ErrorCode.ENG_PATHWAY_004,
            "路径模板 " + template.templateCode() + " 出径条件");
        for (PathwayNode node : nullToEmpty(graphNodes)) {
            PackageReferenceConsistency.requireReferencesSamePackage(
                packageVersion,
                readJsonOrEmpty(node.configJson(), "路径节点配置 " + node.nodeCode(),
                    ErrorCode.ENG_PATHWAY_004),
                ErrorCode.ENG_PATHWAY_004,
                "路径节点 " + node.nodeCode());
        }
        for (PathwayEdge edge : nullToEmpty(graphEdges)) {
            PackageReferenceConsistency.requireReferencesSamePackage(
                packageVersion,
                readJsonOrEmpty(edge.conditionJson(), "路径边条件 " + edge.edgeCode(),
                    ErrorCode.ENG_PATHWAY_004),
                ErrorCode.ENG_PATHWAY_004,
                "路径边 " + edge.edgeCode());
        }
        for (PathwayOutcomeBinding binding : nullToEmpty(graphOutcomeBindings)) {
            PackageReferenceConsistency.requireSamePackage(
                packageVersion,
                binding.packageVersion(),
                ErrorCode.ENG_PATHWAY_004,
                "路径结局指标绑定包版本必须与模板包版本一致",
                "模板包版本",
                "绑定包版本");
        }
    }

    private void ensurePathwayRuntimePackageConsistency(
            PathwayTemplate template,
            String sourceTenantId,
            String requestPackageVersion,
            ErrorCode errorCode,
            String message) {
        PackageReferenceConsistency.requireSamePackage(
            templatePackageVersion(template, sourceTenantId, errorCode),
            requestPackageVersion,
            errorCode,
            message,
            "路径模板包版本",
            "请求路径包版本");
    }

    private String templatePackageVersion(PathwayTemplate template, String sourceTenantId, ErrorCode errorCode) {
        if (isBlank(template.packageId())) {
            throw new ApiException(errorCode, "路径模板缺少路径知识包归属: " + template.templateCode());
        }
        return packages.findByPackageIdAndTenantId(template.packageId(), sourceTenantId)
            .map(KnowledgePackage::packageVersion)
            .filter(version -> !isBlank(version))
            .orElseThrow(() -> new ApiException(errorCode,
                "路径模板所属路径知识包不存在或缺少包版本: " + template.packageId()));
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
            case DECISION, PARALLEL, WAIT_TIMER, SUBPATHWAY, MANUAL_GATE, ORDER_SET -> true;
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
                "等待计时节点 " + node.nodeCode() + " 缺少 clock 或 timeWindowMinutes");
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
                    "节点 " + node.nodeCode() + " 设置了关键时限但未绑定质控指标");
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
                "关键时钟节点 " + node.nodeCode() + " 缺少 clockSla");
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
                "关键时钟节点 " + node.nodeCode() + " 的 clockSla 必须是结构化对象");
        }
        String baselineEvent = requiredText(clockSla, "baselineEvent", node, "SLA 基准事件");
        validateBaselineEvent(baselineEvent, node);
        Integer minMinutes = requiredNonNegativeInt(clockSla, "minMinutes", node);
        Integer targetMinutes = requiredNonNegativeInt(clockSla, "targetMinutes", node);
        Integer maxMinutes = requiredNonNegativeInt(clockSla, "maxMinutes", node);
        if (targetMinutes <= 0) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "关键时钟节点 " + node.nodeCode() + " 的 targetMinutes 必须大于 0");
        }
        if (minMinutes > targetMinutes || targetMinutes > maxMinutes) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "关键时钟节点 " + node.nodeCode() + " 的 SLA 时限必须满足 min <= target <= max");
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

    private Integer requiredNonNegativeInt(JsonNode source, String field, PathwayNode node) {
        JsonNode value = source.get(field);
        if (value == null || value.isNull() || !value.canConvertToInt() || value.asInt() < 0) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "关键时钟节点 " + node.nodeCode() + " 的 " + field + " 必须是非负整数");
        }
        return value.asInt();
    }

    private void validateBaselineEvent(String baselineEvent, PathwayNode node) {
        if (!Set.of("NODE_START", "PATHWAY_ENTRY", "ADMISSION").contains(baselineEvent)) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "关键时钟节点 " + node.nodeCode() + " 不支持 SLA 基准事件: " + baselineEvent);
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
            Integer afterMinutes = requiredNonNegativeInt(item, "afterMinutes", node);
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
                "关键时钟节点 " + node.nodeCode() + " 不支持 SLA 基准事件: " + sla.baselineEvent());
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
            Supplier<String> packageVersionSupplier,
            List<ClinicalClock> source,
            Instant now) {
        return nullToEmpty(source).stream()
            .map(clock -> {
                ClinicalClock projected = projectClockSla(clock, now);
                if (projected != clock && projected.status() == ClinicalClockStatus.TIMEOUT) {
                    domainEvents.clockSlaBreached(new ClockSlaBreachedEvent(
                        runtime.tenantId(),
                        projected.traceId(),
                        packageVersionSupplier.get(),
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
            runtime.encounterId(), runtime.templateId(), currentNode, decision.status(),
            runtime.enteredAt(), decision.status() == PatientPathwayStatus.COMPLETED ? now : runtime.completedAt(),
            decision.status() == PatientPathwayStatus.EXITED ? now : runtime.exitedAt(),
            decision.status() == PatientPathwayStatus.EXITED ? exitReason(request) : runtime.exitReason(),
            request.eventId(), runtime.createdAt(), runtime.createdBy(), now, actor, traceId);
    }

    private PathwayTemplate copyTemplate(PathwayTemplate template, PathwayTemplateStatus status,
                                         Instant now, String actor, String traceId) {
        return new PathwayTemplate(
            template.id(), template.templateId(), template.tenantId(), template.packageId(),
            template.templateCode(), template.name(), template.diseaseCode(),
            template.templateVersion(), template.templateLevel(), template.parentTemplateId(), status,
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
                "路径模板不存在: " + templateId));
    }

    private MergedPathwayGraph resolveTemplateInheritanceGraph(
            PathwayTemplate template,
            String tenantId,
            Set<String> visiting) {
        if (!visiting.add(template.templateId())) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "路径模板继承链存在环: " + template.templateId());
        }
        try {
            List<PathwayNode> currentNodes =
                nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc(template.templateId(), tenantId);
            List<PathwayEdge> currentEdges =
                edges.findByTemplateIdAndTenantIdOrderByPriorityAsc(template.templateId(), tenantId);
            if (isBlank(template.parentTemplateId())) {
                List<PathwayMergedNode> mergedNodes = activeNodes(currentNodes).stream()
                    .map(node -> PathwayMergedNode.from(node, PathwayInheritanceOrigin.ADDED))
                    .sorted(mergedNodeComparator())
                    .toList();
                return new MergedPathwayGraph(
                    mergedNodes,
                    activeEdges(currentEdges, nodeCodeSet(mergedNodes)),
                    List.of());
            }

            PathwayTemplate parent = findTemplate(template.parentTemplateId(), tenantId);
            MergedPathwayGraph parentGraph = resolveTemplateInheritanceGraph(parent, tenantId, visiting);
            return mergeInheritedGraph(parentGraph, currentNodes, currentEdges);
        } finally {
            visiting.remove(template.templateId());
        }
    }

    private MergedPathwayGraph mergeInheritedGraph(
            MergedPathwayGraph parentGraph,
            List<PathwayNode> childNodes,
            List<PathwayEdge> childEdges) {
        LinkedHashMap<String, PathwayMergedNode> parentByCode = new LinkedHashMap<>();
        for (PathwayMergedNode node : parentGraph.nodes()) {
            parentByCode.put(node.nodeCode(), node);
        }

        LinkedHashMap<String, PathwayNode> childActiveByCode = new LinkedHashMap<>();
        for (PathwayNode node : activeNodes(childNodes)) {
            childActiveByCode.put(node.nodeCode(), node);
        }
        Set<String> disabledCodes = new HashSet<>();
        for (PathwayNode node : nullToEmpty(childNodes)) {
            if (Boolean.TRUE.equals(node.disabledFlag())) {
                disabledCodes.add(node.nodeCode());
            }
        }

        List<PathwayTemplateInheritanceDiffItem> diffItems = new ArrayList<>();
        List<PathwayMergedNode> mergedNodes = new ArrayList<>();
        for (PathwayMergedNode parentNode : parentByCode.values()) {
            if (disabledCodes.contains(parentNode.nodeCode())) {
                diffItems.add(new PathwayTemplateInheritanceDiffItem(
                    "NODE", parentNode.nodeCode(), PathwayInheritanceChangeType.DISABLED,
                    null, parentNode.name(), null));
                continue;
            }
            PathwayNode childNode = childActiveByCode.remove(parentNode.nodeCode());
            if (childNode == null) {
                mergedNodes.add(parentNode.withOrigin(PathwayInheritanceOrigin.INHERITED));
                continue;
            }
            appendOverrideDiffItems(diffItems, parentNode, childNode);
            mergedNodes.add(PathwayMergedNode.from(childNode, PathwayInheritanceOrigin.OVERRIDDEN));
        }

        for (PathwayNode childNode : childActiveByCode.values()) {
            diffItems.add(new PathwayTemplateInheritanceDiffItem(
                "NODE", childNode.nodeCode(), PathwayInheritanceChangeType.ADDED,
                null, null, childNode.name()));
            mergedNodes.add(PathwayMergedNode.from(childNode, PathwayInheritanceOrigin.ADDED));
        }

        List<PathwayMergedNode> sortedNodes = mergedNodes.stream()
            .sorted(mergedNodeComparator())
            .toList();
        return new MergedPathwayGraph(
            sortedNodes,
            mergeEdges(parentGraph.edges(), childEdges, nodeCodeSet(sortedNodes)),
            diffItems);
    }

    private void appendOverrideDiffItems(
            List<PathwayTemplateInheritanceDiffItem> diffItems,
            PathwayMergedNode parent,
            PathwayNode child) {
        appendOverrideDiffItem(diffItems, child.nodeCode(), "name", parent.name(), child.name());
        appendOverrideDiffItem(diffItems, child.nodeCode(), "nodeType", parent.nodeType(), child.nodeType());
        appendOverrideDiffItem(diffItems, child.nodeCode(), "milestoneCode", parent.milestoneCode(), child.milestoneCode());
        appendOverrideDiffItem(diffItems, child.nodeCode(), "sortOrder", parent.sortOrder(), child.sortOrder());
        appendOverrideDiffItem(diffItems, child.nodeCode(), "responsibleRole", parent.responsibleRole(), child.responsibleRole());
        appendOverrideDiffItem(diffItems, child.nodeCode(), "accountableRole", parent.accountableRole(), child.accountableRole());
        appendOverrideDiffItem(diffItems, child.nodeCode(), "consultedRolesJson", parent.consultedRolesJson(), child.consultedRolesJson());
        appendOverrideDiffItem(diffItems, child.nodeCode(), "informedRolesJson", parent.informedRolesJson(), child.informedRolesJson());
        appendOverrideDiffItem(diffItems, child.nodeCode(), "dependencyJson", parent.dependencyJson(), child.dependencyJson());
        appendOverrideDiffItem(diffItems, child.nodeCode(), "timeWindowMinutes", parent.timeWindowMinutes(), child.timeWindowMinutes());
        appendOverrideDiffItem(diffItems, child.nodeCode(), "terminalFlag", parent.terminalFlag(), child.terminalFlag());
        appendOverrideDiffItem(diffItems, child.nodeCode(), "configJson", parent.configJson(), child.configJson());
    }

    private void appendOverrideDiffItem(
            List<PathwayTemplateInheritanceDiffItem> diffItems,
            String nodeCode,
            String fieldName,
            Object parentValue,
            Object childValue) {
        if (Objects.equals(parentValue, childValue)) {
            return;
        }
        diffItems.add(new PathwayTemplateInheritanceDiffItem(
            "NODE", nodeCode, PathwayInheritanceChangeType.OVERRIDDEN,
            fieldName, diffValue(parentValue), diffValue(childValue)));
    }

    private List<PathwayEdge> mergeEdges(
            List<PathwayEdge> parentEdges,
            List<PathwayEdge> childEdges,
            Set<String> activeNodeCodes) {
        LinkedHashMap<String, PathwayEdge> byCode = new LinkedHashMap<>();
        Set<String> childEdgeCodes = new HashSet<>();
        for (PathwayEdge edge : nullToEmpty(childEdges)) {
            if (!isBlank(edge.edgeCode())) {
                childEdgeCodes.add(edge.edgeCode());
            }
        }
        for (PathwayEdge edge : nullToEmpty(parentEdges)) {
            if (!childEdgeCodes.contains(edge.edgeCode()) && edgeEndpointsActive(edge, activeNodeCodes)) {
                byCode.put(edge.edgeCode(), edge);
            }
        }
        for (PathwayEdge edge : nullToEmpty(childEdges)) {
            if (edgeEndpointsActive(edge, activeNodeCodes)) {
                byCode.put(edge.edgeCode(), edge);
            }
        }
        return byCode.values().stream()
            .sorted(Comparator
                .comparingInt((PathwayEdge edge) -> safeInt(edge.priority()))
                .thenComparing(PathwayEdge::edgeCode, Comparator.nullsLast(String::compareTo)))
            .toList();
    }

    private Set<String> nodeCodeSet(List<PathwayMergedNode> graphNodes) {
        Set<String> nodeCodes = new HashSet<>();
        for (PathwayMergedNode node : nullToEmpty(graphNodes)) {
            if (!isBlank(node.nodeCode())) {
                nodeCodes.add(node.nodeCode());
            }
        }
        return nodeCodes;
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

    private List<PathwayMilestone> resolveEffectiveMilestones(
            PathwayTemplate template,
            String tenantId,
            Set<String> visiting) {
        if (!visiting.add(template.templateId())) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "路径模板继承链存在环: " + template.templateId());
        }
        try {
            LinkedHashMap<String, PathwayMilestone> byCode = new LinkedHashMap<>();
            if (!isBlank(template.parentTemplateId())) {
                PathwayTemplate parent = findTemplate(template.parentTemplateId(), tenantId);
                for (PathwayMilestone milestone : resolveEffectiveMilestones(parent, tenantId, visiting)) {
                    byCode.put(milestone.milestoneCode(), milestone);
                }
            }
            for (PathwayMilestone milestone
                    : milestones.findByTemplateIdAndTenantIdOrderBySortOrderAsc(template.templateId(), tenantId)) {
                if (!isBlank(milestone.milestoneCode())) {
                    byCode.put(milestone.milestoneCode(), milestone);
                }
            }
            return byCode.values().stream()
                .sorted(Comparator
                    .comparingInt((PathwayMilestone milestone) -> safeInt(milestone.sortOrder()))
                    .thenComparing(PathwayMilestone::milestoneCode, Comparator.nullsLast(String::compareTo)))
                .toList();
        } finally {
            visiting.remove(template.templateId());
        }
    }

    private List<SpecialtyMetricBinding> resolveEffectiveMetricBindings(
            PathwayTemplate template,
            String tenantId,
            Set<String> visiting,
            Set<String> activeNodeCodes) {
        if (!visiting.add(template.templateId())) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "路径模板继承链存在环: " + template.templateId());
        }
        try {
            LinkedHashMap<String, SpecialtyMetricBinding> byKey = new LinkedHashMap<>();
            if (!isBlank(template.parentTemplateId())) {
                PathwayTemplate parent = findTemplate(template.parentTemplateId(), tenantId);
                for (SpecialtyMetricBinding binding
                        : resolveEffectiveMetricBindings(parent, tenantId, visiting, activeNodeCodes)) {
                    byKey.put(metricBindingKey(binding), binding);
                }
            }
            for (SpecialtyMetricBinding binding
                    : metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc(template.templateId(), tenantId)) {
                if (activeNodeCodes.contains(binding.nodeCode())) {
                    byKey.put(metricBindingKey(binding), binding);
                }
            }
            return byKey.values().stream()
                .filter(binding -> activeNodeCodes.contains(binding.nodeCode()))
                .sorted(Comparator
                    .comparing(SpecialtyMetricBinding::nodeCode, Comparator.nullsLast(String::compareTo))
                    .thenComparing(SpecialtyMetricBinding::metricCode, Comparator.nullsLast(String::compareTo)))
                .toList();
        } finally {
            visiting.remove(template.templateId());
        }
    }

    private List<PathwayOutcomeBinding> resolveEffectiveOutcomeBindings(
            PathwayTemplate template,
            String tenantId,
            Set<String> visiting,
            List<PathwayMilestone> graphMilestones) {
        if (!visiting.add(template.templateId())) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004,
                "路径模板继承链存在环: " + template.templateId());
        }
        try {
            LinkedHashMap<String, PathwayOutcomeBinding> byKey = new LinkedHashMap<>();
            if (!isBlank(template.parentTemplateId())) {
                PathwayTemplate parent = findTemplate(template.parentTemplateId(), tenantId);
                for (PathwayOutcomeBinding binding
                        : resolveEffectiveOutcomeBindings(parent, tenantId, visiting, graphMilestones)) {
                    byKey.put(outcomeBindingKey(binding), binding);
                }
            }
            for (PathwayOutcomeBinding binding
                    : outcomeBindings.findByTemplateIdAndTenantIdOrderByScopeAscRefCodeAscIndicatorCodeAsc(
                        template.templateId(), tenantId)) {
                byKey.put(outcomeBindingKey(binding), binding);
            }
            return byKey.values().stream()
                .sorted(Comparator
                    .comparing(PathwayOutcomeBinding::scope, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(PathwayOutcomeBinding::refCode, Comparator.nullsLast(String::compareTo))
                    .thenComparing(PathwayOutcomeBinding::indicatorCode, Comparator.nullsLast(String::compareTo)))
                .toList();
        } finally {
            visiting.remove(template.templateId());
        }
    }

    private String metricBindingKey(SpecialtyMetricBinding binding) {
        return notBlank(binding.nodeCode(), "-") + ":" + notBlank(binding.metricCode(), "-");
    }

    private String outcomeBindingKey(PathwayOutcomeBinding binding) {
        return binding.scope().name() + ":"
            + notBlank(binding.refCode(), "-") + ":"
            + notBlank(binding.indicatorCode(), "-");
    }

    private Comparator<PathwayMergedNode> mergedNodeComparator() {
        return Comparator
            .comparingInt((PathwayMergedNode node) -> safeInt(node.sortOrder()))
            .thenComparing(PathwayMergedNode::nodeCode, Comparator.nullsLast(String::compareTo));
    }

    private static String diffValue(Object value) {
        return value == null ? null : String.valueOf(value);
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
            throw new ApiException(ErrorCode.ENG_PATHWAY_002, "路径模板不存在: " + templateId);
        }
        PathwayTemplate platform = templates.findByTemplateIdAndTenantId(templateId, PlatformTenant.ID)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_PATHWAY_002,
                "路径模板不存在: " + templateId));
        return templates.findByTenantIdAndTemplateCodeAndTemplateVersion(
                tenantId, platform.templateCode(), platform.templateVersion())
            .filter(this::hasActivePathwayAssetVersion)
            .map(override -> new EffectivePathwayTemplate(override, tenantId))
            .orElseGet(() -> new EffectivePathwayTemplate(platform, PlatformTenant.ID));
    }

    /**
     * 按患者入径时保存的模板 ID 读取固定版本，禁止在运行中重新解析到后续激活版本。
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
        throw new ApiException(ErrorCode.ENG_PATHWAY_002, "患者路径绑定的模板版本不存在: " + templateId);
    }

    private String packageVersionForTemplate(PathwayTemplate template, String tenantId) {
        return packages.findByPackageIdAndTenantId(template.packageId(), tenantId)
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_PATHWAY_002,
                "路径模板所属路径知识包不存在: " + template.packageId()))
            .packageVersion();
    }

    private Optional<EffectivePathwayTemplate> resolveEffectiveTemplateForCurrentOrg(
            PathwayTemplate candidate, String tenantId) {
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
            // 草稿模板在当前组织闭包内尚无任何 PUBLISHED 有效版本时，继承解析器按契约抛 NOT_FOUND。
            // 此处回退本地模板（含未发布草稿），由调用方按本地版本投影详情/影响/试运行预览，
            // 而不是把 NOT_FOUND 透传给前台导致路径编排前台流被堵死。见 P5-ACT5-02。
            if (exception.errorCode() == ErrorCode.NOT_FOUND) {
                return Optional.empty();
            }
            throw exception;
        }
        if (resolved.disabled()) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_002, "路径模板已在当前组织停用");
        }
        if (resolved.version() == null) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_002, "当前组织未解析到有效路径版本");
        }
        AssetVersion assetVersion = resolved.version();
        int templateVersion = Integer.parseInt(assetVersion.versionNo());
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
            throw new ApiException(ErrorCode.ENG_PATHWAY_005, "当前路径模板状态不允许发布");
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
            throw new ApiException(ErrorCode.ENG_PATHWAY_004, "路径节点 RACI 角色无法解析", exception);
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

    private record MergedPathwayGraph(
        List<PathwayMergedNode> nodes,
        List<PathwayEdge> edges,
        List<PathwayTemplateInheritanceDiffItem> diffItems
    ) {
        private MergedPathwayGraph {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            edges = edges == null ? List.of() : List.copyOf(edges);
            diffItems = diffItems == null ? List.of() : List.copyOf(diffItems);
        }
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
        String parentTemplateId,
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
        String indicatorCode,
        String packageVersion
    ) {
        private static PathwayOutcomeAssetContent from(PathwayOutcomeBinding binding) {
            return new PathwayOutcomeAssetContent(
                binding.scope(), binding.refCode(), binding.indicatorCode(), binding.packageVersion());
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
