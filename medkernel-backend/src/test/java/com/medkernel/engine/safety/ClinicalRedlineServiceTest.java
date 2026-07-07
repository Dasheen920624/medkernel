package com.medkernel.engine.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.medkernel.engine.cdss.risk.CdssAutomationLevel;
import com.medkernel.engine.cdss.risk.CdssReviewRequirement;
import com.medkernel.engine.cdss.risk.CdssRiskMatrixRepository;
import com.medkernel.engine.cdss.risk.CdssRiskMatrixRule;
import com.medkernel.engine.cdss.risk.CdssRiskMatrixStatus;
import com.medkernel.engine.recommendation.RecommendationRiskLevel;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetDependencyKind;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionService;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.ReleasePort;
import com.medkernel.engine.versioning.VersionReleaseCommand;
import com.medkernel.engine.versioning.VersionPublishQualityGate;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ClinicalRedlineServiceTest {

    private ClinicalRedlineRepository repository;
    private ClinicalRedlineTrialRepository trialRepository;
    private AuditRecorder auditRecorder;
    private AssetVersionService versionService;
    private ReleasePort releasePort;
    private CdssRiskMatrixRepository riskMatrices;
    private ClinicalRedlineService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(ClinicalRedlineRepository.class);
        trialRepository = Mockito.mock(ClinicalRedlineTrialRepository.class);
        auditRecorder = Mockito.mock(AuditRecorder.class);
        versionService = Mockito.mock(AssetVersionService.class);
        releasePort = Mockito.mock(ReleasePort.class);
        riskMatrices = Mockito.mock(CdssRiskMatrixRepository.class);
        service = new ClinicalRedlineService(
            repository, trialRepository, auditRecorder, versionService, releasePort, riskMatrices);
        when(versionService.registerDraft(any(AssetVersionRegisterCommand.class)))
            .thenReturn(assetVersion("av-safety-redline-v1", VersionedAssetType.SAFETY, "SAFETY.RDL-DDI-001"));
        when(riskMatrices.findByTenantIdAndMatrixVersionOrderByTriggerPointAscSeverityLevelAscAutomationLevelAsc(
                "tenant-A", "4"))
            .thenReturn(List.of(
                riskMatrixRule("risk-matrix-critical-ddi", "4"),
                riskMatrixRuleForDraft("risk-matrix-local-rehearsal", "4")));
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-redline", OrgScope.tenant("tenant-A"), "medical-admin-1"));
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void requiredCategoriesCoverTheClinicalSafetyRedlineScope() {
        assertThat(ClinicalRedlineCategory.requiredSafetyCategories())
            .containsExactlyInAnyOrder(
                ClinicalRedlineCategory.DRUG_INTERACTION,
                ClinicalRedlineCategory.CRITICAL_VALUE,
                ClinicalRedlineCategory.DOSE_LIMIT,
                ClinicalRedlineCategory.ANTIMICROBIAL_RESTRICTION,
                ClinicalRedlineCategory.SPECIAL_POPULATION_CONTRAINDICATION,
                ClinicalRedlineCategory.SURGERY_ANESTHESIA_TRANSFUSION);
    }

    @Test
    void activeCatalogListsOnlyDatabaseBackedVersionedRedlinesWithHazardAndRiskMatrixTrace() {
        ClinicalRedlineRule ddi = redline(
            "redline-ddi-warfarin-nsaid",
            ClinicalRedlineCategory.DRUG_INTERACTION,
            "RDL-DDI-001",
            "2026.1",
            ClinicalRedlineStatus.ACTIVE);
        when(repository.findByTenantIdAndStatusOrderByCategoryAscRedlineKeyAscUpdatedAtDesc(
                "tenant-A", ClinicalRedlineStatus.ACTIVE))
            .thenReturn(List.of(ddi));

        ClinicalRedlineCatalogResponse response = service.activeCatalog(null);

        assertThat(response.contentStatus()).isEqualTo(ClinicalRedlineContentStatus.CONFIGURED);
        assertThat(response.traceId()).isEqualTo("trace-redline");
        assertThat(response.redlines()).singleElement().satisfies(item -> {
            assertThat(item.redlineId()).isEqualTo("redline-ddi-warfarin-nsaid");
            assertThat(item.category()).isEqualTo(ClinicalRedlineCategory.DRUG_INTERACTION);
            assertThat(item.redlineVersion()).isEqualTo("2026.1");
            assertThat(item.hazardSeverity()).isEqualTo(RecommendationRiskLevel.CRITICAL);
            assertThat(item.riskMatrixId()).isEqualTo("risk-matrix-critical-ddi");
            assertThat(item.riskMatrixVersion()).isEqualTo("4");
            assertThat(item.reviewRequirement()).isEqualTo(CdssReviewRequirement.PHYSICIAN_CONFIRMATION);
            assertThat(item.silentRunHours()).isEqualTo(168);
            assertThat(item.releaseGate()).isEqualTo("OPT04_REDLINE_SILENT_TRIAL");
            assertThat(item.conditionDsl()).contains("medications[].code");
            assertThat(item.evidenceSource()).isEqualTo("药品说明书与临床指南证据");
            assertThat(item.sourceVersionId()).isEqualTo(42L);
            assertThat(item.lowerTenantOverrideAllowed()).isFalse();
        });
    }

    @Test
    void activeCatalogCanFilterByControlledCategory() {
        ClinicalRedlineRule rule = redline(
            "redline-critical-potassium",
            ClinicalRedlineCategory.CRITICAL_VALUE,
            "RDL-LAB-001",
            "2026.1",
            ClinicalRedlineStatus.ACTIVE);
        when(repository.findByTenantIdAndCategoryAndStatusOrderByRedlineKeyAscUpdatedAtDesc(
                "tenant-A", ClinicalRedlineCategory.CRITICAL_VALUE, ClinicalRedlineStatus.ACTIVE))
            .thenReturn(List.of(rule));

        ClinicalRedlineCatalogResponse response =
            service.activeCatalog(ClinicalRedlineCategory.CRITICAL_VALUE);

        assertThat(response.redlines()).singleElement()
            .extracting(ClinicalRedlineResponse::category)
            .isEqualTo(ClinicalRedlineCategory.CRITICAL_VALUE);
    }

    @Test
    void emptyRepositoryReturnsNotConfiguredInsteadOfHardcodedMedicalConstants() {
        when(repository.findByTenantIdAndStatusOrderByCategoryAscRedlineKeyAscUpdatedAtDesc(
                "tenant-A", ClinicalRedlineStatus.ACTIVE))
            .thenReturn(List.of());

        ClinicalRedlineCatalogResponse response = service.activeCatalog(null);

        assertThat(response.contentStatus()).isEqualTo(ClinicalRedlineContentStatus.NOT_CONFIGURED);
        assertThat(response.redlines()).isEmpty();
    }

    @Test
    void createDraftPersistsSafetyRedlineWithoutPublishingUnifiedAsset() {
        when(repository.findByTenantIdAndRedlineId("tenant-A", "redline-local-rehearsal-baseline"))
            .thenReturn(Optional.empty());
        when(repository.findByTenantIdAndRedlineKeyAndRedlineVersion(
                "tenant-A", "RDL-LOCAL-REHEARSAL", "2026.1"))
            .thenReturn(Optional.empty());
        when(repository.save(any(ClinicalRedlineRule.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ClinicalRedlineResponse response = service.createDraft(validDraftRequest());

        assertThat(response.redlineId()).isEqualTo("redline-local-rehearsal-baseline");
        assertThat(response.status()).isEqualTo(ClinicalRedlineStatus.DRAFT);
        assertThat(response.lowerTenantOverrideAllowed()).isFalse();
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(rule ->
            rule.status() == ClinicalRedlineStatus.DRAFT
                && rule.activeScopeKey() == null
                && rule.redlineKey().equals("RDL-LOCAL-REHEARSAL")
                && rule.title().contains("本地上线演练")
                && rule.clinicalHazard().contains("SAFETY 资产")
        ));
        verify(versionService, org.mockito.Mockito.never()).registerDraft(any());
        verify(releasePort, org.mockito.Mockito.never()).publish(any());
        verify(auditRecorder).record(
            AuditAction.CREATE,
            "mk_engine_clinical_redline",
            "redline-local-rehearsal-baseline",
            "创建临床安全红线草稿");
    }

    @Test
    void createDraftRejectsLowerTenantOverrideForSafetyRedline() {
        ClinicalRedlineDraftRequest request = new ClinicalRedlineDraftRequest(
            "redline-unsafe-override",
            ClinicalRedlineCategory.DOSE_LIMIT,
            "medication-prescribe",
            "TENANT",
            "tenant-A",
            "RDL-UNSAFE-OVERRIDE",
            "2026.1",
            RecommendationRiskLevel.CRITICAL,
            "risk-matrix-dose-limit",
            "4",
            CdssReviewRequirement.PHYSICIAN_CONFIRMATION,
            168,
            "OPT04_REDLINE_SILENT_TRIAL",
            "不合规红线",
            "下级关闭会破坏安全红线",
            "{\"all\":[{\"field\":\"medications[].dose\",\"operator\":\"gt\",\"value\":1}]}",
            "本地上线演练证据",
            "source-version:local-e2e#dose-limit",
            null,
            true
        );

        assertThatThrownBy(() -> service.createDraft(request))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("安全红线禁止下级关闭");
    }

    @Test
    void createDraftRejectsRiskMatrixThatIsNotActiveAndMatching() {
        when(riskMatrices.findByTenantIdAndMatrixVersionOrderByTriggerPointAscSeverityLevelAscAutomationLevelAsc(
                "tenant-A", "4"))
            .thenReturn(List.of());

        assertThatThrownBy(() -> service.createDraft(validDraftRequest()))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("风险矩阵绑定未通过验证");

        verify(repository, org.mockito.Mockito.never()).save(any());
        verify(versionService, org.mockito.Mockito.never()).registerDraft(any());
        verify(releasePort, org.mockito.Mockito.never()).publish(any());
    }

    @Test
    void dryRunRecordsRealSilentWindowEvidenceAndMovesDraftIntoSilentRunning() {
        ClinicalRedlineRule draft = redline(
            "redline-ddi-warfarin-nsaid",
            ClinicalRedlineCategory.DRUG_INTERACTION,
            "RDL-DDI-001",
            "2026.2",
            ClinicalRedlineStatus.DRAFT);
        when(repository.findByTenantIdAndRedlineId("tenant-A", "redline-ddi-warfarin-nsaid"))
            .thenReturn(Optional.of(draft));
        when(repository.save(any(ClinicalRedlineRule.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(trialRepository.save(any(ClinicalRedlineTrial.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ClinicalRedlineTrialResponse response = service.dryRun(new ClinicalRedlineDryRunRequest(
            "redline-ddi-warfarin-nsaid",
            Instant.parse("2026-05-26T00:00:00Z"),
            Instant.parse("2026-06-03T00:00:00Z"),
            1200,
            18,
            1,
            0,
            "evidence://silent-trials/redline-ddi-warfarin-nsaid/2026.2",
            "试运行窗口来自真实临床事件回放统计"));

        assertThat(response.status()).isEqualTo(ClinicalRedlineTrialStatus.PASSED);
        assertThat(response.actualSilentHours()).isEqualTo(192);
        assertThat(response.requiredSilentHours()).isEqualTo(168);
        assertThat(response.traceId()).isEqualTo("trace-redline");
        verify(repository).save(any(ClinicalRedlineRule.class));
        verify(trialRepository).save(any(ClinicalRedlineTrial.class));
        verify(auditRecorder).record(
            AuditAction.EXECUTE,
            "mk_engine_clinical_redline_trial",
            response.trialId(),
            "记录临床安全红线静默试运行证据");
    }

    @Test
    void dryRunRejectsRiskMatrixThatBecameInactiveOrMismatchedAfterDraft() {
        ClinicalRedlineRule draft = redline(
            "redline-risk-matrix-drifted",
            ClinicalRedlineCategory.DRUG_INTERACTION,
            "RDL-DDI-DRIFTED",
            "2026.2",
            ClinicalRedlineStatus.DRAFT);
        when(repository.findByTenantIdAndRedlineId("tenant-A", "redline-risk-matrix-drifted"))
            .thenReturn(Optional.of(draft));
        when(riskMatrices.findByTenantIdAndMatrixVersionOrderByTriggerPointAscSeverityLevelAscAutomationLevelAsc(
                "tenant-A", "4"))
            .thenReturn(List.of());

        assertThatThrownBy(() -> service.dryRun(validDryRunRequest("redline-risk-matrix-drifted")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("风险矩阵绑定未通过验证");

        verify(trialRepository, org.mockito.Mockito.never()).save(any());
        verify(repository, org.mockito.Mockito.never()).save(any());
        verify(versionService, org.mockito.Mockito.never()).registerDraft(any());
        verify(releasePort, org.mockito.Mockito.never()).publish(any());
    }

    @Test
    void dryRunRejectsRuleWithoutHazardEvidenceOrRiskMatrixBinding() {
        ClinicalRedlineRule incomplete = new ClinicalRedlineRule(
            null,
            "redline-incomplete",
            "tenant-A",
            ClinicalRedlineCategory.DOSE_LIMIT,
            "medication-prescribe",
            "TENANT",
            "tenant-A",
            null,
            "RDL-DOSE-001",
            "2026.1",
            ClinicalRedlineStatus.DRAFT,
            RecommendationRiskLevel.CRITICAL,
            "",
            "",
            CdssReviewRequirement.PHYSICIAN_CONFIRMATION,
            168,
            "OPT04_REDLINE_SILENT_TRIAL",
            "剂量上限红线",
            "",
            "{\"field\":\"medications[].dose\"}",
            "",
            "",
            null,
            false,
            Instant.parse("2026-06-04T02:00:00Z"),
            "tester",
            Instant.parse("2026-06-04T02:00:00Z"),
            "tester",
            "trace-redline");
        when(repository.findByTenantIdAndRedlineId("tenant-A", "redline-incomplete"))
            .thenReturn(Optional.of(incomplete));

        assertThatThrownBy(() -> service.dryRun(validDryRunRequest("redline-incomplete")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("危害分析、证据来源和风险矩阵绑定不能为空");
    }

    @Test
    void promoteRejectsBeforeSilentRunEvidenceMeetsRequiredHours() {
        ClinicalRedlineRule silent = redline(
            "redline-ddi-warfarin-nsaid",
            ClinicalRedlineCategory.DRUG_INTERACTION,
            "RDL-DDI-001",
            "2026.2",
            ClinicalRedlineStatus.SILENT_RUNNING);
        ClinicalRedlineTrial shortTrial = trial(
            "trial-short",
            silent,
            ClinicalRedlineTrialStatus.FAILED,
            72,
            1);
        when(repository.findByTenantIdAndRedlineId("tenant-A", "redline-ddi-warfarin-nsaid"))
            .thenReturn(Optional.of(silent));
        when(trialRepository.findByTenantIdAndRedlineIdAndTrialId(
                "tenant-A", "redline-ddi-warfarin-nsaid", "trial-short"))
            .thenReturn(Optional.of(shortTrial));

        assertThatThrownBy(() -> service.promote(new ClinicalRedlinePromoteRequest(
                "redline-ddi-warfarin-nsaid",
                "trial-short",
                "2026.2",
                "试运行未达到红线门槛，禁止上线")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("静默试运行未达标");
    }

    @Test
    void promoteRejectsLowerTenantOverrideAllowedForSafetyRedline() {
        ClinicalRedlineRule silent = redline(
            "redline-ddi-warfarin-nsaid",
            ClinicalRedlineCategory.DRUG_INTERACTION,
            "RDL-DDI-001",
            "2026.2",
            ClinicalRedlineStatus.SILENT_RUNNING,
            true);
        ClinicalRedlineTrial passedTrial = trial(
            "trial-pass",
            silent,
            ClinicalRedlineTrialStatus.PASSED,
            192,
            0);
        when(repository.findByTenantIdAndRedlineId("tenant-A", "redline-ddi-warfarin-nsaid"))
            .thenReturn(Optional.of(silent));
        when(trialRepository.findByTenantIdAndRedlineIdAndTrialId(
                "tenant-A", "redline-ddi-warfarin-nsaid", "trial-pass"))
            .thenReturn(Optional.of(passedTrial));

        assertThatThrownBy(() -> service.promote(new ClinicalRedlinePromoteRequest(
                "redline-ddi-warfarin-nsaid",
                "trial-pass",
                "2026.2",
                "下级可关配置禁止上线")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("安全红线禁止下级关闭");
    }

    @Test
    void promoteActivatesOnlyAfterPassedSilentTrialEvidence() {
        ClinicalRedlineRule silent = redline(
            "redline-ddi-warfarin-nsaid",
            ClinicalRedlineCategory.DRUG_INTERACTION,
            "RDL-DDI-001",
            "2026.2",
            ClinicalRedlineStatus.SILENT_RUNNING);
        ClinicalRedlineTrial passedTrial = trial(
            "trial-pass",
            silent,
            ClinicalRedlineTrialStatus.PASSED,
            192,
            0);
        when(repository.findByTenantIdAndRedlineId("tenant-A", "redline-ddi-warfarin-nsaid"))
            .thenReturn(Optional.of(silent));
        when(trialRepository.findByTenantIdAndRedlineIdAndTrialId(
                "tenant-A", "redline-ddi-warfarin-nsaid", "trial-pass"))
            .thenReturn(Optional.of(passedTrial));
        when(repository.findByTenantIdAndActiveScopeKeyAndStatus(
                "tenant-A",
                "tenant-A|TENANT:tenant-A|DRUG_INTERACTION|medication-prescribe|RDL-DDI-001",
                ClinicalRedlineStatus.ACTIVE))
            .thenReturn(Optional.empty());
        when(repository.save(any(ClinicalRedlineRule.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ClinicalRedlineResponse response = service.promote(new ClinicalRedlinePromoteRequest(
            "redline-ddi-warfarin-nsaid",
            "trial-pass",
            "2026.2",
            "静默试运行达标，按 OPT-04 上线"));

        assertThat(response.status()).isEqualTo(ClinicalRedlineStatus.ACTIVE);
        assertThat(response.redlineVersion()).isEqualTo("2026.2");
        verify(repository).save(any(ClinicalRedlineRule.class));
        org.mockito.ArgumentCaptor<AssetVersionRegisterCommand> assetVersionCaptor =
            org.mockito.ArgumentCaptor.forClass(AssetVersionRegisterCommand.class);
        verify(versionService).registerDraft(assetVersionCaptor.capture());
        AssetVersionRegisterCommand command = assetVersionCaptor.getValue();
        assertThat(command.assetType()).isEqualTo(VersionedAssetType.SAFETY);
        assertThat(command.assetIdentity()).isEqualTo("SAFETY.RDL-DDI-001");
        assertThat(command.content()).contains("\"redlineVersion\":\"2026.2\"");
        assertThat(command.content()).contains("\"riskMatrixId\":\"risk-matrix-critical-ddi\"");
        assertThat(command.content()).contains("\"riskMatrixVersion\":\"4\"");
        assertThat(command.content()).contains("华法林合并非甾体抗炎药出血风险");
        assertThat(command.safetyPolicy()).isEqualTo(AssetVersionSafetyPolicy.SAFETY_REDLINE);
        assertThat(command.overridePolicy()).isEqualTo(AssetVersionOverridePolicy.LOCKED);
        assertThat(command.dependencies()).singleElement().satisfies(dependency -> {
            assertThat(dependency.dependsOnAssetType()).isEqualTo(VersionedAssetType.CDSS_RISK);
            assertThat(dependency.dependsOnIdentity()).isEqualTo("CDSS.RISK.MATRIX");
            assertThat(dependency.minVersionNo()).isNull();
            assertThat(dependency.maxVersionNo()).isNull();
            assertThat(dependency.kind()).isEqualTo(AssetDependencyKind.RUNTIME_ASSET);
        });
        org.mockito.ArgumentCaptor<VersionReleaseCommand> publishCaptor =
            org.mockito.ArgumentCaptor.forClass(VersionReleaseCommand.class);
        verify(releasePort).publish(publishCaptor.capture());
        VersionReleaseCommand publish = publishCaptor.getValue();
        assertThat(publish.assetType()).isEqualTo(VersionedAssetType.SAFETY);
        assertThat(publish.assetIdentity()).isEqualTo("SAFETY.RDL-DDI-001");
        assertThat(publish.versionId()).isEqualTo("av-safety-redline-v1");
        assertThat(publish.impactDigest()).contains("静默试运行达标");
        VersionPublishQualityGate qualityGate = publish.qualityGate();
        assertThat(qualityGate).isNotNull();
        assertThat(qualityGate.schemaValid()).isTrue();
        assertThat(qualityGate.terminologyBindingComplete()).isTrue();
        assertThat(qualityGate.dependencyIntegrityVerified()).isTrue();
        assertThat(qualityGate.safetyMonotonicityVerified()).isTrue();
        assertThat(qualityGate.impactSimulationPassed()).isTrue();
        assertThat(qualityGate.summary()).contains("静默试运行", "安全单调性", "影响评估");
        verify(auditRecorder).record(
            AuditAction.PUBLISH,
            "mk_engine_clinical_redline",
            "redline-ddi-warfarin-nsaid",
            "临床安全红线静默试运行达标后上线");
    }

    @Test
    void promoteFailsClosedWhenQualityGateEvidenceIsIncomplete() {
        ClinicalRedlineRule silent = redline(
            "redline-unknown-field",
            ClinicalRedlineCategory.DRUG_INTERACTION,
            "RDL-UNKNOWN-FIELD",
            "2026.2",
            ClinicalRedlineStatus.SILENT_RUNNING,
            false,
            "{\"all\":[{\"field\":\"legacy.medicationDose\",\"operator\":\"gt\",\"value\":1}]}"
        );
        ClinicalRedlineTrial passedTrial = trial(
            "trial-pass",
            silent,
            ClinicalRedlineTrialStatus.PASSED,
            192,
            0);
        when(repository.findByTenantIdAndRedlineId("tenant-A", "redline-unknown-field"))
            .thenReturn(Optional.of(silent));
        when(trialRepository.findByTenantIdAndRedlineIdAndTrialId(
                "tenant-A", "redline-unknown-field", "trial-pass"))
            .thenReturn(Optional.of(passedTrial));
        when(repository.findByTenantIdAndActiveScopeKeyAndStatus(
                "tenant-A",
                "tenant-A|TENANT:tenant-A|DRUG_INTERACTION|medication-prescribe|RDL-UNKNOWN-FIELD",
                ClinicalRedlineStatus.ACTIVE))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.promote(new ClinicalRedlinePromoteRequest(
                "redline-unknown-field",
                "trial-pass",
                "2026.2",
                "字段绑定证据缺失时禁止上线")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("发布质量校验未全部通过")
            .hasMessageContaining("legacy.medicationDose");

        verify(versionService, org.mockito.Mockito.never()).registerDraft(any());
        verify(releasePort, org.mockito.Mockito.never()).publish(any());
        verify(repository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.argThat(
            rule -> rule.status() == ClinicalRedlineStatus.ACTIVE));
    }

    @Test
    void promoteFailsClosedWhenRiskMatrixBindingDoesNotExist() {
        ClinicalRedlineRule silent = redline(
            "redline-missing-risk-matrix",
            ClinicalRedlineCategory.DOSE_LIMIT,
            "RDL-MISSING-RISK-MATRIX",
            "2026.2",
            ClinicalRedlineStatus.SILENT_RUNNING);
        ClinicalRedlineTrial passedTrial = trial(
            "trial-pass",
            silent,
            ClinicalRedlineTrialStatus.PASSED,
            192,
            0);
        when(repository.findByTenantIdAndRedlineId("tenant-A", "redline-missing-risk-matrix"))
            .thenReturn(Optional.of(silent));
        when(trialRepository.findByTenantIdAndRedlineIdAndTrialId(
                "tenant-A", "redline-missing-risk-matrix", "trial-pass"))
            .thenReturn(Optional.of(passedTrial));
        when(riskMatrices.findByTenantIdAndMatrixVersionOrderByTriggerPointAscSeverityLevelAscAutomationLevelAsc(
                "tenant-A", "4"))
            .thenReturn(List.of());
        when(repository.findByTenantIdAndActiveScopeKeyAndStatus(
                "tenant-A",
                "tenant-A|TENANT:tenant-A|DOSE_LIMIT|medication-prescribe|RDL-MISSING-RISK-MATRIX",
                ClinicalRedlineStatus.ACTIVE))
            .thenReturn(Optional.empty());
        when(repository.save(any(ClinicalRedlineRule.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.promote(new ClinicalRedlinePromoteRequest(
                "redline-missing-risk-matrix",
                "trial-pass",
                "2026.2",
                "风险矩阵绑定不存在时禁止上线")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("发布质量校验未全部通过")
            .hasMessageContaining("风险矩阵");

        verify(versionService, org.mockito.Mockito.never()).registerDraft(any());
        verify(releasePort, org.mockito.Mockito.never()).publish(any());
        verify(repository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.argThat(
            rule -> rule.status() == ClinicalRedlineStatus.ACTIVE));
    }

    @Test
    void createDraftRejectsScopeOutsideCurrentOrganizationContext() {
        ClinicalRedlineDraftRequest request = new ClinicalRedlineDraftRequest(
            "redline-cross-tenant",
            ClinicalRedlineCategory.DOSE_LIMIT,
            "medication-prescribe",
            "TENANT",
            "tenant-B",
            "RDL-CROSS-TENANT",
            "2026.1",
            RecommendationRiskLevel.CRITICAL,
            "risk-matrix-dose-limit",
            "4",
            CdssReviewRequirement.PHYSICIAN_CONFIRMATION,
            168,
            "OPT04_REDLINE_SILENT_TRIAL",
            "跨租户红线",
            "红线适用域必须与当前组织上下文一致",
            "{\"all\":[{\"field\":\"medications[].dose\",\"operator\":\"gt\",\"value\":1}]}",
            "本地上线演练证据",
            "source-version:local-e2e#dose-limit",
            null,
            false
        );

        assertThatThrownBy(() -> service.createDraft(request))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("红线适用域必须与当前组织上下文一致");

        verify(repository, org.mockito.Mockito.never()).save(any());
        verify(versionService, org.mockito.Mockito.never()).registerDraft(any());
        verify(releasePort, org.mockito.Mockito.never()).publish(any());
    }

    @Test
    void createDraftNormalizesScopeTypeBeforePersistingActiveScopeKeys() {
        when(repository.findByTenantIdAndRedlineId("tenant-A", "redline-lower-scope"))
            .thenReturn(Optional.empty());
        when(repository.findByTenantIdAndRedlineKeyAndRedlineVersion(
                "tenant-A", "RDL-LOWER-SCOPE", "2026.1"))
            .thenReturn(Optional.empty());
        when(repository.save(any(ClinicalRedlineRule.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service.createDraft(new ClinicalRedlineDraftRequest(
            "redline-lower-scope",
            ClinicalRedlineCategory.DOSE_LIMIT,
            "medication-prescribe",
            "tenant",
            " tenant-A ",
            "RDL-LOWER-SCOPE",
            "2026.1",
            RecommendationRiskLevel.CRITICAL,
            "risk-matrix-local-rehearsal",
            "4",
            CdssReviewRequirement.PHYSICIAN_CONFIRMATION,
            168,
            "OPT04_REDLINE_SILENT_TRIAL",
            "大小写规范红线",
            "红线适用域必须规范入库",
            "{\"all\":[{\"field\":\"medications[].dose\",\"operator\":\"gt\",\"value\":1}]}",
            "本地上线演练证据",
            "source-version:local-e2e#dose-limit",
            null,
            false
        ));

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(rule ->
            rule.scopeType().equals("TENANT")
                && rule.scopeRef().equals("tenant-A")
                && rule.computedActiveScopeKey().contains("|TENANT:tenant-A|")
        ));
    }

    private ClinicalRedlineRule redline(
            String redlineId,
            ClinicalRedlineCategory category,
            String redlineKey,
            String redlineVersion,
            ClinicalRedlineStatus status) {
        return redline(redlineId, category, redlineKey, redlineVersion, status, false);
    }

    private ClinicalRedlineRule redline(
            String redlineId,
            ClinicalRedlineCategory category,
            String redlineKey,
            String redlineVersion,
            ClinicalRedlineStatus status,
            boolean lowerTenantOverrideAllowed) {
        return redline(redlineId, category, redlineKey, redlineVersion, status, lowerTenantOverrideAllowed, """
            {"all":[{"field":"medications[].code","operator":"in","value":["ATC:B01AA03","ATC:M01A"]}]}
            """);
    }

    private ClinicalRedlineRule redline(
            String redlineId,
            ClinicalRedlineCategory category,
            String redlineKey,
            String redlineVersion,
            ClinicalRedlineStatus status,
            boolean lowerTenantOverrideAllowed,
            String conditionDsl) {
        Instant now = Instant.parse("2026-06-04T02:00:00Z");
        return new ClinicalRedlineRule(
            null,
            redlineId,
            "tenant-A",
            category,
            "medication-prescribe",
            "TENANT",
            "tenant-A",
            "tenant-A|TENANT:tenant-A|" + category.name() + "|medication-prescribe|" + redlineKey,
            redlineKey,
            redlineVersion,
            status,
            RecommendationRiskLevel.CRITICAL,
            "risk-matrix-critical-ddi",
            "4",
            CdssReviewRequirement.PHYSICIAN_CONFIRMATION,
            168,
            "OPT04_REDLINE_SILENT_TRIAL",
            "华法林合并非甾体抗炎药出血风险",
            "合用可能显著增加出血风险",
            conditionDsl,
            "药品说明书与临床指南证据",
            "source-version:42#section-1",
            42L,
            lowerTenantOverrideAllowed,
            now,
            "tester",
            now,
            "tester",
            "trace-redline");
    }

    private ClinicalRedlineDryRunRequest validDryRunRequest(String redlineId) {
        return new ClinicalRedlineDryRunRequest(
            redlineId,
            Instant.parse("2026-05-26T00:00:00Z"),
            Instant.parse("2026-06-03T00:00:00Z"),
            1200,
            18,
            1,
            0,
            "evidence://silent-trials/" + redlineId,
            "试运行窗口来自真实临床事件回放统计");
    }

    private ClinicalRedlineDraftRequest validDraftRequest() {
        return new ClinicalRedlineDraftRequest(
            "redline-local-rehearsal-baseline",
            ClinicalRedlineCategory.DOSE_LIMIT,
            "medication-prescribe",
            "TENANT",
            "tenant-A",
            "RDL-LOCAL-REHEARSAL",
            "2026.1",
            RecommendationRiskLevel.CRITICAL,
            "risk-matrix-local-rehearsal",
            "4",
            CdssReviewRequirement.PHYSICIAN_CONFIRMATION,
            168,
            "OPT04_REDLINE_SILENT_TRIAL",
            "本地上线演练安全红线",
            "用于验证空库环境可以先创建真实安全红线草稿，再经静默试运行和上线门禁纳入 SAFETY 资产。",
            "{\"all\":[{\"field\":\"medications[].dose\",\"operator\":\"gt\",\"value\":1}]}",
            "本地上线演练安全证据",
            "source-version:local-e2e#safety-redline",
            null,
            false
        );
    }

    private ClinicalRedlineTrial trial(
            String trialId,
            ClinicalRedlineRule rule,
            ClinicalRedlineTrialStatus status,
            long actualSilentHours,
            long safetyIncidentCount) {
        Instant completedAt = Instant.parse("2026-06-03T00:00:00Z");
        return new ClinicalRedlineTrial(
            null,
            trialId,
            rule.tenantId(),
            rule.redlineId(),
            rule.redlineKey(),
            rule.redlineVersion(),
            status,
            completedAt.minus(Duration.ofHours(actualSilentHours)),
            completedAt,
            rule.silentRunHours(),
            actualSilentHours,
            1200,
            18,
            1,
            safetyIncidentCount,
            status == ClinicalRedlineTrialStatus.PASSED,
            "evidence://silent-trials/" + trialId,
            "试运行窗口来自真实临床事件回放统计",
            Instant.parse("2026-06-04T02:00:00Z"),
            "tester",
            "trace-redline");
    }

    private AssetVersion assetVersion(String versionId, VersionedAssetType assetType, String assetIdentity) {
        Instant now = Instant.parse("2026-06-04T02:00:00Z");
        return new AssetVersion(
            null,
            versionId,
            "tenant-A",
            assetType,
            assetIdentity,
            "V1",
            null,
            "ALL",
            "b".repeat(64),
            AssetVersionSafetyPolicy.SAFETY_REDLINE,
            AssetVersionOverridePolicy.LOCKED,
            AssetVersionStatus.DRAFT,
            null,
            "test",
            null,
            null,
            now,
            "tester",
            now,
            "tester",
            "trace-redline");
    }

    private CdssRiskMatrixRule riskMatrixRule(String matrixId, String matrixVersion) {
        Instant now = Instant.parse("2026-06-04T02:00:00Z");
        return new CdssRiskMatrixRule(
            null,
            matrixId,
            "tenant-A",
            "medication-prescribe",
            RecommendationRiskLevel.CRITICAL,
            CdssAutomationLevel.INFORM_ONLY,
            RecommendationRiskLevel.CRITICAL,
            CdssReviewRequirement.PHYSICIAN_CONFIRMATION,
            168,
            "OPT04_REDLINE_SILENT_TRIAL",
            false,
            "NMPA_RESERVED",
            "NOT_ASSESSED",
            CdssRiskMatrixStatus.ACTIVE,
            matrixVersion,
            "红线风险矩阵证据",
            now,
            "tester",
            now,
            "tester",
            "trace-redline");
    }

    private CdssRiskMatrixRule riskMatrixRuleForDraft(String matrixId, String matrixVersion) {
        Instant now = Instant.parse("2026-06-04T02:00:00Z");
        return new CdssRiskMatrixRule(
            null,
            matrixId,
            "tenant-A",
            "medication-prescribe",
            RecommendationRiskLevel.CRITICAL,
            CdssAutomationLevel.INFORM_ONLY,
            RecommendationRiskLevel.CRITICAL,
            CdssReviewRequirement.PHYSICIAN_CONFIRMATION,
            168,
            "OPT04_REDLINE_SILENT_TRIAL",
            false,
            "NMPA_RESERVED",
            "NOT_ASSESSED",
            CdssRiskMatrixStatus.ACTIVE,
            matrixVersion,
            "红线风险矩阵证据",
            now,
            "tester",
            now,
            "tester",
            "trace-redline");
    }
}
