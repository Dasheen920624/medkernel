package com.medkernel.engine.pathway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ClinicalEventContext;
import com.medkernel.engine.context.ContextSnapshotResources;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.context.ContextSnapshotStatus;
import com.medkernel.engine.context.canonical.CanonicalObservation;
import com.medkernel.engine.pkg.PackageItem;
import com.medkernel.engine.pkg.PackageItemRepository;
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
    private static final int DEFAULT_CANARY_PERCENT = 10;
    private static final String RELEASE_STEP_CANARY = "canary_release";
    private static final String RELEASE_STEP_FULL = "full_rollout";
    private static final String RELEASE_STEP_ROLLBACK = "evidence_rollback";

    private final SpecialtyPackageRepository packages;
    private final SpecialtyProfileRepository profiles;
    private final PathwayTemplateRepository templates;
    private final PathwayNodeRepository nodes;
    private final PathwayEdgeRepository edges;
    private final PatientPathwayRepository patientPathways;
    private final PathwayVarianceRepository variances;
    private final ClinicalClockRepository clocks;
    private final SpecialtyMetricBindingRepository metricBindings;
    private final ContextSnapshotService contextSnapshots;
    private final PathwayProgressor progressor;
    private final AuditRecorder auditRecorder;
    private final StateTransitionRecorder transitions;
    private final DiagnoseResponseAssembler diagnoseAssembler;
    private final ObjectMapper json;
    private final PathwayFollowupHandoffPort followupHandoff;
    private final ClinicalSafetyGuard safetyGuard;
    private final TerminologyCoverageGate terminologyCoverageGate;
    private final PathwayVersionedAssetAdapter versionedAssets;
    private final AssetVersionRepository assetVersions;
    private final ReleasePort releasePort;
    private final PackageItemRepository packageItems;
    private final InheritanceResolver inheritanceResolver;

    /**
     * 注入路径引擎闭环所需仓库、推进器、审计发布器、状态记录器、诊断装配器和 JSON 工具。
     */
    @Autowired
    public PathwayEngineService(SpecialtyPackageRepository packages,
                                SpecialtyProfileRepository profiles,
                                PathwayTemplateRepository templates,
                                PathwayNodeRepository nodes,
                                PathwayEdgeRepository edges,
                                PatientPathwayRepository patientPathways,
                                PathwayVarianceRepository variances,
                                ClinicalClockRepository clocks,
                                SpecialtyMetricBindingRepository metricBindings,
                                ContextSnapshotService contextSnapshots,
                                PathwayProgressor progressor,
                                AuditRecorder auditRecorder,
                                StateTransitionRecorder transitions,
                                DiagnoseResponseAssembler diagnoseAssembler,
                                ObjectMapper json,
                                ClinicalSafetyGuard safetyGuard,
                                ObjectProvider<PathwayFollowupHandoffPort> followupHandoffProvider,
                                ObjectProvider<TerminologyCoverageGate> terminologyCoverageGateProvider,
                                PathwayVersionedAssetAdapter versionedAssets,
                                AssetVersionRepository assetVersions,
                                ReleasePort releasePort,
                                PackageItemRepository packageItems,
                                InheritanceResolver inheritanceResolver) {
        this(packages, profiles, templates, nodes, edges, patientPathways, variances, clocks,
            metricBindings, contextSnapshots, progressor, auditRecorder, transitions,
            diagnoseAssembler, json,
            followupHandoffProvider.getIfAvailable(PathwayFollowupHandoffPort::noop), safetyGuard,
            terminologyCoverageGateProvider.getIfAvailable(TerminologyCoverageGate::noop),
            versionedAssets, assetVersions, releasePort, packageItems, inheritanceResolver);
    }

    PathwayEngineService(SpecialtyPackageRepository packages,
                         SpecialtyProfileRepository profiles,
                         PathwayTemplateRepository templates,
                         PathwayNodeRepository nodes,
                         PathwayEdgeRepository edges,
                         PatientPathwayRepository patientPathways,
                         PathwayVarianceRepository variances,
                         ClinicalClockRepository clocks,
                         SpecialtyMetricBindingRepository metricBindings,
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
        this.packages = packages;
        this.profiles = profiles;
        this.templates = templates;
        this.nodes = nodes;
        this.edges = edges;
        this.patientPathways = patientPathways;
        this.variances = variances;
        this.clocks = clocks;
        this.metricBindings = metricBindings;
        this.contextSnapshots = contextSnapshots;
        this.progressor = progressor;
        this.auditRecorder = auditRecorder;
        this.transitions = transitions;
        this.diagnoseAssembler = diagnoseAssembler;
        this.json = json;
        this.followupHandoff = followupHandoff == null ? PathwayFollowupHandoffPort.noop() : followupHandoff;
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
        auditRecorder.record(AuditAction.CREATE, PACKAGE_ENTITY, packageId,
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

        bridgePathwayTemplateToPackageItem(template, actor, now, traceId);
        AssetVersion assetVersion = versionedAssets.registerDraft(new AssetVersionRegisterCommand(
            template.tenantId(),
            VersionedAssetType.PATHWAY,
            template.templateCode(),
            String.valueOf(template.templateVersion()),
            releaseOrgScope(template),
            releaseApplicableScope(template),
            pathwayContent(template, savedNodes, savedEdges, savedBindings),
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
            template, savedNodes, savedEdges, savedBindings, assetVersion.status(), traceId);
    }

    private void bridgePathwayTemplateToPackageItem(
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
        List<PathwayNode> graphNodes = nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc(templateId, tenantId);
        List<PathwayEdge> graphEdges = edges.findByTemplateIdAndTenantIdOrderByPriorityAsc(templateId, tenantId);
        List<SpecialtyMetricBinding> graphBindings =
            metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc(templateId, tenantId);
        validatePublishGate(template, graphNodes, graphEdges, graphBindings);
        PathwayTemplateImpactResponse impact = templateImpactFor(
            template, graphNodes, graphEdges, graphBindings,
            patientPathways.findByTemplateIdAndTenantIdOrderByEnteredAtDesc(templateId, tenantId));
        validateReleaseGate(request, impact);
        ensureTerminologyCoverage(graphEdges);

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
            impact.impactDigest(),
            releaseReason(request, "路径发布门禁通过"),
            request == null ? List.of() : request.roleCodes(),
            actor,
            RequestContext.currentTraceId()
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
            impact.impactDigest(),
            releaseReason(request, "路径全量发布门禁通过"),
            request == null ? List.of() : request.roleCodes(),
            actor,
            RequestContext.currentTraceId()
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

    private AssetVersion requireActivePathwayAssetVersion(PathwayTemplate template) {
        return findPathwayAssetVersion(template)
            .filter(assetVersion -> assetVersion.status() == AssetVersionStatus.ACTIVE)
            .orElseThrow(() -> new ApiException(
                ErrorCode.ENG_PATHWAY_005,
                "路径模板统一版本未全量激活，不能入径: "
                    + template.templateCode() + "@" + template.templateVersion()
            ));
    }

    private boolean hasActivePathwayAssetVersion(PathwayTemplate template) {
        return findPathwayAssetVersion(template)
            .filter(assetVersion -> assetVersion.status() == AssetVersionStatus.ACTIVE)
            .isPresent();
    }

    private List<String> advanceCanaryRelease(AssetVersion assetVersion, VersionReleaseCommand command) {
        List<String> evidence = new ArrayList<>();
        AssetVersionStatus status = assetVersion.status();
        if (status == AssetVersionStatus.DRAFT) {
            appendEvidence(evidence, releasePort.submitForReview(command));
            appendEvidence(evidence, releasePort.approveForSilentObservation(command));
            appendEvidence(evidence, releasePort.releaseGray(command));
            return evidence;
        }
        if (status == AssetVersionStatus.PENDING_REVIEW) {
            appendEvidence(evidence, releasePort.approveForSilentObservation(command));
            appendEvidence(evidence, releasePort.releaseGray(command));
            return evidence;
        }
        if (status == AssetVersionStatus.PUBLISHED || status == AssetVersionStatus.ACTIVE) {
            appendEvidence(evidence, releasePort.releaseGray(command));
        }
        return evidence;
    }

    private List<String> advanceFullRelease(AssetVersion assetVersion, VersionReleaseCommand command) {
        List<String> evidence = new ArrayList<>();
        AssetVersionStatus status = assetVersion.status();
        if (status == AssetVersionStatus.DRAFT) {
            appendEvidence(evidence, releasePort.submitForReview(command));
            appendEvidence(evidence, releasePort.approveForSilentObservation(command));
            appendEvidence(evidence, releasePort.releaseFull(command));
            return evidence;
        }
        if (status == AssetVersionStatus.PENDING_REVIEW) {
            appendEvidence(evidence, releasePort.approveForSilentObservation(command));
            appendEvidence(evidence, releasePort.releaseFull(command));
            return evidence;
        }
        if (status == AssetVersionStatus.PUBLISHED || status == AssetVersionStatus.ACTIVE) {
            appendEvidence(evidence, releasePort.releaseFull(command));
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
        return notBlank(template.packageId(), "tenant:" + template.tenantId());
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
            nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc(template.templateId(), template.tenantId()),
            edges.findByTemplateIdAndTenantIdOrderByPriorityAsc(template.templateId(), template.tenantId()),
            metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc(
                template.templateId(), template.tenantId())
        );
    }

    private String pathwayContent(
            PathwayTemplate template,
            List<PathwayNode> graphNodes,
            List<PathwayEdge> graphEdges,
            List<SpecialtyMetricBinding> graphBindings) {
        return writeObject(new PathwayAssetContent(
            template.templateCode(),
            template.name(),
            template.diseaseCode(),
            template.templateVersion(),
            template.templateLevel(),
            template.startNodeCode(),
            template.sourceRef(),
            template.description(),
            template.entryCriteriaJson(),
            template.exitCriteriaJson(),
            nullToEmpty(graphNodes).stream().map(PathwayNodeAssetContent::from).toList(),
            nullToEmpty(graphEdges).stream().map(PathwayEdgeAssetContent::from).toList(),
            nullToEmpty(graphBindings).stream().map(PathwayMetricAssetContent::from).toList()
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
        List<PathwayNode> graphNodes = nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc(templateId, tenantId);
        List<PathwayEdge> graphEdges = edges.findByTemplateIdAndTenantIdOrderByPriorityAsc(templateId, tenantId);
        List<SpecialtyMetricBinding> graphBindings =
            metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc(templateId, tenantId);
        validatePublishGate(template, graphNodes, graphEdges, graphBindings);
        return templateImpactFor(
            template, graphNodes, graphEdges, graphBindings,
            patientPathways.findByTemplateIdAndTenantIdOrderByEnteredAtDesc(templateId, tenantId));
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
        validateReleaseGate(request, impact);
        requireHospitalAdminRole(request);

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
        validateReleaseGate(request, impact);
        requireHospitalAdminRole(request);
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
     * 按状态、病种、专病包和模板编码过滤分页查询路径模板。
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
        List<PathwayTemplate> effectiveRows = effectiveTemplatesByFilter(
            tenantId, status, diseaseCode, packageId, templateCode);
        long total = effectiveRows.size();
        List<PathwayTemplate> rows = slice(effectiveRows, safePage.offset(), safePage.safeSize());
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
     * <p>返回模板主表、按顺序排列的节点、按优先级排列的边和按节点排列的指标绑定。
     */
    @Transactional(readOnly = true)
    public PathwayTemplateDetailResponse templateDetail(String templateId) {
        String tenantId = requireCurrentTenant();
        EffectivePathwayTemplate effective = findEffectiveTemplate(templateId, tenantId);
        PathwayTemplate template = effective.template();
        AssetVersion assetVersion = requirePathwayAssetVersion(template);
        return new PathwayTemplateDetailResponse(
            template,
            nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc(template.templateId(), effective.sourceTenantId()),
            edges.findByTemplateIdAndTenantIdOrderByPriorityAsc(template.templateId(), effective.sourceTenantId()),
            metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc(template.templateId(), effective.sourceTenantId()),
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
        if (!Objects.equals(snapshot.packageVersion(), request.packageVersion())) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_001,
                "患者入径包版本必须与所选上下文快照一致");
        }
        String patientId = snapshot.resources().patient().mpi();
        String encounterId = snapshot.resources().encounters().isEmpty()
            ? null
            : snapshot.resources().encounters().getFirst().encounterId();
        EffectivePathwayTemplate effective = findEffectiveTemplate(request.templateId(), tenantId);
        PathwayTemplate template = effective.template();
        requireActivePathwayAssetVersion(template);
        safetyGuard.assertPathwayTemplateAllowed(template);
        String startNodeCode = isBlank(request.startNodeCode()) ? template.startNodeCode() : request.startNodeCode();
        PathwayNode startNode = nodes.findByTemplateIdAndTenantIdAndNodeCode(
                template.templateId(), effective.sourceTenantId(), startNodeCode)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_PATHWAY_006,
                "入径起始节点不存在: " + startNodeCode));
        List<SpecialtyMetricBinding> graphBindings = metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc(
            template.templateId(), effective.sourceTenantId());
        String traceId = RequestContext.currentTraceId();
        String actor = currentActor();
        Instant now = Instant.now();
        String patientPathwayId = "pp-" + UUID.randomUUID();
        PatientPathway runtime = patientPathways.save(new PatientPathway(
            null, patientPathwayId, tenantId, patientId, encounterId,
            template.templateId(), startNode.nodeCode(), PatientPathwayStatus.NODE_EXECUTING,
            now, null, null, null, null, now, actor, now, actor, traceId));
        ClinicalClock startClock = clocks.save(newClock(
            tenantId, patientPathwayId, startNode, metricCodeForNode(graphBindings, startNode.nodeCode()),
            now, actor, traceId));
        transitions.record(PATIENT_PATHWAY_ENTITY, patientPathwayId, null,
            PatientPathwayStatus.NODE_EXECUTING.name(), "ENTER_PATHWAY", null);
        auditRecorder.record(AuditAction.CREATE, PATIENT_PATHWAY_ENTITY, patientPathwayId,
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
        EffectivePathwayTemplate effective = findEffectiveTemplate(templateId, tenantId);
        PathwayTemplate template = effective.template();
        List<PathwayNode> graphNodes = nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc(
            template.templateId(), effective.sourceTenantId());
        List<PathwayEdge> graphEdges = edges.findByTemplateIdAndTenantIdOrderByPriorityAsc(
            template.templateId(), effective.sourceTenantId());
        String currentNode = request == null || isBlank(request.startNodeCode())
            ? template.startNodeCode() : request.startNodeCode();
        List<String> requestedTargets = request == null ? List.of() : request.requestedNextNodeCodes();
        ContextSnapshotResponse snapshot = request == null || isBlank(request.snapshotId())
            ? null : contextSnapshots.findById(request.snapshotId());
        Map<String, Object> facts = snapshot == null ? Map.of() : contextFacts(snapshot.resources());
        java.util.ArrayList<String> trajectory = new java.util.ArrayList<>();
        trajectory.add(currentNode);
        PatientPathwayStatus finalStatus = PatientPathwayStatus.NODE_EXECUTING;
        for (int i = 0; i <= graphNodes.size(); i++) {
            String requestedTarget = i < requestedTargets.size() ? requestedTargets.get(i) : null;
            PathwayProgressDecision decision = progressor.advance(new PathwayProgressCommand(
                new PathwayGraph(graphNodes, graphEdges), currentNode,
                PathwayAdvanceEventType.COMPLETE, requestedTarget, facts));
            finalStatus = decision.status();
            if (decision.nextNodeCode() == null) {
                break;
            }
            currentNode = decision.nextNodeCode();
            trajectory.add(currentNode);
        }
        return new PathwaySimulationResponse(
            templateId,
            snapshot == null ? null : snapshot.snapshotId(),
            trajectory,
            finalStatus,
            snapshot == null ? null : snapshot.qualityStatus(),
            snapshot == null ? List.of() : snapshot.missingFields(),
            snapshot == null ? Map.of() : snapshot.mappingStatus(),
            snapshot == null ? Map.of() : contextResourceCounts(snapshot.resources()),
            RequestContext.currentTraceId());
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
        List<PathwayNode> graphNodes = nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc(
            effective.template().templateId(), effective.sourceTenantId());
        List<PathwayEdge> graphEdges = edges.findByTemplateIdAndTenantIdOrderByPriorityAsc(
            effective.template().templateId(), effective.sourceTenantId());
        List<SpecialtyMetricBinding> graphBindings = metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc(
            effective.template().templateId(), effective.sourceTenantId());
        ContextSnapshotResponse snapshot = isBlank(request.snapshotId())
            ? null : contextSnapshots.findById(request.snapshotId());
        Map<String, Object> facts = snapshot == null ? Map.of() : contextFacts(snapshot.resources());
        PathwayProgressDecision decision = progressor.advance(new PathwayProgressCommand(
            new PathwayGraph(graphNodes, graphEdges), currentNodeCode,
            request.eventType(), request.requestedNextNodeCode(), facts));

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
            nextClock = clocks.save(newClock(
                tenantId, runtime.patientPathwayId(), nextNode,
                metricCodeForNode(graphBindings, nextNode.nodeCode()), now, actor, traceId));
        }

        PatientPathway updated = copyRuntime(runtime, decision, request, now, actor, traceId);
        patientPathways.save(updated);
        transitions.record(PATIENT_PATHWAY_ENTITY, runtime.patientPathwayId(), runtime.status().name(),
            updated.status().name(), "ADVANCE_PATHWAY", null);
        auditRecorder.record(AuditAction.EXECUTE, PATIENT_PATHWAY_ENTITY, runtime.patientPathwayId(),
            "推进患者路径 " + runtime.patientPathwayId());
        PathwayFollowupHandoffResult followup = handoffFollowupIfCompleted(updated, effective.template());
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
                                                            List<PathwayNode> graphNodes,
                                                            List<PathwayEdge> graphEdges,
                                                            List<SpecialtyMetricBinding> graphBindings,
                                                            List<PatientPathway> runtimes) {
        List<PathwayNode> safeNodes = nullToEmpty(graphNodes);
        List<PathwayEdge> safeEdges = nullToEmpty(graphEdges);
        List<SpecialtyMetricBinding> safeBindings = nullToEmpty(graphBindings);
        List<PatientPathway> safeRuntimes = nullToEmpty(runtimes);
        int timedNodeCount = (int) safeNodes.stream()
            .filter(node -> node.timeWindowMinutes() != null && node.timeWindowMinutes() > 0)
            .count();
        int terminalNodeCount = (int) safeNodes.stream()
            .filter(node -> Boolean.TRUE.equals(node.terminalFlag()))
            .count();
        List<String> releaseEvidence = List.of(
            "拓扑节点 " + safeNodes.size() + " 个，边 " + safeEdges.size()
                + " 条，终止节点 " + terminalNodeCount + " 个",
            "关键时钟节点 " + timedNodeCount + " 个，当前关联患者路径实例 "
                + safeRuntimes.size() + " 条",
            "灰度发布默认 10%，全量前必须保留本次 impactDigest，可按审计记录回滚到上一版本"
        );
        String impactDigest = digest(String.join("|",
            template.templateId(),
            template.templateCode(),
            String.valueOf(template.templateVersion()),
            template.startNodeCode(),
            safeNodes.stream().map(this::nodeImpactSignature).sorted().toList().toString(),
            safeEdges.stream().map(this::edgeImpactSignature).sorted().toList().toString(),
            safeBindings.stream().map(this::bindingImpactSignature).sorted().toList().toString(),
            safeRuntimes.stream().map(PatientPathway::patientPathwayId).sorted().toList().toString()
        ));
        return new PathwayTemplateImpactResponse(
            template.templateId(), "COMPLETE", safeRuntimes.size(), safeNodes.size(), safeEdges.size(),
            timedNodeCount, terminalNodeCount, DEFAULT_CANARY_PERCENT, impactDigest,
            releaseEvidence, RequestContext.currentTraceId());
    }

    private void validateReleaseGate(PathwayOperationRequest request, PathwayTemplateImpactResponse impact) {
        if (request == null || isBlank(request.impactDigest())
                || !Objects.equals(request.impactDigest(), impact.impactDigest())) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004, "路径发布前必须提交当前影响分析摘要");
        }
        if (isBlank(request.reason())) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004, "路径发布前必须填写审核说明");
        }
        if (Boolean.TRUE.equals(request.directFullRollout())
                && !AuthenticatedRoleGuard.has(RoleCode.HOSPITAL_ADMIN)) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004, "路径全量发布必须由院级管理员确认");
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

    private void requireHospitalAdminRole(PathwayOperationRequest request) {
        if (request == null || !AuthenticatedRoleGuard.has(RoleCode.HOSPITAL_ADMIN)) {
            throw new ApiException(ErrorCode.ENG_PATHWAY_004, "路径全量或回滚必须由院级管理员确认");
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
            String.valueOf(node.sortOrder()),
            String.valueOf(node.timeWindowMinutes()),
            String.valueOf(node.terminalFlag()));
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

    private void validatePublishGate(PathwayTemplate template, List<PathwayNode> graphNodes,
                                     List<PathwayEdge> graphEdges,
                                     List<SpecialtyMetricBinding> graphBindings) {
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
        validateClockBindings(graphNodes, graphBindings);
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
                                   PathwayNode node, String metricCode,
                                   Instant now, String actor, String traceId) {
        Instant dueAt = node.timeWindowMinutes() == null ? null
            : now.plusSeconds(node.timeWindowMinutes().longValue() * 60L);
        return new ClinicalClock(
            null, "cc-" + UUID.randomUUID(), tenantId, patientPathwayId,
            node.nodeCode(), metricCode, now, dueAt, null, ClinicalClockStatus.RUNNING,
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
            now, status, clock.createdAt(), clock.createdBy(), now, actor, traceId);
    }

    private Map<String, Object> contextFacts(ContextSnapshotResources resources) {
        if (resources == null) {
            return Map.of();
        }
        LinkedHashMap<String, Object> facts = new LinkedHashMap<>();
        if (resources.patient() != null) {
            facts.put("context.patient.mpi", resources.patient().mpi());
            facts.put("patient.mpi", resources.patient().mpi());
        }
        for (CanonicalObservation observation : resources.observations()) {
            Object value = observation.valueNumeric() == null
                ? observation.valueString()
                : observation.valueNumeric();
            String code = observation.code();
            facts.put("observation." + code + ".value", value);
            facts.put("observation." + code + ".valueNumeric", observation.valueNumeric());
            facts.put("observation." + code + ".criticalFlag", observation.criticalFlag());
            facts.put("context.observations." + code + ".value", value);
            facts.put("context.observations." + code + ".criticalFlag", observation.criticalFlag());
        }
        return facts;
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

    private Optional<EffectivePathwayTemplate> resolveEffectiveTemplateForCurrentOrg(
            PathwayTemplate candidate, String tenantId) {
        String targetOrgUnitId = targetOrgUnitId();
        if (targetOrgUnitId == null) {
            return Optional.empty();
        }
        ResolvedAssetVersion resolved = inheritanceResolver.resolve(new InheritanceResolveQuery(
            tenantId,
            VersionedAssetType.PATHWAY,
            candidate.templateCode(),
            releaseApplicableScope(candidate),
            targetOrgUnitId
        ));
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

    private List<PathwayTemplate> effectiveTemplatesByFilter(String tenantId, String status,
                                                             String diseaseCode, String packageId,
                                                             String templateCode) {
        LinkedHashMap<String, PathwayTemplate> byCodeAndVersion = new LinkedHashMap<>();
        templates.listByFilter(tenantId, status, diseaseCode, packageId, templateCode)
            .forEach(template -> byCodeAndVersion.put(templateKey(template), template));
        if (!PlatformTenant.isPlatformTenant(tenantId)) {
            String platformStatus = status == null ? PathwayTemplateStatus.PUBLISHED.name() : status;
            if (PathwayTemplateStatus.PUBLISHED.name().equals(platformStatus)) {
                templates.listByFilter(PlatformTenant.ID, platformStatus, diseaseCode, null, templateCode)
                    .forEach(template -> byCodeAndVersion.putIfAbsent(templateKey(template), template));
            }
        }
        return List.copyOf(byCodeAndVersion.values());
    }

    private String templateKey(PathwayTemplate template) {
        return template.templateCode() + "#" + template.templateVersion();
    }

    private <T> List<T> slice(List<T> rows, int offset, int limit) {
        if (rows.isEmpty() || offset >= rows.size()) {
            return List.of();
        }
        int end = Math.min(rows.size(), offset + limit);
        return rows.subList(offset, end);
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

    private static String targetOrgUnitId() {
        var scope = RequestContext.currentOrgScope();
        if (scope == null) {
            return null;
        }
        if (scope.specialtyId() != null && !scope.specialtyId().isBlank()) {
            return scope.specialtyId();
        }
        if (scope.departmentId() != null && !scope.departmentId().isBlank()) {
            return scope.departmentId();
        }
        if (scope.siteId() != null && !scope.siteId().isBlank()) {
            return scope.siteId();
        }
        if (scope.campusId() != null && !scope.campusId().isBlank()) {
            return scope.campusId();
        }
        if (scope.hospitalId() != null && !scope.hospitalId().isBlank()) {
            return scope.hospitalId();
        }
        if (scope.groupId() != null && !scope.groupId().isBlank()) {
            return scope.groupId();
        }
        return null;
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record PathwayAssetContent(
        String templateCode,
        String name,
        String diseaseCode,
        Integer templateVersion,
        PathwayTemplateLevel templateLevel,
        String startNodeCode,
        String sourceRef,
        String description,
        String entryCriteriaJson,
        String exitCriteriaJson,
        List<PathwayNodeAssetContent> nodes,
        List<PathwayEdgeAssetContent> edges,
        List<PathwayMetricAssetContent> metricBindings
    ) {}

    private record PathwayNodeAssetContent(
        String nodeCode,
        String name,
        PathwayNodeType nodeType,
        Integer sortOrder,
        String responsibleRole,
        String dependencyJson,
        Integer timeWindowMinutes,
        boolean terminal,
        String configJson
    ) {
        private static PathwayNodeAssetContent from(PathwayNode node) {
            return new PathwayNodeAssetContent(
                node.nodeCode(),
                node.name(),
                node.nodeType(),
                node.sortOrder(),
                node.responsibleRole(),
                node.dependencyJson(),
                node.timeWindowMinutes(),
                node.terminalFlag(),
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

    private record EffectivePathwayTemplate(PathwayTemplate template, String sourceTenantId) {
    }
}
