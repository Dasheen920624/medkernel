package com.medkernel.engine.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.pathway.PatientPathway;
import com.medkernel.engine.pathway.PatientPathwayRepository;
import com.medkernel.engine.pathway.PatientPathwayStatus;
import com.medkernel.engine.pathway.PathwayEdgeRepository;
import com.medkernel.engine.pathway.PathwayNode;
import com.medkernel.engine.pathway.PathwayNodeRepository;
import com.medkernel.engine.pathway.PathwayNodeType;
import com.medkernel.engine.pathway.PathwayTemplate;
import com.medkernel.engine.pathway.PathwayTemplateLevel;
import com.medkernel.engine.pathway.PathwayTemplateRepository;
import com.medkernel.engine.pathway.PathwayTemplateStatus;
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
import com.medkernel.engine.pkg.SyncTarget;
import com.medkernel.engine.pkg.SyncTargetRepository;
import com.medkernel.engine.pkg.SyncTargetStatus;
import com.medkernel.engine.pkg.SyncTargetType;
import org.junit.jupiter.api.Test;

class RelationalRuleImpactIndexTest {

    private final PathwayTemplateRepository templates = mock(PathwayTemplateRepository.class);
    private final PathwayNodeRepository nodes = mock(PathwayNodeRepository.class);
    private final PathwayEdgeRepository edges = mock(PathwayEdgeRepository.class);
    private final PatientPathwayRepository patientPathways = mock(PatientPathwayRepository.class);
    private final PackageItemRepository packageItems = mock(PackageItemRepository.class);
    private final ReleasePlanRepository releasePlans = mock(ReleasePlanRepository.class);
    private final SyncLogRepository syncLogs = mock(SyncLogRepository.class);
    private final SyncTargetRepository syncTargets = mock(SyncTargetRepository.class);

    @Test
    void analyzesRealPathwayRuntimeAndSyncTargetReferencesForRule() {
        RuleDefinition rule = rule();
        RuleVersion version = version();
        RelationalRuleImpactIndex index = new RelationalRuleImpactIndex(
            templates, nodes, edges, patientPathways,
            packageItems, releasePlans, syncLogs, syncTargets,
            new ObjectMapper());

        when(nodes.findByTenantIdAndRuleReference("tenant-A", "rule-1", "RULE.ANTICOAG", "version-1"))
            .thenReturn(List.of(pathwayNodeReferencingRule()));
        when(edges.findByTenantIdAndRuleReference("tenant-A", "rule-1", "RULE.ANTICOAG", "version-1"))
            .thenReturn(List.of());
        when(templates.findByTemplateIdAndTenantId("pt-1", "tenant-A"))
            .thenReturn(Optional.of(pathwayTemplate()));
        when(patientPathways.findByTemplateIdAndTenantIdOrderByEnteredAtDesc("pt-1", "tenant-A"))
            .thenReturn(List.of(
                patientPathway("ppath-active", PatientPathwayStatus.NODE_EXECUTING),
                patientPathway("ppath-done", PatientPathwayStatus.COMPLETED)));

        when(packageItems.findByTenantIdAndAssetTypeAndAssetId("tenant-A", VersionedAssetType.RULE, "rule-1"))
            .thenReturn(List.of(packageItem()));
        when(releasePlans.findByTenantIdAndPackageIdOrderByCreatedAtDesc("tenant-A", "pkg-1"))
            .thenReturn(List.of(releasePlan()));
        when(syncLogs.findByTenantIdAndPlanId("tenant-A", "plan-1"))
            .thenReturn(List.of(syncLog()));
        when(syncTargets.findByTargetIdAndTenantId("target-clinical", "tenant-A"))
            .thenReturn(Optional.of(syncTarget()));

        RuleImpactIndexSnapshot snapshot = index.analyze("tenant-A", rule, version);

        assertThat(snapshot.unavailableScopes()).isEmpty();
        assertThat(snapshot.affectedPathways()).singleElement().satisfies(pathway -> {
            assertThat(pathway.objectType()).isEqualTo("PATHWAY_TEMPLATE");
            assertThat(pathway.objectId()).isEqualTo("pt-1");
            assertThat(pathway.displayName()).isEqualTo("慢阻肺抗凝路径");
            assertThat(pathway.impactReason()).contains("路径模板节点引用规则 RULE.ANTICOAG");
        });
        assertThat(snapshot.inPathPatients()).singleElement().satisfies(patient -> {
            assertThat(patient.objectType()).isEqualTo("PATIENT_PATHWAY");
            assertThat(patient.objectId()).isEqualTo("ppath-active");
            assertThat(patient.displayName()).isEqualTo("患者 patient-1 / 就诊 enc-1");
            assertThat(patient.impactReason()).contains("当前节点 ASSESS");
        });
        assertThat(snapshot.syncTargets()).singleElement().satisfies(target -> {
            assertThat(target.objectType()).isEqualTo("SYNC_TARGET");
            assertThat(target.objectId()).isEqualTo("target-clinical");
            assertThat(target.displayName()).isEqualTo("院内规则库");
            assertThat(target.impactReason()).contains("配置包 pkg-1", "同步状态 SUCCESS");
        });
    }

