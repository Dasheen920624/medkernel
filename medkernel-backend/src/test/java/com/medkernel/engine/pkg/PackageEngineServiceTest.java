package com.medkernel.engine.pkg;

import com.medkernel.engine.versioning.VersionedAssetType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.evaluation.EvaluationIndicator;
import com.medkernel.engine.evaluation.EvaluationIndicatorRepository;
import com.medkernel.engine.evaluation.EvaluationIndicatorStatus;
import com.medkernel.engine.evaluation.EvaluationSubjectType;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.engine.knowledge.KnowledgeDomain;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityRepository;
import com.medkernel.engine.knowledge.KnowledgeIdentityStatus;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.KnowledgeVersionStatus;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
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
import com.medkernel.engine.rule.RuleVersion;
import com.medkernel.engine.rule.RuleVersionRepository;
import com.medkernel.engine.rule.RuleVersionStatus;
import com.medkernel.engine.terminology.TermMappingPackage;
import com.medkernel.engine.terminology.TermMapping;
import com.medkernel.engine.terminology.TermMappingPackageItem;
import com.medkernel.engine.terminology.TermMappingPackageItemRepository;
import com.medkernel.engine.terminology.TermMappingPackageRepository;
import com.medkernel.engine.terminology.TermMappingRepository;
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

    private static final ObjectMapper TEST_MAPPER = new ObjectMapper();

    private KnowledgePackageRepository packageRepository;
    private PackageItemRepository itemRepository;
    private ReleasePlanRepository planRepository;
    private SyncTargetRepository targetRepository;
    private SyncLogRepository logRepository;
    private TransactionTemplate transactionTemplate;

    private RuleDefinitionRepository ruleRepository;
    private RuleVersionRepository ruleVersionRepository;
    private PathwayTemplateRepository pathwayRepository;
    private EvaluationIndicatorRepository evaluationRepository;
    private KnowledgeIdentityRepository knowledgeIdentityRepository;
    private KnowledgeAssetVersionRepository knowledgeVersionRepository;
    private TermMappingPackageRepository terminologyPackageRepository;
    private TermMappingPackageItemRepository terminologyPackageItemRepository;
    private TermMappingRepository terminologyMappingRepository;
    private PilotPackageTemplateRepository pilotTemplateRepository;
    private PilotPackageTemplateItemRepository pilotTemplateItemRepository;

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
        ruleVersionRepository = mock(RuleVersionRepository.class);
        pathwayRepository = mock(PathwayTemplateRepository.class);
        evaluationRepository = mock(EvaluationIndicatorRepository.class);
        knowledgeIdentityRepository = mock(KnowledgeIdentityRepository.class);
        knowledgeVersionRepository = mock(KnowledgeAssetVersionRepository.class);
        terminologyPackageRepository = mock(TermMappingPackageRepository.class);
        terminologyPackageItemRepository = mock(TermMappingPackageItemRepository.class);
        terminologyMappingRepository = mock(TermMappingRepository.class);
        pilotTemplateRepository = mock(PilotPackageTemplateRepository.class);
        pilotTemplateItemRepository = mock(PilotPackageTemplateItemRepository.class);

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
            ruleRepository, ruleVersionRepository, pathwayRepository, evaluationRepository,
            knowledgeIdentityRepository, knowledgeVersionRepository,
            terminologyPackageRepository, terminologyPackageItemRepository, terminologyMappingRepository,
            pilotTemplateRepository, pilotTemplateItemRepository,
            syncPort, auditPublisher,
            transactionTemplate
        );

        when(packageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(itemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(targetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(knowledgeIdentityRepository.save(any())).thenAnswer(inv -> {
            KnowledgeIdentity identity = inv.getArgument(0);
            if (identity.id() != null) {
                return identity;
            }
            return new KnowledgeIdentity(
                101L, identity.tenantId(), identity.identityCode(), identity.domain(), identity.subject(),
                identity.specialtyId(), identity.description(), identity.status(), identity.currentVersionId(),
                identity.createdAt(), identity.createdBy(), identity.updatedAt(), identity.updatedBy()
            );
        });
        when(knowledgeVersionRepository.save(any())).thenAnswer(inv -> {
            KnowledgeAssetVersion version = inv.getArgument(0);
            if (version.id() != null) {
                return version;
            }
            return new KnowledgeAssetVersion(
                201L, version.tenantId(), version.identityId(), version.versionNo(), version.versionLabel(),
                version.sourceDocumentId(), version.sourceVersionId(), version.contentHash(), version.anchors(),
                version.status(), version.riskLevel(), version.authorityLevel(), version.gradeQuality(),
                version.gradeStrength(), version.conflictArbitration(),
                version.effectiveOrganizationScope(), version.effectiveApplicableScope(), version.activeScopeKey(),
                version.effectiveFrom(), version.effectiveTo(),
                version.reviewedBy(), version.reviewedAt(), version.activatedAt(), version.supersededAt(),
                version.withdrawnAt(), version.withdrawnReason(), version.createdAt(), version.createdBy(),
                version.updatedAt(), version.updatedBy()
            );
        });
        when(terminologyPackageRepository.save(any())).thenAnswer(inv -> {
            TermMappingPackage pkg = inv.getArgument(0);
            if (pkg.id() != null) {
                return pkg;
            }
            return TermMappingPackage.imported(
                301L, pkg.tenantId(), pkg.packageCode(), pkg.packageVersion(), pkg.displayName(),
                pkg.scopeLevel(), pkg.scopeCode(), pkg.statusName(), pkg.mappingCount(), pkg.contentHash(),
                pkg.grayScopeJson(), pkg.publishedBy(), pkg.publishedAt(), pkg.rollbackFromPackageId(),
                pkg.createdAt(), pkg.createdBy()
            );
        });
        when(terminologyMappingRepository.save(any())).thenAnswer(inv -> {
            TermMapping mapping = inv.getArgument(0);
            if (mapping.id() != null) {
                return mapping;
            }
            return TermMapping.imported(
                401L, mapping.tenantId(), mapping.localTermId(), mapping.standardTermId(),
                mapping.sourceSystem(), mapping.categoryName(), mapping.confidence(), mapping.riskLevelName(),
                mapping.statusName(), mapping.evidenceText(), mapping.confirmedBy(), mapping.confirmedAt(),
                mapping.createdAt(), mapping.createdBy()
            );
        });
        when(terminologyPackageItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pilotTemplateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pilotTemplateItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

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
    void validatePackageReturnsBlockingIssueWhenPackageHasNoItems() {
        KnowledgePackage pack = new KnowledgePackage(
            1L, "pkg-empty", "tenant-A", "PKG.EMPTY", "1.0.0", "空配置包", null,
            KnowledgePackageStatus.DRAFT, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(packageRepository.findByPackageIdAndTenantId("pkg-empty", "tenant-A"))
            .thenReturn(Optional.of(pack));
        when(itemRepository.findByTenantIdAndPackageId("tenant-A", "pkg-empty"))
            .thenReturn(List.of());

        PackageValidateResponse response = service.validatePackage("pkg-empty");

        assertThat(response.packageId()).isEqualTo("pkg-empty");
        assertThat(response.contentSha256()).matches("[a-f0-9]{64}");
        assertThat(response.valid()).isFalse();
        assertThat(response.itemCount()).isZero();
        assertThat(response.issues()).anySatisfy(issue -> {
            assertThat(issue.field()).isEqualTo("items");
            assertThat(issue.severity()).isEqualTo("BLOCKING");
            assertThat(issue.message()).contains("至少包含一个已审核资产");
        });
    }

    @Test
    void validatePackageReturnsValidWhenPackageHasRealItems() {
        KnowledgePackage pack = new KnowledgePackage(
            1L, "pkg-1", "tenant-A", "PKG.COPD", "1.0.0", "配置包", null,
            KnowledgePackageStatus.DRAFT, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        PackageItem item = new PackageItem(
            10L, "item-1", "tenant-A", "pkg-1", VersionedAssetType.RULE, "rule-1", "1",
            Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(packageRepository.findByPackageIdAndTenantId("pkg-1", "tenant-A"))
            .thenReturn(Optional.of(pack));
        when(itemRepository.findByTenantIdAndPackageId("tenant-A", "pkg-1"))
            .thenReturn(List.of(item));
        when(ruleRepository.findByRuleIdAndTenantId("rule-1", "tenant-A"))
            .thenReturn(Optional.of(publishedRule("rule-1", "dept-rule")));

        PackageValidateResponse response = service.validatePackage("pkg-1");

        assertThat(response.packageId()).isEqualTo("pkg-1");
        assertThat(response.contentSha256()).matches("[a-f0-9]{64}");
        assertThat(response.valid()).isTrue();
        assertThat(response.itemCount()).isEqualTo(1);
        assertThat(response.issues()).isEmpty();
    }

    @Test
    void validatePackageBlocksWhenDeclaredAssetDependencyIsMissing() {
        KnowledgePackage pack = new KnowledgePackage(
            1L, "pkg-missing", "tenant-A", "PKG.MISSING", "1.0.0", "缺依赖配置包", null,
            KnowledgePackageStatus.DRAFT, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        PackageItem item = new PackageItem(
            10L, "item-1", "tenant-A", "pkg-missing", VersionedAssetType.RULE, "rule-missing", "1",
            Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(packageRepository.findByPackageIdAndTenantId("pkg-missing", "tenant-A"))
            .thenReturn(Optional.of(pack));
        when(itemRepository.findByTenantIdAndPackageId("tenant-A", "pkg-missing"))
            .thenReturn(List.of(item));
        when(ruleRepository.findByRuleIdAndTenantId("rule-missing", "tenant-A"))
            .thenReturn(Optional.empty());

        PackageValidateResponse response = service.validatePackage("pkg-missing");

        assertThat(response.valid()).isFalse();
        assertThat(response.issues()).anySatisfy(issue -> {
            assertThat(issue.field()).isEqualTo("items[RULE:rule-missing]");
            assertThat(issue.severity()).isEqualTo("BLOCKING");
            assertThat(issue.message()).contains("入包规则不存在");
        });
    }

    @Test
    void validatePackageAcceptsPublishedKnowledgeAndTerminologyAssets() {
        KnowledgePackage pack = new KnowledgePackage(
            1L, "pkg-assets", "tenant-A", "PKG.ASSETS", "1.0.0", "知识术语配置包", null,
            KnowledgePackageStatus.DRAFT, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(packageRepository.findByPackageIdAndTenantId("pkg-assets", "tenant-A"))
            .thenReturn(Optional.of(pack));
        when(itemRepository.findByTenantIdAndPackageId("tenant-A", "pkg-assets"))
            .thenReturn(List.of(
                packageItem(10L, "pkg-assets", VersionedAssetType.KNOWLEDGE, "KNOW.COPD.GUIDE", "v1"),
                packageItem(11L, "pkg-assets", VersionedAssetType.TERMINOLOGY, "TERM.LAB|DEPARTMENT|CARD", "2026.06")
            ));
        when(knowledgeIdentityRepository.findByTenantIdAndIdentityCode("tenant-A", "KNOW.COPD.GUIDE"))
            .thenReturn(Optional.of(activeKnowledgeIdentity(101L, "KNOW.COPD.GUIDE", 201L)));
        when(knowledgeVersionRepository.findByTenantIdAndIdentityIdAndVersionNo("tenant-A", 101L, "v1"))
            .thenReturn(Optional.of(activeKnowledgeVersion(201L, 101L, "v1")));
        when(terminologyPackageRepository.findByTenantIdAndPackageCodeAndPackageVersionAndScopeLevelAndScopeCode(
                "tenant-A", "TERM.LAB", "2026.06", "DEPARTMENT", "CARD"))
            .thenReturn(Optional.of(publishedTerminologyPackage(
                "TERM.LAB", "2026.06", "DEPARTMENT", "CARD")));

        PackageValidateResponse response = service.validatePackage("pkg-assets");

        assertThat(response.valid()).isTrue();
        assertThat(response.issues()).isEmpty();
    }

    @Test
    void validatePackageBlocksFollowupRuntimePlansUntilTemplateAssetExists() {
        KnowledgePackage pack = new KnowledgePackage(
            1L, "pkg-followup", "tenant-A", "PKG.FOLLOWUP", "1.0.0", "随访配置包", null,
            KnowledgePackageStatus.DRAFT, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        PackageItem item = new PackageItem(
            10L, "item-1", "tenant-A", "pkg-followup", VersionedAssetType.FOLLOWUP, "plan-runtime-1", "1",
            Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(packageRepository.findByPackageIdAndTenantId("pkg-followup", "tenant-A"))
            .thenReturn(Optional.of(pack));
        when(itemRepository.findByTenantIdAndPackageId("tenant-A", "pkg-followup"))
            .thenReturn(List.of(item));

        PackageValidateResponse response = service.validatePackage("pkg-followup");

        assertThat(response.valid()).isFalse();
        assertThat(response.issues()).anySatisfy(issue -> {
            assertThat(issue.field()).isEqualTo("items[FOLLOWUP:plan-runtime-1]");
            assertThat(issue.severity()).isEqualTo("BLOCKING");
            assertThat(issue.message()).contains("随访计划属于患者运行数据");
        });
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
                VersionedAssetType.RULE, "rule-1", "1")))
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

        when(itemRepository.findByTenantIdAndPackageIdAndAssetTypeAndAssetId("tenant-A", "pkg-1", VersionedAssetType.RULE, "rule-1"))
            .thenReturn(Optional.empty());

        PackageItemResponse response = service.addPackageItem("pkg-1", new PackageItemRequest(
            VersionedAssetType.RULE, "rule-1", "1"));

        assertThat(response.itemId()).isNotNull();
        assertThat(response.assetId()).isEqualTo("rule-1");
        verify(auditPublisher).publish(eq(AuditAction.UPDATE), eq("knowledge_package"), eq("pkg-1"), any());
    }

    @Test
    void instantiatePilotTemplateCreatesDraftPackageWithAllRequiredAssetTypes() {
        PilotPackageTemplate template = activePilotTemplate(
            "t-1", "tpl-first-run", "TPL.FIRST_RUN", "试点首发最小可运行集");
        List<PilotPackageTemplateItem> templateItems = List.of(
            pilotTemplateItem("tpl-first-run", 10, VersionedAssetType.KNOWLEDGE, "KNOW.COPD.GUIDE", "v1"),
            pilotTemplateItem("tpl-first-run", 20, VersionedAssetType.TERMINOLOGY, "TERM.LAB|DEPARTMENT|CARD", "2026.06"),
            pilotTemplateItem("tpl-first-run", 30, VersionedAssetType.RULE, "rule-stable", "2"),
            pilotTemplateItem("tpl-first-run", 40, VersionedAssetType.PATHWAY, "pathway-stable", "1")
        );
        when(pilotTemplateRepository.findByTenantIdAndTemplateCodeAndStatus(
                "tenant-A", "TPL.FIRST_RUN", PilotPackageTemplateStatus.ACTIVE))
            .thenReturn(Optional.empty());
        when(pilotTemplateRepository.findByTenantIdAndTemplateCodeAndStatus(
                "t-1", "TPL.FIRST_RUN", PilotPackageTemplateStatus.ACTIVE))
            .thenReturn(Optional.of(template));
        when(pilotTemplateItemRepository.findByTenantIdAndTemplateIdOrderBySortOrderAsc("t-1", "tpl-first-run"))
            .thenReturn(templateItems);
        when(packageRepository.findByTenantIdAndPackageCodeAndPackageVersion(
                "tenant-A", "PKG.FIRST.RUN", "2026.06.03"))
            .thenReturn(Optional.empty());
        when(knowledgeIdentityRepository.findByTenantIdAndIdentityCode("tenant-A", "KNOW.COPD.GUIDE"))
            .thenReturn(Optional.of(activeKnowledgeIdentity(101L, "KNOW.COPD.GUIDE", 201L)));
        when(knowledgeVersionRepository.findByTenantIdAndIdentityIdAndVersionNo("tenant-A", 101L, "v1"))
            .thenReturn(Optional.of(activeKnowledgeVersion(201L, 101L, "v1")));
        when(terminologyPackageRepository.findByTenantIdAndPackageCodeAndPackageVersionAndScopeLevelAndScopeCode(
                "tenant-A", "TERM.LAB", "2026.06", "DEPARTMENT", "CARD"))
            .thenReturn(Optional.of(publishedTerminologyPackage(
                "TERM.LAB", "2026.06", "DEPARTMENT", "CARD")));
        when(ruleRepository.findByRuleIdAndTenantId("rule-stable", "tenant-A"))
            .thenReturn(Optional.of(publishedRule("rule-stable", "dept-rule")));
        when(pathwayRepository.findByTemplateIdAndTenantId("pathway-stable", "tenant-A"))
            .thenReturn(Optional.of(publishedPathway("pathway-stable")));

        PilotPackageInstantiationResponse response = service.instantiatePilotTemplate(
            "TPL.FIRST_RUN",
            new PilotPackageTemplateInstantiateRequest(
                "PKG.FIRST.RUN",
                "2026.06.03",
                "首发配置包",
                "由试点首发模板实例化"
            )
        );

        assertThat(response.templateCode()).isEqualTo("TPL.FIRST_RUN");
        assertThat(response.packageInfo().status()).isEqualTo(KnowledgePackageStatus.DRAFT);
        assertThat(response.packageInfo().packageCode()).isEqualTo("PKG.FIRST.RUN");
        assertThat(response.items()).hasSize(4);
        assertThat(response.items()).extracting(PackageItemResponse::assetType)
            .containsExactly(
                VersionedAssetType.KNOWLEDGE,
                VersionedAssetType.TERMINOLOGY,
                VersionedAssetType.RULE,
                VersionedAssetType.PATHWAY
            );

        ArgumentCaptor<KnowledgePackage> packCap = ArgumentCaptor.forClass(KnowledgePackage.class);
        verify(packageRepository).save(packCap.capture());
        assertThat(packCap.getValue().status()).isEqualTo(KnowledgePackageStatus.DRAFT);
        assertThat(packCap.getValue().traceId()).isEqualTo("trace-pkg");

        ArgumentCaptor<PackageItem> itemCap = ArgumentCaptor.forClass(PackageItem.class);
        verify(itemRepository, org.mockito.Mockito.times(4)).save(itemCap.capture());
        assertThat(itemCap.getAllValues()).allSatisfy(item -> {
            assertThat(item.packageId()).isEqualTo(packCap.getValue().packageId());
            assertThat(item.tenantId()).isEqualTo("tenant-A");
            assertThat(item.traceId()).isEqualTo("trace-pkg");
        });
        verify(auditPublisher).publish(eq(AuditAction.CREATE), eq("knowledge_package"),
            eq(packCap.getValue().packageId()), any());
    }

    @Test
    void instantiatePilotTemplateRejectsMissingDependencyAndLeavesNoPartialDraft() {
        PilotPackageTemplate template = activePilotTemplate(
            "tenant-A", "tpl-missing", "TPL.MISSING", "缺依赖模板");
        when(pilotTemplateRepository.findByTenantIdAndTemplateCodeAndStatus(
                "tenant-A", "TPL.MISSING", PilotPackageTemplateStatus.ACTIVE))
            .thenReturn(Optional.of(template));
        when(pilotTemplateItemRepository.findByTenantIdAndTemplateIdOrderBySortOrderAsc("tenant-A", "tpl-missing"))
            .thenReturn(List.of(pilotTemplateItem(
                "tpl-missing", 10, VersionedAssetType.RULE, "rule-missing", "1")));
        when(packageRepository.findByTenantIdAndPackageCodeAndPackageVersion(
                "tenant-A", "PKG.MISSING", "2026.06.03"))
            .thenReturn(Optional.empty());
        when(ruleRepository.findByRuleIdAndTenantId("rule-missing", "tenant-A"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.instantiatePilotTemplate(
                "TPL.MISSING",
                new PilotPackageTemplateInstantiateRequest(
                    "PKG.MISSING",
                    "2026.06.03",
                    "缺依赖配置包",
                    "不能留下半成品"
                )))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> {
                ApiException api = (ApiException) ex;
                assertThat(api.errorCode()).isEqualTo(ErrorCode.PACKAGE_DEPENDENCY_MISSING);
                assertThat(api.getMessage()).contains("rule-missing");
            });

        verify(packageRepository, never()).save(any(KnowledgePackage.class));
        verify(itemRepository, never()).save(any(PackageItem.class));
    }

    @Test
    void getAssetReadinessReflectsReleasedPackagesAndGrayscaleEvidence() {
        when(pilotTemplateRepository.findByTenantIdAndStatusOrderByTemplateCodeAsc(
                "tenant-A", PilotPackageTemplateStatus.ACTIVE))
            .thenReturn(List.of(activePilotTemplate("tenant-A", "tpl-tenant", "TPL.TENANT", "院内模板")));
        when(pilotTemplateRepository.findByTenantIdAndStatusOrderByTemplateCodeAsc(
                "t-1", PilotPackageTemplateStatus.ACTIVE))
            .thenReturn(List.of(activePilotTemplate("t-1", "tpl-platform", "TPL.PLATFORM", "平台模板")));
        when(packageRepository.findByTenantIdOrderByUpdatedAtDesc("tenant-A"))
            .thenReturn(List.of(
                packageVersion("pkg-active", "2.0.0", KnowledgePackageStatus.ACTIVE),
                packageVersion("pkg-draft", "3.0.0", KnowledgePackageStatus.DRAFT),
                packageVersion("pkg-published", "1.9.0", KnowledgePackageStatus.PUBLISHED)
            ));
        when(planRepository.findByTenantIdOrderByCreatedAtDesc("tenant-A"))
            .thenReturn(List.of(new ReleasePlan(
                1L, "plan-gray", "tenant-A", "pkg-published", "hospital-1",
                ReleaseStrategy.GRAYSCALE, ReleaseScopeType.HOSPITAL,
                "{\"strategy\":\"BED_PERCENT\",\"percentage\":10}",
                ReleasePlanStatus.SUCCESS,
                Instant.now(), "tester", Instant.now(), "tester", "trace"
            )));

        PackageAssetReadinessResponse readiness = service.getAssetReadiness();

        assertThat(readiness.tenantId()).isEqualTo("tenant-A");
        assertThat(readiness.ready()).isTrue();
        assertThat(readiness.templateCount()).isEqualTo(2);
        assertThat(readiness.draftPackageCount()).isEqualTo(1);
        assertThat(readiness.releasedPackageCount()).isEqualTo(2);
        assertThat(readiness.activePackageCount()).isEqualTo(1);
        assertThat(readiness.grayscaleReady()).isTrue();
        assertThat(readiness.readyPackageId()).isEqualTo("pkg-active");
        assertThat(readiness.blockers()).isEmpty();
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
            new PackageItem(1L, "i-1", "tenant-A", "pkg-base", VersionedAssetType.RULE, "rule-1", "1", Instant.now(), "tester", Instant.now(), "tester", "trace"),
            new PackageItem(2L, "i-2", "tenant-A", "pkg-base", VersionedAssetType.PATHWAY, "pathway-1", "1", Instant.now(), "tester", Instant.now(), "tester", "trace")
        );

        // 模拟新包资产：rule-1 (v2 - 更新), pathway-1 (v1 - 未变), evaluation-1 (v1 - 新增)
        List<PackageItem> targetItems = List.of(
            new PackageItem(3L, "i-3", "tenant-A", "pkg-target", VersionedAssetType.RULE, "rule-1", "2", Instant.now(), "tester", Instant.now(), "tester", "trace"),
            new PackageItem(4L, "i-4", "tenant-A", "pkg-target", VersionedAssetType.PATHWAY, "pathway-1", "1", Instant.now(), "tester", Instant.now(), "tester", "trace"),
            new PackageItem(5L, "i-5", "tenant-A", "pkg-target", VersionedAssetType.EVALUATION, "eval-1", "1", Instant.now(), "tester", Instant.now(), "tester", "trace")
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
            packageItem(1L, "pkg-target", VersionedAssetType.RULE, "rule-real", "2"),
            packageItem(2L, "pkg-target", VersionedAssetType.PATHWAY, "pathway-no-dept", "1"),
            packageItem(3L, "pkg-target", VersionedAssetType.EVALUATION, "eval-real", "1"),
            packageItem(4L, "pkg-target", VersionedAssetType.TERMINOLOGY, "term-1", "1")
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
            packageItem(1L, "pkg-base", VersionedAssetType.RULE, "rule-removed", "1"),
            packageItem(2L, "pkg-base", VersionedAssetType.EVALUATION, "eval-updated", "1")
        ));
        when(itemRepository.findByTenantIdAndPackageId("tenant-A", "pkg-target")).thenReturn(List.of(
            packageItem(3L, "pkg-target", VersionedAssetType.EVALUATION, "eval-updated", "2"),
            packageItem(4L, "pkg-target", VersionedAssetType.PATHWAY, "pathway-added", "1")
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
            assertThat(change.assetType()).isEqualTo(VersionedAssetType.RULE);
            assertThat(change.assetId()).isEqualTo("rule-removed");
            assertThat(change.baseVersion()).isEqualTo("1");
            assertThat(change.targetVersion()).isNull();
        });
        assertThat(response.changes()).anySatisfy(change -> {
            assertThat(change.changeType()).isEqualTo(PackageDiffChangeType.UPDATED);
            assertThat(change.assetType()).isEqualTo(VersionedAssetType.EVALUATION);
            assertThat(change.assetId()).isEqualTo("eval-updated");
            assertThat(change.baseVersion()).isEqualTo("1");
            assertThat(change.targetVersion()).isEqualTo("2");
        });
        assertThat(response.changes()).anySatisfy(change -> {
            assertThat(change.changeType()).isEqualTo(PackageDiffChangeType.ADDED);
            assertThat(change.assetType()).isEqualTo(VersionedAssetType.PATHWAY);
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
            packageItem(1L, "pkg-target", VersionedAssetType.RULE, "rule-added", "2")
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
    void exportOfflinePackageReturnsPayloadSha256AndPublishesAudit() throws Exception {
        KnowledgePackage pack = packageVersion("pkg-offline", "3.0.0", KnowledgePackageStatus.PUBLISHED);
        List<PackageItem> items = List.of(
            packageItem(1L, "pkg-offline", VersionedAssetType.RULE, "rule-stable", "2"),
            packageItem(2L, "pkg-offline", VersionedAssetType.EVALUATION, "eval-stable", "1")
        );
        when(packageRepository.findByPackageIdAndTenantId("pkg-offline", "tenant-A"))
            .thenReturn(Optional.of(pack));
        when(itemRepository.findByTenantIdAndPackageId("tenant-A", "pkg-offline"))
            .thenReturn(items);
        when(ruleRepository.findByRuleIdAndTenantId("rule-stable", "tenant-A"))
            .thenReturn(Optional.of(publishedRule("rule-stable", "dept-rule")));
        when(ruleVersionRepository.findByRuleIdAndTenantIdAndVersionNo("rule-stable", "tenant-A", 2))
            .thenReturn(Optional.of(publishedRuleVersion("rule-stable", "rule-version-2", 2)));
        when(evaluationRepository.findByIndicatorIdAndTenantId("eval-stable", "tenant-A"))
            .thenReturn(Optional.of(publishedIndicator("eval-stable", "dept-eval")));

        String exportJson = service.exportOfflinePackage("pkg-offline");

        JsonNode root = TEST_MAPPER.readTree(exportJson);
        assertThat(root.path("format").asText()).isEqualTo("MEDKERNEL_PACKAGE_OFFLINE_V1");
        assertThat(root.path("manifest").path("packageId").asText()).isEqualTo("pkg-offline");
        assertThat(root.path("manifest").path("tenantId").asText()).isEqualTo("tenant-A");
        assertThat(root.path("manifest").path("packageCode").asText()).isEqualTo("PKG.TEST");
        assertThat(root.path("manifest").path("packageVersion").asText()).isEqualTo("3.0.0");
        assertThat(root.path("manifest").path("itemCount").asInt()).isEqualTo(2);
        assertThat(root.path("manifest").path("assetSnapshotCount").asInt()).isEqualTo(2);
        assertThat(root.path("manifest").path("traceId").asText()).isEqualTo("trace-pkg");
        assertThat(root.path("manifest").path("exportedAt").asText()).isNotBlank();

        String payloadJson = TEST_MAPPER.writeValueAsString(root.path("payload"));
        String expectedSha256 = HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(payloadJson.getBytes(StandardCharsets.UTF_8)));
        assertThat(root.path("manifest").path("payloadSha256").asText())
            .matches("[a-f0-9]{64}")
            .isEqualTo(expectedSha256);
        assertThat(root.path("payload").path("packageInfo").path("packageId").asText())
            .isEqualTo("pkg-offline");
        assertThat(root.path("payload").path("items")).hasSize(2);
        JsonNode snapshots = root.path("payload").path("assetSnapshots");
        assertThat(snapshots).hasSize(2);
        assertThat(snapshots).anySatisfy(snapshot -> {
            assertThat(snapshot.path("assetType").asText()).isEqualTo("RULE");
            assertThat(snapshot.path("assetId").asText()).isEqualTo("rule-stable");
            assertThat(snapshot.path("assetVersion").asText()).isEqualTo("2");
            assertThat(snapshot.path("content").path("version").path("dslJson").asText()).contains("rule-stable");
            assertThat(snapshot.path("contentSha256").asText())
                .isEqualTo(sha256Node(snapshot.path("content")));
        });
        assertThat(snapshots).anySatisfy(snapshot -> {
            assertThat(snapshot.path("assetType").asText()).isEqualTo("EVALUATION");
            assertThat(snapshot.path("assetId").asText()).isEqualTo("eval-stable");
            assertThat(snapshot.path("assetVersion").asText()).isEqualTo("1");
            assertThat(snapshot.path("content").path("indicator").path("denominatorDefinition").asText())
                .isEqualTo("denominator");
            assertThat(snapshot.path("contentSha256").asText())
                .isEqualTo(sha256Node(snapshot.path("content")));
        });
        verify(auditPublisher).publish(eq(AuditAction.EXPORT), eq("knowledge_package"), eq("pkg-offline"), any());
    }

    @Test
    void exportOfflinePackageIncludesKnowledgeAndTerminologySnapshots() throws Exception {
        KnowledgePackage pack = packageVersion("pkg-all-assets", "3.1.0", KnowledgePackageStatus.PUBLISHED);
        List<PackageItem> items = List.of(
            packageItem(1L, "pkg-all-assets", VersionedAssetType.KNOWLEDGE, "KNOW.COPD.GUIDE", "v1"),
            packageItem(2L, "pkg-all-assets", VersionedAssetType.TERMINOLOGY, "TERM.LAB|DEPARTMENT|CARD", "2026.06")
        );
        TermMappingPackage terminologyPackage = publishedTerminologyPackage(
            301L, "TERM.LAB", "2026.06", "DEPARTMENT", "CARD");
        TermMapping termMapping = publishedTermMapping(401L);
        TermMappingPackageItem terminologyItem = new TermMappingPackageItem(
            501L, "tenant-A", 301L, 401L,
            "{\"mappingId\":401,\"localTermId\":11,\"standardTermId\":22,\"status\":\"CONFIRMED\"}",
            Instant.parse("2026-06-01T00:00:00Z"), "tester"
        );

        when(packageRepository.findByPackageIdAndTenantId("pkg-all-assets", "tenant-A"))
            .thenReturn(Optional.of(pack));
        when(itemRepository.findByTenantIdAndPackageId("tenant-A", "pkg-all-assets"))
            .thenReturn(items);
        when(knowledgeIdentityRepository.findByTenantIdAndIdentityCode("tenant-A", "KNOW.COPD.GUIDE"))
            .thenReturn(Optional.of(activeKnowledgeIdentity(101L, "KNOW.COPD.GUIDE", 201L)));
        when(knowledgeVersionRepository.findByTenantIdAndIdentityIdAndVersionNo("tenant-A", 101L, "v1"))
            .thenReturn(Optional.of(activeKnowledgeVersion(201L, 101L, "v1")));
        when(terminologyPackageRepository.findByTenantIdAndPackageCodeAndPackageVersionAndScopeLevelAndScopeCode(
                "tenant-A", "TERM.LAB", "2026.06", "DEPARTMENT", "CARD"))
            .thenReturn(Optional.of(terminologyPackage));
        when(terminologyPackageItemRepository.findByTenantIdAndPackageId("tenant-A", 301L))
            .thenReturn(List.of(terminologyItem));
        when(terminologyMappingRepository.findByTenantIdAndId("tenant-A", 401L))
            .thenReturn(Optional.of(termMapping));

        String exportJson = service.exportOfflinePackage("pkg-all-assets");

        JsonNode snapshots = TEST_MAPPER.readTree(exportJson).path("payload").path("assetSnapshots");
        assertThat(snapshots).hasSize(2);
        assertThat(snapshots).anySatisfy(snapshot -> {
            assertThat(snapshot.path("assetType").asText()).isEqualTo("KNOWLEDGE");
            assertThat(snapshot.path("assetId").asText()).isEqualTo("KNOW.COPD.GUIDE");
            assertThat(snapshot.path("content").path("identity").path("identityCode").asText())
                .isEqualTo("KNOW.COPD.GUIDE");
            assertThat(snapshot.path("content").path("version").path("versionNo").asText()).isEqualTo("v1");
            assertThat(snapshot.path("contentSha256").asText()).isEqualTo(sha256Node(snapshot.path("content")));
        });
        assertThat(snapshots).anySatisfy(snapshot -> {
            assertThat(snapshot.path("assetType").asText()).isEqualTo("TERMINOLOGY");
            assertThat(snapshot.path("assetId").asText()).isEqualTo("TERM.LAB|DEPARTMENT|CARD");
            assertThat(snapshot.path("content").path("terminologyPackage").path("packageCode").asText())
                .isEqualTo("TERM.LAB");
            assertThat(snapshot.path("content").path("mappings")).hasSize(1);
            assertThat(snapshot.path("content").path("mappings").get(0).path("localTermId").asLong())
                .isEqualTo(11L);
            assertThat(snapshot.path("contentSha256").asText()).isEqualTo(sha256Node(snapshot.path("content")));
        });
    }

    @Test
    void importOfflinePackagePersistsDraftWithVerifiedPayloadAndNewLocalIds() throws Exception {
        String offlineJson = offlinePackageJson("PKG.IMPORT", "2026.06.01");
        when(packageRepository.findByTenantIdAndPackageCodeAndPackageVersion(
            "tenant-A", "PKG.IMPORT", "2026.06.01"))
            .thenReturn(Optional.empty());
        when(ruleRepository.findByRuleIdAndTenantId("rule-stable", "tenant-A"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(publishedRule("rule-stable", "dept-rule")));
        when(evaluationRepository.findByIndicatorIdAndTenantId("eval-stable", "tenant-A"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(publishedIndicator("eval-stable", "dept-eval")));

        PackageOfflineImportResponse response = service.importOfflinePackage(
            new PackageOfflineImportRequest(offlineJson));

        assertThat(response.packageCode()).isEqualTo("PKG.IMPORT");
        assertThat(response.packageVersion()).isEqualTo("2026.06.01");
        assertThat(response.status()).isEqualTo(KnowledgePackageStatus.DRAFT);
        assertThat(response.itemCount()).isEqualTo(2);
        assertThat(response.payloadSha256()).matches("[a-f0-9]{64}");

        ArgumentCaptor<KnowledgePackage> packCap = ArgumentCaptor.forClass(KnowledgePackage.class);
        verify(packageRepository).save(packCap.capture());
        KnowledgePackage importedPack = packCap.getValue();
        assertThat(importedPack.packageId()).isNotEqualTo("pkg-source");
        assertThat(importedPack.packageCode()).isEqualTo("PKG.IMPORT");
        assertThat(importedPack.status()).isEqualTo(KnowledgePackageStatus.DRAFT);
        assertThat(importedPack.tenantId()).isEqualTo("tenant-A");
        assertThat(importedPack.createdBy()).isEqualTo("tester");

        ArgumentCaptor<PackageItem> itemCap = ArgumentCaptor.forClass(PackageItem.class);
        verify(itemRepository, org.mockito.Mockito.times(2)).save(itemCap.capture());
        assertThat(itemCap.getAllValues()).allSatisfy(item -> {
            assertThat(item.packageId()).isEqualTo(importedPack.packageId());
            assertThat(item.tenantId()).isEqualTo("tenant-A");
            assertThat(item.itemId()).doesNotStartWith("source-item-");
        });
        assertThat(itemCap.getAllValues()).extracting(PackageItem::assetId)
            .containsExactly("rule-stable", "eval-stable");

        ArgumentCaptor<RuleDefinition> ruleCap = ArgumentCaptor.forClass(RuleDefinition.class);
        verify(ruleRepository).save(ruleCap.capture());
        assertThat(ruleCap.getValue().ruleId()).isEqualTo("rule-stable");
        assertThat(ruleCap.getValue().tenantId()).isEqualTo("tenant-A");
        assertThat(ruleCap.getValue().status()).isEqualTo(RuleDefinitionStatus.PUBLISHED);

        ArgumentCaptor<RuleVersion> versionCap = ArgumentCaptor.forClass(RuleVersion.class);
        verify(ruleVersionRepository).save(versionCap.capture());
        assertThat(versionCap.getValue().ruleId()).isEqualTo("rule-stable");
        assertThat(versionCap.getValue().versionNo()).isEqualTo(2);
        assertThat(versionCap.getValue().dslJson()).contains("rule-stable");

        ArgumentCaptor<EvaluationIndicator> indicatorCap = ArgumentCaptor.forClass(EvaluationIndicator.class);
        verify(evaluationRepository).save(indicatorCap.capture());
        assertThat(indicatorCap.getValue().indicatorId()).isEqualTo("eval-stable");
        assertThat(indicatorCap.getValue().tenantId()).isEqualTo("tenant-A");
        assertThat(indicatorCap.getValue().denominatorDefinition()).isEqualTo("denominator");
        verify(auditPublisher).publish(eq(AuditAction.IMPORT), eq("knowledge_package"), eq(importedPack.packageId()), any());
    }

    @Test
    void importOfflinePackagePersistsKnowledgeAndTerminologySnapshots() throws Exception {
        ArrayNode items = TEST_MAPPER.createArrayNode();
        items.add(offlineItem("tenant-A", "source-item-1", "KNOWLEDGE", "KNOW.COPD.GUIDE", "v1"));
        items.add(offlineItem("tenant-A", "source-item-2", "TERMINOLOGY", "TERM.LAB|DEPARTMENT|CARD", "2026.06"));

        ArrayNode snapshots = TEST_MAPPER.createArrayNode();
        snapshots.add(offlineSnapshot("KNOWLEDGE", "KNOW.COPD.GUIDE", "v1",
            offlineKnowledgeContent("KNOW.COPD.GUIDE", "v1")));
        snapshots.add(offlineSnapshot("TERMINOLOGY", "TERM.LAB|DEPARTMENT|CARD", "2026.06",
            offlineTerminologyContent()));
        String offlineJson = offlinePackageJson("PKG.ALL.IMPORT", "2026.06.03", "tenant-A", items, snapshots);

        when(packageRepository.findByTenantIdAndPackageCodeAndPackageVersion(
            "tenant-A", "PKG.ALL.IMPORT", "2026.06.03"))
            .thenReturn(Optional.empty());
        when(knowledgeIdentityRepository.findByTenantIdAndIdentityCode("tenant-A", "KNOW.COPD.GUIDE"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(activeKnowledgeIdentity(101L, "KNOW.COPD.GUIDE", 201L)));
        when(knowledgeVersionRepository.findByTenantIdAndIdentityIdAndVersionNo("tenant-A", 101L, "v1"))
            .thenReturn(Optional.of(activeKnowledgeVersion(201L, 101L, "v1")));
        when(terminologyPackageRepository.findByTenantIdAndPackageCodeAndPackageVersionAndScopeLevelAndScopeCode(
                "tenant-A", "TERM.LAB", "2026.06", "DEPARTMENT", "CARD"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(publishedTerminologyPackage(
                301L, "TERM.LAB", "2026.06", "DEPARTMENT", "CARD")));
        when(terminologyMappingRepository.findByTenantIdAndLocalTermIdAndStandardTermId("tenant-A", 11L, 22L))
            .thenReturn(Optional.empty());

        PackageOfflineImportResponse response = service.importOfflinePackage(
            new PackageOfflineImportRequest(offlineJson));

        assertThat(response.packageCode()).isEqualTo("PKG.ALL.IMPORT");
        assertThat(response.itemCount()).isEqualTo(2);
        verify(knowledgeIdentityRepository, org.mockito.Mockito.atLeastOnce()).save(any(KnowledgeIdentity.class));
        verify(knowledgeVersionRepository).save(any(KnowledgeAssetVersion.class));
        verify(terminologyPackageRepository).save(any(TermMappingPackage.class));
        verify(terminologyMappingRepository).save(any(TermMapping.class));
        verify(terminologyPackageItemRepository).save(any(TermMappingPackageItem.class));

        ArgumentCaptor<PackageItem> itemCap = ArgumentCaptor.forClass(PackageItem.class);
        verify(itemRepository, org.mockito.Mockito.times(2)).save(itemCap.capture());
        assertThat(itemCap.getAllValues()).extracting(PackageItem::assetType)
            .containsExactly(VersionedAssetType.KNOWLEDGE, VersionedAssetType.TERMINOLOGY);
    }

    @Test
    void importOfflinePackageRejectsInvalidTerminologyMappingEnumBeforePersisting() throws Exception {
        ArrayNode items = TEST_MAPPER.createArrayNode();
        items.add(offlineItem("tenant-A", "source-item-1", "TERMINOLOGY", "TERM.LAB|DEPARTMENT|CARD", "2026.06"));

        ObjectNode terminologyContent = offlineTerminologyContent();
        ((ObjectNode) terminologyContent.path("mappings").get(0)).put("riskLevel", "DANGER");
        ArrayNode snapshots = TEST_MAPPER.createArrayNode();
        snapshots.add(offlineSnapshot("TERMINOLOGY", "TERM.LAB|DEPARTMENT|CARD", "2026.06",
            terminologyContent));
        String offlineJson = offlinePackageJson("PKG.BAD.TERM", "2026.06.03", "tenant-A", items, snapshots);

        when(packageRepository.findByTenantIdAndPackageCodeAndPackageVersion(
            "tenant-A", "PKG.BAD.TERM", "2026.06.03"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.importOfflinePackage(new PackageOfflineImportRequest(offlineJson)))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> {
                ApiException api = (ApiException) ex;
                assertThat(api.errorCode()).isEqualTo(ErrorCode.ENG_PACKAGE_002);
                assertThat(api.getMessage())
                    .contains("离线包术语映射枚举不合法")
                    .contains("TermRiskLevel=DANGER");
            });

        verify(packageRepository, never()).save(any());
        verify(terminologyPackageRepository, never()).save(any());
        verify(terminologyMappingRepository, never()).save(any());
        verify(itemRepository, never()).save(any());
    }

    @Test
    void importOfflinePackageKeepsPlatformSourceAsReferenceWithoutCreatingCustomerAssetRows() throws Exception {
        String offlineJson = offlinePackageJson("PKG.PLATFORM", "2026.06.01", "t-1");
        when(packageRepository.findByTenantIdAndPackageCodeAndPackageVersion(
            "tenant-A", "PKG.PLATFORM", "2026.06.01"))
            .thenReturn(Optional.empty());

        PackageOfflineImportResponse response = service.importOfflinePackage(
            new PackageOfflineImportRequest(offlineJson));

        assertThat(response.status()).isEqualTo(KnowledgePackageStatus.DRAFT);
        ArgumentCaptor<KnowledgePackage> packCap = ArgumentCaptor.forClass(KnowledgePackage.class);
        verify(packageRepository).save(packCap.capture());
        assertThat(packCap.getValue().tenantId()).isEqualTo("tenant-A");
        assertThat(packCap.getValue().packageId()).isNotEqualTo("pkg-source");

        ArgumentCaptor<PackageItem> itemCap = ArgumentCaptor.forClass(PackageItem.class);
        verify(itemRepository, org.mockito.Mockito.times(2)).save(itemCap.capture());
        assertThat(itemCap.getAllValues()).allSatisfy(item ->
            assertThat(item.tenantId()).isEqualTo("tenant-A"));
        verify(ruleRepository, never()).save(any());
        verify(ruleVersionRepository, never()).save(any());
        verify(evaluationRepository, never()).save(any());
        verify(ruleRepository, never()).findByRuleIdAndTenantId("rule-stable", "tenant-A");
        verify(evaluationRepository, never()).findByIndicatorIdAndTenantId("eval-stable", "tenant-A");
    }

    @Test
    void importOfflinePackageDoesNotOverwriteCustomerLocalOverrideFromPlatformSource() throws Exception {
        String offlineJson = offlinePackageJson("PKG.PLATFORM.OVERRIDE", "2026.06.01", "t-1");
        when(packageRepository.findByTenantIdAndPackageCodeAndPackageVersion(
            "tenant-A", "PKG.PLATFORM.OVERRIDE", "2026.06.01"))
            .thenReturn(Optional.empty());

        PackageOfflineImportResponse response = service.importOfflinePackage(
            new PackageOfflineImportRequest(offlineJson));

        assertThat(response.status()).isEqualTo(KnowledgePackageStatus.DRAFT);
        verify(ruleRepository, never()).save(any());
        verify(ruleVersionRepository, never()).save(any());
        verify(evaluationRepository, never()).save(any());
        verify(ruleRepository, never()).findByRuleIdAndTenantId("rule-stable", "tenant-A");
        verify(evaluationRepository, never()).findByIndicatorIdAndTenantId("eval-stable", "tenant-A");
    }

    @Test
    void importOfflinePackageRejectsCustomerSourceIntoPlatformTenant() throws Exception {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-pkg", OrgScope.tenant("t-1"), "platform-admin"));
        String offlineJson = offlinePackageJson("PKG.CUSTOMER", "2026.06.01", "tenant-A");

        assertThatThrownBy(() -> service.importOfflinePackage(
            new PackageOfflineImportRequest(offlineJson)))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.TENANT_FORBIDDEN);

        verify(packageRepository, never()).save(any());
        verify(itemRepository, never()).save(any());
    }

    @Test
    void importOfflinePackageRejectsCustomerSourceIntoAnotherCustomerTenant() throws Exception {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-pkg", OrgScope.tenant("tenant-B"), "customer-admin"));
        String offlineJson = offlinePackageJson("PKG.CUSTOMER", "2026.06.01", "tenant-A");

        assertThatThrownBy(() -> service.importOfflinePackage(
            new PackageOfflineImportRequest(offlineJson)))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.TENANT_FORBIDDEN);

        verify(packageRepository, never()).save(any());
        verify(itemRepository, never()).save(any());
    }

    @Test
    void importOfflinePackageRejectsTamperedPayloadSha256BeforePersisting() throws Exception {
        ObjectNode root = (ObjectNode) TEST_MAPPER.readTree(offlinePackageJson("PKG.TAMPER", "2026.06.01"));
        ((ObjectNode) root.path("payload").path("packageInfo")).put("packageVersion", "2026.06.02");

        assertThatThrownBy(() -> service.importOfflinePackage(
            new PackageOfflineImportRequest(TEST_MAPPER.writeValueAsString(root))))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> assertThat(((ApiException) ex).errorCode()).isEqualTo(ErrorCode.ENG_EVID_002));

        verify(packageRepository, never()).save(any());
        verify(itemRepository, never()).save(any());
    }

    @Test
    void importOfflinePackageRejectsMalformedAssetTimestampAsApiError() throws Exception {
        ObjectNode root = (ObjectNode) TEST_MAPPER.readTree(offlinePackageJson("PKG.BAD_TIME", "2026.06.01"));
        ObjectNode ruleSnapshot = (ObjectNode) root.path("payload").path("assetSnapshots").get(0);
        ObjectNode ruleContent = (ObjectNode) ruleSnapshot.path("content");
        ((ObjectNode) ruleContent.path("version")).put("publishedAt", "bad-time");
        ruleSnapshot.put("contentSha256", sha256Node(ruleContent));
        ((ObjectNode) root.path("manifest")).put("payloadSha256", sha256Node(root.path("payload")));

        when(packageRepository.findByTenantIdAndPackageCodeAndPackageVersion(
            "tenant-A", "PKG.BAD_TIME", "2026.06.01"))
            .thenReturn(Optional.empty());
        when(ruleRepository.findByRuleIdAndTenantId("rule-stable", "tenant-A"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.importOfflinePackage(
            new PackageOfflineImportRequest(TEST_MAPPER.writeValueAsString(root))))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> {
                ApiException api = (ApiException) ex;
                assertThat(api.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                assertThat(api.getMessage()).contains("离线包时间格式不合法");
            });

        verify(packageRepository, never()).save(any());
        verify(itemRepository, never()).save(any());
    }

    @Test
    void calculateDiffDoesNotForgeDepartmentWhenAssetLookupFails() {
        KnowledgePackage targetPack = packageVersion("pkg-target", "2.0.0", KnowledgePackageStatus.DRAFT);

        when(packageRepository.findByPackageIdAndTenantId("pkg-target", "tenant-A")).thenReturn(Optional.of(targetPack));
        when(itemRepository.findByTenantIdAndPackageId("tenant-A", "pkg-target")).thenReturn(List.of(
            packageItem(1L, "pkg-target", VersionedAssetType.RULE, "rule-broken", "2")
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
        stubPackageReadyForRelease("pkg-1");

        SyncTarget target = new SyncTarget(
            1L, "target-1", "tenant-A", "同步目标", SyncTargetType.DIFY, "config",
            SyncTargetStatus.ACTIVE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(targetRepository.findByTargetIdAndTenantId("target-1", "tenant-A"))
            .thenReturn(Optional.of(target));

        when(syncPort.sync(eq("tenant-A"), any(ReleasePlan.class), eq(target)))
            .thenReturn("EVIDENCE-DIFY-001");

        PackageSyncResponse response = service.syncPackage("pkg-1", packageSyncRequest(
            "org-1", ReleaseStrategy.FULL, ReleaseScopeType.ALL, null,
            List.of("target-1"), List.of("hospital-admin")
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
        stubPackageReadyForRelease("pkg-1");

        SyncTarget target = new SyncTarget(
            1L, "target-1", "tenant-A", "图谱同步", SyncTargetType.GRAPH_DB, null,
            SyncTargetStatus.ACTIVE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(targetRepository.findByTargetIdAndTenantId("target-1", "tenant-A"))
            .thenReturn(Optional.of(target));

        when(syncPort.sync(eq("tenant-A"), any(ReleasePlan.class), eq(target)))
            .thenThrow(new PackageSyncNotConnectedException("NOT_SYNCED：未配置真实同步适配器"));

        PackageSyncResponse response = service.syncPackage("pkg-1", packageSyncRequest(
            "org-1", ReleaseStrategy.FULL, ReleaseScopeType.ALL, null,
            List.of("target-1"), List.of("hospital-admin")
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
    void releasePackageUsesExistingSyncStateMachine() throws Exception {
        KnowledgePackage pack = new KnowledgePackage(
            1L, "pkg-1", "tenant-A", "PKG.COPD", "1.0.0", "包草稿", null,
            KnowledgePackageStatus.PUBLISHED, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(packageRepository.findByPackageIdAndTenantId("pkg-1", "tenant-A"))
            .thenReturn(Optional.of(pack));
        stubPackageReadyForRelease("pkg-1");

        SyncTarget target = new SyncTarget(
            1L, "target-1", "tenant-A", "院内配置库", SyncTargetType.CLINICAL_DB, "config",
            SyncTargetStatus.ACTIVE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(targetRepository.findByTargetIdAndTenantId("target-1", "tenant-A"))
            .thenReturn(Optional.of(target));
        when(syncPort.sync(eq("tenant-A"), any(ReleasePlan.class), eq(target)))
            .thenReturn("EVIDENCE-RELEASE-001");

        PackageSyncResponse response = service.releasePackage("pkg-1", packageSyncRequest(
            "org-1", ReleaseStrategy.FULL, ReleaseScopeType.ALL, null,
            List.of("target-1"), List.of("hospital-admin")
        ));

        assertThat(response.status()).isEqualTo(ReleasePlanStatus.SUCCESS);
        assertThat(response.logs()).hasSize(1);
        assertThat(response.logs().get(0).syncEvidence()).isEqualTo("EVIDENCE-RELEASE-001");
    }

    @Test
    void releasePackageRejectsDirectFullForNonHospitalAdminRole() throws Exception {
        KnowledgePackage pack = new KnowledgePackage(
            1L, "pkg-full-denied", "tenant-A", "PKG.COPD", "1.0.0", "待全量包", null,
            KnowledgePackageStatus.PUBLISHED, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(packageRepository.findByPackageIdAndTenantId("pkg-full-denied", "tenant-A"))
            .thenReturn(Optional.of(pack));
        stubPackageReadyForRelease("pkg-full-denied");

        PackageSyncRequest request = packageSyncRequest(
            "org-1",
            ReleaseStrategy.FULL,
            ReleaseScopeType.ALL,
            null,
            List.of("target-1"),
            List.of("implementation-engineer")
        );

        assertThatThrownBy(() -> service.releasePackage("pkg-full-denied", request))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_PACKAGE_002);

        verify(planRepository, never()).save(any(ReleasePlan.class));
        verify(syncPort, never()).sync(any(), any(), any());
    }

    @Test
    void releasePackageDefaultsGrayscaleToTenPercentBedScopeWhenNoScopeProvided() throws Exception {
        KnowledgePackage pack = new KnowledgePackage(
            1L, "pkg-gray-default", "tenant-A", "PKG.COPD", "1.0.0", "灰度包", null,
            KnowledgePackageStatus.DRAFT, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(packageRepository.findByPackageIdAndTenantId("pkg-gray-default", "tenant-A"))
            .thenReturn(Optional.of(pack));
        stubPackageReadyForRelease("pkg-gray-default");

        SyncTarget target = new SyncTarget(
            1L, "target-1", "tenant-A", "院内配置库", SyncTargetType.CLINICAL_DB, "config",
            SyncTargetStatus.ACTIVE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(targetRepository.findByTargetIdAndTenantId("target-1", "tenant-A"))
            .thenReturn(Optional.of(target));
        when(syncPort.sync(eq("tenant-A"), any(ReleasePlan.class), eq(target)))
            .thenReturn("EVIDENCE-GRAY-001");

        PackageSyncResponse response = service.releasePackage("pkg-gray-default", packageSyncRequest(
            "hospital-1",
            ReleaseStrategy.GRAYSCALE,
            ReleaseScopeType.ALL,
            null,
            List.of("target-1"),
            List.of("implementation-engineer")
        ));

        assertThat(response.status()).isEqualTo(ReleasePlanStatus.SUCCESS);

        ArgumentCaptor<ReleasePlan> planCap = ArgumentCaptor.forClass(ReleasePlan.class);
        verify(planRepository, org.mockito.Mockito.times(2)).save(planCap.capture());
        ReleasePlan executingPlan = planCap.getAllValues().get(0);
        assertThat(executingPlan.scopeType()).isEqualTo(ReleaseScopeType.HOSPITAL);
        assertThat(executingPlan.scopeValue())
            .contains("\"strategy\":\"BED_PERCENT\"")
            .contains("\"percentage\":10")
            .contains("\"scopeCode\":\"hospital-1\"");
        verify(packageRepository).save(argThat(saved ->
            saved.packageId().equals("pkg-gray-default") && saved.status() == KnowledgePackageStatus.PUBLISHED));
    }

    @Test
    void releasePackageBlocksWhenValidationHasBlockingIssues() throws Exception {
        KnowledgePackage pack = new KnowledgePackage(
            1L, "pkg-bad", "tenant-A", "PKG.TERM", "1.0.0", "术语配置包", null,
            KnowledgePackageStatus.PUBLISHED, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        PackageItem item = new PackageItem(
            10L, "item-1", "tenant-A", "pkg-bad", VersionedAssetType.TERMINOLOGY, "term-map-1", "1",
            Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(packageRepository.findByPackageIdAndTenantId("pkg-bad", "tenant-A"))
            .thenReturn(Optional.of(pack));
        when(itemRepository.findByTenantIdAndPackageId("tenant-A", "pkg-bad"))
            .thenReturn(List.of(item));
        when(targetRepository.findByTargetIdAndTenantId("target-1", "tenant-A"))
            .thenReturn(Optional.of(new SyncTarget(
                1L, "target-1", "tenant-A", "院内配置库", SyncTargetType.CLINICAL_DB, "config",
                SyncTargetStatus.ACTIVE, Instant.now(), "tester", Instant.now(), "tester", "trace"
            )));

        assertThatThrownBy(() -> service.releasePackage("pkg-bad", packageSyncRequest(
            "org-1", ReleaseStrategy.FULL, ReleaseScopeType.ALL, null,
            List.of("target-1"), List.of("hospital-admin")
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("配置包发布前校验未通过")
            .hasMessageContaining("术语映射包资产 ID 必须为 packageCode|scopeLevel|scopeCode");
        verify(planRepository, never()).save(any(ReleasePlan.class));
        verify(syncPort, never()).sync(any(), any(), any());
    }

    @Test
    void listSyncLogsReturnsOnlyPersistedLogsForPackageReleasePlans() {
        ReleasePlan plan = new ReleasePlan(
            10L, "plan-1", "tenant-A", "pkg-1", "org-1",
            ReleaseStrategy.FULL, ReleaseScopeType.ALL, null, ReleasePlanStatus.NOT_SYNCED,
            Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        SyncLog log = new SyncLog(
            20L, "log-1", "tenant-A", "plan-1", "target-1",
            SyncLogStatus.NOT_SYNCED, "NOT_SYNCED", "未配置真实同步适配器", 0, null,
            Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(packageRepository.findByPackageIdAndTenantId("pkg-1", "tenant-A"))
            .thenReturn(Optional.of(new KnowledgePackage(
                1L, "pkg-1", "tenant-A", "PKG.COPD", "1.0.0", "配置包", null,
                KnowledgePackageStatus.PUBLISHED, Instant.now(), "tester", Instant.now(), "tester", "trace"
            )));
        when(planRepository.findByTenantIdAndPackageIdOrderByCreatedAtDesc("tenant-A", "pkg-1"))
            .thenReturn(List.of(plan));
        when(logRepository.findByTenantIdAndPlanId("tenant-A", "plan-1"))
            .thenReturn(List.of(log));

        List<SyncLogResponse> response = service.listSyncLogs("pkg-1");

        assertThat(response).hasSize(1);
        assertThat(response.get(0).planId()).isEqualTo("plan-1");
        assertThat(response.get(0).status()).isEqualTo(SyncLogStatus.NOT_SYNCED);
        assertThat(response.get(0).syncEvidence()).isNull();
    }

    @Test
    void exportSyncEvidenceIncludesFailedSitesAndDoesNotForgeEvidence() throws Exception {
        KnowledgePackage pack = new KnowledgePackage(
            1L, "pkg-1", "tenant-A", "PKG.COPD", "1.0.0", "配置包", null,
            KnowledgePackageStatus.PUBLISHED, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        ReleasePlan plan = new ReleasePlan(
            10L, "plan-1", "tenant-A", "pkg-1", "hospital-1",
            ReleaseStrategy.GRAYSCALE, ReleaseScopeType.HOSPITAL,
            "{\"strategy\":\"BED_PERCENT\",\"percentage\":10,\"scopeCode\":\"hospital-1\"}",
            ReleasePlanStatus.FAILED, Instant.now(), "tester", Instant.now(), "tester", "trace-plan"
        );
        SyncLog successLog = new SyncLog(
            20L, "log-ok", "tenant-A", "plan-1", "target-ok",
            SyncLogStatus.SUCCESS, null, null, 0, "EVIDENCE-OK",
            Instant.now(), "tester", Instant.now(), "tester", "trace-ok"
        );
        SyncLog failedLog = new SyncLog(
            21L, "log-fail", "tenant-A", "plan-1", "target-fail",
            SyncLogStatus.FAILED, "ENG-PACKAGE-005", "目标库写入失败", 0, null,
            Instant.now(), "tester", Instant.now(), "tester", "trace-fail"
        );
        SyncLog notSyncedLog = new SyncLog(
            22L, "log-not-synced", "tenant-A", "plan-1", "target-offline",
            SyncLogStatus.NOT_SYNCED, "NOT_SYNCED", "未配置真实同步适配器", 0, null,
            Instant.now(), "tester", Instant.now(), "tester", "trace-offline"
        );
        when(packageRepository.findByPackageIdAndTenantId("pkg-1", "tenant-A"))
            .thenReturn(Optional.of(pack));
        when(planRepository.findByTenantIdAndPackageIdOrderByCreatedAtDesc("tenant-A", "pkg-1"))
            .thenReturn(List.of(plan));
        when(logRepository.findByTenantIdAndPlanId("tenant-A", "plan-1"))
            .thenReturn(List.of(successLog, failedLog, notSyncedLog));
        when(targetRepository.findByTargetIdAndTenantId("target-ok", "tenant-A"))
            .thenReturn(Optional.of(syncTarget("target-ok", "院内规则库", SyncTargetType.CLINICAL_DB)));
        when(targetRepository.findByTargetIdAndTenantId("target-fail", "tenant-A"))
            .thenReturn(Optional.of(syncTarget("target-fail", "图谱同步", SyncTargetType.GRAPH_DB)));
        when(targetRepository.findByTargetIdAndTenantId("target-offline", "tenant-A"))
            .thenReturn(Optional.empty());

        String ndjson = service.exportSyncEvidence("pkg-1");
        List<JsonNode> lines = ndjson.lines()
            .map(line -> {
                try {
                    return TEST_MAPPER.readTree(line);
                } catch (Exception e) {
                    throw new AssertionError("同步证据导出必须是合法 JSONL", e);
                }
            })
            .toList();

        assertThat(lines).hasSize(5);
        JsonNode summary = lines.get(0);
        assertThat(summary.path("event").asText()).isEqualTo("PACKAGE_SYNC_EVIDENCE_SUMMARY");
        assertThat(summary.path("successTargetCount").asInt()).isEqualTo(1);
        assertThat(summary.path("failedTargetCount").asInt()).isEqualTo(1);
        assertThat(summary.path("notSyncedTargetCount").asInt()).isEqualTo(1);
        assertThat(lines).anySatisfy(line -> {
            assertThat(line.path("event").asText()).isEqualTo("PACKAGE_SYNC_PLAN");
            assertThat(line.path("planId").asText()).isEqualTo("plan-1");
            assertThat(line.path("scopeValue").asText()).contains("BED_PERCENT");
        });
        JsonNode failedLine = lines.stream()
            .filter(line -> "target-fail".equals(line.path("targetId").asText()))
            .findFirst()
            .orElseThrow();
        assertThat(failedLine.path("event").asText()).isEqualTo("PACKAGE_SYNC_TARGET");
        assertThat(failedLine.path("targetName").asText()).isEqualTo("图谱同步");
        assertThat(failedLine.path("status").asText()).isEqualTo("FAILED");
        assertThat(failedLine.path("errorMessage").asText()).contains("目标库写入失败");
        assertThat(failedLine.hasNonNull("syncEvidence")).isFalse();
        JsonNode notSyncedLine = lines.stream()
            .filter(line -> "target-offline".equals(line.path("targetId").asText()))
            .findFirst()
            .orElseThrow();
        assertThat(notSyncedLine.path("status").asText()).isEqualTo("NOT_SYNCED");
        assertThat(notSyncedLine.path("targetName").asText()).isEqualTo("target-offline");
        assertThat(notSyncedLine.hasNonNull("syncEvidence")).isFalse();
        assertThat(lines).anySatisfy(line -> {
            assertThat(line.path("targetId").asText()).isEqualTo("target-ok");
            assertThat(line.path("syncEvidence").asText()).isEqualTo("EVIDENCE-OK");
        });
        verify(auditPublisher).publish(eq(AuditAction.EXPORT), eq("knowledge_package"), eq("pkg-1"),
            argThat(message -> message.contains("导出配置包同步证据")
                && message.contains("失败站点数: 1")
                && message.contains("未接入站点数: 1")));
    }

    @Test
    void syncPackageDoesNotPublishDraftWhenAllTargetsAreNotSynced() throws Exception {
        KnowledgePackage pack = new KnowledgePackage(
            1L, "pkg-draft", "tenant-A", "PKG.TEST", "1.0.0", "待同步草稿包", null,
            KnowledgePackageStatus.DRAFT, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(packageRepository.findByPackageIdAndTenantId("pkg-draft", "tenant-A"))
            .thenReturn(Optional.of(pack));
        stubPackageReadyForRelease("pkg-draft");

        SyncTarget target = new SyncTarget(
            1L, "target-1", "tenant-A", "图谱同步", SyncTargetType.GRAPH_DB, null,
            SyncTargetStatus.ACTIVE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(targetRepository.findByTargetIdAndTenantId("target-1", "tenant-A"))
            .thenReturn(Optional.of(target));
        when(syncPort.sync(eq("tenant-A"), any(ReleasePlan.class), eq(target)))
            .thenThrow(new PackageSyncNotConnectedException("NOT_SYNCED：未配置真实同步适配器"));

        PackageSyncResponse response = service.syncPackage("pkg-draft", packageSyncRequest(
            "org-1", ReleaseStrategy.FULL, ReleaseScopeType.ALL, null,
            List.of("target-1"), List.of("hospital-admin")
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
        stubPackageReadyForRelease("pkg-draft");

        SyncTarget successTarget = new SyncTarget(
            1L, "target-ok", "tenant-A", "规则库", SyncTargetType.CLINICAL_DB, "config",
            SyncTargetStatus.ACTIVE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        SyncTarget failedTarget = new SyncTarget(
            2L, "target-fail", "tenant-A", "图谱同步", SyncTargetType.GRAPH_DB, "config",
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
        stubPackageReadyForRelease("pkg-copd-v2");

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
            1L, "target-1", "tenant-A", "同步目标", SyncTargetType.DIFY, "config",
            SyncTargetStatus.ACTIVE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
        when(targetRepository.findByTargetIdAndTenantId("target-1", "tenant-A"))
            .thenReturn(Optional.of(target));
        when(syncPort.sync(eq("tenant-A"), any(ReleasePlan.class), eq(target)))
            .thenReturn("EVIDENCE-001");

        PackageSyncResponse response = service.syncPackage("pkg-copd-v2", packageSyncRequest(
            "org-1", ReleaseStrategy.FULL, ReleaseScopeType.ALL, null,
            List.of("target-1"), List.of("hospital-admin")
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

    private SyncTarget syncTarget(String targetId, String targetName, SyncTargetType targetType) {
        return new SyncTarget(
            1L, targetId, "tenant-A", targetName, targetType, "config",
            SyncTargetStatus.ACTIVE, Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
    }

    private String offlinePackageJson(String packageCode, String packageVersion) throws Exception {
        return offlinePackageJson(packageCode, packageVersion, "tenant-A");
    }

    private String offlinePackageJson(String packageCode, String packageVersion, String sourceTenantId) throws Exception {
        ArrayNode items = TEST_MAPPER.createArrayNode();
        items.add(offlineItem(sourceTenantId, "source-item-1", "RULE", "rule-stable", "2"));
        items.add(offlineItem(sourceTenantId, "source-item-2", "EVALUATION", "eval-stable", "1"));

        ArrayNode assetSnapshots = TEST_MAPPER.createArrayNode();
        assetSnapshots.add(offlineSnapshot("RULE", "rule-stable", "2", offlineRuleContent("rule-stable", "rule-version-2", 2)));
        assetSnapshots.add(offlineSnapshot("EVALUATION", "eval-stable", "1", offlineEvaluationContent("eval-stable")));
        return offlinePackageJson(packageCode, packageVersion, sourceTenantId, items, assetSnapshots);
    }

    private String offlinePackageJson(
            String packageCode,
            String packageVersion,
            String sourceTenantId,
            ArrayNode items,
            ArrayNode assetSnapshots) throws Exception {
        ObjectNode packageInfo = TEST_MAPPER.createObjectNode();
        packageInfo.put("packageId", "pkg-source");
        packageInfo.put("tenantId", sourceTenantId);
        packageInfo.put("packageCode", packageCode);
        packageInfo.put("packageVersion", packageVersion);
        packageInfo.put("name", "离线导入配置包");
        packageInfo.put("description", "真实离线包导入验收");
        packageInfo.put("status", "PUBLISHED");
        packageInfo.put("createdAt", "2026-06-01T00:00:00Z");
        packageInfo.put("createdBy", "source-user");
        packageInfo.put("updatedAt", "2026-06-01T00:00:00Z");
        packageInfo.put("updatedBy", "source-user");
        packageInfo.put("traceId", "trace-source");

        ObjectNode payload = TEST_MAPPER.createObjectNode();
        payload.set("packageInfo", packageInfo);
        payload.set("items", items);
        payload.set("assetSnapshots", assetSnapshots);

        String payloadSha256 = HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256")
                .digest(TEST_MAPPER.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8)));

        ObjectNode manifest = TEST_MAPPER.createObjectNode();
        manifest.put("packageId", "pkg-source");
        manifest.put("tenantId", sourceTenantId);
        manifest.put("packageCode", packageCode);
        manifest.put("packageVersion", packageVersion);
        manifest.put("status", "PUBLISHED");
        manifest.put("itemCount", items.size());
        manifest.put("assetSnapshotCount", assetSnapshots.size());
        manifest.put("hashAlgorithm", "SHA-256");
        manifest.put("payloadSha256", payloadSha256);
        manifest.put("exportedAt", "2026-06-01T00:00:00Z");
        manifest.put("traceId", "trace-source");

        ObjectNode root = TEST_MAPPER.createObjectNode();
        root.put("format", "MEDKERNEL_PACKAGE_OFFLINE_V1");
        root.set("manifest", manifest);
        root.set("payload", payload);
        return TEST_MAPPER.writeValueAsString(root);
    }

    private ObjectNode offlineSnapshot(String assetType, String assetId, String assetVersion, ObjectNode content) throws Exception {
        ObjectNode snapshot = TEST_MAPPER.createObjectNode();
        snapshot.put("assetType", assetType);
        snapshot.put("assetId", assetId);
        snapshot.put("assetVersion", assetVersion);
        snapshot.put("contentSha256", sha256Node(content));
        snapshot.set("content", content);
        return snapshot;
    }

    private ObjectNode offlineRuleContent(String ruleId, String versionId, int versionNo) {
        ObjectNode rule = TEST_MAPPER.createObjectNode();
        rule.put("ruleId", ruleId);
        rule.put("ruleCode", "RULE.TEST");
        rule.put("name", "测试规则");
        rule.put("ruleType", "QUALITY");
        rule.put("authoringMode", "DSL");
        rule.put("riskLevel", "MEDIUM");
        rule.put("status", "PUBLISHED");
        rule.put("activeVersionId", versionId);
        rule.put("packageVersion", "1.0.0");
        rule.put("applicableOrgUnitId", "dept-rule");

        ObjectNode version = TEST_MAPPER.createObjectNode();
        version.put("versionId", versionId);
        version.put("versionNo", versionNo);
        version.put("sourceRef", "source-ref");
        version.put("changeSummary", "离线迁移规则版本");
        version.put("dslJson", "{\"ruleId\":\"" + ruleId + "\",\"condition\":\"age >= 18\"}");
        version.put("explanationJson", "{\"message\":\"成人规则\"}");
        version.put("status", "PUBLISHED");
        version.put("publishedAt", "2026-06-01T00:00:00Z");
        version.put("publishedBy", "source-user");

        ObjectNode content = TEST_MAPPER.createObjectNode();
        content.set("rule", rule);
        content.set("version", version);
        return content;
    }

    private ObjectNode offlineEvaluationContent(String indicatorId) {
        ObjectNode indicator = TEST_MAPPER.createObjectNode();
        indicator.put("indicatorId", indicatorId);
        indicator.put("indicatorCode", "EVAL.TEST");
        indicator.put("versionNo", 1);
        indicator.put("name", "测试指标");
        indicator.put("subjectType", "DEPARTMENT");
        indicator.put("denominatorDefinition", "denominator");
        indicator.put("numeratorDefinition", "numerator");
        indicator.put("exclusionDefinition", "exclusion");
        indicator.put("scoringDefinition", "scoring");
        indicator.put("timeWindow", "P30D");
        indicator.put("organizationScope", "tenant");
        indicator.put("responsibleDepartmentId", "dept-eval");
        indicator.put("sourceRef", "source-ref");
        indicator.put("packageVersion", "1.0.0");
        indicator.put("status", "PUBLISHED");
        indicator.put("publishedAt", "2026-06-01T00:00:00Z");
        indicator.put("publishedBy", "source-user");
        indicator.put("activatedAt", "2026-06-01T00:00:00Z");

        ObjectNode content = TEST_MAPPER.createObjectNode();
        content.set("indicator", indicator);
        return content;
    }

    private ObjectNode offlineKnowledgeContent(String identityCode, String versionNo) {
        ObjectNode identity = TEST_MAPPER.createObjectNode();
        identity.put("identityCode", identityCode);
        identity.put("domain", "GUIDELINE");
        identity.put("subject", "慢阻肺指南");
        identity.put("specialtyId", "RESP");
        identity.put("description", "权威知识身份");
        identity.put("status", "ACTIVE");
        identity.put("currentVersionNo", versionNo);

        ObjectNode version = TEST_MAPPER.createObjectNode();
        version.put("versionNo", versionNo);
        version.put("versionLabel", "2026 版");
        version.put("sourceDocumentId", 10L);
        version.put("sourceVersionId", 20L);
        version.put("contentHash", "a".repeat(64));
        version.put("anchors", "[]");
        version.put("status", "ACTIVE");
        version.put("riskLevel", "MEDIUM");
        version.put("authorityLevel", "B_GUIDELINE");
        version.put("conflictArbitration", "无冲突");
        version.put("effectiveFrom", "2026-01-01T00:00:00Z");
        version.put("reviewedBy", "reviewer");
        version.put("reviewedAt", "2026-05-01T00:00:00Z");
        version.put("activatedAt", "2026-06-01T00:00:00Z");

        ObjectNode content = TEST_MAPPER.createObjectNode();
        content.set("identity", identity);
        content.set("version", version);
        return content;
    }

    private ObjectNode offlineTerminologyContent() {
        ObjectNode terminologyPackage = TEST_MAPPER.createObjectNode();
        terminologyPackage.put("packageCode", "TERM.LAB");
        terminologyPackage.put("packageVersion", "2026.06");
        terminologyPackage.put("displayName", "检验术语映射包");
        terminologyPackage.put("scopeLevel", "DEPARTMENT");
        terminologyPackage.put("scopeCode", "CARD");
        terminologyPackage.put("status", "PUBLISHED");
        terminologyPackage.put("mappingCount", 1);
        terminologyPackage.put("contentHash", "b".repeat(64));
        terminologyPackage.put("publishedBy", "tester");
        terminologyPackage.put("publishedAt", "2026-06-01T00:00:00Z");

        ObjectNode mapping = TEST_MAPPER.createObjectNode();
        mapping.put("localTermId", 11L);
        mapping.put("standardTermId", 22L);
        mapping.put("sourceSystem", "LIS");
        mapping.put("category", "LAB");
        mapping.put("confidence", 0.98);
        mapping.put("riskLevel", "HIGH");
        mapping.put("status", "CONFIRMED");
        mapping.put("evidenceText", "来源编码一致且人工确认");
        mapping.put("confirmedBy", "tester");
        mapping.put("confirmedAt", "2026-06-01T00:00:00Z");

        ObjectNode packageItem = TEST_MAPPER.createObjectNode();
        packageItem.put(
            "mappingSnapshot",
            "{\"mappingId\":401,\"localTermId\":11,\"standardTermId\":22,\"status\":\"CONFIRMED\"}"
        );

        ObjectNode content = TEST_MAPPER.createObjectNode();
        content.set("terminologyPackage", terminologyPackage);
        content.set("mappings", TEST_MAPPER.createArrayNode().add(mapping));
        content.set("items", TEST_MAPPER.createArrayNode().add(packageItem));
        return content;
    }

    private String sha256Node(JsonNode node) throws Exception {
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256")
                .digest(TEST_MAPPER.writeValueAsString(node).getBytes(StandardCharsets.UTF_8)));
    }

    private ObjectNode offlineItem(String sourceTenantId, String itemId, String assetType, String assetId, String assetVersion) {
        ObjectNode item = TEST_MAPPER.createObjectNode();
        item.put("itemId", itemId);
        item.put("tenantId", sourceTenantId);
        item.put("packageId", "pkg-source");
        item.put("assetType", assetType);
        item.put("assetId", assetId);
        item.put("assetVersion", assetVersion);
        item.put("createdAt", "2026-06-01T00:00:00Z");
        item.put("createdBy", "source-user");
        item.put("updatedAt", "2026-06-01T00:00:00Z");
        item.put("updatedBy", "source-user");
        item.put("traceId", "trace-source");
        return item;
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
            30L, targetId, "tenant-A", "图谱同步", SyncTargetType.GRAPH_DB, "config",
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

    private PackageSyncRequest packageSyncRequest(
            String targetOrgUnitId,
            ReleaseStrategy strategy,
            ReleaseScopeType scopeType,
            String scopeValue,
            List<String> targetIds,
            List<String> roleCodes) {
        return new PackageSyncRequest(
            "req-pkg-release", "trace-pkg-release", "tenant-A", null, "hospital-1", null,
            null, null, null, "tester", roleCodes, "pkg-ctx",
            targetOrgUnitId, strategy, scopeType, scopeValue, targetIds
        );
    }

    private void stubPackageReadyForRelease(String packageId) {
        String ruleId = "rule-ready-" + packageId;
        when(itemRepository.findByTenantIdAndPackageId("tenant-A", packageId))
            .thenReturn(List.of(packageItem(900L, packageId, VersionedAssetType.RULE, ruleId, "1")));
        when(ruleRepository.findByRuleIdAndTenantId(ruleId, "tenant-A"))
            .thenReturn(Optional.of(publishedRule(ruleId, "dept-rule")));
    }

    private PackageItem packageItem(
            long id,
            String packageId,
            VersionedAssetType assetType,
            String assetId,
            String assetVersion) {
        return new PackageItem(
            id, "item-" + id, "tenant-A", packageId, assetType, assetId, assetVersion,
            Instant.now(), "tester", Instant.now(), "tester", "trace"
        );
    }

    private PilotPackageTemplate activePilotTemplate(
            String tenantId,
            String templateId,
            String templateCode,
            String name) {
        return new PilotPackageTemplate(
            1L,
            templateId,
            tenantId,
            templateCode,
            name,
            "试点首发配置包模板",
            "PKG.PILOT",
            "1.0.0",
            PilotPackageTemplateStatus.ACTIVE,
            Instant.now(),
            "tester",
            Instant.now(),
            "tester",
            "trace"
        );
    }

    private PilotPackageTemplateItem pilotTemplateItem(
            String templateId,
            int sortOrder,
            VersionedAssetType assetType,
            String assetId,
            String assetVersion) {
        return new PilotPackageTemplateItem(
            (long) sortOrder,
            "tpl-item-" + sortOrder,
            templateId.startsWith("tpl-first") ? "t-1" : "tenant-A",
            templateId,
            assetType,
            assetId,
            assetVersion,
            true,
            sortOrder,
            "首发模板必需资产",
            Instant.now(),
            "tester",
            Instant.now(),
            "tester",
            "trace"
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

    private RuleVersion publishedRuleVersion(String ruleId, String versionId, int versionNo) {
        return new RuleVersion(
            1L, versionId, "tenant-A", ruleId, versionNo, "source-ref",
            "离线迁移规则版本", "{\"ruleId\":\"" + ruleId + "\",\"condition\":\"age >= 18\"}",
            "{\"message\":\"成人规则\"}", RuleVersionStatus.PUBLISHED,
            Instant.parse("2026-06-01T00:00:00Z"), "source-user", null,
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

    private KnowledgeIdentity activeKnowledgeIdentity(Long id, String identityCode, Long currentVersionId) {
        return new KnowledgeIdentity(
            id, "tenant-A", identityCode, KnowledgeDomain.GUIDELINE, "慢阻肺指南",
            "RESP", "权威知识身份", KnowledgeIdentityStatus.ACTIVE, currentVersionId,
            Instant.now(), "tester", Instant.now(), "tester"
        );
    }

    private KnowledgeAssetVersion activeKnowledgeVersion(Long id, Long identityId, String versionNo) {
        return new KnowledgeAssetVersion(
            id, "tenant-A", identityId, versionNo, "2026 版",
            10L, 20L, "a".repeat(64), "[]",
            KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.MEDIUM,
            SourceAuthorityLevel.B_GUIDELINE, null, null, "无冲突",
            "tenant:tenant-A", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE,
            KnowledgeAssetVersion.activeScopeKey(identityId, "tenant:tenant-A", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE),
            Instant.parse("2026-01-01T00:00:00Z"), null,
            "reviewer", Instant.parse("2026-05-01T00:00:00Z"),
            Instant.parse("2026-06-01T00:00:00Z"), null, null, null,
            Instant.now(), "tester", Instant.now(), "tester"
        );
    }

    private TermMappingPackage publishedTerminologyPackage(
            String packageCode,
            String packageVersion,
            String scopeLevel,
            String scopeCode) {
        return publishedTerminologyPackage(null, packageCode, packageVersion, scopeLevel, scopeCode);
    }

    private TermMappingPackage publishedTerminologyPackage(
            Long id,
            String packageCode,
            String packageVersion,
            String scopeLevel,
            String scopeCode) {
        return TermMappingPackage.imported(
            id,
            "tenant-A", packageCode, packageVersion, "检验术语映射包",
            scopeLevel, scopeCode, "PUBLISHED", 1, "b".repeat(64),
            null, "tester", Instant.parse("2026-06-01T00:00:00Z"),
            null, Instant.now(), "tester"
        );
    }

    private TermMapping publishedTermMapping(Long id) {
        return TermMapping.imported(
            id,
            "tenant-A",
            11L,
            22L,
            "LIS",
            "LAB",
            0.98,
            "HIGH",
            "CONFIRMED",
            "来源编码一致且人工确认",
            "tester",
            Instant.parse("2026-06-01T00:00:00Z"),
            Instant.now(),
            "tester"
        );
    }
}
