package com.medkernel.engine.pkg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.medkernel.engine.evaluation.EvaluationIndicator;
import com.medkernel.engine.evaluation.EvaluationIndicatorRepository;
import com.medkernel.engine.evaluation.EvaluationIndicatorStatus;
import com.medkernel.engine.evaluation.EvaluationSubjectType;
import com.medkernel.engine.pathway.PathwayTemplate;
import com.medkernel.engine.pathway.PathwayTemplateLevel;
import com.medkernel.engine.pathway.PathwayTemplateRepository;
import com.medkernel.engine.pathway.PathwayTemplateStatus;
import com.medkernel.engine.rule.RuleAuthoringMode;
import com.medkernel.engine.rule.RuleDefinition;
import com.medkernel.engine.rule.RuleDefinitionRepository;
import com.medkernel.engine.rule.RuleDefinitionStatus;
import com.medkernel.engine.rule.RuleRiskLevel;
import com.medkernel.engine.rule.RuleType;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditEventPublisher;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class PackageEngineServiceTest {

    private KnowledgePackageRepository packageRepository;
    private PackageItemRepository itemRepository;
    private ReleasePlanRepository planRepository;
    private SyncTargetRepository targetRepository;
    private SyncLogRepository logRepository;
    private TransactionTemplate transactionTemplate;

    private RuleDefinitionRepository ruleRepository;
    private PathwayTemplateRepository pathwayRepository;
    private EvaluationIndicatorRepository evaluationRepository;

    private PackageSyncPort syncPort;
    private AuditEventPublisher auditPublisher;

    private PackageEngineService service;

    @BeforeEach
    void setUp() {
        packageRepository = mock(KnowledgePackageRepository.class);
        itemRepository = mock(PackageItemRepository.class);
        planRepository = mock(ReleasePlanRepository.class);
        targetRepository = mock(SyncTargetRepository.class);
        logRepository = mock(SyncLogRepository.class);

        ruleRepository = mock(RuleDefinitionRepository.class);
        pathwayRepository = mock(PathwayTemplateRepository.class);
        evaluationRepository = mock(EvaluationIndicatorRepository.class);

        syncPort = mock(PackageSyncPort.class);
        auditPublisher = mock(AuditEventPublisher.class);
        transactionTemplate = mock(TransactionTemplate.class);

        // 模拟 TransactionTemplate 编程式事务在测试下的行为
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        doAnswer(inv -> {
            Consumer<TransactionStatus> consumer = inv.getArgument(0);
            consumer.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        service = new PackageEngineService(
            packageRepository, itemRepository, planRepository, targetRepository, logRepository,
            ruleRepository, pathwayRepository, evaluationRepository, syncPort, auditPublisher,
            transactionTemplate
        );

        when(packageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(itemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(targetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RequestContext.restore(new RequestContext.Snapshot(
            "trace-pkg", OrgScope.tenant("tenant-A"), "tester"));
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void createPackageSucceedsAndPersists() {
        when(packageRepository.findByTenantIdAndPackageCodeAndPackageVersion("tenant-A", "PKG.COPD", "1.0.0"))
            .thenReturn(Optional.empty());

        PackageResponse response = service.createPackage(new PackageCreateRequest(
            "PKG.COPD", "1.0.0", "慢阻肺专病包", "资产说明"));

        assertThat(response.packageId()).isNotNull();
        assertThat(response.status()).isEqualTo(KnowledgePackageStatus.DRAFT);
        
        ArgumentCaptor<KnowledgePackage> packCap = ArgumentCaptor.forClass(KnowledgePackage.class);
        verify(packageRepository).save(packCap.capture());
        assertThat(packCap.getValue().tenantId()).isEqualTo("tenant-A");
        verify(auditPublisher).publish(eq(AuditAction.CREATE), eq("knowledge_package"), any(), any());
    }

    @Test
    void createPackageFailsWhenVersionDuplicate() {
        KnowledgePackage existing = new KnowledgePackage(
            1L, "pkg-1", "tenant-A", "PKG.COPD", "1.0.0", "已有包", null,
            KnowledgePackageStatus.DRAFT, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(packageRepository.findByTenantIdAndPackageCodeAndPackageVersion("tenant-A", "PKG.COPD", "1.0.0"))
            .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createPackage(new PackageCreateRequest(
                "PKG.COPD", "1.0.0", "慢阻肺专病包", "资产说明")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PACKAGE_004);
    }

    @Test
    void addPackageItemFailsWhenAssetNotPublished() {
        KnowledgePackage pack = new KnowledgePackage(
            1L, "pkg-1", "tenant-A", "PKG.COPD", "1.0.0", "包草稿", null,
            KnowledgePackageStatus.DRAFT, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(packageRepository.findByPackageIdAndTenantId("pkg-1", "tenant-A"))
            .thenReturn(Optional.of(pack));

        // 模拟一个草稿状态的规则，未审核通过不允许入包
        RuleDefinition rule = mock(RuleDefinition.class);
        when(rule.status()).thenReturn(RuleDefinitionStatus.DRAFT);
        when(ruleRepository.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(rule));

        assertThatThrownBy(() -> service.addPackageItem("pkg-1", new PackageItemRequest(
                PackageItemAssetType.RULE, "rule-1", "1")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PACKAGE_002);
    }

    @Test
    void addPackageItemSucceedsWhenAssetPublished() {
        KnowledgePackage pack = new KnowledgePackage(
            1L, "pkg-1", "tenant-A", "PKG.COPD", "1.0.0", "包草稿", null,
            KnowledgePackageStatus.DRAFT, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(packageRepository.findByPackageIdAndTenantId("pkg-1", "tenant-A"))
            .thenReturn(Optional.of(pack));

        RuleDefinition rule = mock(RuleDefinition.class);
        when(rule.status()).thenReturn(RuleDefinitionStatus.PUBLISHED);
        when(ruleRepository.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(rule));

        when(itemRepository.findByTenantIdAndPackageIdAndAssetTypeAndAssetId("tenant-A", "pkg-1", PackageItemAssetType.RULE, "rule-1"))
            .thenReturn(Optional.empty());

        PackageItemResponse response = service.addPackageItem("pkg-1", new PackageItemRequest(
            PackageItemAssetType.RULE, "rule-1", "1"));

        assertThat(response.itemId()).isNotNull();
        assertThat(response.assetId()).isEqualTo("rule-1");
        verify(auditPublisher).publish(eq(AuditAction.UPDATE), eq("knowledge_package"), eq("pkg-1"), any());
    }

    @Test
    void calculateDiffComputesCorrectStats() {
        KnowledgePackage targetPack = new KnowledgePackage(
            1L, "pkg-target", "tenant-A", "PKG.COPD", "2.0.0", "新包", null,
            KnowledgePackageStatus.DRAFT, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        KnowledgePackage basePack = new KnowledgePackage(
            2L, "pkg-base", "tenant-A", "PKG.COPD", "1.0.0", "老包", null,
            KnowledgePackageStatus.ACTIVE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );

        when(packageRepository.findByPackageIdAndTenantId("pkg-target", "tenant-A")).thenReturn(Optional.of(targetPack));
        when(packageRepository.findByPackageIdAndTenantId("pkg-base", "tenant-A")).thenReturn(Optional.of(basePack));

        // 模拟老包资产：rule-1 (v1), pathway-1 (v1)
        List<PackageItem> baseItems = List.of(
            new PackageItem(1L, "i-1", "tenant-A", "pkg-base", PackageItemAssetType.RULE, "rule-1", "1", Instant.now(), "tester", Instant.now(), "tester", "trace"),
            new PackageItem(2L, "i-2", "tenant-A", "pkg-base", PackageItemAssetType.PATHWAY, "pathway-1", "1", Instant.now(), "tester", Instant.now(), "tester", "trace")
        );

        // 模拟新包资产：rule-1 (v2 - 更新), pathway-1 (v1 - 未变), evaluation-1 (v1 - 新增)
        List<PackageItem> targetItems = List.of(
            new PackageItem(3L, "i-3", "tenant-A", "pkg-target", PackageItemAssetType.RULE, "rule-1", "2", Instant.now(), "tester", Instant.now(), "tester", "trace"),
            new PackageItem(4L, "i-4", "tenant-A", "pkg-target", PackageItemAssetType.PATHWAY, "pathway-1", "1", Instant.now(), "tester", Instant.now(), "tester", "trace"),
            new PackageItem(5L, "i-5", "tenant-A", "pkg-target", PackageItemAssetType.EVALUATION, "eval-1", "1", Instant.now(), "tester", Instant.now(), "tester", "trace")
        );

        when(itemRepository.findByTenantIdAndPackageId("tenant-A", "pkg-base")).thenReturn(baseItems);
        when(itemRepository.findByTenantIdAndPackageId("tenant-A", "pkg-target")).thenReturn(targetItems);
        when(ruleRepository.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(publishedRule("rule-1", null)));
        when(pathwayRepository.findByTemplateIdAndTenantId("pathway-1", "tenant-A"))
            .thenReturn(Optional.of(publishedPathway("pathway-1")));
        when(evaluationRepository.findByIndicatorIdAndTenantId("eval-1", "tenant-A"))
            .thenReturn(Optional.of(publishedIndicator("eval-1", null)));

        PackageDiffResponse response = service.calculateDiff("pkg-target", "pkg-base");

        assertThat(response.baseVersion()).isEqualTo("1.0.0");
        assertThat(response.targetVersion()).isEqualTo("2.0.0");
        assertThat(response.addedCount()).isEqualTo(1); // eval-1
        assertThat(response.updatedCount()).isEqualTo(1); // rule-1
        assertThat(response.removedCount()).isEqualTo(0); // pathway-1还在
    }

    @Test
    void calculateDiffUsesOnlyRealAssetDepartments() {
        KnowledgePackage targetPack = packageVersion("pkg-target", "2.0.0", KnowledgePackageStatus.DRAFT);

        when(packageRepository.findByPackageIdAndTenantId("pkg-target", "tenant-A")).thenReturn(Optional.of(targetPack));
        when(itemRepository.findByTenantIdAndPackageId("tenant-A", "pkg-target")).thenReturn(List.of(
            packageItem(1L, "pkg-target", PackageItemAssetType.RULE, "rule-real", "2"),
            packageItem(2L, "pkg-target", PackageItemAssetType.PATHWAY, "pathway-no-dept", "1"),
            packageItem(3L, "pkg-target", PackageItemAssetType.EVALUATION, "eval-real", "1"),
            packageItem(4L, "pkg-target", PackageItemAssetType.TERMINOLOGY, "term-1", "1")
        ));
        when(ruleRepository.findByRuleIdAndTenantId("rule-real", "tenant-A"))
            .thenReturn(Optional.of(publishedRule("rule-real", "dept-rule")));
        when(pathwayRepository.findByTemplateIdAndTenantId("pathway-no-dept", "tenant-A"))
            .thenReturn(Optional.of(publishedPathway("pathway-no-dept")));
        when(evaluationRepository.findByIndicatorIdAndTenantId("eval-real", "tenant-A"))
            .thenReturn(Optional.of(publishedIndicator("eval-real", "dept-eval")));

        PackageDiffResponse response = service.calculateDiff("pkg-target", null);

        assertThat(response.affectedDepartments())
            .containsExactlyInAnyOrder("dept-rule", "dept-eval")
            .doesNotContain("dept-default");
    }

    @Test
    void calculateDiffIncludesRemovedAssetImpactAndChangedRows() {
        KnowledgePackage targetPack = packageVersion("pkg-target", "2.0.0", KnowledgePackageStatus.DRAFT);
        KnowledgePackage basePack = packageVersion("pkg-base", "1.0.0", KnowledgePackageStatus.ACTIVE);

        when(packageRepository.findByPackageIdAndTenantId("pkg-target", "tenant-A")).thenReturn(Optional.of(targetPack));
        when(packageRepository.findByPackageIdAndTenantId("pkg-base", "tenant-A")).thenReturn(Optional.of(basePack));
        when(itemRepository.findByTenantIdAndPackageId("tenant-A", "pkg-base")).thenReturn(List.of(
            packageItem(1L, "pkg-base", PackageItemAssetType.RULE, "rule-removed", "1"),
            packageItem(2L, "pkg-base", PackageItemAssetType.EVALUATION, "eval-updated", "1")
        ));
        when(itemRepository.findByTenantIdAndPackageId("tenant-A", "pkg-target")).thenReturn(List.of(
            packageItem(3L, "pkg-target", PackageItemAssetType.EVALUATION, "eval-updated", "2"),
            packageItem(4L, "pkg-target", PackageItemAssetType.PATHWAY, "pathway-added", "1")
        ));
        when(ruleRepository.findByRuleIdAndTenantId("rule-removed", "tenant-A"))
            .thenReturn(Optional.of(publishedRule("rule-removed", "dept-removed")));
        when(evaluationRepository.findByIndicatorIdAndTenantId("eval-updated", "tenant-A"))
            .thenReturn(Optional.of(publishedIndicator("eval-updated", "dept-eval")));
        when(pathwayRepository.findByTemplateIdAndTenantId("pathway-added", "tenant-A"))
            .thenReturn(Optional.of(publishedPathway("pathway-added")));

        PackageDiffResponse response = service.calculateDiff("pkg-target", "pkg-base");

        assertThat(response.addedCount()).isEqualTo(1);
        assertThat(response.updatedCount()).isEqualTo(1);
        assertThat(response.removedCount()).isEqualTo(1);
        assertThat(response.affectedDepartments())
            .containsExactlyInAnyOrder("dept-removed", "dept-eval");
        assertThat(response.changes()).anySatisfy(change -> {
            assertThat(change.changeType()).isEqualTo(PackageDiffChangeType.REMOVED);
            assertThat(change.assetType()).isEqualTo(PackageItemAssetType.RULE);
            assertThat(change.assetId()).isEqualTo("rule-removed");
            assertThat(change.baseVersion()).isEqualTo("1");
            assertThat(change.targetVersion()).isNull();
        });
        assertThat(response.changes()).anySatisfy(change -> {
            assertThat(change.changeType()).isEqualTo(PackageDiffChangeType.UPDATED);
            assertThat(change.assetType()).isEqualTo(PackageItemAssetType.EVALUATION);
            assertThat(change.assetId()).isEqualTo("eval-updated");
            assertThat(change.baseVersion()).isEqualTo("1");
            assertThat(change.targetVersion()).isEqualTo("2");
        });
        assertThat(response.changes()).anySatisfy(change -> {
            assertThat(change.changeType()).isEqualTo(PackageDiffChangeType.ADDED);
            assertThat(change.assetType()).isEqualTo(PackageItemAssetType.PATHWAY);
            assertThat(change.assetId()).isEqualTo("pathway-added");
            assertThat(change.baseVersion()).isNull();
            assertThat(change.targetVersion()).isEqualTo("1");
        });
    }

    @Test
    void exportDiffEvidenceReturnsNdjsonFromRealDiffAndPublishesAudit() {
        KnowledgePackage targetPack = packageVersion("pkg-target", "2.0.0", KnowledgePackageStatus.DRAFT);

        when(packageRepository.findByPackageIdAndTenantId("pkg-target", "tenant-A")).thenReturn(Optional.of(targetPack));
        when(itemRepository.findByTenantIdAndPackageId("tenant-A", "pkg-target")).thenReturn(List.of(
            packageItem(1L, "pkg-target", PackageItemAssetType.RULE, "rule-added", "2")
        ));
        when(ruleRepository.findByRuleIdAndTenantId("rule-added", "tenant-A"))
            .thenReturn(Optional.of(publishedRule("rule-added", "dept-rule")));

        String ndjson = service.exportDiffEvidence("pkg-target", null);

        assertThat(ndjson)
            .contains("\"event\":\"PACKAGE_DIFF_SUMMARY\"")
            .contains("\"packageId\":\"pkg-target\"")
            .contains("\"targetVersion\":\"2.0.0\"")
            .contains("\"addedCount\":1")
            .contains("\"event\":\"PACKAGE_DIFF_AFFECTED_DEPARTMENT\"")
            .contains("\"departmentId\":\"dept-rule\"")
            .contains("\"event\":\"PACKAGE_DIFF_CHANGE\"")
            .contains("\"changeType\":\"ADDED\"")
            .contains("\"assetId\":\"rule-added\"");
        verify(auditPublisher).publish(eq(AuditAction.EXPORT), eq("knowledge_package"), eq("pkg-target"), any());
    }

    @Test
    void calculateDiffDoesNotForgeDepartmentWhenAssetLookupFails() {
        KnowledgePackage targetPack = packageVersion("pkg-target", "2.0.0", KnowledgePackageStatus.DRAFT);

        when(packageRepository.findByPackageIdAndTenantId("pkg-target", "tenant-A")).thenReturn(Optional.of(targetPack));
        when(itemRepository.findByTenantIdAndPackageId("tenant-A", "pkg-target")).thenReturn(List.of(
            packageItem(1L, "pkg-target", PackageItemAssetType.RULE, "rule-broken", "2")
        ));
        when(ruleRepository.findByRuleIdAndTenantId("rule-broken", "tenant-A"))
            .thenThrow(new IllegalStateException("规则资产查询失败"));

        assertThatThrownBy(() -> service.calculateDiff("pkg-target", null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("规则资产查询失败");
    }

    @Test
    void syncPackageExecutesSyncOnAllChannelsAndActivatesPackage() throws Exception {
        KnowledgePackage pack = new KnowledgePackage(
            1L, "pkg-1", "tenant-A", "PKG.COPD", "1.0.0", "包草稿", null,
            KnowledgePackageStatus.PUBLISHED, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(packageRepository.findByPackageIdAndTenantId("pkg-1", "tenant-A"))
            .thenReturn(Optional.of(pack));

        SyncTarget target = new SyncTarget(
            1L, "target-1", "tenant-A", "投影目标", SyncTargetType.DIFY, "config",
            SyncTargetStatus.ACTIVE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(targetRepository.findByTargetIdAndTenantId("target-1", "tenant-A"))
            .thenReturn(Optional.of(target));

        when(syncPort.sync(eq("tenant-A"), any(ReleasePlan.class), eq(target)))
            .thenReturn("EVIDENCE-DIFY-001");

        PackageSyncResponse response = service.syncPackage("pkg-1", new PackageSyncRequest(
            "org-1", ReleaseStrategy.FULL, ReleaseScopeType.ALL, null, List.of("target-1")
        ));

        assertThat(response.status()).isEqualTo(ReleasePlanStatus.SUCCESS);
        assertThat(response.logs()).hasSize(1);
        assertThat(response.logs().get(0).syncEvidence()).isEqualTo("EVIDENCE-DIFY-001");

        ArgumentCaptor<KnowledgePackage> packCap = ArgumentCaptor.forClass(KnowledgePackage.class);
        verify(packageRepository).save(packCap.capture());
        // 全量成功后，原包状态应该原子更新为 ACTIVE
        assertThat(packCap.getValue().status()).isEqualTo(KnowledgePackageStatus.ACTIVE);
        verify(auditPublisher).publish(eq(AuditAction.PUBLISH), eq("knowledge_package"), eq("pkg-1"), any());
    }

    @Test
    void syncPackageMarksNotSyncedWhenDefaultPortHasNoRealChannel() throws Exception {
        KnowledgePackage pack = new KnowledgePackage(
            1L, "pkg-1", "tenant-A", "PKG.COPD", "1.0.0", "包草稿", null,
            KnowledgePackageStatus.PUBLISHED, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(packageRepository.findByPackageIdAndTenantId("pkg-1", "tenant-A"))
            .thenReturn(Optional.of(pack));

        SyncTarget target = new SyncTarget(
            1L, "target-1", "tenant-A", "图投影", SyncTargetType.GRAPH_DB, null,
            SyncTargetStatus.ACTIVE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(targetRepository.findByTargetIdAndTenantId("target-1", "tenant-A"))
            .thenReturn(Optional.of(target));

        when(syncPort.sync(eq("tenant-A"), any(ReleasePlan.class), eq(target)))
            .thenThrow(new PackageSyncNotConnectedException("NOT_SYNCED：未配置真实同步适配器"));

        PackageSyncResponse response = service.syncPackage("pkg-1", new PackageSyncRequest(
            "org-1", ReleaseStrategy.FULL, ReleaseScopeType.ALL, null, List.of("target-1")
        ));

        assertThat(response.status()).isEqualTo(ReleasePlanStatus.NOT_SYNCED);
        assertThat(response.logs()).hasSize(1);
        assertThat(response.logs().get(0).status()).isEqualTo(SyncLogStatus.NOT_SYNCED);
        assertThat(response.logs().get(0).errorCode()).isEqualTo("NOT_SYNCED");
        assertThat(response.logs().get(0).errorMessage()).contains("未配置真实同步适配器");
        assertThat(response.logs().get(0).syncEvidence()).isNull();

        verify(packageRepository, org.mockito.Mockito.never()).save(any(KnowledgePackage.class));
    }

    @Test
    void syncPackageDoesNotPublishDraftWhenAllTargetsAreNotSynced() throws Exception {
        KnowledgePackage pack = new KnowledgePackage(
            1L, "pkg-draft", "tenant-A", "PKG.TEST", "1.0.0", "待同步草稿包", null,
            KnowledgePackageStatus.DRAFT, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(packageRepository.findByPackageIdAndTenantId("pkg-draft", "tenant-A"))
            .thenReturn(Optional.of(pack));

        SyncTarget target = new SyncTarget(
            1L, "target-1", "tenant-A", "图投影", SyncTargetType.GRAPH_DB, null,
            SyncTargetStatus.ACTIVE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(targetRepository.findByTargetIdAndTenantId("target-1", "tenant-A"))
            .thenReturn(Optional.of(target));
        when(syncPort.sync(eq("tenant-A"), any(ReleasePlan.class), eq(target)))
            .thenThrow(new PackageSyncNotConnectedException("NOT_SYNCED：未配置真实同步适配器"));

        PackageSyncResponse response = service.syncPackage("pkg-draft", new PackageSyncRequest(
            "org-1", ReleaseStrategy.FULL, ReleaseScopeType.ALL, null, List.of("target-1")
        ));

        assertThat(response.status()).isEqualTo(ReleasePlanStatus.NOT_SYNCED);
        assertThat(response.logs()).hasSize(1);
        assertThat(response.logs().get(0).status()).isEqualTo(SyncLogStatus.NOT_SYNCED);
        verify(packageRepository, org.mockito.Mockito.never()).save(any(KnowledgePackage.class));
    }

    @Test
    void syncPackageFailsPlanAndDoesNotPublishDraftWhenAnyTargetFails() throws Exception {
        KnowledgePackage pack = new KnowledgePackage(
            1L, "pkg-draft", "tenant-A", "PKG.TEST", "1.0.0", "待灰度草稿包", null,
            KnowledgePackageStatus.DRAFT, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(packageRepository.findByPackageIdAndTenantId("pkg-draft", "tenant-A"))
            .thenReturn(Optional.of(pack));

        SyncTarget successTarget = new SyncTarget(
            1L, "target-ok", "tenant-A", "规则库", SyncTargetType.CLINICAL_DB, "config",
            SyncTargetStatus.ACTIVE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        SyncTarget failedTarget = new SyncTarget(
            2L, "target-fail", "tenant-A", "图投影", SyncTargetType.GRAPH_DB, "config",
            SyncTargetStatus.ACTIVE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(targetRepository.findByTargetIdAndTenantId("target-ok", "tenant-A"))
            .thenReturn(Optional.of(successTarget));
        when(targetRepository.findByTargetIdAndTenantId("target-fail", "tenant-A"))
            .thenReturn(Optional.of(failedTarget));
        when(syncPort.sync(eq("tenant-A"), any(ReleasePlan.class), eq(successTarget)))
            .thenReturn("EVIDENCE-OK");
        when(syncPort.sync(eq("tenant-A"), any(ReleasePlan.class), eq(failedTarget)))
            .thenThrow(new IllegalStateException("目标库写入失败"));

        PackageSyncResponse response = service.syncPackage("pkg-draft", new PackageSyncRequest(
            "dept-1", ReleaseStrategy.GRAYSCALE, ReleaseScopeType.DEPARTMENT, "dept-1",
            List.of("target-ok", "target-fail")
        ));

        assertThat(response.status()).isEqualTo(ReleasePlanStatus.FAILED);
        assertThat(response.logs()).extracting(SyncLogResponse::status)
            .containsExactly(SyncLogStatus.SUCCESS, SyncLogStatus.FAILED);
        verify(packageRepository, org.mockito.Mockito.never()).save(any(KnowledgePackage.class));
    }

    @Test
    void rollbackPackageRejectsMissingHighRiskConfirmationAndKeepsStatus() {
        KnowledgePackage currentActive = new KnowledgePackage(
            1L, "pkg-1", "tenant-A", "PKG.COPD", "2.0.0", "当前在用包", null,
            KnowledgePackageStatus.ACTIVE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        KnowledgePackage targetRollback = new KnowledgePackage(
            2L, "pkg-2", "tenant-A", "PKG.COPD", "1.0.0", "历史老包", null,
            KnowledgePackageStatus.OFFLINE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );

        when(packageRepository.findByPackageIdAndTenantId("pkg-1", "tenant-A")).thenReturn(Optional.of(currentActive));
        when(packageRepository.findByPackageIdAndTenantId("pkg-2", "tenant-A")).thenReturn(Optional.of(targetRollback));

        PackageRollbackRequest request = new PackageRollbackRequest(
            "pkg-2", "2.0.0", "1.0.0", "临床专家已确认回滚窗口", false
        );

        assertThatThrownBy(() -> service.rollbackPackage("pkg-1", request))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PACKAGE_002);

        verify(packageRepository, org.mockito.Mockito.never()).save(any(KnowledgePackage.class));
    }

    @Test
    void rollbackPackageRejectsVersionMismatchAndKeepsStatus() {
        KnowledgePackage currentActive = new KnowledgePackage(
            1L, "pkg-1", "tenant-A", "PKG.COPD", "2.0.0", "当前在用包", null,
            KnowledgePackageStatus.ACTIVE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        KnowledgePackage targetRollback = new KnowledgePackage(
            2L, "pkg-2", "tenant-A", "PKG.COPD", "1.0.0", "历史老包", null,
            KnowledgePackageStatus.OFFLINE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );

        when(packageRepository.findByPackageIdAndTenantId("pkg-1", "tenant-A")).thenReturn(Optional.of(currentActive));
        when(packageRepository.findByPackageIdAndTenantId("pkg-2", "tenant-A")).thenReturn(Optional.of(targetRollback));

        PackageRollbackRequest request = new PackageRollbackRequest(
            "pkg-2", "2.0.1", "1.0.0", "临床专家已确认回滚窗口", true
        );

        assertThatThrownBy(() -> service.rollbackPackage("pkg-1", request))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PACKAGE_002);

        verify(packageRepository, org.mockito.Mockito.never()).save(any(KnowledgePackage.class));
    }

    @Test
    void rollbackPackageRejectsTargetFromDifferentPackageCode() {
        KnowledgePackage currentActive = new KnowledgePackage(
            1L, "pkg-1", "tenant-A", "PKG.COPD", "2.0.0", "当前在用包", null,
            KnowledgePackageStatus.ACTIVE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        KnowledgePackage targetRollback = new KnowledgePackage(
            2L, "pkg-2", "tenant-A", "PKG.DIABETES", "1.0.0", "其他专病包", null,
            KnowledgePackageStatus.OFFLINE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );

        when(packageRepository.findByPackageIdAndTenantId("pkg-1", "tenant-A")).thenReturn(Optional.of(currentActive));
        when(packageRepository.findByPackageIdAndTenantId("pkg-2", "tenant-A")).thenReturn(Optional.of(targetRollback));

        PackageRollbackRequest request = new PackageRollbackRequest(
            "pkg-2", "2.0.0", "1.0.0", "临床专家已确认回滚窗口", true
        );

        assertThatThrownBy(() -> service.rollbackPackage("pkg-1", request))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PACKAGE_002);

        verify(packageRepository, org.mockito.Mockito.never()).save(any(KnowledgePackage.class));
    }

    @Test
    void rollbackPackageRejectsPublishedTargetAndKeepsStatus() {
        KnowledgePackage currentActive = new KnowledgePackage(
            1L, "pkg-1", "tenant-A", "PKG.COPD", "2.0.0", "当前在用包", null,
            KnowledgePackageStatus.ACTIVE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        KnowledgePackage targetRollback = new KnowledgePackage(
            2L, "pkg-2", "tenant-A", "PKG.COPD", "1.0.0", "从未激活的预发布包", null,
            KnowledgePackageStatus.PUBLISHED, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );

        when(packageRepository.findByPackageIdAndTenantId("pkg-1", "tenant-A")).thenReturn(Optional.of(currentActive));
        when(packageRepository.findByPackageIdAndTenantId("pkg-2", "tenant-A")).thenReturn(Optional.of(targetRollback));

        PackageRollbackRequest request = new PackageRollbackRequest(
            "pkg-2", "2.0.0", "1.0.0", "临床专家已确认回滚窗口", true
        );

        assertThatThrownBy(() -> service.rollbackPackage("pkg-1", request))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PACKAGE_002);

        verify(packageRepository, org.mockito.Mockito.never()).save(any(KnowledgePackage.class));
    }

    @Test
    void rollbackPackageCreatesRollbackPlanAndSyncLogsBeforeSwitchingStatus() throws Exception {
        KnowledgePackage currentActive = new KnowledgePackage(
            1L, "pkg-1", "tenant-A", "PKG.COPD", "2.0.0", "当前在用包", null,
            KnowledgePackageStatus.ACTIVE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        KnowledgePackage targetRollback = new KnowledgePackage(
            2L, "pkg-2", "tenant-A", "PKG.COPD", "1.0.0", "历史老包", null,
            KnowledgePackageStatus.OFFLINE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );

        when(packageRepository.findByPackageIdAndTenantId("pkg-1", "tenant-A")).thenReturn(Optional.of(currentActive));
        when(packageRepository.findByPackageIdAndTenantId("pkg-2", "tenant-A")).thenReturn(Optional.of(targetRollback));
        SyncTarget target = givenSuccessfulRollbackSource("pkg-1", "plan-current", "target-1", "org-1");
        when(syncPort.sync(eq("tenant-A"), any(ReleasePlan.class), eq(target)))
            .thenReturn("EVIDENCE-ROLLBACK");

        PackageRollbackRequest request = new PackageRollbackRequest(
            "pkg-2", "2.0.0", "1.0.0", "临床专家已确认回滚窗口", true
        );

        PackageResponse response = service.rollbackPackage("pkg-1", request);

        assertThat(response.packageId()).isEqualTo("pkg-2");
        assertThat(response.status()).isEqualTo(KnowledgePackageStatus.ACTIVE);

        ArgumentCaptor<ReleasePlan> planCap = ArgumentCaptor.forClass(ReleasePlan.class);
        verify(planRepository, org.mockito.Mockito.times(2)).save(planCap.capture());
        assertThat(planCap.getAllValues()).anySatisfy(plan -> {
            assertThat(plan.packageId()).isEqualTo("pkg-2");
            assertThat(plan.status()).isEqualTo(ReleasePlanStatus.EXECUTING);
            assertThat(plan.targetOrgUnitId()).isEqualTo("org-1");
        });
        assertThat(planCap.getAllValues()).anySatisfy(plan -> {
            assertThat(plan.packageId()).isEqualTo("pkg-2");
            assertThat(plan.status()).isEqualTo(ReleasePlanStatus.ROLLBACKED);
        });

        ArgumentCaptor<SyncLog> logCap = ArgumentCaptor.forClass(SyncLog.class);
        verify(logRepository, org.mockito.Mockito.times(2)).save(logCap.capture());
        assertThat(logCap.getAllValues()).anySatisfy(log -> {
            assertThat(log.targetId()).isEqualTo("target-1");
            assertThat(log.status()).isEqualTo(SyncLogStatus.RUNNING);
        });
        assertThat(logCap.getAllValues()).anySatisfy(log -> {
            assertThat(log.targetId()).isEqualTo("target-1");
            assertThat(log.status()).isEqualTo(SyncLogStatus.SUCCESS);
            assertThat(log.syncEvidence()).isEqualTo("EVIDENCE-ROLLBACK");
        });
        verify(syncPort).sync(eq("tenant-A"), any(ReleasePlan.class), eq(target));
    }

    @Test
    void rollbackPackageKeepsStatusAndMarksPlanNotSyncedWhenReverseProjectionNotConnected() throws Exception {
        KnowledgePackage currentActive = new KnowledgePackage(
            1L, "pkg-1", "tenant-A", "PKG.COPD", "2.0.0", "当前在用包", null,
            KnowledgePackageStatus.ACTIVE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        KnowledgePackage targetRollback = new KnowledgePackage(
            2L, "pkg-2", "tenant-A", "PKG.COPD", "1.0.0", "历史老包", null,
            KnowledgePackageStatus.OFFLINE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );

        when(packageRepository.findByPackageIdAndTenantId("pkg-1", "tenant-A")).thenReturn(Optional.of(currentActive));
        when(packageRepository.findByPackageIdAndTenantId("pkg-2", "tenant-A")).thenReturn(Optional.of(targetRollback));
        SyncTarget target = givenSuccessfulRollbackSource("pkg-1", "plan-current", "target-1", "org-1");
        when(syncPort.sync(eq("tenant-A"), any(ReleasePlan.class), eq(target)))
            .thenThrow(new PackageSyncNotConnectedException("NOT_SYNCED：未配置真实同步适配器"));

        PackageRollbackRequest request = new PackageRollbackRequest(
            "pkg-2", "2.0.0", "1.0.0", "临床专家已确认回滚窗口", true
        );

        assertThatThrownBy(() -> service.rollbackPackage("pkg-1", request))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PACKAGE_005);

        verify(packageRepository, org.mockito.Mockito.never()).save(any(KnowledgePackage.class));

        ArgumentCaptor<ReleasePlan> planCap = ArgumentCaptor.forClass(ReleasePlan.class);
        verify(planRepository, org.mockito.Mockito.times(2)).save(planCap.capture());
        assertThat(planCap.getAllValues()).anySatisfy(plan ->
            assertThat(plan.status()).isEqualTo(ReleasePlanStatus.NOT_SYNCED));

        ArgumentCaptor<SyncLog> logCap = ArgumentCaptor.forClass(SyncLog.class);
        verify(logRepository, org.mockito.Mockito.times(2)).save(logCap.capture());
        assertThat(logCap.getAllValues()).anySatisfy(log -> {
            assertThat(log.status()).isEqualTo(SyncLogStatus.NOT_SYNCED);
            assertThat(log.errorCode()).isEqualTo("NOT_SYNCED");
            assertThat(log.syncEvidence()).isNull();
        });
    }

    @Test
    void rollbackPackageMarksPlanFailedWhenOriginalSyncTargetMissing() {
        KnowledgePackage currentActive = new KnowledgePackage(
            1L, "pkg-1", "tenant-A", "PKG.COPD", "2.0.0", "当前在用包", null,
            KnowledgePackageStatus.ACTIVE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        KnowledgePackage targetRollback = new KnowledgePackage(
            2L, "pkg-2", "tenant-A", "PKG.COPD", "1.0.0", "历史老包", null,
            KnowledgePackageStatus.OFFLINE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        ReleasePlan originalPlan = new ReleasePlan(
            10L, "plan-current", "tenant-A", "pkg-1", "org-1",
            ReleaseStrategy.FULL, ReleaseScopeType.ALL, null, ReleasePlanStatus.SUCCESS,
            Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        SyncLog originalSuccessLog = new SyncLog(
            20L, "log-current", "tenant-A", "plan-current", "target-missing",
            SyncLogStatus.SUCCESS, null, null, 0, "EVIDENCE-CURRENT",
            Instant.now(), "tester", Instant.now(), "tester", "trace"
        );

        when(packageRepository.findByPackageIdAndTenantId("pkg-1", "tenant-A")).thenReturn(Optional.of(currentActive));
        when(packageRepository.findByPackageIdAndTenantId("pkg-2", "tenant-A")).thenReturn(Optional.of(targetRollback));
        when(planRepository.findByTenantIdAndPackageIdOrderByCreatedAtDesc("tenant-A", "pkg-1"))
            .thenReturn(List.of(originalPlan));
        when(logRepository.findByTenantIdAndPlanId("tenant-A", "plan-current"))
            .thenReturn(List.of(originalSuccessLog));
        when(targetRepository.findByTargetIdAndTenantId("target-missing", "tenant-A"))
            .thenReturn(Optional.empty());

        PackageRollbackRequest request = new PackageRollbackRequest(
            "pkg-2", "2.0.0", "1.0.0", "临床专家已确认回滚窗口", true
        );

        assertThatThrownBy(() -> service.rollbackPackage("pkg-1", request))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PACKAGE_005);

        verify(packageRepository, org.mockito.Mockito.never()).save(any(KnowledgePackage.class));

        ArgumentCaptor<ReleasePlan> planCap = ArgumentCaptor.forClass(ReleasePlan.class);
        verify(planRepository, org.mockito.Mockito.times(2)).save(planCap.capture());
        assertThat(planCap.getAllValues()).anySatisfy(plan ->
            assertThat(plan.status()).isEqualTo(ReleasePlanStatus.FAILED));

        ArgumentCaptor<SyncLog> logCap = ArgumentCaptor.forClass(SyncLog.class);
        verify(logRepository, org.mockito.Mockito.times(2)).save(logCap.capture());
        assertThat(logCap.getAllValues()).anySatisfy(log -> {
            assertThat(log.status()).isEqualTo(SyncLogStatus.FAILED);
            assertThat(log.errorCode()).isEqualTo("ENG-PACKAGE-001");
            assertThat(log.targetId()).isEqualTo("target-missing");
        });
    }

    @Test
    void rollbackPackageMarksPlanFailedWhenReverseProjectionReturnsBlankEvidence() throws Exception {
        KnowledgePackage currentActive = new KnowledgePackage(
            1L, "pkg-1", "tenant-A", "PKG.COPD", "2.0.0", "当前在用包", null,
            KnowledgePackageStatus.ACTIVE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        KnowledgePackage targetRollback = new KnowledgePackage(
            2L, "pkg-2", "tenant-A", "PKG.COPD", "1.0.0", "历史老包", null,
            KnowledgePackageStatus.OFFLINE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );

        when(packageRepository.findByPackageIdAndTenantId("pkg-1", "tenant-A")).thenReturn(Optional.of(currentActive));
        when(packageRepository.findByPackageIdAndTenantId("pkg-2", "tenant-A")).thenReturn(Optional.of(targetRollback));
        SyncTarget target = givenSuccessfulRollbackSource("pkg-1", "plan-current", "target-1", "org-1");
        when(syncPort.sync(eq("tenant-A"), any(ReleasePlan.class), eq(target)))
            .thenReturn(" ");

        PackageRollbackRequest request = new PackageRollbackRequest(
            "pkg-2", "2.0.0", "1.0.0", "临床专家已确认回滚窗口", true
        );

        assertThatThrownBy(() -> service.rollbackPackage("pkg-1", request))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PACKAGE_005);

        verify(packageRepository, org.mockito.Mockito.never()).save(any(KnowledgePackage.class));

        ArgumentCaptor<ReleasePlan> planCap = ArgumentCaptor.forClass(ReleasePlan.class);
        verify(planRepository, org.mockito.Mockito.times(2)).save(planCap.capture());
        assertThat(planCap.getAllValues()).anySatisfy(plan ->
            assertThat(plan.status()).isEqualTo(ReleasePlanStatus.FAILED));

        ArgumentCaptor<SyncLog> logCap = ArgumentCaptor.forClass(SyncLog.class);
        verify(logRepository, org.mockito.Mockito.times(2)).save(logCap.capture());
        assertThat(logCap.getAllValues()).anySatisfy(log -> {
            assertThat(log.status()).isEqualTo(SyncLogStatus.FAILED);
            assertThat(log.errorCode()).isEqualTo("ENG-PACKAGE-005");
            assertThat(log.syncEvidence()).isNull();
        });
    }

    @Test
    void rollbackPackageSwitchesActiveStatusAndRecordsAudit() throws Exception {
        KnowledgePackage currentActive = new KnowledgePackage(
            1L, "pkg-1", "tenant-A", "PKG.COPD", "2.0.0", "当前在用包", null,
            KnowledgePackageStatus.ACTIVE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        KnowledgePackage targetRollback = new KnowledgePackage(
            2L, "pkg-2", "tenant-A", "PKG.COPD", "1.0.0", "历史老包", null,
            KnowledgePackageStatus.OFFLINE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );

        when(packageRepository.findByPackageIdAndTenantId("pkg-1", "tenant-A")).thenReturn(Optional.of(currentActive));
        when(packageRepository.findByPackageIdAndTenantId("pkg-2", "tenant-A")).thenReturn(Optional.of(targetRollback));
        SyncTarget target = givenSuccessfulRollbackSource("pkg-1", "plan-current", "target-1", "org-1");
        when(syncPort.sync(eq("tenant-A"), any(ReleasePlan.class), eq(target)))
            .thenReturn("EVIDENCE-ROLLBACK");

        PackageRollbackRequest request = new PackageRollbackRequest(
            "pkg-2", "2.0.0", "1.0.0", "临床专家已确认回滚窗口", true
        );

        PackageResponse response = service.rollbackPackage("pkg-1", request);

        assertThat(response.packageId()).isEqualTo("pkg-2");
        assertThat(response.status()).isEqualTo(KnowledgePackageStatus.ACTIVE);

        ArgumentCaptor<KnowledgePackage> packCap = ArgumentCaptor.forClass(KnowledgePackage.class);
        // 保存两个包的状态切换
        verify(packageRepository, org.mockito.Mockito.times(2)).save(packCap.capture());
        List<KnowledgePackage> savedPacks = packCap.getAllValues();
        
        assertThat(savedPacks).anySatisfy(p -> {
            assertThat(p.packageId()).isEqualTo("pkg-1");
            assertThat(p.status()).isEqualTo(KnowledgePackageStatus.OFFLINE);
        });
        assertThat(savedPacks).anySatisfy(p -> {
            assertThat(p.packageId()).isEqualTo("pkg-2");
            assertThat(p.status()).isEqualTo(KnowledgePackageStatus.ACTIVE);
        });

        verify(auditPublisher).publish(eq(AuditAction.ROLLBACK), eq("knowledge_package"), eq("pkg-2"), any());
    }

    @Test
    void syncPackageDoesNotAffectOtherPackageCodes() throws Exception {
        // 模拟当前待激活包 (COPD v2.0)
        KnowledgePackage pack = new KnowledgePackage(
            1L, "pkg-copd-v2", "tenant-A", "PKG.COPD", "2.0.0", "慢阻肺包v2", null,
            KnowledgePackageStatus.PUBLISHED, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(packageRepository.findByPackageIdAndTenantId("pkg-copd-v2", "tenant-A"))
            .thenReturn(Optional.of(pack));

        // 模拟同一个租户下有多个 ACTIVE 状态的不同业务包
        // 1. COPD 的老版本包 (PKG.COPD v1.0) -> 应该被失效
        KnowledgePackage oldCopd = new KnowledgePackage(
            2L, "pkg-copd-v1", "tenant-A", "PKG.COPD", "1.0.0", "慢阻肺包v1", null,
            KnowledgePackageStatus.ACTIVE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        // 2. 脑卒中的包 (PKG.STROKE v1.0) -> 不应该被失效！
        KnowledgePackage stroke = new KnowledgePackage(
            3L, "pkg-stroke-v1", "tenant-A", "PKG.STROKE", "1.0.0", "脑卒中包v1", null,
            KnowledgePackageStatus.ACTIVE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );

        when(packageRepository.findByTenantIdOrderByUpdatedAtDesc("tenant-A"))
            .thenReturn(List.of(oldCopd, stroke));

        SyncTarget target = new SyncTarget(
            1L, "target-1", "tenant-A", "投影目标", SyncTargetType.DIFY, "config",
            SyncTargetStatus.ACTIVE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(targetRepository.findByTargetIdAndTenantId("target-1", "tenant-A"))
            .thenReturn(Optional.of(target));
        when(syncPort.sync(eq("tenant-A"), any(ReleasePlan.class), eq(target)))
            .thenReturn("EVIDENCE-001");

        PackageSyncResponse response = service.syncPackage("pkg-copd-v2", new PackageSyncRequest(
            "org-1", ReleaseStrategy.FULL, ReleaseScopeType.ALL, null, List.of("target-1")
        ));

        assertThat(response.status()).isEqualTo(ReleasePlanStatus.SUCCESS);

        ArgumentCaptor<KnowledgePackage> packCap = ArgumentCaptor.forClass(KnowledgePackage.class);
        // 我们只在小事务3里对需要变更状态的包调用 save。
        // 原本待激活的包会被 save 为 ACTIVE。
        // 被失效的 COPD 包会被 save 为 OFFLINE。
        // STROKE 的包绝对不应该被调用 save！
        verify(packageRepository, org.mockito.Mockito.atLeastOnce()).save(packCap.capture());
        List<KnowledgePackage> savedPacks = packCap.getAllValues();

        // 验证 COPD 发生状态原子切换
        assertThat(savedPacks).anySatisfy(p -> {
            assertThat(p.packageId()).isEqualTo("pkg-copd-v1");
            assertThat(p.status()).isEqualTo(KnowledgePackageStatus.OFFLINE);
        });
        assertThat(savedPacks).anySatisfy(p -> {
            assertThat(p.packageId()).isEqualTo("pkg-copd-v2");
            assertThat(p.status()).isEqualTo(KnowledgePackageStatus.ACTIVE);
        });

        // 验证 STROKE 的包绝不在被保存失效的对象列表中，它仍旧保持 ACTIVE！
        assertThat(savedPacks).noneSatisfy(p -> {
            assertThat(p.packageId()).isEqualTo("pkg-stroke-v1");
        });
    }

    @Test
    void listSyncTargetsRetrievesActiveTargets() {
        SyncTarget activeTarget = new SyncTarget(
            1L, "target-active", "tenant-A", "激活通道", SyncTargetType.DIFY, "config",
            SyncTargetStatus.ACTIVE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(targetRepository.findByTenantIdAndStatus("tenant-A", SyncTargetStatus.ACTIVE))
            .thenReturn(List.of(activeTarget));

        List<SyncTarget> results = service.listSyncTargets();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).targetId()).isEqualTo("target-active");
    }

    private KnowledgePackage packageVersion(String packageId, String version, KnowledgePackageStatus status) {
        return new KnowledgePackage(
            1L, packageId, "tenant-A", "PKG.TEST", version, "测试知识包", null,
            status, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
    }

    private SyncTarget givenSuccessfulRollbackSource(
            String currentPackageId,
            String planId,
            String targetId,
            String targetOrgUnitId) {
        ReleasePlan originalPlan = new ReleasePlan(
            10L, planId, "tenant-A", currentPackageId, targetOrgUnitId,
            ReleaseStrategy.FULL, ReleaseScopeType.ALL, null, ReleasePlanStatus.SUCCESS,
            Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        SyncLog originalSuccessLog = new SyncLog(
            20L, "log-" + targetId, "tenant-A", planId, targetId,
            SyncLogStatus.SUCCESS, null, null, 0, "EVIDENCE-CURRENT",
            Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        SyncTarget target = new SyncTarget(
            30L, targetId, "tenant-A", "图投影", SyncTargetType.GRAPH_DB, "config",
            SyncTargetStatus.ACTIVE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );

        when(planRepository.findByTenantIdAndPackageIdOrderByCreatedAtDesc("tenant-A", currentPackageId))
            .thenReturn(List.of(originalPlan));
        when(logRepository.findByTenantIdAndPlanId("tenant-A", planId))
            .thenReturn(List.of(originalSuccessLog));
        when(targetRepository.findByTargetIdAndTenantId(targetId, "tenant-A"))
            .thenReturn(Optional.of(target));
        return target;
    }

    private PackageItem packageItem(
            long id,
            String packageId,
            PackageItemAssetType assetType,
            String assetId,
            String assetVersion) {
        return new PackageItem(
            id, "item-" + id, "tenant-A", packageId, assetType, assetId, assetVersion,
            Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
    }

    private RuleDefinition publishedRule(String ruleId, String applicableOrgUnitId) {
        return new RuleDefinition(
            1L, ruleId, "tenant-A", "RULE.TEST", "测试规则", RuleType.QUALITY,
            RuleAuthoringMode.DSL, RuleRiskLevel.MEDIUM, RuleDefinitionStatus.PUBLISHED,
            "rule-version-1", "1.0.0", applicableOrgUnitId,
            Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
    }

    private PathwayTemplate publishedPathway(String templateId) {
        return new PathwayTemplate(
            1L, templateId, "tenant-A", "pathway-package", "PATH.TEST", "测试路径",
            "DISEASE.TEST", 1, PathwayTemplateLevel.DEPARTMENT, PathwayTemplateStatus.PUBLISHED,
            "start", "source-ref", "路径说明", "{}", "{}",
            Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
    }

    private EvaluationIndicator publishedIndicator(String indicatorId, String responsibleDepartmentId) {
        return new EvaluationIndicator(
            1L, indicatorId, "tenant-A", "EVAL.TEST", 1, "测试指标",
            EvaluationSubjectType.DEPARTMENT, "denominator", "numerator", "exclusion",
            "scoring", "P30D", "tenant", responsibleDepartmentId, "source-ref",
            "1.0.0", EvaluationIndicatorStatus.PUBLISHED, Instant.now(), "tester",
            Instant.now(), Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
    }
}