    private RuleDefinition rule() {
        Instant now = Instant.now();
        return new RuleDefinition(
            1L, "rule-1", "tenant-A", "RULE.ANTICOAG", "抗凝风险提示",
            RuleType.ORDER, RuleAuthoringMode.DSL, RuleRiskLevel.HIGH,
            RuleDefinitionStatus.DRAFT, "version-1", "rpv-1", "dept-1",
            now, "tester", now, "tester", "trace-rule");
    }

    private RuleVersion version() {
        Instant now = Instant.now();
        return new RuleVersion(
            1L, "version-1", "tenant-A", "rule-1", 1,
            "院内抗凝用药管理规范 2026", "初始版本",
            "{\"trigger\":\"ORDER_SIGN\",\"when\":{},\"then\":[],\"explain\":{}}",
            "{}", RuleVersionStatus.DRAFT, null, null, null,
            now, "tester", now, "tester", "trace-rule");
    }

    private PathwayNode pathwayNodeReferencingRule() {
        Instant now = Instant.now();
        return new PathwayNode(
            1L, "node-1", "tenant-A", "pt-1", "ASSESS", "抗凝风险评估",
            PathwayNodeType.ASSESSMENT, 10, "specialist", null, 240, false,
            "{\"ruleRefs\":[{\"ruleId\":\"rule-1\",\"ruleCode\":\"RULE.ANTICOAG\",\"versionId\":\"version-1\"}]}",
            now, "tester", now, "tester", "trace-path");
    }

    private PathwayTemplate pathwayTemplate() {
        Instant now = Instant.now();
        return new PathwayTemplate(
            1L, "pt-1", "tenant-A", "spkg-1", "TPL.COPD", "慢阻肺抗凝路径",
            "J44", 2, PathwayTemplateLevel.HOSPITAL, PathwayTemplateStatus.PUBLISHED,
            "ASSESS", "院内路径 2026", "含抗凝风险评估", "{}", "{}",
            now, "tester", now, "tester", "trace-path");
    }

    private PatientPathway patientPathway(String id, PatientPathwayStatus status) {
        Instant now = Instant.now();
        return new PatientPathway(
            1L, id, "tenant-A", "patient-1", "enc-1", "pt-1",
            "ASSESS", status, now, null, null, null, "evt-1",
            now, "tester", now, "tester", "trace-runtime");
    }

    private PackageItem packageItem() {
        Instant now = Instant.now();
        return new PackageItem(
            1L, "item-1", "tenant-A", "pkg-1", VersionedAssetType.RULE,
            "rule-1", "1", now, "tester", now, "tester", "trace-pkg");
    }

    private ReleasePlan releasePlan() {
        Instant now = Instant.now();
        return new ReleasePlan(
            1L, "plan-1", "tenant-A", "pkg-1", "hospital-1",
            ReleaseStrategy.FULL, ReleaseScopeType.ALL, null, ReleasePlanStatus.SUCCESS,
            now, "tester", now, "tester", "trace-plan");
    }

    private SyncLog syncLog() {
        Instant now = Instant.now();
        return new SyncLog(
            1L, "log-1", "tenant-A", "plan-1", "target-clinical",
            SyncLogStatus.SUCCESS, null, null, 0, "sha256:sync",
            now, "tester", now, "tester", "trace-sync");
    }

    private SyncTarget syncTarget() {
        Instant now = Instant.now();
        return new SyncTarget(
            1L, "target-clinical", "tenant-A", "院内规则库",
            SyncTargetType.CLINICAL_DB, "config-ref", SyncTargetStatus.ACTIVE,
            now, "tester", now, "tester", "trace-target");
    }
}
