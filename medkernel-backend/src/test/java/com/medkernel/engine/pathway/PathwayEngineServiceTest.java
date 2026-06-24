package com.medkernel.engine.pathway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ContextSnapshotResources;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.context.ContextSnapshotStatus;
import com.medkernel.engine.context.MissingFieldEntry;
import com.medkernel.engine.context.QualityStatus;
import com.medkernel.engine.context.canonical.CanonicalEncounter;
import com.medkernel.engine.context.canonical.CanonicalObservation;
import com.medkernel.engine.context.canonical.CanonicalPatient;
import com.medkernel.engine.evaluation.EvaluationIndicator;
import com.medkernel.engine.evaluation.EvaluationIndicatorRepository;
import com.medkernel.engine.evaluation.EvaluationIndicatorStatus;
import com.medkernel.engine.evaluation.EvaluationSubjectType;
import com.medkernel.engine.event.ClockSlaBreachedEvent;
import com.medkernel.engine.event.EngineDomainEventPort;
import com.medkernel.engine.event.PathwayVarianceRecordedEvent;
import com.medkernel.engine.safety.ClinicalSafetyGuard;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionNumbers;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.InheritanceResolver;
import com.medkernel.engine.versioning.ResolvedAssetVersion;
import com.medkernel.engine.versioning.SourceTier;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.observability.DiagnoseResponse;
import com.medkernel.shared.observability.DiagnoseResponseAssembler;
import com.medkernel.shared.observability.StateTransitionRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class PathwayEngineServiceTest {

    private PathwayTemplateRepository templates;
    private PathwayNodeRepository nodes;
    private PathwayMilestoneRepository milestones;
    private PathwayEdgeRepository edges;
    private PatientPathwayRepository patientPathways;
    private PathwayVarianceRepository variances;
    private ClinicalClockRepository clocks;
    private SpecialtyMetricBindingRepository metricBindings;
    private PathwayOutcomeBindingRepository outcomeBindings;
    private EvaluationIndicatorRepository evaluationIndicators;
    private ContextSnapshotService contextSnapshots;
    private AuditRecorder auditRecorder;
    private StateTransitionRecorder transitions;
    private DiagnoseResponseAssembler diagnoseAssembler;
    private PathwayFollowupHandoffPort followupHandoff;
    private PathwayWorklistPort worklist;
    private EngineDomainEventPort domainEvents;
    private ClinicalSafetyGuard safetyGuard;
    private PathwayVersionedAssetAdapter versionedAssets;
    private AssetVersionRepository assetVersions;
    private InheritanceResolver inheritanceResolver;
    private RuntimeReleasePathwaySelector runtimePathways;
    private ObjectMapper json;
    private PathwayEngineService service;

    @BeforeEach
    void setUp() {
        templates = mock(PathwayTemplateRepository.class);
        nodes = mock(PathwayNodeRepository.class);
        milestones = mock(PathwayMilestoneRepository.class);
        edges = mock(PathwayEdgeRepository.class);
        patientPathways = mock(PatientPathwayRepository.class);
        variances = mock(PathwayVarianceRepository.class);
        clocks = mock(ClinicalClockRepository.class);
        metricBindings = mock(SpecialtyMetricBindingRepository.class);
        outcomeBindings = mock(PathwayOutcomeBindingRepository.class);
        evaluationIndicators = mock(EvaluationIndicatorRepository.class);
        contextSnapshots = mock(ContextSnapshotService.class);
        auditRecorder = mock(AuditRecorder.class);
        transitions = mock(StateTransitionRecorder.class);
        diagnoseAssembler = mock(DiagnoseResponseAssembler.class);
        followupHandoff = mock(PathwayFollowupHandoffPort.class);
        worklist = mock(PathwayWorklistPort.class);
        domainEvents = mock(EngineDomainEventPort.class);
        safetyGuard = mock(ClinicalSafetyGuard.class);
        versionedAssets = mock(PathwayVersionedAssetAdapter.class);
        assetVersions = mock(AssetVersionRepository.class);
        inheritanceResolver = mock(InheritanceResolver.class);
        runtimePathways = mock(RuntimeReleasePathwaySelector.class);
        json = new ObjectMapper();
        json.findAndRegisterModules();
        service = new PathwayEngineService(
            templates, nodes, milestones, edges, patientPathways, variances,
            clocks, metricBindings, outcomeBindings, evaluationIndicators,
            contextSnapshots, new PathwayProgressor(), auditRecorder,
            transitions, diagnoseAssembler, json, followupHandoff, worklist, domainEvents, safetyGuard,
            versionedAssets, assetVersions, inheritanceResolver, runtimePathways);

        when(templates.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(nodes.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(milestones.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(edges.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(patientPathways.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(variances.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clocks.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(metricBindings.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outcomeBindings.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(versionedAssets.registerDraft(any())).thenReturn(assetVersion(
            "av-pathway-default", VersionedAssetType.PATHWAY, "TPL.COPD", "1", AssetVersionStatus.DRAFT));
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            "tenant-A", VersionedAssetType.PATHWAY, "TPL.COPD", canonicalAssetVersionNo("1")))
            .thenReturn(Optional.of(assetVersion(
                "av-pathway-default", VersionedAssetType.PATHWAY, "TPL.COPD", "1",
                AssetVersionStatus.DRAFT)));
        when(runtimePathways.requireEntryCandidate(any(), any(), any(), any()))
            .thenAnswer(invocation -> runtimeReference(invocation.getArgument(3)));
        when(runtimePathways.requireProgressPathway(any(), any(), any(), any()))
            .thenAnswer(invocation -> runtimeReferenceByVersion(invocation.getArgument(2)));

        RequestContext.restore(new RequestContext.Snapshot(
            "trace-pathway", OrgScope.tenant("tenant-A"), "tester"));
        authenticate(RoleCode.ENGINE_OPERATOR);
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void createTemplatePersistsNodesEdgesAndMetricBindings() {
        PathwayTemplateDetailResponse response = service.createTemplate(templateRequest());

        assertThat(response.template().templateId()).startsWith("pt-");
        assertThat(response.nodes()).hasSize(2);
        assertThat(response.deploymentStatus()).isEqualTo(AssetVersionStatus.DRAFT);
        ArgumentCaptor<PathwayTemplate> templateCap = ArgumentCaptor.forClass(PathwayTemplate.class);
        ArgumentCaptor<PathwayNode> nodeCap = ArgumentCaptor.forClass(PathwayNode.class);
        ArgumentCaptor<PathwayEdge> edgeCap = ArgumentCaptor.forClass(PathwayEdge.class);
        ArgumentCaptor<SpecialtyMetricBinding> bindingCap = ArgumentCaptor.forClass(SpecialtyMetricBinding.class);
        verify(templates).save(templateCap.capture());
        verify(nodes, org.mockito.Mockito.times(2)).save(nodeCap.capture());
        verify(edges).save(edgeCap.capture());
        verify(metricBindings, org.mockito.Mockito.times(2)).save(bindingCap.capture());
        assertThat(templateCap.getValue().status()).isEqualTo(PathwayTemplateStatus.DRAFT);
        assertThat(nodeCap.getAllValues()).extracting(PathwayNode::nodeCode)
            .containsExactly("ASSESS", "FOLLOWUP");
        assertThat(edgeCap.getValue().fromNodeCode()).isEqualTo("ASSESS");
        assertThat(bindingCap.getAllValues()).extracting(SpecialtyMetricBinding::nodeCode)
            .containsExactly("ASSESS", "FOLLOWUP");
        assertThat(bindingCap.getAllValues()).extracting(SpecialtyMetricBinding::metricCode)
            .containsExactly("COPD.TIME_TO_ASSESS", "COPD.TIME_TO_FOLLOWUP");
        assertThat(templateCap.getValue().entryMode()).isEqualTo(PathwayEntryMode.AUTO_SUGGEST);
        verify(versionedAssets).registerDraft(org.mockito.Mockito.argThat(command ->
            command.assetType() == VersionedAssetType.PATHWAY
                && command.tenantId().equals("tenant-A")
                && command.assetIdentity().equals("TPL.COPD")
                && command.organizationScope() == null
                && command.applicableScope().equals("disease:COPD")
                && command.content().contains("\"nodeCode\":\"ASSESS\"")
                && command.content().contains("\"fromNodeCode\":\"ASSESS\"")
                && command.content().contains("\"metricCode\":\"COPD.TIME_TO_ASSESS\"")
                && command.content().contains("\"metricCode\":\"COPD.TIME_TO_FOLLOWUP\"")
        ));
    }

    @Test
    void createTemplateRegistersStableRuleAndOrderSetDependencies() {
        service.createTemplate(templateRequestWithAssetReferences());

        verify(versionedAssets).registerDraft(org.mockito.Mockito.argThat(command ->
            command.dependencies().stream().anyMatch(dependency ->
                dependency.dependsOnAssetType() == VersionedAssetType.ORDER_SET
                    && dependency.dependsOnIdentity().equals("ORDER.COPD.FOLLOWUP"))
                && command.dependencies().stream().anyMatch(dependency ->
                    dependency.dependsOnAssetType() == VersionedAssetType.RULE
                        && dependency.dependsOnIdentity().equals("RULE.COPD.STABLE"))
        ));
    }

    @Test
    void createTemplateRejectsConditionFieldsOutsideContextCatalog() {
        PathwayTemplateCreateRequest request = pathwayRequest(
            "ASSESS",
            List.of(
                new PathwayNodeRequest("ASSESS", "入径评估", PathwayNodeType.ASSESSMENT,
                    10, "医生", null, 1440, false,
                    json(clockSlaConfig("NODE_START", 0, 1440, 1560))),
                new PathwayNodeRequest("FOLLOWUP", "随访", PathwayNodeType.FOLLOWUP,
                    20, "护士", null, 43200, true,
                    json(clockSlaConfig("NODE_START", 0, 43200, 44640)))
            ),
            List.of(new PathwayEdgeRequest("EDGE.ASSESS.FOLLOWUP", "ASSESS", "FOLLOWUP",
                PathwayEdgeType.CONDITION,
                json("{\"fact\":\"order.drugClass\",\"operator\":\"equals\",\"value\":\"ANTICOAGULANT\"}"),
                10)),
            List.of(
                new SpecialtyMetricBindingRequest("ASSESS", "COPD.TIME_TO_ASSESS", true),
                new SpecialtyMetricBindingRequest("FOLLOWUP", "COPD.TIME_TO_FOLLOWUP", true)
            ));

        assertThatThrownBy(() -> service.createTemplate(request))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("字段目录不存在")
            .hasMessageContaining("order.drugClass");

        verify(versionedAssets, never()).registerDraft(any());
    }

    @Test
    void createTemplateAutomaticallyAllocatesNextBusinessVersion() {
        when(templates.findTopByTenantIdAndTemplateCodeOrderByTemplateVersionDesc(
            "tenant-A", "TPL.COPD"))
            .thenReturn(Optional.of(template(
                "pt-v3", "tenant-A", "TPL.COPD", 3, PathwayTemplateStatus.OFFLINE)));

        service.createTemplate(templateRequest());

        ArgumentCaptor<PathwayTemplate> templateCap = ArgumentCaptor.forClass(PathwayTemplate.class);
        verify(templates).save(templateCap.capture());
        assertThat(templateCap.getValue().templateVersion()).isEqualTo(4);
        verify(versionedAssets).registerDraft(org.mockito.Mockito.argThat(command ->
            command.assetIdentity().equals("TPL.COPD")
        ));
    }

    @Test
    void createTemplateRequestDoesNotExposeBusinessVersionInput() {
        assertThat(PathwayTemplateCreateRequest.class.getRecordComponents())
            .extracting(component -> component.getName())
            .doesNotContain("templateVersion");
    }

    @Test
    void createTemplateReportsConcurrentVersionAllocationConflictHonestly() {
        when(templates.findTopByTenantIdAndTemplateCodeOrderByTemplateVersionDesc(
            "tenant-A", "TPL.COPD"))
            .thenReturn(Optional.of(template(
                "pt-v3", "tenant-A", "TPL.COPD", 3, PathwayTemplateStatus.PUBLISHED)));
        when(templates.save(any())).thenThrow(new DuplicateKeyException("uk_pathway_template_tenant_code"));

        assertThatThrownBy(() -> service.createTemplate(templateRequest()))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("版本并发创建冲突")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);

        verify(nodes, never()).save(any());
        verify(versionedAssets, never()).registerDraft(any());
    }

    @Test
    void createTemplatePersistsOutcomeBindingsAndRegistersThemInPathwayAsset() {
        when(evaluationIndicators.findByTenantIdAndIndicatorCodeAndStatus(
            "tenant-A", "QI_COPD_LOS", EvaluationIndicatorStatus.ACTIVE))
            .thenReturn(List.of(evaluationIndicator("QI_COPD_LOS", EvaluationSubjectType.PATHWAY)));

        PathwayTemplateDetailResponse response = service.createTemplate(templateRequestWithOutcomeBindings());

        assertThat(response.outcomeBindings()).hasSize(2);
        ArgumentCaptor<PathwayOutcomeBinding> bindingCap = ArgumentCaptor.forClass(PathwayOutcomeBinding.class);
        verify(outcomeBindings, org.mockito.Mockito.times(2)).save(bindingCap.capture());
        assertThat(bindingCap.getAllValues()).extracting(PathwayOutcomeBinding::indicatorCode)
            .containsExactly("QI_COPD_LOS", "QI_COPD_LOS");
        assertThat(bindingCap.getAllValues()).extracting(PathwayOutcomeBinding::scope)
            .containsExactly(PathwayOutcomeScope.TEMPLATE, PathwayOutcomeScope.MILESTONE);
        verify(versionedAssets).registerDraft(org.mockito.Mockito.argThat(command ->
            command.content().contains("\"outcomeBindings\"")
                && command.content().contains("\"indicatorCode\":\"QI_COPD_LOS\"")
                && command.content().contains("\"scope\":\"MILESTONE\"")
        ));
    }

    @Test
    void createTemplateRejectsOutcomeBindingWhenEvaluationIndicatorIsNotActive() {
        when(evaluationIndicators.findByTenantIdAndIndicatorCodeAndStatus(
            "tenant-A", "QI_COPD_LOS", EvaluationIndicatorStatus.ACTIVE))
            .thenReturn(List.of());

        assertThatThrownBy(() -> service.createTemplate(templateRequestWithOutcomeBindings()))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("结局指标未激活或不存在: QI_COPD_LOS")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PATHWAY_004);
        verify(outcomeBindings, never()).save(any());
    }

    @Test
    void createTemplatePersistsPhaseMilestonesAndBindsNodesToDaySequence() {
        PathwayTemplateDetailResponse response = service.createTemplate(templateRequestWithMilestones());

        assertThat(response.milestones()).hasSize(2);
        assertThat(response.milestones()).extracting(PathwayMilestone::milestoneCode)
            .containsExactly("M-PREOP-ASSESS", "M-POD7-FOLLOWUP");
        assertThat(response.milestones()).extracting(PathwayMilestone::phaseCode)
            .containsExactly("PREOP", "POSTOP");
        ArgumentCaptor<PathwayMilestone> milestoneCap = ArgumentCaptor.forClass(PathwayMilestone.class);
        ArgumentCaptor<PathwayNode> nodeCap = ArgumentCaptor.forClass(PathwayNode.class);
        verify(milestones, org.mockito.Mockito.times(2)).save(milestoneCap.capture());
        verify(nodes, org.mockito.Mockito.times(2)).save(nodeCap.capture());
        assertThat(milestoneCap.getAllValues()).extracting(PathwayMilestone::dayOffset)
            .containsExactly(0, 7);
        assertThat(nodeCap.getAllValues()).extracting(PathwayNode::milestoneCode)
            .containsExactly("M-PREOP-ASSESS", "M-POD7-FOLLOWUP");
        verify(versionedAssets).registerDraft(org.mockito.Mockito.argThat(command ->
            command.content().contains("\"phaseCode\":\"PREOP\"")
                && command.content().contains("\"dayOffset\":7")
                && command.content().contains("\"milestoneCode\":\"M-POD7-FOLLOWUP\"")
        ));
    }

    @Test
    void templateDetailReturnsLocalDraftWhenOrgUnitHasNoPublishedVersionYet() {
        // 院级数据范围的治理员查看自己刚建的 DRAFT 路径模板：组织闭包内尚无任何 PUBLISHED 版本，
        // 继承解析器按契约抛 NOT_FOUND。详情接口必须回退本地草稿投影（供试运行/发布前预览），
        // 而不是把 NOT_FOUND 抛给前台导致详情抽屉空白、整个路径编排前台流被堵死。回归 P5-ACT5-02。
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-pathway",
            new OrgScope("tenant-A", null, "hospital-1", null, null, null, null, null),
            "tester"));
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.DRAFT)));
        when(inheritanceResolver.resolve(any()))
            .thenThrow(new ApiException(ErrorCode.NOT_FOUND, "未找到可继承的 PUBLISHED 资产版本"));

        PathwayTemplateDetailResponse response = service.templateDetail("pt-1");

        assertThat(response.template().templateId()).isEqualTo("pt-1");
        assertThat(response.template().status()).isEqualTo(PathwayTemplateStatus.DRAFT);
        assertThat(response.deploymentStatus()).isEqualTo(AssetVersionStatus.DRAFT);
    }

    @Test
    void templateDetailKeepsSelectedDraftVersionWhenOlderVersionIsPublishedForOrg() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-pathway",
            new OrgScope("tenant-A", null, "hospital-1", null, null, "dept-1", null),
            "tester"));
        PathwayTemplate selectedDraft = template(
            "pt-v2", "tenant-A", "TPL.COPD", 2, PathwayTemplateStatus.DRAFT);
        when(templates.findByTemplateIdAndTenantId("pt-v2", "tenant-A"))
            .thenReturn(Optional.of(selectedDraft));
        when(inheritanceResolver.resolve(any())).thenReturn(new ResolvedAssetVersion(
            assetVersion("av-pathway-1", VersionedAssetType.PATHWAY, "TPL.COPD", "1",
                AssetVersionStatus.PUBLISHED),
            "dept-1", false, true, false, null, SourceTier.ORG));
        stubPathwayAssetStatus("tenant-A", "TPL.COPD", "2", AssetVersionStatus.DRAFT);

        PathwayTemplateDetailResponse response = service.templateDetail("pt-v2");

        assertThat(response.template().templateId()).isEqualTo("pt-v2");
        assertThat(response.template().templateVersion()).isEqualTo(2);
        assertThat(response.deploymentStatus()).isEqualTo(AssetVersionStatus.DRAFT);
        verify(inheritanceResolver, never()).resolve(any());
    }

    @Test
    void templateDetailProjectsUnifiedDeploymentStatus() {
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.PUBLISHED)));
        stubPathwayAssetStatus("tenant-A", "TPL.COPD", "1", AssetVersionStatus.PUBLISHED);

        PathwayTemplateDetailResponse response = service.templateDetail("pt-1");

        assertThat(response.deploymentStatus()).isEqualTo(AssetVersionStatus.PUBLISHED);
    }

    @Test
    void templateDetailProvidesNextVersionAfterHighestHistoricalVersion() {
        PathwayTemplate current = template(
            "pt-v2", "tenant-A", "TPL.COPD", 2, PathwayTemplateStatus.PUBLISHED);
        PathwayTemplate historicalV3 = template(
            "pt-v3", "tenant-A", "TPL.COPD", 3, PathwayTemplateStatus.OFFLINE);
        when(templates.findByTemplateIdAndTenantId("pt-v2", "tenant-A"))
            .thenReturn(Optional.of(current));
        when(templates.findTopByTenantIdAndTemplateCodeOrderByTemplateVersionDesc(
            "tenant-A", "TPL.COPD"))
            .thenReturn(Optional.of(historicalV3));
        stubPathwayAssetStatus("tenant-A", "TPL.COPD", "2", AssetVersionStatus.PUBLISHED);

        PathwayTemplateDetailResponse response = service.templateDetail("pt-v2");

        assertThat(response.nextVersionNo()).isEqualTo(4);
    }

    @Test
    void listTemplatesCanFilterRollbackHistoryByTemplateCode() {
        PathwayTemplate history = template(
            "pt-history", "tenant-A", "TPL.COPD", 1, PathwayTemplateStatus.OFFLINE);
        when(templates.countByFilter(
                "tenant-A", PathwayTemplateStatus.OFFLINE.name(), null, "TPL.COPD", null))
            .thenReturn(1L);
        when(templates.pageByFilter(
                "tenant-A", PathwayTemplateStatus.OFFLINE.name(), null, "TPL.COPD", null, 0, 20))
            .thenReturn(List.of(history));

        PageResponse<PathwayTemplate> response = service.listTemplates(
            new PathwayTemplateFilter(PathwayTemplateStatus.OFFLINE, null, "TPL.COPD", null),
            PageRequest.defaults());

        assertThat(response.items()).extracting(PathwayTemplate::templateId)
            .containsExactly("pt-history");
        verify(templates, never()).listByFilter(any(), any(), any(), any(), any());
    }

    @Test
    void listTemplatesUsesEffectiveRepositoryPagingForCustomerTenantWithoutLoadingSnapshots() {
        PathwayTemplate localOverride = template(
            "pt-local", "tenant-A", "TPL.COPD", 1, PathwayTemplateStatus.PUBLISHED);
        PathwayTemplate platformOnly = template(
            "pt-platform-stroke", "t-1", "TPL.STROKE", 1, PathwayTemplateStatus.PUBLISHED);
        when(templates.countEffectiveByFilter(
            "tenant-A", PlatformTenant.ID, null, PathwayTemplateStatus.PUBLISHED.name(), null, null, "%路径%"))
            .thenReturn(2L);
        when(templates.pageEffectiveByFilter(
            "tenant-A", PlatformTenant.ID, null, PathwayTemplateStatus.PUBLISHED.name(), null, null, "%路径%", 0, 20))
            .thenReturn(List.of(localOverride, platformOnly));

        PageResponse<PathwayTemplate> response = service.listTemplates(
            new PathwayTemplateFilter(null, null, null, "路径"),
            PageRequest.defaults());

        assertThat(response.total()).isEqualTo(2L);
        assertThat(response.items()).extracting(PathwayTemplate::templateId)
            .containsExactly("pt-local", "pt-platform-stroke");
        verify(templates, never()).listByFilter(any(), any(), any(), any(), any());
    }

    @Test
    void listPatientPathwaysUsesTenantScopedServerPagination() {
        PatientPathway runtime = patientPathway(PatientPathwayStatus.NODE_EXECUTING, "ASSESS");
        when(patientPathways.countByTenantIdAndFilters("tenant-A", "patient-1", "NODE_EXECUTING"))
            .thenReturn(1L);
        when(patientPathways.pageByTenantIdAndFilters("tenant-A", "patient-1", "NODE_EXECUTING", 0, 20))
            .thenReturn(List.of(runtime));

        PageResponse<PatientPathway> response = service.listPatientPathways(
            "patient-1", PatientPathwayStatus.NODE_EXECUTING, PageRequest.defaults());

        assertThat(response.total()).isEqualTo(1L);
        assertThat(response.items()).containsExactly(runtime);
        verify(patientPathways).countByTenantIdAndFilters("tenant-A", "patient-1", "NODE_EXECUTING");
        verify(patientPathways).pageByTenantIdAndFilters("tenant-A", "patient-1", "NODE_EXECUTING", 0, 20);
    }

    @Test
    void createTemplateRejectsMissingStartNodeBeforeRegisteringRuntimeAsset() {
        PathwayTemplateCreateRequest request = pathwayRequest(
            "ASSESS",
            List.of(new PathwayNodeRequest(
                "LAB", "检验复核", PathwayNodeType.ASSESSMENT,
                10, "医生", null, null, true, null)),
            List.of(),
            List.of());

        assertThatThrownBy(() -> service.createTemplate(request))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("缺少有效起始节点")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PATHWAY_004);
        verify(versionedAssets, never()).registerDraft(any());
    }

    @Test
    void createTemplateRejectsRichNodeMissingRequiredConfig() {
        PathwayTemplateCreateRequest request = pathwayRequest(
            "ASSESS",
            List.of(
                new PathwayNodeRequest("ASSESS", "入径评估", PathwayNodeType.ASSESSMENT,
                    10, "医生", null, null, false, null),
                new PathwayNodeRequest("ORDER", "医嘱套餐", PathwayNodeType.ORDER_SET,
                    20, "医生", null, null, false, null),
                new PathwayNodeRequest("FOLLOWUP", "随访", PathwayNodeType.FOLLOWUP,
                    30, "护士", null, null, true, null)
            ),
            List.of(
                new PathwayEdgeRequest("EDGE.ASSESS.ORDER", "ASSESS", "ORDER", PathwayEdgeType.DEFAULT, null, 1),
                new PathwayEdgeRequest("EDGE.ORDER.FOLLOWUP", "ORDER", "FOLLOWUP", PathwayEdgeType.DEFAULT, null, 2)
            ),
            List.of());

        assertThatThrownBy(() -> service.createTemplate(request))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("医嘱套餐节点 ORDER 缺少引用")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PATHWAY_004);
        verify(versionedAssets, never()).registerDraft(any());
    }

    @Test
    void createTemplateRejectsPathwayGraphCycle() {
        PathwayTemplateCreateRequest request = pathwayRequest(
            "ASSESS",
            List.of(
                new PathwayNodeRequest("ASSESS", "入径评估", PathwayNodeType.ASSESSMENT,
                    10, "医生", null, null, false, null),
                new PathwayNodeRequest("REVIEW", "复核", PathwayNodeType.ASSESSMENT,
                    20, "医生", null, null, false, null),
                new PathwayNodeRequest("FOLLOWUP", "随访", PathwayNodeType.FOLLOWUP,
                    30, "护士", null, null, true, null)
            ),
            List.of(
                new PathwayEdgeRequest("EDGE.ASSESS.REVIEW", "ASSESS", "REVIEW", PathwayEdgeType.DEFAULT, null, 1),
                new PathwayEdgeRequest("EDGE.REVIEW.ASSESS", "REVIEW", "ASSESS", PathwayEdgeType.DEFAULT, null, 2),
                new PathwayEdgeRequest("EDGE.REVIEW.FOLLOWUP", "REVIEW", "FOLLOWUP", PathwayEdgeType.CONDITION,
                    json("{\"fact\":\"review.done\",\"operator\":\"equals\",\"value\":true}"), 3)
            ),
            List.of());

        assertThatThrownBy(() -> service.createTemplate(request))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("路径图存在环")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PATHWAY_004);
        verify(versionedAssets, never()).registerDraft(any());
    }

    @Test
    void createTemplateRejectsManualRuntimeVersionInPathwayReferences() {
        PathwayTemplateCreateRequest request = pathwayRequest(
            "ASSESS",
            List.of(
                new PathwayNodeRequest("ASSESS", "入径评估", PathwayNodeType.ASSESSMENT,
                    10, "医生", null, null, false, null),
                new PathwayNodeRequest("ORDER", "医嘱套餐", PathwayNodeType.ORDER_SET,
                    20, "医生", null, null, false,
                    json("{\"orderSetRef\":\"OS.COPD\",\"packageVersion\":\"pkg-2026.07\"}")),
                new PathwayNodeRequest("FOLLOWUP", "随访", PathwayNodeType.FOLLOWUP,
                    30, "护士", null, null, true, null)
            ),
            List.of(
                new PathwayEdgeRequest("EDGE.ASSESS.ORDER", "ASSESS", "ORDER", PathwayEdgeType.DEFAULT, null, 1),
                new PathwayEdgeRequest("EDGE.ORDER.FOLLOWUP", "ORDER", "FOLLOWUP", PathwayEdgeType.DEFAULT, null, 2)
            ),
            List.of());

        assertThatThrownBy(() -> service.createTemplate(request))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("不得手工携带运行定位字段")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PATHWAY_004);
        verify(versionedAssets, never()).registerDraft(any());
    }

    @Test
    void createTemplateRejectsDecisionNodeWithoutDefaultFallbackBranch() {
        PathwayTemplateCreateRequest request = pathwayRequest(
            "DECIDE",
            List.of(
                new PathwayNodeRequest("DECIDE", "风险分层", PathwayNodeType.DECISION,
                    10, "医生", null, null, false, null),
                new PathwayNodeRequest("ICU", "重症路径", PathwayNodeType.NURSING,
                    20, "护士", null, null, true, null),
                new PathwayNodeRequest("WARD", "普通病房", PathwayNodeType.NURSING,
                    30, "护士", null, null, true, null)
            ),
            List.of(
                new PathwayEdgeRequest("EDGE.DECIDE.ICU", "DECIDE", "ICU", PathwayEdgeType.CONDITION,
                    json("{\"fact\":\"risk.level\",\"operator\":\"equals\",\"value\":\"HIGH\"}"), 1),
                new PathwayEdgeRequest("EDGE.DECIDE.WARD", "DECIDE", "WARD", PathwayEdgeType.CONDITION,
                    json("{\"fact\":\"risk.level\",\"operator\":\"equals\",\"value\":\"LOW\"}"), 2)
            ),
            List.of());

        assertThatThrownBy(() -> service.createTemplate(request))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("决策节点 DECIDE 必须配置默认兜底分支")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PATHWAY_004);
        verify(versionedAssets, never()).registerDraft(any());
    }

    @Test
    void createTemplateRejectsTimedNodeWithoutQualityMetricBinding() {
        PathwayTemplateCreateRequest request = pathwayRequest(
            "ASSESS",
            List.of(
                new PathwayNodeRequest("ASSESS", "入径评估", PathwayNodeType.ASSESSMENT,
                    10, "医生", null, 60, false, json(clockSlaConfig("NODE_START", 0, 60, 90))),
                new PathwayNodeRequest("FOLLOWUP", "随访", PathwayNodeType.FOLLOWUP,
                    20, "护士", null, null, true, null)
            ),
            List.of(new PathwayEdgeRequest(
                "EDGE.ASSESS.FOLLOWUP", "ASSESS", "FOLLOWUP", PathwayEdgeType.DEFAULT, null, 1)),
            List.of());

        assertThatThrownBy(() -> service.createTemplate(request))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.PATHWAY_CLOCK_MISSING);
        verify(versionedAssets, never()).registerDraft(any());
    }

    @Test
    void createTemplateRejectsTimedNodeWithoutClinicalClockSla() {
        PathwayTemplateCreateRequest request = pathwayRequest(
            "ASSESS",
            List.of(
                new PathwayNodeRequest("ASSESS", "入径评估", PathwayNodeType.ASSESSMENT,
                    10, "医生", null, 60, false, json("{}")),
                new PathwayNodeRequest("FOLLOWUP", "随访", PathwayNodeType.FOLLOWUP,
                    20, "护士", null, null, true, null)
            ),
            List.of(new PathwayEdgeRequest(
                "EDGE.ASSESS.FOLLOWUP", "ASSESS", "FOLLOWUP", PathwayEdgeType.DEFAULT, null, 1)),
            List.of(new SpecialtyMetricBindingRequest("ASSESS", "STEMI.DOOR_TO_BALLOON", true)));

        assertThatThrownBy(() -> service.createTemplate(request))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("缺少 clockSla")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PATHWAY_004);
        verify(versionedAssets, never()).registerDraft(any());
    }

    @Test
    void enterPatientPathwayCreatesRuntimeAndStartClock() {
        when(contextSnapshots.findById("ctx-active-1")).thenReturn(contextSnapshot("ctx-active-1"));
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.PUBLISHED)));
        when(nodes.findByTemplateIdAndTenantIdAndNodeCode("pt-1", "tenant-A", "ASSESS"))
            .thenReturn(Optional.of(node(
                "ASSESS", PathwayNodeType.ASSESSMENT, 10, false,
                clockSlaConfig("ADMISSION", 0, 120, 180), "医生", 120)));
        when(metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(binding("ASSESS", "COPD.TIME_TO_ASSESS", true)));
        stubPathwayAssetStatus("tenant-A", "TPL.COPD", "1", AssetVersionStatus.PUBLISHED);

        PatientPathwayDetailResponse response = service.enterPatientPathway(new PatientPathwayEnterRequest(
            "ctx-active-1", "patient-view", "pt-1", null));

        assertThat(response.patientPathway().patientPathwayId()).startsWith("pp-");
        assertThat(response.patientPathway().patientId()).isEqualTo("patient-1");
        assertThat(response.patientPathway().encounterId()).isEqualTo("enc-1");
        assertThat(response.patientPathway().status()).isEqualTo(PatientPathwayStatus.NODE_EXECUTING);
        assertThat(response.clocks()).hasSize(1);
        ArgumentCaptor<ClinicalClock> clockCap = ArgumentCaptor.forClass(ClinicalClock.class);
        verify(clocks).save(clockCap.capture());
        assertThat(clockCap.getValue().nodeCode()).isEqualTo("ASSESS");
        assertThat(clockCap.getValue().metricCode()).isEqualTo("COPD.TIME_TO_ASSESS");
        assertThat(clockCap.getValue().dueAt()).isNotNull();
        assertThat(clockCap.getValue().baselineEvent()).isEqualTo("ADMISSION");
        assertThat(clockCap.getValue().baselineAt()).isNotNull();
        assertThat(clockCap.getValue().minDueAt()).isEqualTo(clockCap.getValue().baselineAt());
        assertThat(clockCap.getValue().targetDueAt()).isEqualTo(clockCap.getValue().dueAt());
        assertThat(clockCap.getValue().maxDueAt())
            .isEqualTo(clockCap.getValue().baselineAt().plusSeconds(180L * 60L));
        assertThat(clockCap.getValue().escalationLevel()).isEqualTo(ClinicalClockEscalationLevel.NONE);
        assertThat(clockCap.getValue().escalationPolicyJson()).contains("QUALITY_RECORD");
    }

    @Test
    void entryCandidatesUseSnapshotRuntimeReleaseAndExposeNoVersionSelector() {
        when(contextSnapshots.findById("ctx-active-1")).thenReturn(contextSnapshot("ctx-active-1"));
        when(runtimePathways.selectEntryCandidates(
                "tenant-A", "runtime-release-test", "result-review"))
            .thenReturn(new RuntimePathwaySelection(
                "runtime-release-test",
                "baseline-A12",
                List.of(new RuntimePathwayReference(
                    "tenant-A", "pt-1", "TPL.COPD", "av-pathway-v1", 1,
                    "稳定期随访路径", "COPD"))));

        PathwayEntryCandidateResponse response =
            service.entryCandidates("ctx-active-1", "result-review");

        assertThat(response.contextSnapshotId()).isEqualTo("ctx-active-1");
        assertThat(response.triggerPoint()).isEqualTo("result-review");
        assertThat(response.candidates()).containsExactly(new PathwayEntryCandidate(
            "pt-1", "TPL.COPD", "稳定期随访路径", "COPD"));
        verify(runtimePathways).selectEntryCandidates(
            "tenant-A", "runtime-release-test", "result-review");
    }

    @Test
    void enterPatientPathwayCreatesStartNodeWorklistFromRaciRoles() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-pathway",
            new OrgScope("tenant-A", "group-A", "hospital-A", "campus-A", "site-A", "dept-A", "specialty-A"),
            "tester"));
        PathwayNode startNode = nodeWithRaci(
            "ASSESS",
            PathwayNodeType.ASSESSMENT,
            10,
            false,
            "clinical-user",
            "engine-operator",
            List.of("clinical-user"),
            List.of("engine-operator"),
            clockSlaConfig("ADMISSION", 0, 120, 180),
            120);
        when(contextSnapshots.findById("ctx-active-1")).thenReturn(contextSnapshot("ctx-active-1"));
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.PUBLISHED)));
        when(nodes.findByTemplateIdAndTenantIdAndNodeCode("pt-1", "tenant-A", "ASSESS"))
            .thenReturn(Optional.of(startNode));
        when(metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(binding("ASSESS", "COPD.TIME_TO_ASSESS", true)));
        stubPathwayAssetStatus("tenant-A", "TPL.COPD", "1", AssetVersionStatus.PUBLISHED);
        when(inheritanceResolver.resolve(any())).thenReturn(new ResolvedAssetVersion(
            assetVersion("av-pathway-1", VersionedAssetType.PATHWAY, "TPL.COPD", "1", AssetVersionStatus.PUBLISHED),
            "dept-A", false, false, false, null, SourceTier.ORG));

        service.enterPatientPathway(new PatientPathwayEnterRequest(
            "ctx-active-1", "patient-view", "pt-1", null));

        ArgumentCaptor<PathwayNodeWorklistCommand> commandCap =
            ArgumentCaptor.forClass(PathwayNodeWorklistCommand.class);
        verify(worklist).openNodeTodo(commandCap.capture());
        PathwayNodeWorklistCommand command = commandCap.getValue();
        assertThat(command.tenantId()).isEqualTo("tenant-A");
        assertThat(command.orgUnitId()).isEqualTo("dept-A");
        assertThat(command.patientPathwayId()).startsWith("pp-");
        assertThat(command.patientId()).isEqualTo("patient-1");
        assertThat(command.encounterId()).isEqualTo("enc-1");
        assertThat(command.nodeCode()).isEqualTo("ASSESS");
        assertThat(command.nodeName()).isEqualTo("ASSESS");
        assertThat(command.responsibleRole()).isEqualTo("clinical-user");
        assertThat(command.accountableRole()).isEqualTo("engine-operator");
        assertThat(command.consultedRoles()).containsExactly("clinical-user");
        assertThat(command.informedRoles()).containsExactly("engine-operator");
        assertThat(command.dueAt()).isNotNull();
        assertThat(command.deepLink()).startsWith("/clinical/pathways?patientPathwayId=pp-");
        assertThat(command.traceId()).isEqualTo("trace-pathway");
    }

    @Test
    void clocksProjectTimeoutEscalationFromClockSlaPolicy() {
        PatientPathway runtime = patientPathway(PatientPathwayStatus.NODE_EXECUTING, "ABX");
        Instant baselineAt = Instant.now().minusSeconds(2 * 60L * 60L);
        ClinicalClock overdueClock = clockWithSla(
            "clock-abx", "ABX", baselineAt, 0, 60, 90, ClinicalClockStatus.RUNNING);
        when(patientPathways.findByPatientPathwayIdAndTenantId("pp-1", "tenant-A"))
            .thenReturn(Optional.of(runtime));
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.PUBLISHED)));
        when(clocks.findByPatientPathwayIdAndTenantIdOrderByStartedAtAsc("pp-1", "tenant-A"))
            .thenReturn(List.of(overdueClock));

        List<ClinicalClock> response = service.clocks("pp-1");

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().status()).isEqualTo(ClinicalClockStatus.TIMEOUT);
        assertThat(response.getFirst().escalationLevel()).isEqualTo(ClinicalClockEscalationLevel.QUALITY_RECORD);
        assertThat(response.getFirst().metricCode()).isEqualTo("COPD.TIME_TO_FOLLOWUP");
        ArgumentCaptor<ClockSlaBreachedEvent> eventCap = ArgumentCaptor.forClass(ClockSlaBreachedEvent.class);
        verify(domainEvents).clockSlaBreached(eventCap.capture());
        assertThat(eventCap.getValue().tenantId()).isEqualTo("tenant-A");
        assertThat(eventCap.getValue().traceId()).isEqualTo("trace-pathway");
        assertThat(eventCap.getValue().runtimeReleaseId()).isEqualTo("release-H1");
        assertThat(eventCap.getValue().patientPathwayId()).isEqualTo("pp-1");
        assertThat(eventCap.getValue().clockId()).isEqualTo("clock-abx");
        assertThat(eventCap.getValue().escalationLevel()).isEqualTo("QUALITY_RECORD");
    }

    @Test
    void patientDetailReportsMilestoneAchievementFromCompletedClocksAndCurrentNode() {
        PatientPathway runtime = patientPathway(PatientPathwayStatus.NODE_EXECUTING, "FOLLOWUP");
        PathwayMilestone preop = milestone("M-PREOP-ASSESS", "PREOP", "术前", "入径评估", 0, 60, 1);
        PathwayMilestone followup = milestone("M-POD7-FOLLOWUP", "POSTOP", "术后", "出院后随访", 7, 10080, 2);
        when(patientPathways.findByPatientPathwayIdAndTenantId("pp-1", "tenant-A"))
            .thenReturn(Optional.of(runtime));
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.PUBLISHED)));
        when(milestones.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(preop, followup));
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(
                node("pt-1", "tenant-A", "ASSESS", "M-PREOP-ASSESS", 10, false),
                node("pt-1", "tenant-A", "FOLLOWUP", "M-POD7-FOLLOWUP", 20, true)
            ));
        Instant completedAt = runtime.enteredAt().plusSeconds(1800);
        ClinicalClock assessClock = completedClock("clock-assess", "ASSESS", completedAt);
        ClinicalClock followupClock = clock("clock-followup", "FOLLOWUP", ClinicalClockStatus.RUNNING);
        when(variances.findByPatientPathwayIdAndTenantIdOrderByCreatedAtAsc("pp-1", "tenant-A"))
            .thenReturn(List.of());
        when(clocks.findByPatientPathwayIdAndTenantIdOrderByStartedAtAsc("pp-1", "tenant-A"))
            .thenReturn(List.of(assessClock, followupClock));

        PatientPathwayDetailResponse response = service.patientDetail("pp-1");

        assertThat(response.milestoneStatuses()).extracting(PathwayMilestoneRuntimeStatus::milestoneCode)
            .containsExactly("M-PREOP-ASSESS", "M-POD7-FOLLOWUP");
        assertThat(response.milestoneStatuses()).extracting(PathwayMilestoneRuntimeStatus::status)
            .containsExactly(PathwayMilestoneStatus.ACHIEVED, PathwayMilestoneStatus.CURRENT);
        assertThat(response.milestoneStatuses().get(0).achievedAt()).isEqualTo(completedAt);
        assertThat(response.milestoneStatuses().get(1).expectedAt())
            .isEqualTo(runtime.enteredAt().plusSeconds(10080L * 60L));
    }

    @Test
    void enterPatientPathwayRejectsAutomaticEntryWhenExclusionCriteriaMatches() {
        when(contextSnapshots.findById("ctx-active-1")).thenReturn(contextSnapshot("ctx-active-1"));
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(templateWithEntryCriteria(PathwayEntryMode.AUTO_SUGGEST,
                "{\"include\":{\"all\":[{\"fact\":\"patient.mpi\",\"operator\":\"equals\",\"value\":\"patient-1\"}]},"
                    + "\"exclude\":{\"any\":[{\"fact\":\"observation.HB.value\",\"operator\":\"lt\",\"value\":90}]}}"
            )));
        when(nodes.findByTemplateIdAndTenantIdAndNodeCode("pt-1", "tenant-A", "ASSESS"))
            .thenReturn(Optional.of(node("ASSESS", 10, false)));
        stubPathwayAssetStatus("tenant-A", "TPL.COPD", "1", AssetVersionStatus.PUBLISHED);

        assertThatThrownBy(() -> service.enterPatientPathway(new PatientPathwayEnterRequest(
                "ctx-active-1", "patient-view", "pt-1", null)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("排除")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PATHWAY_001);

        verify(patientPathways, never()).save(any());
        verify(clocks, never()).save(any());
    }

    @Test
    void enterPatientPathwayUsesCanonicalObservationPathForEntryIncludeCriteria() {
        when(contextSnapshots.findById("ctx-active-1")).thenReturn(contextSnapshot("ctx-active-1"));
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(templateWithEntryCriteria(PathwayEntryMode.AUTO_SUGGEST,
                "{\"include\":{\"all\":[{\"fact\":\"observations[].valueNumeric\",\"operator\":\"lt\",\"value\":90}]}}"
            )));
        when(nodes.findByTemplateIdAndTenantIdAndNodeCode("pt-1", "tenant-A", "ASSESS"))
            .thenReturn(Optional.of(node("ASSESS", 10, false)));
        stubPathwayAssetStatus("tenant-A", "TPL.COPD", "1", AssetVersionStatus.PUBLISHED);

        PatientPathwayDetailResponse response = service.enterPatientPathway(new PatientPathwayEnterRequest(
            "ctx-active-1", "patient-view", "pt-1", null));

        assertThat(response.patientPathway().patientPathwayId()).startsWith("pp-");
        verify(patientPathways).save(any());
        verify(clocks).save(any());
    }

    @Test
    void enterPatientPathwayUsesCanonicalObservationPathForEntryExcludeCriteria() {
        when(contextSnapshots.findById("ctx-active-1")).thenReturn(contextSnapshot("ctx-active-1"));
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(templateWithEntryCriteria(PathwayEntryMode.AUTO_SUGGEST,
                "{\"include\":{\"all\":[{\"fact\":\"patient.mpi\",\"operator\":\"equals\",\"value\":\"patient-1\"}]},"
                    + "\"exclude\":{\"any\":[{\"fact\":\"observations[].valueNumeric\",\"operator\":\"lt\",\"value\":90}]}}"
            )));
        when(nodes.findByTemplateIdAndTenantIdAndNodeCode("pt-1", "tenant-A", "ASSESS"))
            .thenReturn(Optional.of(node("ASSESS", 10, false)));
        stubPathwayAssetStatus("tenant-A", "TPL.COPD", "1", AssetVersionStatus.PUBLISHED);

        assertThatThrownBy(() -> service.enterPatientPathway(new PatientPathwayEnterRequest(
                "ctx-active-1", "patient-view", "pt-1", null)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("排除")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PATHWAY_001);

        verify(patientPathways, never()).save(any());
        verify(clocks, never()).save(any());
    }

    @Test
    void enterPatientPathwayAllowsManualConfirmationWhenIncludeCriteriaDoesNotMatch() {
        when(contextSnapshots.findById("ctx-active-1")).thenReturn(contextSnapshot("ctx-active-1"));
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(templateWithEntryCriteria(PathwayEntryMode.MANUAL_CONFIRM,
                "{\"include\":{\"all\":[{\"fact\":\"patient.mpi\",\"operator\":\"equals\",\"value\":\"another-patient\"}]},"
                    + "\"exclude\":{\"any\":[{\"fact\":\"observation.HB.value\",\"operator\":\"lt\",\"value\":50}]}}"
            )));
        when(nodes.findByTemplateIdAndTenantIdAndNodeCode("pt-1", "tenant-A", "ASSESS"))
            .thenReturn(Optional.of(node("ASSESS", 10, false)));
        stubPathwayAssetStatus("tenant-A", "TPL.COPD", "1", AssetVersionStatus.PUBLISHED);

        PatientPathwayDetailResponse response = service.enterPatientPathway(new PatientPathwayEnterRequest(
            "ctx-active-1", "patient-view", "pt-1", null));

        assertThat(response.patientPathway().patientPathwayId()).startsWith("pp-");
        assertThat(response.patientPathway().status()).isEqualTo(PatientPathwayStatus.NODE_EXECUTING);
        verify(patientPathways).save(any());
        verify(clocks).save(any());
    }

    @Test
    void enterPatientPathwayRejectsNonActiveContextSnapshot() {
        ContextSnapshotResponse active = contextSnapshot("ctx-revoked-1");
        when(contextSnapshots.findById("ctx-revoked-1")).thenReturn(new ContextSnapshotResponse(
            active.snapshotId(), ContextSnapshotStatus.SUPERSEDED, active.resources(),
            "runtime-release-test", active.qualityStatus(), active.missingFields(),
            active.mappingStatus(), active.createdAt(), active.traceId()));

        assertThatThrownBy(() -> service.enterPatientPathway(new PatientPathwayEnterRequest(
                "ctx-revoked-1", "patient-view", "pt-1", null)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("已生效上下文")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PATHWAY_001);

        verify(patientPathways, never()).save(any());
    }

    @Test
    void enterPatientPathwayUsesSnapshotRuntimeReleaseAndPathwayTemplateOnly() {
        when(contextSnapshots.findById("ctx-active-1"))
            .thenReturn(contextSnapshot("ctx-active-1", "patient-1"));
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.PUBLISHED)));
        when(nodes.findByTemplateIdAndTenantIdAndNodeCode("pt-1", "tenant-A", "ASSESS"))
            .thenReturn(Optional.of(node(
                "ASSESS", PathwayNodeType.ASSESSMENT, 10, false,
                clockSlaConfig("ADMISSION", 0, 120, 180), "医生", 120)));
        when(metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(binding("ASSESS", "COPD.TIME_TO_ASSESS", true)));
        stubPathwayAssetStatus("tenant-A", "TPL.COPD", "1", AssetVersionStatus.PUBLISHED);

        PatientPathwayDetailResponse response = service.enterPatientPathway(new PatientPathwayEnterRequest(
            "ctx-active-1", "patient-view", "pt-1", null));

        assertThat(response.patientPathway().patientId()).isEqualTo("patient-1");
        assertThat(response.patientPathway().status()).isEqualTo(PatientPathwayStatus.NODE_EXECUTING);
        verify(patientPathways).save(any());
        verify(clocks).save(any());
    }

    @Test
    void enterPatientPathwayRejectsTemplateOutsideRuntimeReleaseCandidateSet() {
        when(contextSnapshots.findById("ctx-active-1")).thenReturn(contextSnapshot("ctx-active-1"));
        when(runtimePathways.requireEntryCandidate(
                "tenant-A", "runtime-release-test", "patient-view", "pt-1"))
            .thenThrow(new ApiException(
                ErrorCode.ENG_PATHWAY_006,
                "所选路径不是当前机构生效版本与触发点下的入径候选：pt-1"));

        assertThatThrownBy(() -> service.enterPatientPathway(new PatientPathwayEnterRequest(
                "ctx-active-1", "patient-view", "pt-1", null)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("入径候选")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PATHWAY_006);

        verify(patientPathways, never()).save(any());
        verify(clocks, never()).save(any());
    }

    @Test
    void enterPatientPathwayDoesNotApplyPatientCohortGateAfterRuntimeReleaseSelection() {
        when(contextSnapshots.findById("ctx-canary-in"))
            .thenReturn(contextSnapshot("ctx-canary-in", "patient-canary-2"));
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.PUBLISHED)));
        when(nodes.findByTemplateIdAndTenantIdAndNodeCode("pt-1", "tenant-A", "ASSESS"))
            .thenReturn(Optional.of(node(
                "ASSESS", PathwayNodeType.ASSESSMENT, 10, false,
                clockSlaConfig("ADMISSION", 0, 120, 180), "医生", 120)));
        when(metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(binding("ASSESS", "COPD.TIME_TO_ASSESS", true)));

        PatientPathwayDetailResponse response = service.enterPatientPathway(
            new PatientPathwayEnterRequest("ctx-canary-in", "patient-view", "pt-1", null));

        assertThat(response.patientPathway().patientId()).isEqualTo("patient-canary-2");
        assertThat(response.patientPathway().status()).isEqualTo(PatientPathwayStatus.NODE_EXECUTING);
        assertThat(response.clocks()).hasSize(1);
    }

    @Test
    void enterPatientPathwayUsesExactVersionSelectedByRuntimeRelease() {
        InheritanceResolver resolver = mock(InheritanceResolver.class);
        PathwayEngineService inheritedService = new PathwayEngineService(
            templates, nodes, milestones, edges, patientPathways, variances,
            clocks, metricBindings, outcomeBindings, evaluationIndicators,
            contextSnapshots, new PathwayProgressor(), auditRecorder,
            transitions, diagnoseAssembler, json, followupHandoff, safetyGuard,
            versionedAssets, assetVersions, resolver, runtimePathways);
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-pathway", new OrgScope("tenant-A", null, "hosp-1", null, null, "dept-1", null), "tester"));
        when(runtimePathways.requireEntryCandidate(
                "tenant-A", "runtime-release-test", "patient-view", "pt-v2"))
            .thenReturn(new RuntimePathwayReference(
                "tenant-A", "pt-v2", "TPL.COPD", "av-pathway-v2", 2,
                "稳定期随访路径", "COPD"));
        when(templates.findByTemplateIdAndTenantId("pt-v2", "tenant-A"))
            .thenReturn(Optional.of(template("pt-v2", "tenant-A", "TPL.COPD", 2, PathwayTemplateStatus.PUBLISHED)));
        when(nodes.findByTemplateIdAndTenantIdAndNodeCode("pt-v2", "tenant-A", "ASSESS"))
            .thenReturn(Optional.of(node("pt-v2", "tenant-A", "ASSESS", 10, false)));
        when(metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc("pt-v2", "tenant-A"))
            .thenReturn(List.of(binding("ASSESS", "COPD.TIME_TO_ASSESS", true)));
        when(contextSnapshots.findById("ctx-active-1")).thenReturn(contextSnapshot("ctx-active-1"));

        PatientPathwayDetailResponse response = inheritedService.enterPatientPathway(new PatientPathwayEnterRequest(
            "ctx-active-1", "patient-view", "pt-v2", null));

        assertThat(response.patientPathway().templateId()).isEqualTo("pt-v2");
        assertThat(response.patientPathway().runtimeReleaseId()).isEqualTo("runtime-release-test");
        assertThat(response.patientPathway().pathwayVersionId()).isEqualTo("av-pathway-v2");
        verify(resolver, never()).resolve(any());
    }

    @Test
    void enterPatientPathwayRejectsVersionThatRuntimeReleaseCannotResolve() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-pathway",
            new OrgScope("tenant-A", null, "hospital-1", null, null, "dept-1", null),
            "tester"));
        when(contextSnapshots.findById("ctx-active-1")).thenReturn(contextSnapshot("ctx-active-1"));
        when(runtimePathways.requireEntryCandidate(
                "tenant-A", "runtime-release-test", "patient-view", "pt-v2"))
            .thenThrow(new ApiException(
                ErrorCode.ENG_PATHWAY_006,
                "机构生效版本锁定路径版本未发布：TPL.COPD@V2"));

        assertThatThrownBy(() -> service.enterPatientPathway(new PatientPathwayEnterRequest(
                "ctx-active-1", "patient-view", "pt-v2", null)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("机构生效版本锁定路径版本未发布");

        verify(patientPathways, never()).save(any());
        verify(inheritanceResolver, never()).resolve(any());
    }

    @Test
    void enterPatientPathwayRejectsWithdrawnTemplateSourceBeforeCreatingRuntime() {
        when(contextSnapshots.findById("ctx-active-1")).thenReturn(contextSnapshot("ctx-active-1"));
        PathwayTemplate withdrawnTemplate = templateWithSourceRef(
            PathwayTemplateStatus.PUBLISHED, "knowledge-version:5");
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(withdrawnTemplate));
        stubPathwayAssetStatus("tenant-A", "TPL.COPD", "1", AssetVersionStatus.PUBLISHED);
        doThrow(new ApiException(ErrorCode.CONFLICT, "路径模板引用已撤回知识版本"))
            .when(safetyGuard).assertPathwayTemplateAllowed(withdrawnTemplate);

        assertThatThrownBy(() -> service.enterPatientPathway(new PatientPathwayEnterRequest(
                "ctx-active-1", "patient-view", "pt-1", null)))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT);

        verify(patientPathways, never()).save(any());
        verify(clocks, never()).save(any());
    }

    @Test
    void enterPatientPathwayFallsBackToPlatformTemplateAndKeepsRuntimeInCustomerTenant() {
        when(contextSnapshots.findById("ctx-active-1")).thenReturn(contextSnapshot("ctx-active-1"));
        PathwayTemplate platformTemplate = template(
            "pt-platform", "t-1", "TPL.COPD", 1, PathwayTemplateStatus.PUBLISHED);
        PathwayNode platformStart = node("pt-platform", "t-1", "ASSESS", 10, false);
        when(templates.findByTemplateIdAndTenantId("pt-platform", "tenant-A")).thenReturn(Optional.empty());
        when(templates.findByTemplateIdAndTenantId("pt-platform", "t-1")).thenReturn(Optional.of(platformTemplate));
        when(templates.findByTenantIdAndTemplateCodeAndTemplateVersion("tenant-A", "TPL.COPD", 1))
            .thenReturn(Optional.empty());
        when(nodes.findByTemplateIdAndTenantIdAndNodeCode("pt-platform", "t-1", "ASSESS"))
            .thenReturn(Optional.of(platformStart));
        stubPathwayAssetStatus("t-1", "TPL.COPD", "1", AssetVersionStatus.PUBLISHED);

        PatientPathwayDetailResponse response = service.enterPatientPathway(new PatientPathwayEnterRequest(
            "ctx-active-1", "patient-view", "pt-platform", null));

        assertThat(response.patientPathway().tenantId()).isEqualTo("tenant-A");
        assertThat(response.patientPathway().templateId()).isEqualTo("pt-platform");
        ArgumentCaptor<ClinicalClock> clockCap = ArgumentCaptor.forClass(ClinicalClock.class);
        verify(clocks).save(clockCap.capture());
        assertThat(clockCap.getValue().tenantId()).isEqualTo("tenant-A");
        assertThat(clockCap.getValue().nodeCode()).isEqualTo("ASSESS");
    }

    @Test
    void enterPatientPathwayUsesLocalOverrideOnlyWhenRuntimeReleaseSelectsIt() {
        when(contextSnapshots.findById("ctx-active-1")).thenReturn(contextSnapshot("ctx-active-1"));
        PathwayTemplate localOverride = template(
            "pt-local", "tenant-A", "TPL.COPD", 1, PathwayTemplateStatus.PUBLISHED);
        PathwayNode localStart = node("pt-local", "tenant-A", "ASSESS", 10, false);
        when(runtimePathways.requireEntryCandidate(
                "tenant-A", "runtime-release-test", "patient-view", "pt-local"))
            .thenReturn(new RuntimePathwayReference(
                "tenant-A", "pt-local", "TPL.COPD", "av-pathway-local-v1", 1,
                "稳定期随访路径", "COPD"));
        when(templates.findByTemplateIdAndTenantId("pt-local", "tenant-A"))
            .thenReturn(Optional.of(localOverride));
        when(nodes.findByTemplateIdAndTenantIdAndNodeCode("pt-local", "tenant-A", "ASSESS"))
            .thenReturn(Optional.of(localStart));

        PatientPathwayDetailResponse response = service.enterPatientPathway(new PatientPathwayEnterRequest(
            "ctx-active-1", "patient-view", "pt-local", null));

        assertThat(response.patientPathway().tenantId()).isEqualTo("tenant-A");
        assertThat(response.patientPathway().templateId()).isEqualTo("pt-local");
        verify(nodes).findByTemplateIdAndTenantIdAndNodeCode("pt-local", "tenant-A", "ASSESS");
    }

    @Test
    void enterPatientPathwayKeepsPlatformActiveUntilLocalReviewedOverrideIsActivated() {
        when(contextSnapshots.findById("ctx-active-1")).thenReturn(contextSnapshot("ctx-active-1"));
        PathwayTemplate platformTemplate = template(
            "pt-platform", "t-1", "TPL.COPD", 1, PathwayTemplateStatus.PUBLISHED);
        PathwayTemplate localReviewed = template(
            "pt-local", "tenant-A", "TPL.COPD", 1, PathwayTemplateStatus.PUBLISHED);
        when(templates.findByTemplateIdAndTenantId("pt-platform", "tenant-A")).thenReturn(Optional.empty());
        when(templates.findByTemplateIdAndTenantId("pt-platform", "t-1")).thenReturn(Optional.of(platformTemplate));
        when(templates.findByTenantIdAndTemplateCodeAndTemplateVersion("tenant-A", "TPL.COPD", 1))
            .thenReturn(Optional.of(localReviewed));
        when(nodes.findByTemplateIdAndTenantIdAndNodeCode("pt-platform", "t-1", "ASSESS"))
            .thenReturn(Optional.of(node("pt-platform", "t-1", "ASSESS", 10, false)));
        when(metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc("pt-platform", "t-1"))
            .thenReturn(List.of());
        stubPathwayAssetStatus("tenant-A", "TPL.COPD", "1", AssetVersionStatus.DRAFT);
        stubPathwayAssetStatus("t-1", "TPL.COPD", "1", AssetVersionStatus.PUBLISHED);

        PatientPathwayDetailResponse response = service.enterPatientPathway(new PatientPathwayEnterRequest(
            "ctx-active-1", "patient-view", "pt-platform", null));

        assertThat(response.patientPathway().templateId()).isEqualTo("pt-platform");
        verify(nodes).findByTemplateIdAndTenantIdAndNodeCode("pt-platform", "t-1", "ASSESS");
        verify(nodes, never()).findByTemplateIdAndTenantIdAndNodeCode("pt-local", "tenant-A", "ASSESS");
    }

    @Test
    void advanceCompleteMovesToNextNodeAndClosesCurrentClock() {
        PatientPathway runtime = patientPathway(PatientPathwayStatus.NODE_EXECUTING, "ASSESS");
        ClinicalClock currentClock = clock("clock-1", "ASSESS", ClinicalClockStatus.RUNNING);
        when(patientPathways.findByPatientPathwayIdAndTenantId("pp-1", "tenant-A"))
            .thenReturn(Optional.of(runtime));
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.PUBLISHED)));
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(node("ASSESS", 10, false), node("FOLLOWUP", 20, true)));
        when(edges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(edge("ASSESS", "FOLLOWUP", PathwayEdgeType.DEFAULT)));
        when(clocks.findByPatientPathwayIdAndTenantIdOrderByStartedAtAsc("pp-1", "tenant-A"))
            .thenReturn(List.of(currentClock));

        PathwayAdvanceResponse response = service.advance(new PathwayAdvanceRequest(
            "pp-1", PathwayAdvanceEventType.COMPLETE, null, null, null, null, null, null, "evt-1",
            "patient-view"));

        assertThat(response.status()).isEqualTo(PatientPathwayStatus.NODE_EXECUTING);
        assertThat(response.nextNodeCode()).isEqualTo("FOLLOWUP");
        ArgumentCaptor<PatientPathway> pathwayCap = ArgumentCaptor.forClass(PatientPathway.class);
        ArgumentCaptor<ClinicalClock> clockCap = ArgumentCaptor.forClass(ClinicalClock.class);
        verify(patientPathways).save(pathwayCap.capture());
        verify(clocks, org.mockito.Mockito.times(2)).save(clockCap.capture());
        assertThat(pathwayCap.getValue().currentNodeCode()).isEqualTo("FOLLOWUP");
        assertThat(clockCap.getAllValues()).anySatisfy(saved ->
            assertThat(saved.status()).isEqualTo(ClinicalClockStatus.COMPLETED));
        assertThat(clockCap.getAllValues()).anySatisfy(saved ->
            assertThat(saved.nodeCode()).isEqualTo("FOLLOWUP"));
    }

    @Test
    void advanceKeepsRuntimePinnedToEntryTemplateWhenNewVersionBecomesActive() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-pathway", new OrgScope("tenant-A", null, "hosp-1", null, null, "dept-1", null), "tester"));
        PatientPathway runtime = new PatientPathway(
            1L, "pp-1", "tenant-A", "patient-1", "enc-1", "pt-v1",
            "release-H3", "av-pathway-v1", "ASSESS",
            PatientPathwayStatus.NODE_EXECUTING, Instant.now().minusSeconds(60),
            null, null, null, null, Instant.now().minusSeconds(60), "tester",
            Instant.now().minusSeconds(60), "tester", "trace-pathway");
        PathwayTemplate entryTemplate =
            template("pt-v1", "tenant-A", "TPL.COPD", 1, PathwayTemplateStatus.PUBLISHED);
        PathwayTemplate newlyActiveTemplate =
            template("pt-v2", "tenant-A", "TPL.COPD", 2, PathwayTemplateStatus.PUBLISHED);
        when(patientPathways.findByPatientPathwayIdAndTenantId("pp-1", "tenant-A"))
            .thenReturn(Optional.of(runtime));
        when(runtimePathways.requireProgressPathway(
                "tenant-A", "release-H3", "av-pathway-v1", "patient-view"))
            .thenReturn(new RuntimePathwayReference(
                "tenant-A", "pt-v1", "TPL.COPD", "av-pathway-v1", 1,
                "稳定期随访路径", "COPD"));
        when(templates.findByTemplateIdAndTenantId("pt-v1", "tenant-A"))
            .thenReturn(Optional.of(entryTemplate));
        when(inheritanceResolver.resolve(any())).thenReturn(new ResolvedAssetVersion(
            assetVersion("av-pathway-2", VersionedAssetType.PATHWAY, "TPL.COPD", "2", AssetVersionStatus.PUBLISHED),
            "dept-1", false, true, false, null, SourceTier.ORG));
        when(templates.findByTenantIdAndTemplateCodeAndTemplateVersion("tenant-A", "TPL.COPD", 2))
            .thenReturn(Optional.of(newlyActiveTemplate));
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-v1", "tenant-A"))
            .thenReturn(List.of(
                node("pt-v1", "tenant-A", "ASSESS", 10, false),
                node("pt-v1", "tenant-A", "FOLLOWUP", 20, true)));
        when(edges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pt-v1", "tenant-A"))
            .thenReturn(List.of(edge(
                "pt-v1", "tenant-A", "ASSESS", "FOLLOWUP", PathwayEdgeType.DEFAULT, null, 10)));
        when(metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc("pt-v1", "tenant-A"))
            .thenReturn(List.of());
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-v2", "tenant-A"))
            .thenReturn(List.of(
                node("pt-v2", "tenant-A", "ASSESS", 10, false),
                node("pt-v2", "tenant-A", "NEW_REVIEW", 20, true)));
        when(edges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pt-v2", "tenant-A"))
            .thenReturn(List.of(edge(
                "pt-v2", "tenant-A", "ASSESS", "NEW_REVIEW", PathwayEdgeType.DEFAULT, null, 10)));
        when(metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc("pt-v2", "tenant-A"))
            .thenReturn(List.of());
        when(clocks.findByPatientPathwayIdAndTenantIdOrderByStartedAtAsc("pp-1", "tenant-A"))
            .thenReturn(List.of(clock("clock-1", "ASSESS", ClinicalClockStatus.RUNNING)));

        PathwayAdvanceResponse response = service.advance(new PathwayAdvanceRequest(
            "pp-1", PathwayAdvanceEventType.COMPLETE, null, null, null, null, null, null, "evt-pinned",
            "patient-view"));

        assertThat(response.nextNodeCode()).isEqualTo("FOLLOWUP");
        verify(nodes).findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-v1", "tenant-A");
        verify(nodes, never()).findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-v2", "tenant-A");
    }

    @Test
    void advanceClosesPreviousNodeTodoAndCreatesNextNodeWorklist() {
        PatientPathway runtime = patientPathway(PatientPathwayStatus.NODE_EXECUTING, "ASSESS");
        PathwayNode assess = nodeWithRaci(
            "ASSESS", PathwayNodeType.ASSESSMENT, 10, false,
            "clinical-user", "engine-operator", List.of("clinical-user"), List.of("engine-operator"),
            clockSlaConfig("NODE_START", 0, 60, 90), 60);
        PathwayNode followup = nodeWithRaci(
            "FOLLOWUP", PathwayNodeType.FOLLOWUP, 20, true,
            "clinical-user", "clinical-user", List.of("clinical-user"), List.of("engine-operator"),
            clockSlaConfig("NODE_START", 0, 120, 180), 120);
        when(patientPathways.findByPatientPathwayIdAndTenantId("pp-1", "tenant-A"))
            .thenReturn(Optional.of(runtime));
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.PUBLISHED)));
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(assess, followup));
        when(edges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(edge("ASSESS", "FOLLOWUP", PathwayEdgeType.DEFAULT)));
        when(metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(binding("FOLLOWUP", "COPD.TIME_TO_FOLLOWUP", true)));
        when(clocks.findByPatientPathwayIdAndTenantIdOrderByStartedAtAsc("pp-1", "tenant-A"))
            .thenReturn(List.of(clock("clock-assess", "ASSESS", ClinicalClockStatus.RUNNING)));

        PathwayAdvanceResponse response = service.advance(new PathwayAdvanceRequest(
            "pp-1", PathwayAdvanceEventType.COMPLETE, null, null, null, null, null, null, "evt-next",
            "patient-view"));

        assertThat(response.nextNodeCode()).isEqualTo("FOLLOWUP");
        ArgumentCaptor<PathwayNodeWorklistCompletionCommand> closeCap =
            ArgumentCaptor.forClass(PathwayNodeWorklistCompletionCommand.class);
        verify(worklist).completeNodeTodo(closeCap.capture());
        assertThat(closeCap.getValue().patientPathwayId()).isEqualTo("pp-1");
        assertThat(closeCap.getValue().nodeCode()).isEqualTo("ASSESS");
        assertThat(closeCap.getValue().completionReason()).contains("路径已推进");

        ArgumentCaptor<PathwayNodeWorklistCommand> openCap =
            ArgumentCaptor.forClass(PathwayNodeWorklistCommand.class);
        verify(worklist).openNodeTodo(openCap.capture());
        assertThat(openCap.getValue().nodeCode()).isEqualTo("FOLLOWUP");
        assertThat(openCap.getValue().responsibleRole()).isEqualTo("clinical-user");
        assertThat(openCap.getValue().accountableRole()).isEqualTo("clinical-user");
        assertThat(openCap.getValue().consultedRoles()).containsExactly("clinical-user");
        assertThat(openCap.getValue().informedRoles()).containsExactly("engine-operator");
        assertThat(openCap.getValue().dueAt()).isNotNull();
    }

    @Test
    void advanceCompletedPathwayCreatesFollowupHandoff() {
        PatientPathway runtime = patientPathway(PatientPathwayStatus.NODE_EXECUTING, "FOLLOWUP");
        ClinicalClock currentClock = clock("clock-followup", "FOLLOWUP", ClinicalClockStatus.RUNNING);
        when(patientPathways.findByPatientPathwayIdAndTenantId("pp-1", "tenant-A"))
            .thenReturn(Optional.of(runtime));
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.PUBLISHED)));
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(node("FOLLOWUP", 20, true)));
        when(edges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pt-1", "tenant-A"))
            .thenReturn(List.of());
        when(clocks.findByPatientPathwayIdAndTenantIdOrderByStartedAtAsc("pp-1", "tenant-A"))
            .thenReturn(List.of(currentClock));
        when(followupHandoff.handoff(any()))
            .thenReturn(new PathwayFollowupHandoffResult("plan-path-1", 1, "ACTIVE", "trace-pathway"));

        PathwayAdvanceResponse response = service.advance(new PathwayAdvanceRequest(
            "pp-1", PathwayAdvanceEventType.COMPLETE, null, null, null, null, null, null, "evt-complete",
            "patient-view"));

        assertThat(response.status()).isEqualTo(PatientPathwayStatus.COMPLETED);
        assertThat(response.followupPlanId()).isEqualTo("plan-path-1");
        assertThat(response.followupTaskCount()).isEqualTo(1);
        ArgumentCaptor<PathwayFollowupHandoffCommand> handoffCap =
            ArgumentCaptor.forClass(PathwayFollowupHandoffCommand.class);
        verify(followupHandoff).handoff(handoffCap.capture());
        assertThat(handoffCap.getValue().patientPathwayId()).isEqualTo("pp-1");
        assertThat(handoffCap.getValue().patientId()).isEqualTo("patient-1");
        assertThat(handoffCap.getValue().encounterId()).isEqualTo("enc-1");
        assertThat(handoffCap.getValue().templateId()).isEqualTo("pt-1");
        assertThat(handoffCap.getValue().diseaseCode()).isEqualTo("COPD");
        assertThat(handoffCap.getValue().taskTypes()).containsExactly("QUESTIONNAIRE");
    }

    @Test
    void advanceCanUseClinicalContextPackageDifferentFromPathwayPackageAndReturnDecisionEvidence() {
        PatientPathway runtime = patientPathway(PatientPathwayStatus.NODE_EXECUTING, "ASSESS");
        when(patientPathways.findByPatientPathwayIdAndTenantId("pp-1", "tenant-A"))
            .thenReturn(Optional.of(runtime));
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.PUBLISHED)));
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(
                node("ASSESS", 10, false),
                node("TRANSFUSION_REVIEW", 20, true),
                node("FOLLOWUP", 30, true)));
        when(edges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(
                edge("ASSESS", "TRANSFUSION_REVIEW", PathwayEdgeType.CONDITION,
                    "{\"fact\":\"observation.HB.value\",\"operator\":\"lt\",\"value\":90}", 1),
                edge("ASSESS", "FOLLOWUP", PathwayEdgeType.DEFAULT, null, 2)));
        when(clocks.findByPatientPathwayIdAndTenantIdOrderByStartedAtAsc("pp-1", "tenant-A"))
            .thenReturn(List.of(clock("clock-1", "ASSESS", ClinicalClockStatus.RUNNING)));
        when(contextSnapshots.findById("ctx-real-1"))
            .thenReturn(contextSnapshot("ctx-real-1", "patient-1"));

        PathwayAdvanceResponse response = service.advance(new PathwayAdvanceRequest(
            "req-advance-1", "trace-pathway", "tenant-A", null, null, null, null,
            null, null, "doctor-1", List.of("clinical-user"),
            "ctx-real-1", "patient-view", "pp-1", PathwayAdvanceEventType.COMPLETE, null, null,
            null, null, null, null, null, null, null, "evt-real-1"));

        assertThat(response.nextNodeCode()).isEqualTo("TRANSFUSION_REVIEW");
        assertThat(response.edgeCode()).isEqualTo("EDGE.ASSESS.TRANSFUSION_REVIEW");
        assertThat(response.edgeType()).isEqualTo(PathwayEdgeType.CONDITION);
        assertThat(response.snapshotId()).isEqualTo("ctx-real-1");
        assertThat(response.contextQualityStatus()).isEqualTo(QualityStatus.PARTIAL);
        assertThat(response.contextResourceCounts()).containsEntry("observations", 1);
        assertThat(response.decisionEvidence()).containsEntry("observation.HB.value", new BigDecimal("86"));
        verify(contextSnapshots).findById("ctx-real-1");
    }

    @Test
    void varianceCanPausePathwayAndPersistVariance() {
        when(patientPathways.findByPatientPathwayIdAndTenantId("pp-1", "tenant-A"))
            .thenReturn(Optional.of(patientPathway(PatientPathwayStatus.NODE_EXECUTING, "ASSESS")));
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.PUBLISHED)));
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(node("ASSESS", 10, false), node("FOLLOWUP", 20, true)));
        when(edges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(edge("ASSESS", "FOLLOWUP", PathwayEdgeType.DEFAULT)));
        when(clocks.findByPatientPathwayIdAndTenantIdOrderByStartedAtAsc("pp-1", "tenant-A"))
            .thenReturn(List.of(clock("clock-1", "ASSESS", ClinicalClockStatus.RUNNING)));

        PathwayAdvanceResponse response = service.advance(new PathwayAdvanceRequest(
            "pp-1", PathwayAdvanceEventType.VARIANCE, null, null, VarianceType.CLINICAL,
            "CLINICAL_ESCALATION", "医生根据患者情况调整节点", "主管医师",
            VarianceResolutionDecision.HOLD, "等待多学科确认", null, "evt-2", "patient-view"));

        assertThat(response.status()).isEqualTo(PatientPathwayStatus.VARIANCE);
        assertThat(response.varianceId()).startsWith("pv-");
        ArgumentCaptor<PathwayVariance> varianceCap = ArgumentCaptor.forClass(PathwayVariance.class);
        verify(variances).save(varianceCap.capture());
        assertThat(varianceCap.getValue().varianceType()).isEqualTo(VarianceType.CLINICAL);
        assertThat(varianceCap.getValue().reasonCode()).isEqualTo("CLINICAL_ESCALATION");
        assertThat(varianceCap.getValue().responsibleRole()).isEqualTo("主管医师");
        assertThat(varianceCap.getValue().resolutionDecision()).isEqualTo(VarianceResolutionDecision.HOLD);
        assertThat(varianceCap.getValue().continueNodeCode()).isNull();
        ArgumentCaptor<PathwayVarianceRecordedEvent> eventCap =
            ArgumentCaptor.forClass(PathwayVarianceRecordedEvent.class);
        verify(domainEvents).pathwayVarianceRecorded(eventCap.capture());
        assertThat(eventCap.getValue().tenantId()).isEqualTo("tenant-A");
        assertThat(eventCap.getValue().traceId()).isEqualTo("trace-pathway");
        assertThat(eventCap.getValue().runtimeReleaseId()).isEqualTo("release-H1");
        assertThat(eventCap.getValue().patientPathwayId()).isEqualTo("pp-1");
        assertThat(eventCap.getValue().varianceId()).isEqualTo(varianceCap.getValue().varianceId());
        assertThat(eventCap.getValue().responsibleRole()).isEqualTo("主管医师");
        assertThat(eventCap.getValue().resolutionDecision()).isEqualTo("HOLD");
    }

    @Test
    void varianceCanReenterRequestedNodeAndPersistVarianceDecision() {
        when(patientPathways.findByPatientPathwayIdAndTenantId("pp-1", "tenant-A"))
            .thenReturn(Optional.of(patientPathway(PatientPathwayStatus.NODE_EXECUTING, "ASSESS")));
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.PUBLISHED)));
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(node("ASSESS", 10, false), node("FOLLOWUP", 20, true)));
        when(edges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(edge("ASSESS", "FOLLOWUP", PathwayEdgeType.DEFAULT)));
        when(clocks.findByPatientPathwayIdAndTenantIdOrderByStartedAtAsc("pp-1", "tenant-A"))
            .thenReturn(List.of(clock("clock-1", "ASSESS", ClinicalClockStatus.RUNNING)));

        PathwayAdvanceResponse response = service.advance(new PathwayAdvanceRequest(
            "pp-1", PathwayAdvanceEventType.VARIANCE, null, "FOLLOWUP", VarianceType.PATIENT,
            "PATIENT_REFUSES_STANDARD_NODE", "患者拒绝标准观察节点并签署知情记录", "主管医师",
            VarianceResolutionDecision.REENTER, "转入随访节点并记录人工确认", null, "evt-reenter",
            "patient-view"));

        assertThat(response.status()).isEqualTo(PatientPathwayStatus.NODE_EXECUTING);
        assertThat(response.nextNodeCode()).isEqualTo("FOLLOWUP");
        ArgumentCaptor<PathwayVariance> varianceCap = ArgumentCaptor.forClass(PathwayVariance.class);
        verify(variances).save(varianceCap.capture());
        assertThat(varianceCap.getValue().varianceType()).isEqualTo(VarianceType.PATIENT);
        assertThat(varianceCap.getValue().reasonCode()).isEqualTo("PATIENT_REFUSES_STANDARD_NODE");
        assertThat(varianceCap.getValue().responsibleRole()).isEqualTo("主管医师");
        assertThat(varianceCap.getValue().resolutionDecision()).isEqualTo(VarianceResolutionDecision.REENTER);
        assertThat(varianceCap.getValue().continueNodeCode()).isEqualTo("FOLLOWUP");
        ArgumentCaptor<PatientPathway> pathwayCap = ArgumentCaptor.forClass(PatientPathway.class);
        verify(patientPathways).save(pathwayCap.capture());
        assertThat(pathwayCap.getValue().status()).isEqualTo(PatientPathwayStatus.NODE_EXECUTING);
        assertThat(pathwayCap.getValue().currentNodeCode()).isEqualTo("FOLLOWUP");
    }

    @Test
    void varianceCanTerminatePathwayAndPersistVarianceDecision() {
        when(patientPathways.findByPatientPathwayIdAndTenantId("pp-1", "tenant-A"))
            .thenReturn(Optional.of(patientPathway(PatientPathwayStatus.NODE_EXECUTING, "ASSESS")));
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.PUBLISHED)));
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(node("ASSESS", 10, false), node("FOLLOWUP", 20, true)));
        when(edges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(edge("ASSESS", "FOLLOWUP", PathwayEdgeType.DEFAULT)));
        when(clocks.findByPatientPathwayIdAndTenantIdOrderByStartedAtAsc("pp-1", "tenant-A"))
            .thenReturn(List.of(clock("clock-1", "ASSESS", ClinicalClockStatus.RUNNING)));

        PathwayAdvanceResponse response = service.advance(new PathwayAdvanceRequest(
            "pp-1", PathwayAdvanceEventType.VARIANCE, null, null, VarianceType.FAMILY,
            "FAMILY_DECLINES_PATHWAY", "家属拒绝继续执行标准路径", "主管医师",
            VarianceResolutionDecision.TERMINATE, "完成风险告知并终止路径", "家属拒绝继续路径",
            "evt-terminate", "patient-view"));

        assertThat(response.status()).isEqualTo(PatientPathwayStatus.EXITED);
        ArgumentCaptor<PathwayVariance> varianceCap = ArgumentCaptor.forClass(PathwayVariance.class);
        verify(variances).save(varianceCap.capture());
        assertThat(varianceCap.getValue().varianceType()).isEqualTo(VarianceType.FAMILY);
        assertThat(varianceCap.getValue().reasonCode()).isEqualTo("FAMILY_DECLINES_PATHWAY");
        assertThat(varianceCap.getValue().resolutionDecision()).isEqualTo(VarianceResolutionDecision.TERMINATE);
        assertThat(varianceCap.getValue().continueNodeCode()).isNull();
        ArgumentCaptor<PatientPathway> pathwayCap = ArgumentCaptor.forClass(PatientPathway.class);
        verify(patientPathways).save(pathwayCap.capture());
        assertThat(pathwayCap.getValue().status()).isEqualTo(PatientPathwayStatus.EXITED);
        assertThat(pathwayCap.getValue().exitReason()).isEqualTo("家属拒绝继续路径");
    }

    @Test
    void exitClosesCurrentClockAndMarksRuntimeExited() {
        when(patientPathways.findByPatientPathwayIdAndTenantId("pp-1", "tenant-A"))
            .thenReturn(Optional.of(patientPathway(PatientPathwayStatus.NODE_EXECUTING, "ASSESS")));
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.PUBLISHED)));
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(node("ASSESS", 10, false)));
        when(edges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pt-1", "tenant-A"))
            .thenReturn(List.of());
        when(clocks.findByPatientPathwayIdAndTenantIdOrderByStartedAtAsc("pp-1", "tenant-A"))
            .thenReturn(List.of(clock("clock-1", "ASSESS", ClinicalClockStatus.RUNNING)));

        PathwayAdvanceResponse response = service.advance(new PathwayAdvanceRequest(
            "pp-1", PathwayAdvanceEventType.EXIT, null, null, null, null, null, "患者转院", "evt-3",
            "patient-view"));

        assertThat(response.status()).isEqualTo(PatientPathwayStatus.EXITED);
        ArgumentCaptor<PatientPathway> pathwayCap = ArgumentCaptor.forClass(PatientPathway.class);
        verify(patientPathways).save(pathwayCap.capture());
        assertThat(pathwayCap.getValue().exitReason()).isEqualTo("患者转院");
        assertThat(pathwayCap.getValue().exitedAt()).isNotNull();
    }

    @Test
    void exitRejectsWhenExitIncludeCriteriaDoesNotMatchSnapshotFacts() {
        when(patientPathways.findByPatientPathwayIdAndTenantId("pp-1", "tenant-A"))
            .thenReturn(Optional.of(patientPathway(PatientPathwayStatus.NODE_EXECUTING, "ASSESS")));
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(templateWithExitCriteria(
                "{\"include\":{\"all\":[{\"fact\":\"patient.mpi\",\"operator\":\"equals\",\"value\":\"other-patient\"}]}}"
            )));
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(node("ASSESS", 10, false)));
        when(edges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pt-1", "tenant-A"))
            .thenReturn(List.of());
        when(clocks.findByPatientPathwayIdAndTenantIdOrderByStartedAtAsc("pp-1", "tenant-A"))
            .thenReturn(List.of(clock("clock-1", "ASSESS", ClinicalClockStatus.RUNNING)));
        when(contextSnapshots.findById("ctx-active-1")).thenReturn(contextSnapshot("ctx-active-1"));

        assertThatThrownBy(() -> service.advance(new PathwayAdvanceRequest(
                "pp-1", PathwayAdvanceEventType.EXIT, null, null, null,
                null, null, "患者转院", "evt-exit", "ctx-active-1", "patient-view")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("出径")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PATHWAY_001);

        verify(patientPathways, never()).save(any());
        verify(clocks, never()).save(any());
    }

    @Test
    void exitAllowsCanonicalObservationPathForExitIncludeCriteria() {
        when(patientPathways.findByPatientPathwayIdAndTenantId("pp-1", "tenant-A"))
            .thenReturn(Optional.of(patientPathway(PatientPathwayStatus.NODE_EXECUTING, "ASSESS")));
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(templateWithExitCriteria(
                "{\"include\":{\"all\":[{\"fact\":\"observations[].valueNumeric\",\"operator\":\"lt\",\"value\":90}]}}"
            )));
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(node("ASSESS", 10, false)));
        when(edges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pt-1", "tenant-A"))
            .thenReturn(List.of());
        when(clocks.findByPatientPathwayIdAndTenantIdOrderByStartedAtAsc("pp-1", "tenant-A"))
            .thenReturn(List.of(clock("clock-1", "ASSESS", ClinicalClockStatus.RUNNING)));
        when(contextSnapshots.findById("ctx-active-1")).thenReturn(contextSnapshot("ctx-active-1"));

        PathwayAdvanceResponse response = service.advance(new PathwayAdvanceRequest(
            "pp-1", PathwayAdvanceEventType.EXIT, null, null, null,
            null, null, "达到出径标准", "evt-exit", "ctx-active-1", "patient-view"));

        assertThat(response.status()).isEqualTo(PatientPathwayStatus.EXITED);
        verify(patientPathways).save(any());
        verify(clocks).save(any());
    }

    @Test
    void exitRejectsCanonicalObservationPathForExitExcludeCriteria() {
        when(patientPathways.findByPatientPathwayIdAndTenantId("pp-1", "tenant-A"))
            .thenReturn(Optional.of(patientPathway(PatientPathwayStatus.NODE_EXECUTING, "ASSESS")));
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(templateWithExitCriteria(
                "{\"exclude\":{\"any\":[{\"fact\":\"observations[].valueNumeric\",\"operator\":\"lt\",\"value\":90}]}}"
            )));
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(node("ASSESS", 10, false)));
        when(edges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pt-1", "tenant-A"))
            .thenReturn(List.of());
        when(clocks.findByPatientPathwayIdAndTenantIdOrderByStartedAtAsc("pp-1", "tenant-A"))
            .thenReturn(List.of(clock("clock-1", "ASSESS", ClinicalClockStatus.RUNNING)));
        when(contextSnapshots.findById("ctx-active-1")).thenReturn(contextSnapshot("ctx-active-1"));

        assertThatThrownBy(() -> service.advance(new PathwayAdvanceRequest(
                "pp-1", PathwayAdvanceEventType.EXIT, null, null, null,
                null, null, "达到出径标准", "evt-exit", "ctx-active-1", "patient-view")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("出径排除")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PATHWAY_001);

        verify(patientPathways, never()).save(any());
        verify(clocks, never()).save(any());
    }

    @Test
    void simulateAllowsClinicalContextPackageDifferentFromPathwayPackageAndReturnsContextEvidence() {
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.PUBLISHED)));
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(node("ASSESS", 10, false), node("FOLLOWUP", 20, true)));
        when(edges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(edge("ASSESS", "FOLLOWUP", PathwayEdgeType.DEFAULT)));
        when(contextSnapshots.findById("ctx-real-1"))
            .thenReturn(contextSnapshot("ctx-real-1", "patient-1"));

        PathwaySimulationResponse response = service.simulate(
            "pt-1",
            new PathwaySimulateRequest(
                "req-sim-1", "trace-pathway", "tenant-A", null, null, null, null,
                null, null, "doctor-1", List.of("clinical-user"),
                PathwaySimulationMode.SINGLE_SNAPSHOT, List.of(), null,
                "ctx-real-1", "ASSESS", List.of()));

        assertThat(response.snapshotId()).isEqualTo("ctx-real-1");
        assertThat(response.contextQualityStatus()).isEqualTo(QualityStatus.PARTIAL);
        assertThat(response.contextResourceCounts()).containsEntry("observations", 1);
        assertThat(response.missingFields()).containsExactly(
            new MissingFieldEntry("CONDITION", "*", "WARN"));
        assertThat(response.mappingStatus()).containsEntry("OBSERVATION:obs-1:code:HB", "MAPPED");
        verify(contextSnapshots).findById("ctx-real-1");
        verify(patientPathways, never()).save(any());
        verify(variances, never()).save(any());
        verify(clocks, never()).save(any());
    }

    @Test
    void simulateUsesSnapshotObservationFactsToChooseConditionEdge() {
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.PUBLISHED)));
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(
                node("ASSESS", 10, false),
                node("TRANSFUSION_REVIEW", 20, true),
                node("FOLLOWUP", 30, true)));
        when(edges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(
                edge("ASSESS", "TRANSFUSION_REVIEW", PathwayEdgeType.CONDITION,
                    "{\"fact\":\"observation.HB.value\",\"operator\":\"lt\",\"value\":90}", 1),
                edge("ASSESS", "FOLLOWUP", PathwayEdgeType.DEFAULT, null, 2)));
        when(contextSnapshots.findById("ctx-real-1")).thenReturn(contextSnapshot("ctx-real-1"));

        PathwaySimulationResponse response = service.simulate(
            "pt-1",
            new PathwaySimulateRequest("ctx-real-1", "ASSESS", List.of()));

        assertThat(response.nodeTrajectory()).containsExactly("ASSESS", "TRANSFUSION_REVIEW");
        assertThat(response.finalStatus()).isEqualTo(PatientPathwayStatus.COMPLETED);
    }

    @Test
    void simulateRejectsSnapshotWhenEntryExclusionCriteriaMatches() {
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(templateWithEntryCriteria(PathwayEntryMode.AUTO_SUGGEST,
                "{\"include\":{\"all\":[{\"fact\":\"patient.mpi\",\"operator\":\"equals\",\"value\":\"patient-1\"}]},"
                    + "\"exclude\":{\"any\":[{\"fact\":\"observation.HB.value\",\"operator\":\"lt\",\"value\":90}]}}"
            )));
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(node("ASSESS", 10, false), node("FOLLOWUP", 20, true)));
        when(edges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(edge("ASSESS", "FOLLOWUP", PathwayEdgeType.DEFAULT)));
        when(contextSnapshots.findById("ctx-real-1")).thenReturn(contextSnapshot("ctx-real-1"));

        assertThatThrownBy(() -> service.simulate(
                "pt-1",
                new PathwaySimulateRequest("ctx-real-1", "ASSESS", List.of())))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("排除")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PATHWAY_001);

        verify(patientPathways, never()).save(any());
        verify(clocks, never()).save(any());
    }

    @Test
    void simulateFailsWhenLegacyGraphExceedsMaxStepGuard() {
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.PUBLISHED)));
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(
                node("ASSESS", PathwayNodeType.ASSESSMENT, 10, false, null, "医生", null),
                node("REVIEW", PathwayNodeType.ASSESSMENT, 20, false, null, "医生", null)
            ));
        when(edges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(
                edge("ASSESS", "REVIEW", PathwayEdgeType.DEFAULT),
                edge("REVIEW", "ASSESS", PathwayEdgeType.DEFAULT)
            ));

        assertThatThrownBy(() -> service.simulate(
                "pt-1",
                new PathwaySimulateRequest(null, "ASSESS", List.of())))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("超过最大推进步数")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PATHWAY_004);
    }

    @Test
    void simulateQueueReplayRunsOrderedSnapshotsWithoutWritingRuntimeFacts() {
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.PUBLISHED)));
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(node("ASSESS", 10, false), node("FOLLOWUP", 20, true)));
        when(edges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(edge("ASSESS", "FOLLOWUP", PathwayEdgeType.DEFAULT)));
        when(contextSnapshots.findById("ctx-real-1")).thenReturn(contextSnapshot("ctx-real-1"));
        when(contextSnapshots.findById("ctx-real-2")).thenReturn(contextSnapshot("ctx-real-2"));

        PathwaySimulationResponse response = service.simulate(
            "pt-1",
            new PathwaySimulateRequest(
                PathwaySimulationMode.QUEUE_REPLAY,
                List.of("ctx-real-1", "ctx-real-2"),
                null,
                "ASSESS",
                List.of(),
                null));

        assertThat(response.simulationMode()).isEqualTo(PathwaySimulationMode.QUEUE_REPLAY);
        assertThat(response.replaySteps()).extracting(PathwaySimulationReplayStep::snapshotId)
            .containsExactly("ctx-real-1", "ctx-real-2");
        assertThat(response.replaySteps()).allSatisfy(step ->
            assertThat(step.nodeTrajectory()).containsExactly("ASSESS", "FOLLOWUP"));
        verify(contextSnapshots).findById("ctx-real-1");
        verify(contextSnapshots).findById("ctx-real-2");
        verify(patientPathways, never()).save(any());
        verify(variances, never()).save(any());
        verify(clocks, never()).save(any());
    }

    @Test
    void enterPatientPathwayReturnsCoordinationWarningsForParallelOrderSetConflict() {
        PathwayTemplate activeTemplate = template(
            "pt-active", "tenant-A", "TPL.SEPSIS", 1, PathwayTemplateStatus.PUBLISHED);
        PathwayTemplate newTemplate = template(
            "pt-new", "tenant-A", "TPL.COPD", 1, PathwayTemplateStatus.PUBLISHED);
        PatientPathway activeRuntime = new PatientPathway(
            1L, "pp-active", "tenant-A", "patient-1", "enc-1", "pt-active",
            "release-H1", "av-pathway-active", "ORDER_ABX",
            PatientPathwayStatus.NODE_EXECUTING, Instant.now().minusSeconds(600),
            null, null, null, null, Instant.now().minusSeconds(600), "tester",
            Instant.now().minusSeconds(60), "tester", "trace-pathway");
        when(contextSnapshots.findById("ctx-active-1")).thenReturn(contextSnapshot("ctx-active-1"));
        when(templates.findByTemplateIdAndTenantId("pt-new", "tenant-A"))
            .thenReturn(Optional.of(newTemplate));
        when(templates.findByTemplateIdAndTenantId("pt-active", "tenant-A"))
            .thenReturn(Optional.of(activeTemplate));
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            "tenant-A", VersionedAssetType.PATHWAY, "TPL.COPD", canonicalAssetVersionNo("1")))
            .thenReturn(Optional.of(assetVersion(
                "av-pathway-new", VersionedAssetType.PATHWAY, "TPL.COPD", "1",
                AssetVersionStatus.PUBLISHED)));
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-new", "tenant-A"))
            .thenReturn(List.of(
                node("pt-new", "tenant-A", "ASSESS", PathwayNodeType.ASSESSMENT, null, 10, false,
                    null, "医生", 60),
                node("pt-new", "tenant-A", "ORDER_ABX", PathwayNodeType.ORDER_SET, null, 20, true,
                    "{\"orderSetRef\":\"OS.ABX\"}", "医生", null)));
        when(edges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pt-new", "tenant-A"))
            .thenReturn(List.of(edge("pt-new", "tenant-A", "EDGE.NEW", "ASSESS", "ORDER_ABX",
                PathwayEdgeType.DEFAULT)));
        when(metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc("pt-new", "tenant-A"))
            .thenReturn(List.of());
        when(outcomeBindings.findByTemplateIdAndTenantIdOrderByScopeAscRefCodeAscIndicatorCodeAsc(
            "pt-new", "tenant-A"))
            .thenReturn(List.of());
        when(patientPathways.findActiveByTenantIdAndPatientIdOrderByEnteredAtDesc(
            "tenant-A", "patient-1", 0, 20))
            .thenReturn(List.of(activeRuntime));
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-active", "tenant-A"))
            .thenReturn(List.of(
                node("pt-active", "tenant-A", "ORDER_ABX", PathwayNodeType.ORDER_SET, null, 10, false,
                    "{\"orderSetRef\":\"OS.ABX\"}", "医生", null),
                node("pt-active", "tenant-A", "FOLLOWUP", PathwayNodeType.FOLLOWUP, null, 20, true,
                    null, "护士", null)));
        when(edges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pt-active", "tenant-A"))
            .thenReturn(List.of(edge("pt-active", "tenant-A", "EDGE.ACTIVE", "ORDER_ABX", "FOLLOWUP",
                PathwayEdgeType.DEFAULT)));
        when(metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc("pt-active", "tenant-A"))
            .thenReturn(List.of());
        when(outcomeBindings.findByTemplateIdAndTenantIdOrderByScopeAscRefCodeAscIndicatorCodeAsc(
            "pt-active", "tenant-A"))
            .thenReturn(List.of());

        PatientPathwayDetailResponse response = service.enterPatientPathway(new PatientPathwayEnterRequest(
            "ctx-active-1", "patient-view", "pt-new", null));

        assertThat(response.coordinationWarnings()).hasSize(1);
        PathwayCoordinationWarning warning = response.coordinationWarnings().getFirst();
        assertThat(warning.warningType()).isEqualTo(PathwayCoordinationWarningType.ORDER_SET_CONFLICT);
        assertThat(warning.message()).contains("OS.ABX").contains("仅提示协调");
        assertThat(response.patientPathway().status()).isEqualTo(PatientPathwayStatus.NODE_EXECUTING);
        verify(patientPathways).save(any());
    }

    @Test
    void diagnoseAssemblesFromPatientPathway() {
        PatientPathway runtime = patientPathway(PatientPathwayStatus.VARIANCE, "ASSESS");
        DiagnoseResponse expected = new DiagnoseResponse(
            "patient_pathway", "pp-1", "tenant-A", "VARIANCE",
            runtime, List.of(), List.of(), Map.of("template", List.of("pt-1")),
            null, "trace-pathway", null);
        when(patientPathways.findByPatientPathwayIdAndTenantId("pp-1", "tenant-A"))
            .thenReturn(Optional.of(runtime));
        when(diagnoseAssembler.assemble(eq("patient_pathway"), eq("pp-1"), eq("tenant-A"),
            eq("VARIANCE"), eq(runtime), eq(List.of()), eq(Map.of("template", List.of("pt-1"))),
            any(), eq("trace-pathway")))
            .thenReturn(expected);

        DiagnoseResponse actual = service.diagnose("pp-1");

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void variancesOnlyReturnsTenantScopedRuntimeFacts() {
        PatientPathway runtime = patientPathway(PatientPathwayStatus.VARIANCE, "ASSESS");
        PathwayVariance variance = variance("pv-1", VarianceType.CLINICAL);
        when(patientPathways.findByPatientPathwayIdAndTenantId("pp-1", "tenant-A"))
            .thenReturn(Optional.of(runtime));
        when(variances.findByPatientPathwayIdAndTenantIdOrderByCreatedAtAsc("pp-1", "tenant-A"))
            .thenReturn(List.of(variance));

        List<PathwayVariance> actual = service.variances("pp-1");

        assertThat(actual).containsExactly(variance);
    }

    private PathwayTemplateCreateRequest pathwayRequest(String startNodeCode,
                                                        List<PathwayNodeRequest> nodes,
                                                        List<PathwayEdgeRequest> edges,
                                                        List<SpecialtyMetricBindingRequest> metricBindings) {
        return new PathwayTemplateCreateRequest(
            "TPL.COPD", "稳定期随访路径", "COPD",
            PathwayTemplateLevel.STANDARD, PathwayEntryMode.AUTO_SUGGEST,
            startNodeCode, "专病路径专家共识 2026",
            "用于路径 API 测试", json("{\"diagnosis\":\"COPD\"}"), json("{\"completed\":true}"),
            nodes, edges, metricBindings
        );
    }

    private PathwayTemplateCreateRequest templateRequest() {
        return new PathwayTemplateCreateRequest(
            "TPL.COPD", "稳定期随访路径", "COPD",
            PathwayTemplateLevel.STANDARD, PathwayEntryMode.AUTO_SUGGEST,
            "ASSESS", "专病路径专家共识 2026",
            "用于路径 API 测试", json("{\"diagnosis\":\"COPD\"}"), json("{\"completed\":true}"),
            List.of(
                new PathwayNodeRequest("ASSESS", "入径评估", PathwayNodeType.ASSESSMENT,
                    10, "医生", null, 1440, false,
                    json(clockSlaConfig("NODE_START", 0, 1440, 1560))),
                new PathwayNodeRequest("FOLLOWUP", "随访", PathwayNodeType.FOLLOWUP,
                    20, "护士", null, 43200, true,
                    json(clockSlaConfig("NODE_START", 0, 43200, 44640)))
            ),
            List.of(new PathwayEdgeRequest("EDGE.ASSESS.FOLLOWUP", "ASSESS", "FOLLOWUP",
                PathwayEdgeType.DEFAULT, null, 10)),
            List.of(
                new SpecialtyMetricBindingRequest("ASSESS", "COPD.TIME_TO_ASSESS", true),
                new SpecialtyMetricBindingRequest("FOLLOWUP", "COPD.TIME_TO_FOLLOWUP", true)
            )
        );
    }

    private PathwayTemplateCreateRequest templateRequestWithAssetReferences() {
        PathwayTemplateCreateRequest base = templateRequest();
        return new PathwayTemplateCreateRequest(
            base.templateCode(), base.name(), base.diseaseCode(),
            base.templateLevel(), base.entryMode(), base.startNodeCode(), base.sourceRef(),
            base.description(), base.entryCriteria(), base.exitCriteria(), base.milestones(),
            List.of(
                new PathwayNodeRequest("ASSESS", "入径评估", PathwayNodeType.ASSESSMENT,
                    10, "医生", null, 1440, false,
                    json("""
                        {
                          "clockSla": {
                            "baselineEvent": "NODE_START",
                            "minMinutes": 0,
                            "targetMinutes": 1440,
                            "maxMinutes": 1560,
                            "escalations": [
                              { "level": "REMINDER", "afterMinutes": 1440 },
                              { "level": "REPORT", "afterMinutes": 1500 },
                              { "level": "QUALITY_RECORD", "afterMinutes": 1560 }
                            ]
                          },
                          "orderSetRef": "ORDER.COPD.FOLLOWUP"
                        }
                        """)),
                new PathwayNodeRequest("FOLLOWUP", "随访", PathwayNodeType.FOLLOWUP,
                    20, "护士", null, 43200, true,
                    json(clockSlaConfig("NODE_START", 0, 43200, 44640)))
            ),
            List.of(new PathwayEdgeRequest(
                "EDGE.ASSESS.FOLLOWUP", "ASSESS", "FOLLOWUP", PathwayEdgeType.CONDITION,
                json("""
                    {
	                      "ruleRef": "RULE.COPD.STABLE",
	                      "ruleAssetId": "rule-copd-stable"
                    }
                    """),
                10)),
            base.metricBindings()
        );
    }

    private PathwayTemplateCreateRequest templateRequestWithMilestones() {
        return new PathwayTemplateCreateRequest(
            "TPL.COPD", "稳定期随访路径", "COPD",
            PathwayTemplateLevel.STANDARD, PathwayEntryMode.AUTO_SUGGEST,
            "ASSESS", "专病路径专家共识 2026",
            "用于路径 API 测试", json("{\"diagnosis\":\"COPD\"}"), json("{\"completed\":true}"),
            List.of(
                new PathwayMilestoneRequest("PREOP", "术前", "M-PREOP-ASSESS",
                    "入径评估", 0, 60, json("{\"all\":[\"ASSESS\"]}"), 1),
                new PathwayMilestoneRequest("POSTOP", "术后", "M-POD7-FOLLOWUP",
                    "出院后随访", 7, 10080, json("{\"all\":[\"FOLLOWUP\"]}"), 2)
            ),
            List.of(
                new PathwayNodeRequest("ASSESS", "入径评估", PathwayNodeType.ASSESSMENT,
                    "M-PREOP-ASSESS", 10, "医生", null, 1440, false,
                    json(clockSlaConfig("NODE_START", 0, 1440, 1560))),
                new PathwayNodeRequest("FOLLOWUP", "随访", PathwayNodeType.FOLLOWUP,
                    "M-POD7-FOLLOWUP", 20, "护士", null, 43200, true,
                    json(clockSlaConfig("NODE_START", 0, 43200, 44640)))
            ),
            List.of(new PathwayEdgeRequest("EDGE.ASSESS.FOLLOWUP", "ASSESS", "FOLLOWUP",
                PathwayEdgeType.DEFAULT, null, 10)),
            List.of(
                new SpecialtyMetricBindingRequest("ASSESS", "COPD.TIME_TO_ASSESS", true),
                new SpecialtyMetricBindingRequest("FOLLOWUP", "COPD.TIME_TO_FOLLOWUP", true)
            )
        );
    }

    private PathwayTemplateCreateRequest templateRequestWithOutcomeBindings() {
        return new PathwayTemplateCreateRequest(
            null, null, null, null, null, null, null, null, null, null, List.of(),
            "TPL.COPD", "稳定期随访路径", "COPD",
            PathwayTemplateLevel.STANDARD, PathwayEntryMode.AUTO_SUGGEST,
            "ASSESS", "专病路径专家共识 2026",
            "用于路径 API 测试", json("{\"diagnosis\":\"COPD\"}"), json("{\"completed\":true}"),
            List.of(
                new PathwayMilestoneRequest("PREOP", "术前", "M-PREOP-ASSESS",
                    "入径评估", 0, 60, json("{\"all\":[\"ASSESS\"]}"), 1)
            ),
            List.of(
                new PathwayNodeRequest("ASSESS", "入径评估", PathwayNodeType.ASSESSMENT,
                    "M-PREOP-ASSESS", 10, "医生", null, 1440, false,
                    json(clockSlaConfig("NODE_START", 0, 1440, 1560))),
                new PathwayNodeRequest("FOLLOWUP", "随访", PathwayNodeType.FOLLOWUP,
                    null, 20, "护士", null, 43200, true,
                    json(clockSlaConfig("NODE_START", 0, 43200, 44640)))
            ),
            List.of(new PathwayEdgeRequest("EDGE.ASSESS.FOLLOWUP", "ASSESS", "FOLLOWUP",
                PathwayEdgeType.DEFAULT, null, 10)),
            List.of(
                new SpecialtyMetricBindingRequest("ASSESS", "COPD.TIME_TO_ASSESS", true),
                new SpecialtyMetricBindingRequest("FOLLOWUP", "COPD.TIME_TO_FOLLOWUP", true)
            ),
            List.of(
                new PathwayOutcomeBindingRequest(PathwayOutcomeScope.TEMPLATE, null, "QI_COPD_LOS"),
                new PathwayOutcomeBindingRequest(PathwayOutcomeScope.MILESTONE, "M-PREOP-ASSESS", "QI_COPD_LOS")
            )
        );
    }

    private EvaluationIndicator evaluationIndicator(String indicatorCode, EvaluationSubjectType subjectType) {
        Instant now = Instant.now();
        return new EvaluationIndicator(
            1L,
            "ei-" + indicatorCode,
            "tenant-A",
            indicatorCode,
            1,
            indicatorCode + " 指标",
            subjectType,
            "{\"field\":\"patient\"}",
            "{\"field\":\"outcome\"}",
            null,
            "{\"method\":\"ratio\"}",
            "P30D",
            "/TENANT-A",
            "dept-1",
            "质控指标规范",
            EvaluationIndicatorStatus.ACTIVE,
            now.minusSeconds(3600),
            "tester",
            now.minusSeconds(3000),
            now.minusSeconds(7200),
            "tester",
            now.minusSeconds(60),
            "tester",
            "trace-pathway");
    }

    private PathwayTemplate template(PathwayTemplateStatus status) {
        return template("pt-1", "tenant-A", "TPL.COPD", 1, status);
    }

    private PathwayTemplate template(PathwayTemplateStatus status, String startNodeCode) {
        Instant now = Instant.now();
        return new PathwayTemplate(
            1L, "pt-1", "tenant-A", "TPL.COPD", "稳定期随访路径",
            "COPD", 1, PathwayTemplateLevel.STANDARD, status,
            PathwayEntryMode.AUTO_SUGGEST, startNodeCode,
            "专病路径专家共识 2026", "用于路径 API 测试",
            "{\"diagnosis\":\"COPD\"}", "{\"completed\":true}",
            now, "tester", now, "tester", "trace-pathway");
    }

    private PathwayTemplate templateWithSourceRef(PathwayTemplateStatus status, String sourceRef) {
        Instant now = Instant.now();
        return new PathwayTemplate(
            1L, "pt-1", "tenant-A", "TPL.COPD", "稳定期随访路径",
            "COPD", 1, PathwayTemplateLevel.STANDARD, status, PathwayEntryMode.AUTO_SUGGEST, "ASSESS",
            sourceRef, "用于路径 API 测试",
            "{\"diagnosis\":\"COPD\"}", "{\"completed\":true}",
            now, "tester", now, "tester", "trace-pathway");
    }

    private PathwayTemplate templateWithEntryCriteria(PathwayEntryMode entryMode, String entryCriteriaJson) {
        Instant now = Instant.now();
        return new PathwayTemplate(
            1L, "pt-1", "tenant-A", "TPL.COPD", "稳定期随访路径",
            "COPD", 1, PathwayTemplateLevel.STANDARD, PathwayTemplateStatus.PUBLISHED,
            entryMode, "ASSESS",
            "专病路径专家共识 2026", "用于路径 API 测试",
            entryCriteriaJson, "{\"completed\":true}",
            now, "tester", now, "tester", "trace-pathway");
    }

    private PathwayTemplate templateWithExitCriteria(String exitCriteriaJson) {
        Instant now = Instant.now();
        return new PathwayTemplate(
            1L, "pt-1", "tenant-A", "TPL.COPD", "稳定期随访路径",
            "COPD", 1, PathwayTemplateLevel.STANDARD, PathwayTemplateStatus.PUBLISHED,
            PathwayEntryMode.AUTO_SUGGEST, "ASSESS",
            "专病路径专家共识 2026", "用于路径 API 测试",
            "{}", exitCriteriaJson,
            now, "tester", now, "tester", "trace-pathway");
    }

    private PathwayTemplate template(String templateId, String tenantId, String templateCode,
                                     Integer templateVersion, PathwayTemplateStatus status) {
        Instant now = Instant.now();
        return new PathwayTemplate(
            1L, templateId, tenantId, templateCode, "稳定期随访路径",
            "COPD", templateVersion, PathwayTemplateLevel.STANDARD, status,
            PathwayEntryMode.AUTO_SUGGEST, "ASSESS",
            "专病路径专家共识 2026", "用于路径 API 测试",
            "{\"diagnosis\":\"COPD\"}", "{\"completed\":true}",
            now, "tester", now, "tester", "trace-pathway");
    }

    private PathwayNode node(String code, int sortOrder, boolean terminal) {
        return node("pt-1", "tenant-A", code, sortOrder, terminal);
    }

    private PathwayNode node(String templateId, String tenantId, String code, int sortOrder, boolean terminal) {
        return node(templateId, tenantId, code, null, sortOrder, terminal);
    }

    private PathwayNode node(String templateId, String tenantId, String code,
                             String milestoneCode, int sortOrder, boolean terminal) {
        return node(templateId, tenantId, code,
            terminal ? PathwayNodeType.FOLLOWUP : PathwayNodeType.ASSESSMENT,
            milestoneCode, sortOrder, terminal, null, "医生", 1440);
    }

    private PathwayNode node(String code, PathwayNodeType nodeType, int sortOrder, boolean terminal,
                             String configJson, String responsibleRole, Integer timeWindowMinutes) {
        return node("pt-1", "tenant-A", code, nodeType, null, sortOrder, terminal,
            configJson, responsibleRole, timeWindowMinutes);
    }

    private PathwayNode node(String templateId, String tenantId, String code,
                             PathwayNodeType nodeType, String milestoneCode, int sortOrder, boolean terminal,
                             String configJson, String responsibleRole, Integer timeWindowMinutes) {
        Instant now = Instant.now();
        String effectiveConfigJson = configJson;
        if (effectiveConfigJson == null && timeWindowMinutes != null && timeWindowMinutes > 0) {
            effectiveConfigJson = clockSlaConfig("NODE_START", 0, 60, 90);
        }
        return new PathwayNode(
            null, "pn-" + code, tenantId, templateId, code, code,
            nodeType, milestoneCode, sortOrder, responsibleRole, null, timeWindowMinutes, terminal, effectiveConfigJson,
            now, "tester", now, "tester", "trace-pathway");
    }

    private PathwayNode disabledNode(String templateId, String tenantId, String code) {
        Instant now = Instant.now();
        return new PathwayNode(
            null, "pn-" + code + "-disabled", tenantId, templateId, code, code,
            PathwayNodeType.ASSESSMENT, null, 0, "医生", "医生", "[]", "[]",
            null, null, false, true, "{}",
            now, "tester", now, "tester", "trace-pathway");
    }

    private PathwayNode nodeWithRaci(String code,
                                     PathwayNodeType nodeType,
                                     int sortOrder,
                                     boolean terminal,
                                     String responsibleRole,
                                     String accountableRole,
                                     List<String> consultedRoles,
                                     List<String> informedRoles,
                                     String configJson,
                                     Integer timeWindowMinutes) {
        Instant now = Instant.now();
        return new PathwayNode(
            null, "pn-" + code, "tenant-A", "pt-1", code, code,
            nodeType, null, sortOrder,
            responsibleRole, accountableRole, writeJson(consultedRoles), writeJson(informedRoles),
            null, timeWindowMinutes, terminal, configJson,
            now, "tester", now, "tester", "trace-pathway");
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("测试 JSON 序列化失败", exception);
        }
    }

    private String clockSlaConfig(String baselineEvent, int minMinutes, int targetMinutes, int maxMinutes) {
        return """
            {
              "clockSla": {
                "baselineEvent": "%s",
                "minMinutes": %d,
                "targetMinutes": %d,
                "maxMinutes": %d,
                "escalations": [
                  { "level": "REMINDER", "afterMinutes": %d },
                  { "level": "REPORT", "afterMinutes": %d },
                  { "level": "QUALITY_RECORD", "afterMinutes": %d }
                ]
              }
            }
            """.formatted(
                baselineEvent, minMinutes, targetMinutes, maxMinutes, targetMinutes,
                targetMinutes + ((maxMinutes - targetMinutes) / 2), maxMinutes);
    }

    private PathwayMilestone milestone(String milestoneCode, String phaseCode, String phaseName,
                                       String name, Integer dayOffset,
                                       Integer expectedOffsetMinutes, Integer sortOrder) {
        Instant now = Instant.now();
        return new PathwayMilestone(
            null, "pm-" + milestoneCode, "tenant-A", "pt-1", phaseCode, phaseName,
            milestoneCode, name, dayOffset, expectedOffsetMinutes, null, sortOrder,
            now, "tester", now, "tester", "trace-pathway");
    }

    private PathwayEdge edge(String from, String to, PathwayEdgeType type) {
        return edge(from, to, type, null, 10);
    }

    private PathwayEdge edge(String from, String to, PathwayEdgeType type, String conditionJson, int priority) {
        return edge("pt-1", "tenant-A", from, to, type, conditionJson, priority);
    }

    private PathwayEdge edge(String templateId, String tenantId, String edgeCode, String from, String to,
                             PathwayEdgeType type) {
        Instant now = Instant.now();
        return new PathwayEdge(
            null, "pe-" + edgeCode, tenantId, templateId,
            edgeCode, from, to, type, null, 10,
            now, "tester", now, "tester", "trace-pathway");
    }

    private PathwayEdge edge(String templateId, String tenantId, String from, String to,
                             PathwayEdgeType type, String conditionJson, int priority) {
        Instant now = Instant.now();
        return new PathwayEdge(
            null, "pe-" + from + "-" + to, tenantId, templateId,
            "EDGE." + from + "." + to, from, to, type, conditionJson, priority,
            now, "tester", now, "tester", "trace-pathway");
    }

    private PatientPathway patientPathway(PatientPathwayStatus status, String currentNodeCode) {
        Instant now = Instant.now();
        return new PatientPathway(
            1L, "pp-1", "tenant-A", "patient-1", "enc-1", "pt-1",
            "release-H1", "av-pathway-v1", currentNodeCode, status,
            now.minusSeconds(60), null, null, null, null,
            now.minusSeconds(60), "tester", now.minusSeconds(60), "tester", "trace-pathway");
    }

    private RuntimePathwayReference runtimeReference(String templateId) {
        return switch (templateId) {
            case "pt-platform" -> new RuntimePathwayReference(
                "t-1", templateId, "TPL.COPD", "av-pathway-platform-v1", 1,
                "稳定期随访路径", "COPD");
            case "pt-v2" -> new RuntimePathwayReference(
                "tenant-A", templateId, "TPL.COPD", "av-pathway-v2", 2,
                "稳定期随访路径", "COPD");
            case "pt-new" -> new RuntimePathwayReference(
                "tenant-A", templateId, "TPL.COPD", "av-pathway-new", 1,
                "稳定期随访路径", "COPD");
            default -> new RuntimePathwayReference(
                "tenant-A", templateId, "TPL.COPD", "av-pathway-v1", 1,
                "稳定期随访路径", "COPD");
        };
    }

    private RuntimePathwayReference runtimeReferenceByVersion(String pathwayVersionId) {
        return new RuntimePathwayReference(
            "tenant-A", "pt-1", "TPL.COPD", pathwayVersionId, 1,
            "稳定期随访路径", "COPD");
    }

    private AssetVersion assetVersion(String versionId, VersionedAssetType type,
                                      String identity, String versionNo, AssetVersionStatus status) {
        return assetVersion("tenant-A", versionId, type, identity, versionNo, status);
    }

    private AssetVersion assetVersion(String tenantId, String versionId, VersionedAssetType type,
                                      String identity, String versionNo, AssetVersionStatus status) {
        Instant now = Instant.parse("2026-06-06T04:00:00Z");
        String canonicalVersionNo = canonicalAssetVersionNo(versionNo);
        return new AssetVersion(
            1L, versionId, tenantId, type, identity, canonicalVersionNo,
            "/TENANT-A/HOSP-A", "specialty:cardiology",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.FREE, status,
            "version:" + versionId, "统一发布测试", null, null,
            now, "tester", now, "tester", "trace-pathway");
    }

    private void stubPathwayAssetStatus(String tenantId, String identity, String versionNo,
                                        AssetVersionStatus status) {
        String canonicalVersionNo = canonicalAssetVersionNo(versionNo);
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            tenantId, VersionedAssetType.PATHWAY, identity, canonicalVersionNo))
            .thenReturn(Optional.of(assetVersion(
                tenantId, "av-" + identity + "-" + canonicalVersionNo,
                VersionedAssetType.PATHWAY, identity, canonicalVersionNo, status)));
    }

    private String canonicalAssetVersionNo(String versionNo) {
        return AssetVersionNumbers.canonical(
            AssetVersionNumbers.intSequence(versionNo, "测试统一资产版本号"));
    }

    private ClinicalClock clock(String clockId, String nodeCode, ClinicalClockStatus status) {
        Instant now = Instant.now();
        return new ClinicalClock(
            1L, clockId, "tenant-A", "pp-1", nodeCode, "COPD.TIME_TO_FOLLOWUP",
            now.minusSeconds(60), now.plusSeconds(3600), null, status,
            null, null, null, null, null, ClinicalClockEscalationLevel.NONE, null,
            now.minusSeconds(60), "tester", now.minusSeconds(60), "tester", "trace-pathway");
    }

    private ClinicalClock completedClock(String clockId, String nodeCode, Instant completedAt) {
        return new ClinicalClock(
            1L, clockId, "tenant-A", "pp-1", nodeCode, "COPD.TIME_TO_FOLLOWUP",
            completedAt.minusSeconds(1800), completedAt.plusSeconds(3600), completedAt,
            ClinicalClockStatus.COMPLETED,
            null, null, null, null, null, ClinicalClockEscalationLevel.NONE, null,
            completedAt.minusSeconds(1800), "tester", completedAt, "tester", "trace-pathway");
    }

    private ClinicalClock clockWithSla(String clockId, String nodeCode, Instant baselineAt,
                                       int minMinutes, int targetMinutes, int maxMinutes,
                                       ClinicalClockStatus status) {
        return new ClinicalClock(
            1L, clockId, "tenant-A", "pp-1", nodeCode, "COPD.TIME_TO_FOLLOWUP",
            baselineAt, baselineAt.plusSeconds(targetMinutes * 60L), null, status,
            "ADMISSION", baselineAt, baselineAt.plusSeconds(minMinutes * 60L),
            baselineAt.plusSeconds(targetMinutes * 60L), baselineAt.plusSeconds(maxMinutes * 60L),
            ClinicalClockEscalationLevel.NONE,
            """
                [
                  { "level": "REMINDER", "afterMinutes": 60 },
                  { "level": "REPORT", "afterMinutes": 75 },
                  { "level": "QUALITY_RECORD", "afterMinutes": 90 }
                ]
                """,
            baselineAt, "tester", baselineAt, "tester", "trace-pathway");
    }

    private PathwayVariance variance(String varianceId, VarianceType type) {
        Instant now = Instant.now();
        return new PathwayVariance(
            1L, varianceId, "tenant-A", "pp-1", "ASSESS", type,
            "CLINICAL_ESCALATION", "医生根据患者情况调整节点", "主管医师",
            VarianceResolutionDecision.REENTER, "人工确认后继续", "FOLLOWUP",
            now.minusSeconds(30), "tester", now.minusSeconds(30), "tester", "trace-pathway");
    }

    private SpecialtyMetricBinding binding(String nodeCode, String metricCode, boolean required) {
        return binding("pt-1", "tenant-A", nodeCode, metricCode, required);
    }

    private SpecialtyMetricBinding binding(String templateId, String tenantId, String nodeCode,
                                            String metricCode, boolean required) {
        Instant now = Instant.now();
        return new SpecialtyMetricBinding(
            1L, "smb-" + nodeCode, tenantId, templateId, nodeCode, metricCode,
            required, now, "tester", now, "tester", "trace-pathway");
    }

    private ContextSnapshotResponse contextSnapshot(String snapshotId) {
        return contextSnapshot(snapshotId, "patient-1");
    }

    private ContextSnapshotResponse contextSnapshot(String snapshotId, String patientId) {
        Instant now = Instant.now();
        ContextSnapshotResources resources = new ContextSnapshotResources(
            new CanonicalPatient(
                patientId, "脱敏患者", LocalDate.of(1980, 1, 1), "F",
                List.of(), "MPI", "p-1", "v1", now, now, QualityStatus.VALID),
            List.of(),
            List.of(new CanonicalEncounter(
                "enc-1", "OUTPATIENT", now.minusSeconds(3600), null, "dept-1",
                "doctor-1", null, "EMR", "enc-source-1", "v1", now, now, QualityStatus.VALID)),
            List.of(),
            List.of(),
            List.of(new CanonicalObservation(
                "obs-1", "HB", "血红蛋白", new BigDecimal("86"), null, "g/L",
                "115-150", "LOW", "LIS", "obs-rec-1", "v1", now, now, QualityStatus.PARTIAL)),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            ContextSnapshotResources.emptyExtensions());
        return new ContextSnapshotResponse(
            snapshotId,
            ContextSnapshotStatus.ACTIVE,
            resources,
            "runtime-release-test",
            QualityStatus.PARTIAL,
            List.of(new MissingFieldEntry("CONDITION", "*", "WARN")),
            Map.of("OBSERVATION:obs-1:code:HB", "MAPPED"),
            now,
            "trace-pathway");
    }

    private void authenticate(RoleCode role) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "tester",
                "n/a",
                List.of(new SimpleGrantedAuthority(role.authority()))
            )
        );
    }

    private JsonNode json(String source) {
        try {
            return source == null ? null : json.readTree(source);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
