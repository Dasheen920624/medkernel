package com.medkernel.engine.pathway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.test.context.TestPropertySource;

@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:pathway-repo-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class PathwayRepositoryTest {

    @Autowired PathwayTemplateRepository templates;
    @Autowired PathwayNodeRepository nodes;
    @Autowired PathwayEdgeRepository edges;
    @Autowired PatientPathwayRepository patientPathways;
    @Autowired PathwayVarianceRepository variances;
    @Autowired ClinicalClockRepository clocks;
    @Autowired SpecialtyMetricBindingRepository metricBindings;

    @AfterEach
    void wipe() {
        metricBindings.deleteAll();
        clocks.deleteAll();
        variances.deleteAll();
        patientPathways.deleteAll();
        edges.deleteAll();
        nodes.deleteAll();
        templates.deleteAll();
    }

    @Test
    void persistsPathwayAssetsAndRuntimeFacts() {
        String templateId = "pt-" + UUID.randomUUID();
        String patientPathwayId = "pp-" + UUID.randomUUID();

        PathwayTemplate savedTemplate = templates.save(sampleTemplate(templateId, "tenant-A", "COPD"));
        PathwayNode start = nodes.save(sampleNode("pn-1", "tenant-A", templateId, "ASSESS", 10, false));
        PathwayNode finish = nodes.save(sampleNode("pn-2", "tenant-A", templateId, "FOLLOWUP", 20, true));
        PathwayEdge savedEdge = edges.save(sampleEdge("pe-" + UUID.randomUUID(), "tenant-A", templateId));
        PatientPathway savedPathway = patientPathways.save(samplePatientPathway(patientPathwayId, "tenant-A", templateId));
        PathwayVariance savedVariance = variances.save(sampleVariance("pv-" + UUID.randomUUID(), "tenant-A", patientPathwayId));
        ClinicalClock savedClock = clocks.save(sampleClock("cc-" + UUID.randomUUID(), "tenant-A", patientPathwayId));
        SpecialtyMetricBinding savedBinding = metricBindings.save(sampleBinding("smb-" + UUID.randomUUID(), "tenant-A", templateId));

        assertThat(savedTemplate.id()).isNotNull();
        assertThat(start.id()).isNotNull();
        assertThat(finish.id()).isNotNull();
        assertThat(savedEdge.id()).isNotNull();
        assertThat(savedPathway.id()).isNotNull();
        assertThat(savedVariance.id()).isNotNull();
        assertThat(savedClock.id()).isNotNull();
        assertThat(savedBinding.id()).isNotNull();

        assertThat(templates.findByTemplateIdAndTenantId(templateId, "tenant-A")).isPresent();
        assertThat(nodes.findByTemplateIdAndTenantIdOrderBySortOrderAsc(templateId, "tenant-A"))
            .extracting(PathwayNode::nodeCode)
            .containsExactly("ASSESS", "FOLLOWUP");
        assertThat(edges.findByTemplateIdAndTenantIdAndFromNodeCodeOrderByPriorityAsc(templateId, "tenant-A", "ASSESS"))
            .extracting(PathwayEdge::toNodeCode)
            .containsExactly("FOLLOWUP");
        assertThat(patientPathways.findByPatientPathwayIdAndTenantId(patientPathwayId, "tenant-A")).isPresent();
        assertThat(variances.findByPatientPathwayIdAndTenantIdOrderByCreatedAtAsc(patientPathwayId, "tenant-A"))
            .extracting(PathwayVariance::varianceType)
            .containsExactly(VarianceType.CLINICAL);
        assertThat(clocks.findByPatientPathwayIdAndTenantIdOrderByStartedAtAsc(patientPathwayId, "tenant-A"))
            .extracting(ClinicalClock::status)
            .containsExactly(ClinicalClockStatus.RUNNING);
        assertThat(metricBindings.findByTemplateIdAndTenantIdOrderByNodeCodeAsc(templateId, "tenant-A"))
            .extracting(SpecialtyMetricBinding::metricCode)
            .containsExactly("COPD.TIME_TO_FOLLOWUP");
    }

    @Test
    void repositoryQueriesDoNotLeakAcrossTenants() {
        String templateId = "pt-" + UUID.randomUUID();
        templates.save(sampleTemplate(templateId, "tenant-A", "COPD"));

        Optional<PathwayTemplate> wrongTenant = templates.findByTemplateIdAndTenantId(templateId, "tenant-B");

        assertThat(wrongTenant).isEmpty();
    }

    @Test
    void pagesTemplatesByStatusDiseaseCodeAndKeyword() {
        templates.save(sampleTemplate("pt-a", "tenant-A", "COPD", "慢阻肺稳定期路径"));
        templates.save(sampleTemplate("pt-b", "tenant-A", "COPD", "慢阻肺急性期路径"));
        templates.save(sampleTemplate("pt-c", "tenant-A", "STROKE"));
        templates.save(sampleTemplate("pt-d", "tenant-B", "COPD"));

        long total = templates.countByFilter("tenant-A", "DRAFT", "COPD", null, null);
        List<PathwayTemplate> rows = templates.pageByFilter("tenant-A", "DRAFT", "COPD", null, null, 0, 10);
        long codeTotal = templates.countByFilter("tenant-A", "DRAFT", "COPD", "TPL.pt-a", null);
        List<PathwayTemplate> codeRows = templates.pageByFilter(
            "tenant-A", "DRAFT", "COPD", "TPL.pt-a", null, 0, 10);
        long keywordTotal = templates.countByFilter("tenant-A", "DRAFT", "COPD", null, "%稳定%");
        List<PathwayTemplate> keywordRows = templates.pageByFilter(
            "tenant-A", "DRAFT", "COPD", null, "%稳定%", 0, 10);

        assertThat(total).isEqualTo(2);
        assertThat(rows).extracting(PathwayTemplate::tenantId).containsOnly("tenant-A");
        assertThat(rows).extracting(PathwayTemplate::diseaseCode).containsOnly("COPD");
        assertThat(codeTotal).isEqualTo(1);
        assertThat(codeRows).extracting(PathwayTemplate::templateId).containsExactly("pt-a");
        assertThat(keywordTotal).isEqualTo(1);
        assertThat(keywordRows).extracting(PathwayTemplate::templateId).containsExactly("pt-a");
    }

    @Test
    void pagesEffectiveTemplatesWithoutMaterializingTenantAndPlatformSnapshots() {
        PathwayTemplate platformShadowed = templates.save(sampleTemplate(
            "pt-platform-shadowed", "t-1", "COPD",
            "平台慢阻肺路径", PathwayTemplateStatus.PUBLISHED));
        PathwayTemplate platformOnly = templates.save(sampleTemplate(
            "pt-platform-stroke", "t-1", "STROKE",
            "平台卒中路径", PathwayTemplateStatus.PUBLISHED));
        PathwayTemplate localOverride = templates.save(sampleTemplate(
            "pt-platform-shadowed", "tenant-A", "COPD",
            "本院慢阻肺路径", PathwayTemplateStatus.PUBLISHED));

        long total = templates.countEffectiveByFilter(
            "tenant-A", "t-1", null, "PUBLISHED", null, null, null);
        List<PathwayTemplate> rows = templates.pageEffectiveByFilter(
            "tenant-A", "t-1", null, "PUBLISHED", null, null, null, 0, 20);

        assertThat(total).isEqualTo(2L);
        assertThat(rows).extracting(PathwayTemplate::id)
            .containsExactlyInAnyOrder(localOverride.id(), platformOnly.id());
        assertThat(rows).extracting(PathwayTemplate::id)
            .doesNotContain(platformShadowed.id());
    }

    @Test
    void pagesPatientPathwaysByTenantPatientAndStatusWithoutCrossTenantLeakage() {
        patientPathways.save(samplePatientPathway(
            "pp-active-a", "tenant-A", "pt-1", "patient-1", PatientPathwayStatus.NODE_EXECUTING));
        patientPathways.save(samplePatientPathway(
            "pp-completed-a", "tenant-A", "pt-1", "patient-1", PatientPathwayStatus.COMPLETED));
        patientPathways.save(samplePatientPathway(
            "pp-active-b", "tenant-B", "pt-1", "patient-1", PatientPathwayStatus.NODE_EXECUTING));

        long filteredTotal = patientPathways.countByTenantIdAndFilters(
            "tenant-A", "patient-1", PatientPathwayStatus.NODE_EXECUTING.name());
        List<PatientPathway> filteredRows = patientPathways.pageByTenantIdAndFilters(
            "tenant-A", "patient-1", PatientPathwayStatus.NODE_EXECUTING.name(), 0, 20);

        assertThat(filteredTotal).isEqualTo(1L);
        assertThat(filteredRows).extracting(PatientPathway::patientPathwayId).containsExactly("pp-active-a");
        assertThat(patientPathways.countActiveByTenantId("tenant-A")).isEqualTo(1L);
        assertThat(patientPathways.countActiveByTenantIdAndPatientId("tenant-A", "patient-1")).isEqualTo(1L);
        assertThat(patientPathways.findActiveByTenantIdAndPatientIdOrderByEnteredAtDesc(
                "tenant-A", "patient-1", 0, 5))
            .extracting(PatientPathway::patientPathwayId)
            .containsExactly("pp-active-a");
    }

    private PathwayTemplate sampleTemplate(String templateId, String tenantId, String diseaseCode) {
        return sampleTemplate(templateId, tenantId, diseaseCode, "稳定期随访路径");
    }

    private PathwayTemplate sampleTemplate(
            String templateId,
            String tenantId,
            String diseaseCode,
            String name) {
        return sampleTemplate(templateId, tenantId, diseaseCode, name, PathwayTemplateStatus.DRAFT);
    }

    private PathwayTemplate sampleTemplate(
            String templateId,
            String tenantId,
            String diseaseCode,
            String name,
            PathwayTemplateStatus status) {
        Instant now = Instant.now();
        return new PathwayTemplate(
            null, templateId, tenantId, "TPL." + templateId, name,
            diseaseCode, 1, PathwayTemplateLevel.STANDARD, status,
            PathwayEntryMode.AUTO_SUGGEST, "ASSESS", "专病路径专家共识 2026", "用于路径 API 测试",
            "{\"diagnosis\":\"COPD\"}", "{\"completed\":true}",
            now, "tester", now, "tester", "trace-pathway");
    }

    private PathwayNode sampleNode(String nodeId, String tenantId, String templateId,
                                   String nodeCode, int sortOrder, boolean terminal) {
        Instant now = Instant.now();
        return new PathwayNode(
            null, nodeId, tenantId, templateId, nodeCode, nodeCode,
            terminal ? PathwayNodeType.FOLLOWUP : PathwayNodeType.ASSESSMENT,
            null, sortOrder, "医生", null, 1440, terminal, null,
            now, "tester", now, "tester", "trace-pathway");
    }

    private PathwayEdge sampleEdge(String edgeId, String tenantId, String templateId) {
        Instant now = Instant.now();
        return new PathwayEdge(
            null, edgeId, tenantId, templateId, "EDGE.ASSESS.FOLLOWUP",
            "ASSESS", "FOLLOWUP", PathwayEdgeType.DEFAULT, null, 10,
            now, "tester", now, "tester", "trace-pathway");
    }

    private PatientPathway samplePatientPathway(String patientPathwayId, String tenantId, String templateId) {
        return samplePatientPathway(
            patientPathwayId, tenantId, templateId, "patient-1", PatientPathwayStatus.NODE_EXECUTING);
    }

    private PatientPathway samplePatientPathway(String patientPathwayId, String tenantId, String templateId,
                                                String patientId, PatientPathwayStatus status) {
        Instant now = Instant.now();
        return new PatientPathway(
            null, patientPathwayId, tenantId, patientId, "enc-1", templateId,
            "release-H1", "av-pathway-v1", "ASSESS", status, now, null, null, null, null,
            now, "tester", now, "tester", "trace-pathway");
    }

    private PathwayVariance sampleVariance(String varianceId, String tenantId, String patientPathwayId) {
        Instant now = Instant.now();
        return new PathwayVariance(
            null, varianceId, tenantId, patientPathwayId, "ASSESS", VarianceType.CLINICAL,
            "CLINICAL_ESCALATION", "医生根据患者情况调整节点", "主管医师",
            VarianceResolutionDecision.REENTER, "继续随访", "FOLLOWUP",
            now, "tester", now, "tester", "trace-pathway");
    }

    private ClinicalClock sampleClock(String clockId, String tenantId, String patientPathwayId) {
        Instant now = Instant.now();
        return new ClinicalClock(
            null, clockId, tenantId, patientPathwayId, "ASSESS", "COPD.TIME_TO_FOLLOWUP",
            now, now.plusSeconds(86_400), null, ClinicalClockStatus.RUNNING,
            null, null, null, null, null, ClinicalClockEscalationLevel.NONE, null,
            now, "tester", now, "tester", "trace-pathway");
    }

    private SpecialtyMetricBinding sampleBinding(String bindingId, String tenantId, String templateId) {
        Instant now = Instant.now();
        return new SpecialtyMetricBinding(
            null, bindingId, tenantId, templateId, "ASSESS",
            "COPD.TIME_TO_FOLLOWUP", true, now, "tester", now, "tester", "trace-pathway");
    }
}
