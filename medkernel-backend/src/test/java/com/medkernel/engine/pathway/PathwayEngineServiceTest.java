package com.medkernel.engine.pathway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private AuditEventPublisher auditPublisher;
    private StateTransitionRecorder transitions;
    private DiagnoseResponseAssembler diagnoseAssembler;
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
        auditPublisher = mock(AuditEventPublisher.class);
        transitions = mock(StateTransitionRecorder.class);
        diagnoseAssembler = mock(DiagnoseResponseAssembler.class);
        json = new ObjectMapper();
        json.findAndRegisterModules();
        service = new PathwayEngineService(
            packages, profiles, templates, nodes, edges, patientPathways, variances,
            clocks, metricBindings, new PathwayProgressor(), auditPublisher,
            transitions, diagnoseAssembler, json);

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

        PathwayTemplatePublishResponse response = service.publishTemplate("pt-1");

        assertThat(response.status()).isEqualTo(PathwayTemplateStatus.PUBLISHED);
        ArgumentCaptor<PathwayTemplate> templateCap = ArgumentCaptor.forClass(PathwayTemplate.class);
        verify(templates).save(templateCap.capture());
        assertThat(templateCap.getValue().status()).isEqualTo(PathwayTemplateStatus.PUBLISHED);
        verify(auditPublisher).publish(AuditAction.PUBLISH, "pathway_template", "pt-1", "发布路径模板 TPL.COPD");
    }

    @Test
    void enterPatientPathwayCreatesRuntimeAndStartClock() {
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(template(PathwayTemplateStatus.PUBLISHED)));
        when(nodes.findByTemplateIdAndTenantIdAndNodeCode("pt-1", "tenant-A", "ASSESS"))
            .thenReturn(Optional.of(node("ASSESS", 10, false)));

        PatientPathwayDetailResponse response = service.enterPatientPathway(new PatientPathwayEnterRequest(
            "patient-1", "enc-1", "pt-1", null));

        assertThat(response.patientPathway().patientPathwayId()).startsWith("pp-");
        assertThat(response.patientPathway().status()).isEqualTo(PatientPathwayStatus.NODE_EXECUTING);
        assertThat(response.clocks()).hasSize(1);
        ArgumentCaptor<ClinicalClock> clockCap = ArgumentCaptor.forClass(ClinicalClock.class);
        verify(clocks).save(clockCap.capture());
        assertThat(clockCap.getValue().nodeCode()).isEqualTo("ASSESS");
        assertThat(clockCap.getValue().dueAt()).isNotNull();
    }

    @Test
    void advanceCompleteMovesToNextNodeAndClosesCurrentClock() {
        PatientPathway runtime = patientPathway(PatientPathwayStatus.NODE_EXECUTING, "ASSESS");
        ClinicalClock currentClock = clock("clock-1", "ASSESS", ClinicalClockStatus.RUNNING);
        when(patientPathways.findByPatientPathwayIdAndTenantId("pp-1", "tenant-A"))
            .thenReturn(Optional.of(runtime));
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
    void varianceCanPausePathwayAndPersistVariance() {
        when(patientPathways.findByPatientPathwayIdAndTenantId("pp-1", "tenant-A"))
            .thenReturn(Optional.of(patientPathway(PatientPathwayStatus.NODE_EXECUTING, "ASSESS")));
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
        Instant now = Instant.now();
        return new PathwayTemplate(
            1L, "pt-1", "tenant-A", "sp-1", "TPL.COPD", "稳定期随访路径",
            "COPD", 1, PathwayTemplateLevel.STANDARD, status, "ASSESS",
            "专病路径专家共识 2026", "用于路径 API 测试",
            "{\"diagnosis\":\"COPD\"}", "{\"completed\":true}",
            now, "tester", now, "tester", "trace-pathway");
    }

    private PathwayNode node(String code, int sortOrder, boolean terminal) {
        Instant now = Instant.now();
        return new PathwayNode(
            null, "pn-" + code, "tenant-A", "pt-1", code, code,
            terminal ? PathwayNodeType.FOLLOWUP : PathwayNodeType.ASSESSMENT,
            sortOrder, "医生", null, 1440, terminal, null,
            now, "tester", now, "tester", "trace-pathway");
    }

    private PathwayEdge edge(String from, String to, PathwayEdgeType type) {
        Instant now = Instant.now();
        return new PathwayEdge(
            null, "pe-" + from + "-" + to, "tenant-A", "pt-1",
            "EDGE." + from + "." + to, from, to, type, null, 10,
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

    private JsonNode json(String source) {
        try {
            return source == null ? null : json.readTree(source);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
