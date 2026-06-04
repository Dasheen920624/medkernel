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
import com.medkernel.engine.context.canonical.CanonicalObservation;
import com.medkernel.engine.context.canonical.CanonicalPatient;
import com.medkernel.engine.safety.ClinicalSafetyGuard;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEventPublisher;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.observability.DiagnoseResponse;
import com.medkernel.shared.observability.DiagnoseResponseAssembler;
import com.medkernel.shared.observability.StateTransitionRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PathwayEngineServiceTest {

    private SpecialtyPackageRepository packages;
    private SpecialtyProfileRepository profiles;
    private PathwayTemplateRepository templates;
    private PathwayNodeRepository nodes;
    private PathwayEdgeRepository edges;
    private PatientPathwayRepository patientPathways;
    private PathwayVarianceRepository variances;
    private ClinicalClockRepository clocks;
    private SpecialtyMetricBindingRepository metricBindings;
    private ContextSnapshotService contextSnapshots;
    private AuditEventPublisher auditPublisher;
    private StateTransitionRecorder transitions;
    private DiagnoseResponseAssembler diagnoseAssembler;
    private PathwayFollowupHandoffPort followupHandoff;
    private ClinicalSafetyGuard safetyGuard;
    private ObjectMapper json;
    private PathwayEngineService service;

    @BeforeEach
    void setUp() {
        packages = mock(SpecialtyPackageRepository.class);
        profiles = mock(SpecialtyProfileRepository.class);
        templates = mock(PathwayTemplateRepository.class);
        nodes = mock(PathwayNodeRepository.class);
        edges = mock(PathwayEdgeRepository.class);
        patientPathways = mock(PatientPathwayRepository.class);
        variances = mock(PathwayVarianceRepository.class);
        clocks = mock(ClinicalClockRepository.class);
        metricBindings = mock(SpecialtyMetricBindingRepository.class);
        contextSnapshots = mock(ContextSnapshotService.class);
        auditPublisher = mock(AuditEventPublisher.class);
        transitions = mock(StateTransitionRecorder.class);
        diagnoseAssembler = mock(DiagnoseResponseAssembler.class);
        followupHandoff = mock(PathwayFollowupHandoffPort.class);
        safetyGuard = mock(ClinicalSafetyGuard.class);
        json = new ObjectMapper();
        json.findAndRegisterModules();
        service = new PathwayEngineService(
            packages, profiles, templates, nodes, edges, patientPathways, variances,
            clocks, metricBindings, contextSnapshots, new PathwayProgressor(), auditPublisher,
            transitions, diagnoseAssembler, json, followupHandoff, safetyGuard);

        when(packages.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(profiles.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(templates.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(nodes.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(edges.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(patientPathways.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(variances.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(clocks.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(metricBindings.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RequestContext.restore(new RequestContext.Snapshot(
            "trace-pathway", OrgScope.tenant("tenant-A"), "tester"));
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void createSpecialtyPackagePersistsPackageAndProfiles() {
        SpecialtyPackageResponse response = service.createPackage(new SpecialtyPackageCreateRequest(
            "PKG.COPD", "COPD", "慢阻肺专病包", "1.0.0", "专病路径专家共识 2026",
            "稳定期路径", List.of(new SpecialtyProfileRequest(
                "DEFAULT", "默认画像", json("{\"risk\":\"medium\"}"),
                json("{\"diagnosis\":\"COPD\"}"), json("{\"status\":\"stable\"}"),
                json("{\"days\":30}")))));

        assertThat(response.packageId()).startsWith("sp-");
        assertThat(response.status()).isEqualTo(SpecialtyPackageStatus.DRAFT);
        assertThat(response.traceId()).isEqualTo("trace-pathway");
        ArgumentCaptor<SpecialtyPackage> packageCap = ArgumentCaptor.forClass(SpecialtyPackage.class);
        ArgumentCaptor<SpecialtyProfile> profileCap = ArgumentCaptor.forClass(SpecialtyProfile.class);
        verify(packages).save(packageCap.capture());
        verify(profiles).save(profileCap.capture());
        assertThat(packageCap.getValue().tenantId()).isEqualTo("tenant-A");
        assertThat(profileCap.getValue().packageId()).isEqualTo(response.packageId());
        verify(auditPublisher).publish(AuditAction.CREATE, "specialty_package",
            response.packageId(), "创建专病包 PKG.COPD");
    }

    @Test
    void createTemplatePersistsNodesEdgesAndMetricBindings() {
        when(packages.findByPackageIdAndTenantId("sp-1", "tenant-A"))
            .thenReturn(Optional.of(packageAsset(SpecialtyPackageStatus.DRAFT)));

        PathwayTemplateDetailResponse response = service.createTemplate(templateRequest());

        assertThat(response.template().templateId()).startsWith("pt-");
        assertThat(response.nodes()).hasSize(2);
        ArgumentCaptor<PathwayTemplate> templateCap = ArgumentCaptor.forClass(PathwayTemplate.class);
        ArgumentCaptor<PathwayNode> nodeCap = ArgumentCaptor.forClass(PathwayNode.class);
        ArgumentCaptor<PathwayEdge> edgeCap = ArgumentCaptor.forClass(PathwayEdge.class);
        ArgumentCaptor<SpecialtyMetricBinding> bindingCap = ArgumentCaptor.forClass(SpecialtyMetricBinding.class);
        verify(templates).save(templateCap.capture());
        verify(nodes, org.mockito.Mockito.times(2)).save(nodeCap.capture());
        verify(edges).save(edgeCap.capture());
        verify(metricBindings).save(bindingCap.capture());
        assertThat(templateCap.getValue().status()).isEqualTo(PathwayTemplateStatus.DRAFT);
        assertThat(nodeCap.getAllValues()).extracting(PathwayNode::nodeCode)
            .containsExactly("ASSESS", "FOLLOWUP");
        assertThat(edgeCap.getValue().fromNodeCode()).isEqualTo("ASSESS");
        assertThat(bindingCap.getValue().metricCode()).isEqualTo("COPD.TIME_TO_FOLLOWUP");
    }

    @Test
    void listTemplatesCanFilterRollbackHistoryByTemplateCode() {
        PathwayTemplate history = template(
            "pt-history", "tenant-A", "TPL.COPD", 1, PathwayTemplateStatus.OFFLINE);
        when(templates.listByFilter(
                "tenant-A", PathwayTemplateStatus.OFFLINE.name(), null, null, "TPL.COPD"))
            .thenReturn(List.of(history));

        PageResponse<PathwayTemplate> response = service.listTemplates(
            new PathwayTemplateFilter(PathwayTemplateStatus.OFFLINE, null, null, "TPL.COPD"),
            PageRequest.defaults());

        assertThat(response.items()).extracting(PathwayTemplate::templateId)
            .containsExactly("pt-history");
    }

    @Test
    void publishFailsWhenStartNodeIsMissing() {
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.DRAFT)));
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(node("LAB", 10, false)));
        when(edges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pt-1", "tenant-A"))
            .thenReturn(List.of());

        assertThatThrownBy(() -> service.publishTemplate("pt-1"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PATHWAY_004);
    }

    @Test
    void publishSucceedsWhenTemplateGraphIsValid() {
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.DRAFT)));
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(node("ASSESS", 10, false), node("FOLLOWUP", 20, true)));
        when(edges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(edge("ASSESS", "FOLLOWUP", PathwayEdgeType.DEFAULT)));
        when(metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(
                binding("ASSESS", "COPD.TIME_TO_ASSESS", true),
                binding("FOLLOWUP", "COPD.TIME_TO_FOLLOWUP", true)));
        when(patientPathways.findByTemplateIdAndTenantIdOrderByEnteredAtDesc("pt-1", "tenant-A"))
            .thenReturn(List.of());

        PathwayTemplateImpactResponse impact = service.templateImpact("pt-1");
        PathwayTemplatePublishResponse response = service.publishTemplate(
            "pt-1",
            new PathwayOperationRequest(impact.impactDigest(), "已核查影响摘要与灰度发布安排"));

        assertThat(response.status()).isEqualTo(PathwayTemplateStatus.PUBLISHED);
        assertThat(response.releaseStep()).isEqualTo("canary_release");
        assertThat(response.canaryPercent()).isEqualTo(10);
        assertThat(response.impactDigest()).isEqualTo(impact.impactDigest());
        ArgumentCaptor<PathwayTemplate> templateCap = ArgumentCaptor.forClass(PathwayTemplate.class);
        verify(templates).save(templateCap.capture());
        assertThat(templateCap.getValue().status()).isEqualTo(PathwayTemplateStatus.PUBLISHED);
        verify(auditPublisher).publish(AuditAction.PUBLISH, "pathway_template", "pt-1", "发布路径模板 TPL.COPD");
    }

    @Test
    void templateImpactUsesRealGraphAndRuntimeFactsForReleaseDigest() {
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.DRAFT)));
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(node("ASSESS", 10, false), node("FOLLOWUP", 20, true)));
        when(edges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(edge("ASSESS", "FOLLOWUP", PathwayEdgeType.DEFAULT)));
        when(metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(
                binding("ASSESS", "COPD.TIME_TO_ASSESS", true),
                binding("FOLLOWUP", "COPD.TIME_TO_FOLLOWUP", true)));
        when(patientPathways.findByTemplateIdAndTenantIdOrderByEnteredAtDesc("pt-1", "tenant-A"))
            .thenReturn(List.of(patientPathway(PatientPathwayStatus.NODE_EXECUTING, "ASSESS")));

        PathwayTemplateImpactResponse response = service.templateImpact("pt-1");

        assertThat(response.analysisStatus()).isEqualTo("COMPLETE");
        assertThat(response.affectedPatientPathways()).isEqualTo(1);
        assertThat(response.nodeCount()).isEqualTo(2);
        assertThat(response.edgeCount()).isEqualTo(1);
        assertThat(response.timedNodeCount()).isEqualTo(2);
        assertThat(response.terminalNodeCount()).isEqualTo(1);
        assertThat(response.canaryPercent()).isEqualTo(10);
        assertThat(response.impactDigest()).startsWith("sha256:");
        assertThat(response.releaseEvidence()).anySatisfy(evidence ->
            assertThat(evidence).contains("灰度发布默认 10%"));
    }

    @Test
    void templateImpactDigestIgnoresLifecycleStatusChanges() {
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.DRAFT)))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.PUBLISHED)));
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(node("ASSESS", 10, false), node("FOLLOWUP", 20, true)));
        when(edges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(edge("ASSESS", "FOLLOWUP", PathwayEdgeType.DEFAULT)));
        when(metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(
                binding("ASSESS", "COPD.TIME_TO_ASSESS", true),
                binding("FOLLOWUP", "COPD.TIME_TO_FOLLOWUP", true)));
        when(patientPathways.findByTemplateIdAndTenantIdOrderByEnteredAtDesc("pt-1", "tenant-A"))
            .thenReturn(List.of(patientPathway(PatientPathwayStatus.NODE_EXECUTING, "ASSESS")));

        PathwayTemplateImpactResponse draftImpact = service.templateImpact("pt-1");
        PathwayTemplateImpactResponse publishedImpact = service.templateImpact("pt-1");

        assertThat(publishedImpact.impactDigest()).isEqualTo(draftImpact.impactDigest());
    }

    @Test
    void publishRejectsMissingCurrentImpactDigestAndReviewReason() {
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.DRAFT)));
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(node("ASSESS", 10, false), node("FOLLOWUP", 20, true)));
        when(edges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(edge("ASSESS", "FOLLOWUP", PathwayEdgeType.DEFAULT)));
        when(metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(
                binding("ASSESS", "COPD.TIME_TO_ASSESS", true),
                binding("FOLLOWUP", "COPD.TIME_TO_FOLLOWUP", true)));
        when(patientPathways.findByTemplateIdAndTenantIdOrderByEnteredAtDesc("pt-1", "tenant-A"))
            .thenReturn(List.of());

        assertThatThrownBy(() -> service.publishTemplate("pt-1", new PathwayOperationRequest(null, "")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PATHWAY_004);
        verify(templates, never()).save(any());
    }

    @Test
    void fullRolloutRequiresHospitalAdminAndKeepsImpactDigestEvidence() {
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.PUBLISHED)));
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(node("ASSESS", 10, false), node("FOLLOWUP", 20, true)));
        when(edges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(edge("ASSESS", "FOLLOWUP", PathwayEdgeType.DEFAULT)));
        when(metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(
                binding("ASSESS", "COPD.TIME_TO_ASSESS", true),
                binding("FOLLOWUP", "COPD.TIME_TO_FOLLOWUP", true)));
        when(patientPathways.findByTemplateIdAndTenantIdOrderByEnteredAtDesc("pt-1", "tenant-A"))
            .thenReturn(List.of());
        PathwayTemplateImpactResponse impact = service.templateImpact("pt-1");

        assertThatThrownBy(() -> service.fullRolloutTemplate(
            "pt-1", operationRequest(impact.impactDigest(), "全量前复核", List.of("doctor"), null)))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PATHWAY_004);

        PathwayTemplatePublishResponse response = service.fullRolloutTemplate(
            "pt-1", operationRequest(impact.impactDigest(), "院级管理员确认全量", List.of("hospital-admin"), null));

        assertThat(response.releaseStep()).isEqualTo("full_rollout");
        assertThat(response.impactDigest()).isEqualTo(impact.impactDigest());
        verify(transitions).record("pathway_template", "pt-1", PathwayTemplateStatus.PUBLISHED.name(),
            PathwayTemplateStatus.PUBLISHED.name(), "FULL_ROLLOUT_PATHWAY_TEMPLATE", null);
    }

    @Test
    void rollbackPublishedTemplateRestoresTargetVersionAndLeavesAuditEvidence() {
        PathwayTemplate current = template("pt-current", "tenant-A", "TPL.COPD", 2, PathwayTemplateStatus.PUBLISHED);
        PathwayTemplate target = template("pt-old", "tenant-A", "TPL.COPD", 1, PathwayTemplateStatus.OFFLINE);
        when(templates.findByTemplateIdAndTenantId("pt-current", "tenant-A"))
            .thenReturn(Optional.of(current));
        when(templates.findByTemplateIdAndTenantId("pt-old", "tenant-A"))
            .thenReturn(Optional.of(target));
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-current", "tenant-A"))
            .thenReturn(List.of(
                node("pt-current", "tenant-A", "ASSESS", 10, false),
                node("pt-current", "tenant-A", "FOLLOWUP", 20, true)));
        when(edges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pt-current", "tenant-A"))
            .thenReturn(List.of(edge("ASSESS", "FOLLOWUP", PathwayEdgeType.DEFAULT)));
        when(metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc("pt-current", "tenant-A"))
            .thenReturn(List.of(
                binding("ASSESS", "COPD.TIME_TO_ASSESS", true),
                binding("FOLLOWUP", "COPD.TIME_TO_FOLLOWUP", true)));
        when(patientPathways.findByTemplateIdAndTenantIdOrderByEnteredAtDesc("pt-current", "tenant-A"))
            .thenReturn(List.of());
        PathwayTemplateImpactResponse impact = service.templateImpact("pt-current");

        PathwayTemplatePublishResponse response = service.rollbackTemplate(
            "pt-current",
            operationRequest(impact.impactDigest(), "灰度异常，回滚到上一版本", List.of("hospital-admin"), "pt-old"));

        assertThat(response.templateId()).isEqualTo("pt-old");
        assertThat(response.status()).isEqualTo(PathwayTemplateStatus.PUBLISHED);
        assertThat(response.releaseStep()).isEqualTo("evidence_rollback");
        ArgumentCaptor<PathwayTemplate> templateCap = ArgumentCaptor.forClass(PathwayTemplate.class);
        verify(templates, org.mockito.Mockito.times(2)).save(templateCap.capture());
        assertThat(templateCap.getAllValues()).anySatisfy(saved -> {
            assertThat(saved.templateId()).isEqualTo("pt-current");
            assertThat(saved.status()).isEqualTo(PathwayTemplateStatus.OFFLINE);
        });
        assertThat(templateCap.getAllValues()).anySatisfy(saved -> {
            assertThat(saved.templateId()).isEqualTo("pt-old");
            assertThat(saved.status()).isEqualTo(PathwayTemplateStatus.PUBLISHED);
        });
        verify(auditPublisher).publish(AuditAction.ROLLBACK, "pathway_template", "pt-old",
            "回滚路径模板 TPL.COPD");
    }

    @Test
    void publishFailsWhenTimedNodeHasNoQualityMetricBinding() {
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.DRAFT)));
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(node("ASSESS", 10, false), node("FOLLOWUP", 20, true)));
        when(edges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(edge("ASSESS", "FOLLOWUP", PathwayEdgeType.DEFAULT)));
        when(metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(binding("FOLLOWUP", "COPD.TIME_TO_FOLLOWUP", true)));

        assertThatThrownBy(() -> service.publishTemplate("pt-1"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.PATHWAY_CLOCK_MISSING);
    }

    @Test
    void enterPatientPathwayCreatesRuntimeAndStartClock() {
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.PUBLISHED)));
        when(nodes.findByTemplateIdAndTenantIdAndNodeCode("pt-1", "tenant-A", "ASSESS"))
            .thenReturn(Optional.of(node("ASSESS", 10, false)));
        when(metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(binding("ASSESS", "COPD.TIME_TO_ASSESS", true)));

        PatientPathwayDetailResponse response = service.enterPatientPathway(new PatientPathwayEnterRequest(
            "patient-1", "enc-1", "pt-1", null));

        assertThat(response.patientPathway().patientPathwayId()).startsWith("pp-");
        assertThat(response.patientPathway().status()).isEqualTo(PatientPathwayStatus.NODE_EXECUTING);
        assertThat(response.clocks()).hasSize(1);
        ArgumentCaptor<ClinicalClock> clockCap = ArgumentCaptor.forClass(ClinicalClock.class);
        verify(clocks).save(clockCap.capture());
        assertThat(clockCap.getValue().nodeCode()).isEqualTo("ASSESS");
        assertThat(clockCap.getValue().metricCode()).isEqualTo("COPD.TIME_TO_ASSESS");
        assertThat(clockCap.getValue().dueAt()).isNotNull();
    }

    @Test
    void enterPatientPathwayRejectsWithdrawnTemplateSourceBeforeCreatingRuntime() {
        PathwayTemplate withdrawnTemplate = templateWithSourceRef(
            PathwayTemplateStatus.PUBLISHED, "knowledge-version:5");
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(withdrawnTemplate));
        doThrow(new ApiException(ErrorCode.CONFLICT, "路径模板引用已撤回知识版本"))
            .when(safetyGuard).assertPathwayTemplateAllowed(withdrawnTemplate);

        assertThatThrownBy(() -> service.enterPatientPathway(new PatientPathwayEnterRequest(
                "patient-1", "enc-1", "pt-1", null)))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT);

        verify(patientPathways, never()).save(any());
        verify(clocks, never()).save(any());
    }

    @Test
    void enterPatientPathwayFallsBackToPlatformTemplateAndKeepsRuntimeInCustomerTenant() {
        PathwayTemplate platformTemplate = template(
            "pt-platform", "t-1", "TPL.COPD", 1, PathwayTemplateStatus.PUBLISHED);
        PathwayNode platformStart = node("pt-platform", "t-1", "ASSESS", 10, false);
        when(templates.findByTemplateIdAndTenantId("pt-platform", "tenant-A")).thenReturn(Optional.empty());
        when(templates.findByTemplateIdAndTenantId("pt-platform", "t-1")).thenReturn(Optional.of(platformTemplate));
        when(templates.findByTenantIdAndTemplateCodeAndTemplateVersion("tenant-A", "TPL.COPD", 1))
            .thenReturn(Optional.empty());
        when(nodes.findByTemplateIdAndTenantIdAndNodeCode("pt-platform", "t-1", "ASSESS"))
            .thenReturn(Optional.of(platformStart));

        PatientPathwayDetailResponse response = service.enterPatientPathway(new PatientPathwayEnterRequest(
            "patient-1", "enc-1", "pt-platform", null));

        assertThat(response.patientPathway().tenantId()).isEqualTo("tenant-A");
        assertThat(response.patientPathway().templateId()).isEqualTo("pt-platform");
        ArgumentCaptor<ClinicalClock> clockCap = ArgumentCaptor.forClass(ClinicalClock.class);
        verify(clocks).save(clockCap.capture());
        assertThat(clockCap.getValue().tenantId()).isEqualTo("tenant-A");
        assertThat(clockCap.getValue().nodeCode()).isEqualTo("ASSESS");
    }

    @Test
    void enterPatientPathwayPrefersLocalTemplateWithSameCodeAndVersionOverPlatformTemplate() {
        PathwayTemplate platformTemplate = template(
            "pt-platform", "t-1", "TPL.COPD", 1, PathwayTemplateStatus.PUBLISHED);
        PathwayTemplate localOverride = template(
            "pt-local", "tenant-A", "TPL.COPD", 1, PathwayTemplateStatus.PUBLISHED);
        PathwayNode localStart = node("pt-local", "tenant-A", "ASSESS", 10, false);
        when(templates.findByTemplateIdAndTenantId("pt-platform", "tenant-A")).thenReturn(Optional.empty());
        when(templates.findByTemplateIdAndTenantId("pt-platform", "t-1")).thenReturn(Optional.of(platformTemplate));
        when(templates.findByTenantIdAndTemplateCodeAndTemplateVersion("tenant-A", "TPL.COPD", 1))
            .thenReturn(Optional.of(localOverride));
        when(nodes.findByTemplateIdAndTenantIdAndNodeCode("pt-local", "tenant-A", "ASSESS"))
            .thenReturn(Optional.of(localStart));

        PatientPathwayDetailResponse response = service.enterPatientPathway(new PatientPathwayEnterRequest(
            "patient-1", "enc-1", "pt-platform", null));

        assertThat(response.patientPathway().tenantId()).isEqualTo("tenant-A");
        assertThat(response.patientPathway().templateId()).isEqualTo("pt-local");
        verify(nodes).findByTemplateIdAndTenantIdAndNodeCode("pt-local", "tenant-A", "ASSESS");
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
            "pp-1", PathwayAdvanceEventType.COMPLETE, null, null, null, null, null, null, "evt-1"));

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
            "pp-1", PathwayAdvanceEventType.COMPLETE, null, null, null, null, null, null, "evt-complete"));

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
    void advanceCanUseSnapshotFactsAndReturnDecisionEvidence() {
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
        when(contextSnapshots.findById("ctx-real-1")).thenReturn(contextSnapshot("ctx-real-1"));

        PathwayAdvanceResponse response = service.advance(new PathwayAdvanceRequest(
            "pp-1", PathwayAdvanceEventType.COMPLETE, null, null, null,
            null, null, null, "evt-real-1", "ctx-real-1"));

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
            "pp-1", PathwayAdvanceEventType.VARIANCE, null, null, VarianceType.DOCTOR_CHOICE,
            "医生根据患者情况调整节点", "人工确认后继续", null, "evt-2"));

        assertThat(response.status()).isEqualTo(PatientPathwayStatus.VARIANCE);
        assertThat(response.varianceId()).startsWith("pv-");
        ArgumentCaptor<PathwayVariance> varianceCap = ArgumentCaptor.forClass(PathwayVariance.class);
        verify(variances).save(varianceCap.capture());
        assertThat(varianceCap.getValue().varianceType()).isEqualTo(VarianceType.DOCTOR_CHOICE);
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
            "pp-1", PathwayAdvanceEventType.EXIT, null, null, null, null, null, "患者转院", "evt-3"));

        assertThat(response.status()).isEqualTo(PatientPathwayStatus.EXITED);
        ArgumentCaptor<PatientPathway> pathwayCap = ArgumentCaptor.forClass(PatientPathway.class);
        verify(patientPathways).save(pathwayCap.capture());
        assertThat(pathwayCap.getValue().exitReason()).isEqualTo("患者转院");
        assertThat(pathwayCap.getValue().exitedAt()).isNotNull();
    }

    @Test
    void simulateReadsApi01SnapshotAndReturnsContextEvidenceWithoutWritingRuntimeFacts() {
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.PUBLISHED)));
        when(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(node("ASSESS", 10, false), node("FOLLOWUP", 20, true)));
        when(edges.findByTemplateIdAndTenantIdOrderByPriorityAsc("pt-1", "tenant-A"))
            .thenReturn(List.of(edge("ASSESS", "FOLLOWUP", PathwayEdgeType.DEFAULT)));
        when(contextSnapshots.findById("ctx-real-1")).thenReturn(contextSnapshot("ctx-real-1"));

        PathwaySimulationResponse response = service.simulate(
            "pt-1",
            new PathwaySimulateRequest("ctx-real-1", "ASSESS", List.of()));

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
        PathwayVariance variance = variance("pv-1", VarianceType.DOCTOR_CHOICE);
        when(patientPathways.findByPatientPathwayIdAndTenantId("pp-1", "tenant-A"))
            .thenReturn(Optional.of(runtime));
        when(variances.findByPatientPathwayIdAndTenantIdOrderByCreatedAtAsc("pp-1", "tenant-A"))
            .thenReturn(List.of(variance));

        List<PathwayVariance> actual = service.variances("pp-1");

        assertThat(actual).containsExactly(variance);
    }

    private PathwayTemplateCreateRequest templateRequest() {
        return new PathwayTemplateCreateRequest(
            "sp-1", "TPL.COPD", "稳定期随访路径", "COPD", 1,
            PathwayTemplateLevel.STANDARD, "ASSESS", "专病路径专家共识 2026",
            "用于路径 API 测试", json("{\"diagnosis\":\"COPD\"}"), json("{\"completed\":true}"),
            List.of(
                new PathwayNodeRequest("ASSESS", "入径评估", PathwayNodeType.ASSESSMENT,
                    10, "医生", null, 1440, false, null),
                new PathwayNodeRequest("FOLLOWUP", "随访", PathwayNodeType.FOLLOWUP,
                    20, "护士", null, 43200, true, null)
            ),
            List.of(new PathwayEdgeRequest("EDGE.ASSESS.FOLLOWUP", "ASSESS", "FOLLOWUP",
                PathwayEdgeType.DEFAULT, null, 10)),
            List.of(new SpecialtyMetricBindingRequest("ASSESS", "COPD.TIME_TO_FOLLOWUP", true))
        );
    }

    private SpecialtyPackage packageAsset(SpecialtyPackageStatus status) {
        Instant now = Instant.now();
        return new SpecialtyPackage(
            1L, "sp-1", "tenant-A", "PKG.COPD", "COPD", "慢阻肺专病包",
            "1.0.0", status, "专病路径专家共识 2026", "稳定期路径",
            null, null, now, "tester", now, "tester", "trace-pathway");
    }

    private PathwayTemplate template(PathwayTemplateStatus status) {
        return template("pt-1", "tenant-A", "TPL.COPD", 1, status);
    }

    private PathwayTemplate templateWithSourceRef(PathwayTemplateStatus status, String sourceRef) {
        Instant now = Instant.now();
        return new PathwayTemplate(
            1L, "pt-1", "tenant-A", "sp-1", "TPL.COPD", "稳定期随访路径",
            "COPD", 1, PathwayTemplateLevel.STANDARD, status, "ASSESS",
            sourceRef, "用于路径 API 测试",
            "{\"diagnosis\":\"COPD\"}", "{\"completed\":true}",
            now, "tester", now, "tester", "trace-pathway");
    }

    private PathwayTemplate template(String templateId, String tenantId, String templateCode,
                                     Integer templateVersion, PathwayTemplateStatus status) {
        Instant now = Instant.now();
        return new PathwayTemplate(
            1L, templateId, tenantId, "sp-1", templateCode, "稳定期随访路径",
            "COPD", templateVersion, PathwayTemplateLevel.STANDARD, status, "ASSESS",
            "专病路径专家共识 2026", "用于路径 API 测试",
            "{\"diagnosis\":\"COPD\"}", "{\"completed\":true}",
            now, "tester", now, "tester", "trace-pathway");
    }

    private PathwayOperationRequest operationRequest(String impactDigest, String reason,
                                                     List<String> roleCodes,
                                                     String rollbackTargetTemplateId) {
        return new PathwayOperationRequest(
            null, null, null, null, null, null, null, null, null, null, roleCodes, null,
            impactDigest, reason, "submit_review", false, rollbackTargetTemplateId);
    }

    private PathwayNode node(String code, int sortOrder, boolean terminal) {
        return node("pt-1", "tenant-A", code, sortOrder, terminal);
    }

    private PathwayNode node(String templateId, String tenantId, String code, int sortOrder, boolean terminal) {
        Instant now = Instant.now();
        return new PathwayNode(
            null, "pn-" + code, tenantId, templateId, code, code,
            terminal ? PathwayNodeType.FOLLOWUP : PathwayNodeType.ASSESSMENT,
            sortOrder, "医生", null, 1440, terminal, null,
            now, "tester", now, "tester", "trace-pathway");
    }

    private PathwayEdge edge(String from, String to, PathwayEdgeType type) {
        return edge(from, to, type, null, 10);
    }

    private PathwayEdge edge(String from, String to, PathwayEdgeType type, String conditionJson, int priority) {
        Instant now = Instant.now();
        return new PathwayEdge(
            null, "pe-" + from + "-" + to, "tenant-A", "pt-1",
            "EDGE." + from + "." + to, from, to, type, conditionJson, priority,
            now, "tester", now, "tester", "trace-pathway");
    }

    private PatientPathway patientPathway(PatientPathwayStatus status, String currentNodeCode) {
        Instant now = Instant.now();
        return new PatientPathway(
            1L, "pp-1", "tenant-A", "patient-1", "enc-1", "pt-1",
            currentNodeCode, status, now.minusSeconds(60), null, null, null, null,
            now.minusSeconds(60), "tester", now.minusSeconds(60), "tester", "trace-pathway");
    }

    private ClinicalClock clock(String clockId, String nodeCode, ClinicalClockStatus status) {
        Instant now = Instant.now();
        return new ClinicalClock(
            1L, clockId, "tenant-A", "pp-1", nodeCode, "COPD.TIME_TO_FOLLOWUP",
            now.minusSeconds(60), now.plusSeconds(3600), null, status,
            now.minusSeconds(60), "tester", now.minusSeconds(60), "tester", "trace-pathway");
    }

    private PathwayVariance variance(String varianceId, VarianceType type) {
        Instant now = Instant.now();
        return new PathwayVariance(
            1L, varianceId, "tenant-A", "pp-1", "ASSESS", type,
            "医生根据患者情况调整节点", "人工确认后继续", "FOLLOWUP",
            now.minusSeconds(30), "tester", now.minusSeconds(30), "tester", "trace-pathway");
    }

    private SpecialtyMetricBinding binding(String nodeCode, String metricCode, boolean required) {
        Instant now = Instant.now();
        return new SpecialtyMetricBinding(
            1L, "smb-" + nodeCode, "tenant-A", "sp-1", "pt-1", nodeCode, metricCode,
            required, now, "tester", now, "tester", "trace-pathway");
    }

    private ContextSnapshotResponse contextSnapshot(String snapshotId) {
        Instant now = Instant.now();
        ContextSnapshotResources resources = new ContextSnapshotResources(
            new CanonicalPatient(
                "patient-1", "脱敏患者", LocalDate.of(1980, 1, 1), "F",
                List.of(), List.of(), "MPI", "p-1", "v1", now, now, QualityStatus.VALID),
            List.of(),
            List.of(),
            List.of(),
            List.of(new CanonicalObservation(
                "obs-1", "HB", "血红蛋白", new BigDecimal("86"), null, "g/L",
                "115-150", "LOW", "LIS", "obs-rec-1", "v1", now, now, QualityStatus.PARTIAL)),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        return new ContextSnapshotResponse(
            snapshotId,
            ContextSnapshotStatus.ACTIVE,
            resources,
            "pkg-2026.06",
            "pkg-2026.06",
            "pkg-2026.06",
            "pkg-2026.06",
            QualityStatus.PARTIAL,
            List.of(new MissingFieldEntry("CONDITION", "*", "WARN")),
            Map.of("OBSERVATION:obs-1:code:HB", "MAPPED"),
            now,
            "trace-pathway");
    }

    private JsonNode json(String source) {
        try {
            return source == null ? null : json.readTree(source);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
