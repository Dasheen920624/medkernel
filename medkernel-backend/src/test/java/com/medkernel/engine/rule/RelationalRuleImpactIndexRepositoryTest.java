package com.medkernel.engine.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.integration.domain.IntegrationAdapter;
import com.medkernel.engine.integration.repository.IntegrationAdapterRepository;
import com.medkernel.engine.pathway.PatientPathway;
import com.medkernel.engine.pathway.PatientPathwayRepository;
import com.medkernel.engine.pathway.PatientPathwayStatus;
import com.medkernel.engine.pathway.PathwayEdgeRepository;
import com.medkernel.engine.pathway.PathwayEntryMode;
import com.medkernel.engine.pathway.PathwayNode;
import com.medkernel.engine.pathway.PathwayNodeRepository;
import com.medkernel.engine.pathway.PathwayNodeType;
import com.medkernel.engine.pathway.PathwayTemplate;
import com.medkernel.engine.pathway.PathwayTemplateLevel;
import com.medkernel.engine.pathway.PathwayTemplateRepository;
import com.medkernel.engine.pathway.PathwayTemplateStatus;
import com.medkernel.engine.pkg.KnowledgePackage;
import com.medkernel.engine.pkg.KnowledgePackageRepository;
import com.medkernel.engine.pkg.KnowledgePackageStatus;
import com.medkernel.engine.pkg.PackageItem;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.engine.pkg.PackageItemRepository;
import com.medkernel.engine.pkg.ReleasePlan;
import com.medkernel.engine.pkg.ReleasePlanRepository;
import com.medkernel.engine.pkg.ReleasePlanStatus;
import com.medkernel.engine.pkg.ReleaseScopeType;
import com.medkernel.engine.pkg.ReleaseStrategy;
import com.medkernel.engine.pkg.SyncLog;
import com.medkernel.engine.pkg.SyncLogRepository;
import com.medkernel.engine.pkg.SyncLogStatus;
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
    "spring.datasource.url=jdbc:h2:mem:rule-impact-repo-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class RelationalRuleImpactIndexRepositoryTest {

    @Autowired PathwayTemplateRepository templates;
    @Autowired PathwayNodeRepository nodes;
    @Autowired PathwayEdgeRepository edges;
    @Autowired PatientPathwayRepository patientPathways;
    @Autowired KnowledgePackageRepository packages;
    @Autowired PackageItemRepository packageItems;
    @Autowired ReleasePlanRepository releasePlans;
    @Autowired SyncLogRepository syncLogs;
    @Autowired IntegrationAdapterRepository integrationAdapters;

    @AfterEach
    void wipe() {
        syncLogs.deleteAll();
        integrationAdapters.deleteAll();
        releasePlans.deleteAll();
        packageItems.deleteAll();
        packages.deleteAll();
        patientPathways.deleteAll();
        edges.deleteAll();
        nodes.deleteAll();
        templates.deleteAll();
    }

    @Test
    void queriesRealRuleReferencesAcrossPathwayRuntimeAndSyncTables() {
        String suffix = UUID.randomUUID().toString();
        String ruleId = "rule-" + suffix;
        String versionId = "rv-" + suffix;
        String templateId = "pt-" + suffix;
        String packageId = "pkg-" + suffix;
        String planId = "plan-" + suffix;
        String adapterId = "target-" + suffix;
        RuleDefinition rule = rule(ruleId, versionId);
        RuleVersion version = version(ruleId, versionId);

        templates.save(template(templateId));
        nodes.save(nodeReferencingRule(templateId, ruleId, versionId));
        patientPathways.save(patientPathway("ppath-active-" + suffix, templateId, PatientPathwayStatus.ENTERED));
        patientPathways.save(patientPathway("ppath-done-" + suffix, templateId, PatientPathwayStatus.COMPLETED));
        packages.save(knowledgePackage(packageId));
        packageItems.save(packageItem(packageId, ruleId));
        releasePlans.save(releasePlan(packageId, planId));
        integrationAdapters.save(integrationAdapter(adapterId));
        syncLogs.save(syncLog(planId, adapterId));

        RelationalRuleImpactIndex index = new RelationalRuleImpactIndex(
            templates, nodes, edges, patientPathways, packageItems,
            releasePlans, syncLogs, integrationAdapters, new ObjectMapper());

        RuleImpactIndexSnapshot snapshot = index.analyze("tenant-A", rule, version);

        assertThat(snapshot.unavailableScopes()).isEmpty();
        assertThat(snapshot.affectedPathways()).extracting(RuleImpactObject::objectId)
            .containsExactly(templateId);
        assertThat(snapshot.inPathPatients()).extracting(RuleImpactObject::objectId)
            .containsExactly("ppath-active-" + suffix);
        assertThat(snapshot.integrationAdapters()).extracting(RuleImpactObject::objectId)
            .containsExactly(adapterId);
    }

    private RuleDefinition rule(String ruleId, String versionId) {
        Instant now = Instant.now();
        return new RuleDefinition(
            null, ruleId, "tenant-A", "RULE.IMPACT.TEST", "规则影响索引测试",
            RuleType.ORDER, RuleAuthoringMode.DSL, RuleRiskLevel.HIGH,
            100, null, 0, RuleDefinitionStatus.DRAFT, versionId, "pkg-version", "dept-1",
            now, "tester", now, "tester", "trace-rule");
    }

    private RuleVersion version(String ruleId, String versionId) {
        Instant now = Instant.now();
        return new RuleVersion(
            null, versionId, "tenant-A", ruleId, 1, "测试来源",
            "索引验证", "{\"trigger\":\"order-sign\",\"when\":{},\"then\":[],\"explain\":{}}",
            "{}", RuleVersionStatus.DRAFT, null, null, null,
            now, "tester", now, "tester", "trace-rule");
    }

    private PathwayTemplate template(String templateId) {
        Instant now = Instant.now();
        return new PathwayTemplate(
            null, templateId, "tenant-A", "spkg-test", "TPL.IMPACT.TEST", "影响索引路径",
            "D-SCOPE", 1, PathwayTemplateLevel.HOSPITAL, PathwayTemplateStatus.PUBLISHED,
            PathwayEntryMode.AUTO_SUGGEST, "ASSESS", "测试路径来源", "用于规则影响索引验证", "{}", "{}",
            now, "tester", now, "tester", "trace-path");
    }

    private PathwayNode nodeReferencingRule(String templateId, String ruleId, String versionId) {
        Instant now = Instant.now();
        return new PathwayNode(
            null, "node-" + templateId, "tenant-A", templateId, "ASSESS", "影响评估",
            PathwayNodeType.ASSESSMENT, null, 10, "knowledge-governor", null, 120, false,
            "{\"ruleRefs\":[{\"ruleId\":\"" + ruleId + "\",\"versionId\":\"" + versionId + "\"}]}",
            now, "tester", now, "tester", "trace-path");
    }

    private PatientPathway patientPathway(String patientPathwayId, String templateId, PatientPathwayStatus status) {
        Instant now = Instant.now();
        return new PatientPathway(
            null, patientPathwayId, "tenant-A", "patient-test", "enc-test",
            templateId, "ASSESS", status, now, null, null, null, null,
            now, "tester", now, "tester", "trace-runtime");
    }

    private KnowledgePackage knowledgePackage(String packageId) {
        Instant now = Instant.now();
        return new KnowledgePackage(
            null, packageId, "tenant-A", "PKG.IMPACT.TEST", "1.0.0",
            "影响索引配置包", "用于规则影响索引验证", KnowledgePackageStatus.ACTIVE,
            now, "tester", now, "tester", "trace-pkg");
    }

    private PackageItem packageItem(String packageId, String ruleId) {
        Instant now = Instant.now();
        return new PackageItem(
            null, "item-" + packageId, "tenant-A", packageId,
            VersionedAssetType.RULE, ruleId, "1",
            now, "tester", now, "tester", "trace-pkg");
    }

    private ReleasePlan releasePlan(String packageId, String planId) {
        Instant now = Instant.now();
        return new ReleasePlan(
            null, planId, "tenant-A", packageId, "hospital-test",
            ReleaseStrategy.FULL, ReleaseScopeType.ALL, null, ReleasePlanStatus.SUCCESS,
            now, "tester", now, "tester", "trace-plan");
    }

    private IntegrationAdapter integrationAdapter(String adapterId) {
        Instant now = Instant.now();
        return new IntegrationAdapter(
            null, adapterId, "tenant-A", "院内规则库",
            "REST", "ACTIVE", "config-ref", "HEALTHY", 5L, now,
            now, "tester", now, "tester");
    }

    private SyncLog syncLog(String planId, String adapterId) {
        Instant now = Instant.now();
        return new SyncLog(
            null, "log-" + planId, "tenant-A", planId, adapterId,
            SyncLogStatus.SUCCESS, null, null, 0, "sha256:sync",
            now, "tester", now, "tester", "trace-sync");
    }
}
