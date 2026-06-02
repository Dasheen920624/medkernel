package com.medkernel.engine.terminology;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TerminologyServiceTest {

    private StandardTermRepository standardTermRepository;
    private LocalTermRepository localTermRepository;
    private TermMappingRepository mappingRepository;
    private MappingCandidateRepository candidateRepository;
    private MappingConflictRepository conflictRepository;
    private TermMappingPackageRepository packageRepository;
    private TermMappingPackageItemRepository packageItemRepository;
    private TermMappingPackageReleaseRepository packageReleaseRepository;
    private TerminologyService service;

    @BeforeEach
    void setUp() {
        standardTermRepository = Mockito.mock(StandardTermRepository.class);
        localTermRepository = Mockito.mock(LocalTermRepository.class);
        mappingRepository = Mockito.mock(TermMappingRepository.class);
        candidateRepository = Mockito.mock(MappingCandidateRepository.class);
        conflictRepository = Mockito.mock(MappingConflictRepository.class);
        packageRepository = Mockito.mock(TermMappingPackageRepository.class);
        packageItemRepository = Mockito.mock(TermMappingPackageItemRepository.class);
        packageReleaseRepository = Mockito.mock(TermMappingPackageReleaseRepository.class);
        service = new TerminologyService(
            standardTermRepository,
            localTermRepository,
            mappingRepository,
            candidateRepository,
            conflictRepository,
            packageRepository,
            packageItemRepository,
            packageReleaseRepository
        );
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-1"), "u-99"));
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void pageLocalTermsNormalizesKeywordAndEnumFilters() {
        when(localTermRepository.countByFilter("t-1", "LIS", "LAB", "UNMAPPED", "%肌钙蛋白%"))
            .thenReturn(2L);
        when(localTermRepository.pageByFilter(
            eq("t-1"), eq("LIS"), eq("LAB"), eq("UNMAPPED"), eq("%肌钙蛋白%"), anyInt(), anyInt()
        )).thenReturn(List.of(localTerm(1L)));

        PageResponse<LocalTerm> page = service.pageLocalTerms(
            new PageRequest(1, 20, null),
            new LocalTermFilter("LIS", TermCategory.LAB, LocalTermStatus.UNMAPPED, "  肌钙蛋白  ")
        );

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).hasSize(1);
    }

    @Test
    void confirmCandidateCreatesMappingAndMarksCandidateConfirmed() {
        MappingCandidate candidate = candidate(10L, MappingCandidateStatus.PENDING);
        when(candidateRepository.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(candidate));
        when(localTermRepository.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(localTerm(1L)));
        when(standardTermRepository.findByTenantIdAndId("t-1", 2L)).thenReturn(Optional.of(standardTerm(2L, TermCategory.LAB)));
        when(mappingRepository.findByTenantIdAndLocalTermIdAndStandardTermId("t-1", 1L, 2L))
            .thenReturn(Optional.empty());
        when(mappingRepository.save(any(TermMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(candidateRepository.save(any(MappingCandidate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TermMapping mapping = service.confirmCandidate(
            10L,
            confirmRequest(true, "已逐条核对标准码与院内码")
        );

        assertThat(mapping.localTermId()).isEqualTo(1L);
        assertThat(mapping.standardTermId()).isEqualTo(2L);
        assertThat(mapping.sourceSystem()).isEqualTo("LIS");
        assertThat(mapping.category()).isEqualTo(TermCategory.LAB);
        assertThat(mapping.status()).isEqualTo(TermMappingStatus.CONFIRMED);
        assertThat(mapping.confirmedBy()).isEqualTo("u-99");
        assertThat(mapping.confirmedAt()).isNotNull();

        ArgumentCaptor<MappingCandidate> savedCandidate = ArgumentCaptor.forClass(MappingCandidate.class);
        verify(candidateRepository).save(savedCandidate.capture());
        assertThat(savedCandidate.getValue().status()).isEqualTo(MappingCandidateStatus.CONFIRMED);
        assertThat(savedCandidate.getValue().reviewNote()).isEqualTo("专家逐条确认");
    }

    @Test
    void confirmCandidateRejectsCategoryMismatch() {
        MappingCandidate candidate = candidate(10L, MappingCandidateStatus.PENDING);
        when(candidateRepository.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(candidate));
        when(localTermRepository.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(localTerm(1L)));
        when(standardTermRepository.findByTenantIdAndId("t-1", 2L)).thenReturn(Optional.of(standardTerm(2L, TermCategory.DRUG)));

        assertThatThrownBy(() -> service.confirmCandidate(
                10L,
                confirmRequest(true, "已逐条核对标准码与院内码")
            ))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void confirmCandidateRejectsNonPendingCandidate() {
        when(candidateRepository.findByTenantIdAndId("t-1", 10L))
            .thenReturn(Optional.of(candidate(10L, MappingCandidateStatus.CONFIRMED)));

        assertThatThrownBy(() -> service.confirmCandidate(10L, confirmRequest(true, "重复确认前已核对")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void confirmCandidateRejectsHighRiskWithoutSecondConfirmation() {
        when(candidateRepository.findByTenantIdAndId("t-1", 10L))
            .thenReturn(Optional.of(candidate(10L, MappingCandidateStatus.PENDING)));

        assertThatThrownBy(() -> service.confirmCandidate(
                10L,
                confirmRequest(false, null)
            ))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.MAPPING_HIGH_RISK_AUTOCONFIRM_DENIED);
    }

    @Test
    void batchConfirmRejectsAnyHighRiskCandidate() {
        MappingCandidate highRisk = candidate(10L, MappingCandidateStatus.PENDING);
        when(candidateRepository.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(highRisk));
        when(candidateRepository.findByTenantIdAndId("t-1", 11L))
            .thenReturn(Optional.of(candidate(11L, MappingCandidateStatus.PENDING, TermRiskLevel.LOW)));

        assertThatThrownBy(() -> service.batchConfirmCandidates(batchConfirmRequest(List.of(10L, 11L))))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.MAPPING_HIGH_RISK_BATCH_DENIED);

        verify(mappingRepository, Mockito.never()).save(any(TermMapping.class));
        verify(candidateRepository, Mockito.never()).save(any(MappingCandidate.class));
    }

    @Test
    void batchConfirmAcceptsOrdinaryCandidatesAndReturnsCandidateIds() {
        when(candidateRepository.findByTenantIdAndId("t-1", 10L))
            .thenReturn(Optional.of(candidate(10L, MappingCandidateStatus.PENDING, TermRiskLevel.LOW)));
        when(candidateRepository.findByTenantIdAndId("t-1", 11L))
            .thenReturn(Optional.of(candidate(11L, MappingCandidateStatus.PENDING, TermRiskLevel.MEDIUM)));
        when(localTermRepository.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(localTerm(1L)));
        when(standardTermRepository.findByTenantIdAndId("t-1", 2L)).thenReturn(Optional.of(standardTerm(2L, TermCategory.LAB)));
        when(mappingRepository.findByTenantIdAndLocalTermIdAndStandardTermId("t-1", 1L, 2L))
            .thenReturn(Optional.empty());
        when(mappingRepository.save(any(TermMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(candidateRepository.save(any(MappingCandidate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TerminologyBatchConfirmResponse response = service.batchConfirmCandidates(batchConfirmRequest(List.of(10L, 11L)));

        assertThat(response.confirmedCount()).isEqualTo(2);
        assertThat(response.confirmedCandidateIds()).containsExactly(10L, 11L);
        verify(mappingRepository, Mockito.times(2)).save(any(TermMapping.class));
        verify(candidateRepository, Mockito.times(2)).save(any(MappingCandidate.class));
    }

    @Test
    void batchConfirmDeduplicatesCandidateIdsBeforeConfirming() {
        when(candidateRepository.findByTenantIdAndId("t-1", 10L))
            .thenReturn(Optional.of(candidate(10L, MappingCandidateStatus.PENDING, TermRiskLevel.LOW)));
        when(localTermRepository.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(localTerm(1L)));
        when(standardTermRepository.findByTenantIdAndId("t-1", 2L)).thenReturn(Optional.of(standardTerm(2L, TermCategory.LAB)));
        when(mappingRepository.findByTenantIdAndLocalTermIdAndStandardTermId("t-1", 1L, 2L))
            .thenReturn(Optional.empty());
        when(mappingRepository.save(any(TermMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(candidateRepository.save(any(MappingCandidate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TerminologyBatchConfirmResponse response = service.batchConfirmCandidates(batchConfirmRequest(List.of(10L, 10L)));

        assertThat(response.confirmedCount()).isEqualTo(1);
        assertThat(response.confirmedCandidateIds()).containsExactly(10L);
        verify(candidateRepository, Mockito.times(1)).findByTenantIdAndId("t-1", 10L);
        verify(mappingRepository, Mockito.times(1)).save(any(TermMapping.class));
        verify(candidateRepository, Mockito.times(1)).save(any(MappingCandidate.class));
    }

    @Test
    void writeRequestRejectsTenantMismatch() {
        assertThatThrownBy(() -> service.generateCandidates(candidateGenerationRequest("t-2")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ORG_SCOPE_DENIED);
    }

    @Test
    void resolveConflictMarksConflictResolvedWithCurrentUser() {
        when(conflictRepository.findByTenantIdAndId("t-1", 20L))
            .thenReturn(Optional.of(conflict(20L, MappingConflictStatus.OPEN)));
        when(conflictRepository.save(any(MappingConflict.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MappingConflict resolved = service.resolveConflict(
            20L,
            resolveConflictRequest("保留检验系统一对一映射")
        );

        assertThat(resolved.status()).isEqualTo(MappingConflictStatus.RESOLVED);
        assertThat(resolved.resolvedBy()).isEqualTo("u-99");
        assertThat(resolved.resolvedAt()).isNotNull();
        assertThat(resolved.resolutionNote()).isEqualTo("保留检验系统一对一映射");
    }

    @Test
    void buildPackageSnapshotsConfirmedMappings() {
        when(mappingRepository.findConfirmedByTenantIdAndScope("t-1", "DEPARTMENT", "CARD"))
            .thenReturn(List.of(mapping(100L, TermMappingStatus.CONFIRMED)));
        when(packageRepository.save(any(TermMappingPackage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(packageItemRepository.save(any(TermMappingPackageItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TermMappingPackage pkg = service.buildPackage(buildPackageRequest());

        assertThat(pkg.status()).isEqualTo(TermMappingPackageStatus.DRAFT);
        assertThat(pkg.mappingCount()).isEqualTo(1);
        assertThat(pkg.contentHash()).hasSize(64);

        ArgumentCaptor<TermMappingPackageItem> itemCaptor = ArgumentCaptor.forClass(TermMappingPackageItem.class);
        verify(packageItemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().mappingId()).isEqualTo(100L);
        assertThat(itemCaptor.getValue().mappingSnapshot()).contains("\"mappingId\":100");
    }

    @Test
    void publishPackageFullSupersedesPreviousPublishedPackageAndRecordsEvent() {
        TermMappingPackage draft = pkg(30L, "PKG-LAB-CARD", "2026.05.25", TermMappingPackageStatus.DRAFT);
        TermMappingPackage previous = pkg(29L, "PKG-LAB-CARD", "2026.05.01", TermMappingPackageStatus.PUBLISHED);
        when(packageRepository.findByTenantIdAndId("t-1", 30L)).thenReturn(Optional.of(draft));
        when(packageRepository.findActiveByTenantIdAndPackageCodeAndScope("t-1", "PKG-LAB-CARD", "DEPARTMENT", "CARD"))
            .thenReturn(List.of(previous));
        when(packageRepository.save(any(TermMappingPackage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(packageReleaseRepository.save(any(TermMappingPackageRelease.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TermMappingPackage published = service.publishPackage(
            30L,
            publishPackageRequest(PackageReleaseMode.FULL, "全量发布", null)
        );

        assertThat(published.status()).isEqualTo(TermMappingPackageStatus.PUBLISHED);
        assertThat(published.publishedBy()).isEqualTo("u-99");
        assertThat(published.publishedAt()).isNotNull();

        ArgumentCaptor<TermMappingPackage> packageCaptor = ArgumentCaptor.forClass(TermMappingPackage.class);
        verify(packageRepository, Mockito.times(2)).save(packageCaptor.capture());
        assertThat(packageCaptor.getAllValues())
            .extracting(TermMappingPackage::status)
            .contains(TermMappingPackageStatus.SUPERSEDED, TermMappingPackageStatus.PUBLISHED);

        ArgumentCaptor<TermMappingPackageRelease> releaseCaptor =
            ArgumentCaptor.forClass(TermMappingPackageRelease.class);
        verify(packageReleaseRepository).save(releaseCaptor.capture());
        assertThat(releaseCaptor.getValue().eventType()).isEqualTo(TermPackageReleaseEventType.PUBLISH);
        assertThat(releaseCaptor.getValue().releaseMode()).isEqualTo(PackageReleaseMode.FULL);
    }

    @Test
    void rollbackPackageMarksCurrentRolledBackAndReactivatesTarget() {
        TermMappingPackage current = pkg(30L, "PKG-LAB-CARD", "2026.05.25", TermMappingPackageStatus.PUBLISHED);
        TermMappingPackage target = pkg(29L, "PKG-LAB-CARD", "2026.05.01", TermMappingPackageStatus.SUPERSEDED);
        when(packageRepository.findByTenantIdAndId("t-1", 30L)).thenReturn(Optional.of(current));
        when(packageRepository.findByTenantIdAndId("t-1", 29L)).thenReturn(Optional.of(target));
        when(packageRepository.save(any(TermMappingPackage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(packageReleaseRepository.save(any(TermMappingPackageRelease.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TermMappingPackage restored = service.rollbackPackage(
            30L,
            rollbackPackageRequest(29L, "发现院内码映射异常")
        );

        assertThat(restored.status()).isEqualTo(TermMappingPackageStatus.PUBLISHED);
        assertThat(restored.publishedBy()).isEqualTo("u-99");

        ArgumentCaptor<TermMappingPackage> packageCaptor = ArgumentCaptor.forClass(TermMappingPackage.class);
        verify(packageRepository, Mockito.times(2)).save(packageCaptor.capture());
        assertThat(packageCaptor.getAllValues())
            .extracting(TermMappingPackage::status)
            .contains(TermMappingPackageStatus.ROLLED_BACK, TermMappingPackageStatus.PUBLISHED);

        ArgumentCaptor<TermMappingPackageRelease> releaseCaptor =
            ArgumentCaptor.forClass(TermMappingPackageRelease.class);
        verify(packageReleaseRepository).save(releaseCaptor.capture());
        assertThat(releaseCaptor.getValue().eventType()).isEqualTo(TermPackageReleaseEventType.ROLLBACK);
        assertThat(releaseCaptor.getValue().targetPackageId()).isEqualTo(29L);
    }

    @Test
    void rollbackPackageRejectsNonRollbackPointTarget() {
        TermMappingPackage current = pkg(30L, "PKG-LAB-CARD", "2026.05.25", TermMappingPackageStatus.PUBLISHED);
        TermMappingPackage target = pkg(29L, "PKG-LAB-CARD", "2026.05.01", TermMappingPackageStatus.DRAFT);
        when(packageRepository.findByTenantIdAndId("t-1", 30L)).thenReturn(Optional.of(current));
        when(packageRepository.findByTenantIdAndId("t-1", 29L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.rollbackPackage(
            30L,
            rollbackPackageRequest(29L, "草稿不能作为回滚点")
        ))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void requiresTenantContext() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.empty(), null));

        assertThatThrownBy(() -> service.pageMappings(PageRequest.defaults(), MappingFilter.empty()))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.TENANT_CONTEXT_MISSING);
    }

    private LocalTerm localTerm(Long id) {
        Instant now = Instant.now();
        return new LocalTerm(
            id, "t-1", "LIS", "LIS-TNT", TermCategory.LAB, "肌钙蛋白T", "肌钙蛋白t",
            "CARD", LocalTermStatus.UNMAPPED, now, now, now, "system", now, "system"
        );
    }

    private MappingCandidate candidate(Long id, MappingCandidateStatus status) {
        Instant now = Instant.now();
        return new MappingCandidate(
            id, "t-1", 1L, 2L, 0.96, MappingCandidateSource.RULE,
            TermRiskLevel.HIGH, "同义词 + 单位一致", false, status, null,
            null, null, now, "system", now, "system"
        );
    }

    private StandardTerm standardTerm(Long id, TermCategory category) {
        Instant now = Instant.now();
        return new StandardTerm(
            id, "t-1", "LOINC", "718-7", category, "血红蛋白", "血红蛋白",
            "2.78", StandardTermStatus.ACTIVE, null, "LOINC", now, "system", now, "system"
        );
    }

    private MappingConflict conflict(Long id, MappingConflictStatus status) {
        Instant now = Instant.now();
        return new MappingConflict(
            id, "t-1", MappingConflictType.ONE_TO_MANY, 1L, 2L, null,
            TermRiskLevel.HIGH, "同一院内码存在多个标准候选", status,
            null, null, null, now, "system", now, "system"
        );
    }

    private TermMapping mapping(Long id, TermMappingStatus status) {
        Instant now = Instant.now();
        return new TermMapping(
            id, "t-1", 1L, 2L, "LIS", TermCategory.LAB, 0.96,
            TermRiskLevel.HIGH, status, "同义词 + 单位一致", "u-99", now,
            now, "system", now, "system"
        );
    }

    private TermMappingPackage pkg(Long id, String packageCode, String version, TermMappingPackageStatus status) {
        Instant now = Instant.now();
        return new TermMappingPackage(
            id, "t-1", packageCode, version, "检验映射包", "DEPARTMENT", "CARD",
            status, 1, "0".repeat(64), null, null, null, null,
            now, "system", now, "system"
        );
    }

    @Test
    void generateCandidatesCreatesRuleCandidatesWithHighRiskFlag() {
        LocalTerm local = localTerm(1L); // "肌钙蛋白T"
        StandardTerm standardMatch = standardTerm(2L, TermCategory.LAB); // "血红蛋白" -> 蛋白(2字重合) -> 2/4 = 0.5 相似度
        StandardTerm standardMismatchCategory = new StandardTerm(
            3L, "t-1", "LOINC", "718-8", TermCategory.DRUG, "肌钙蛋白", "肌钙蛋白",
            "2.78", StandardTermStatus.ACTIVE, null, "LOINC", Instant.now(), "system", Instant.now(), "system"
        );

        when(localTermRepository.findByTenantIdAndSourceSystemAndStatus("t-1", "LIS", LocalTermStatus.UNMAPPED))
            .thenReturn(List.of(local));
        when(standardTermRepository.findByTenantIdAndStatus("t-1", StandardTermStatus.ACTIVE))
            .thenReturn(List.of(standardMatch, standardMismatchCategory));

        when(candidateRepository.findByTenantIdAndLocalTermIdAndStandardTermIdAndStatus(
            eq("t-1"), eq(1L), eq(2L), eq(MappingCandidateStatus.PENDING)
        )).thenReturn(Optional.empty());

        when(candidateRepository.save(any(MappingCandidate.class))).thenAnswer(inv -> inv.getArgument(0));

        TerminologyCandidateGenerationResponse response = service.generateCandidates(candidateGenerationRequest("t-1"));
        assertThat(response.generatedCount()).isEqualTo(1); // MismatchCategory 虽名字匹配，但因分类(DRUG)不同被过滤
        assertThat(response.candidates())
            .singleElement()
            .satisfies(candidate -> {
                assertThat(candidate.semanticMatchScore()).isEqualTo(0.4444444444444444);
                assertThat(candidate.highRiskFlag()).isTrue();
                assertThat(candidate.source()).isEqualTo(MappingCandidateSource.RULE);
            });

        ArgumentCaptor<MappingCandidate> candidateCaptor = ArgumentCaptor.forClass(MappingCandidate.class);
        verify(candidateRepository).save(candidateCaptor.capture());
        MappingCandidate created = candidateCaptor.getValue();
        assertThat(created.localTermId()).isEqualTo(1L);
        assertThat(created.standardTermId()).isEqualTo(2L);
        assertThat(created.status()).isEqualTo(MappingCandidateStatus.PENDING);
        assertThat(created.confidence()).isEqualTo(0.4444444444444444); // "肌钙蛋白T" 对 "血红蛋白" LCS "蛋白"(2字) => 2 * 2 / (5 + 4) = 4/9
        assertThat(created.riskLevel()).isEqualTo(TermRiskLevel.HIGH); // sim = 0.444 -> HIGH risk
        assertThat(created.candidateSource()).isEqualTo(MappingCandidateSource.RULE);
    }

    private TerminologyCandidateGenerationRequest candidateGenerationRequest(String tenantId) {
        return new TerminologyCandidateGenerationRequest(
            "req-api04-001", "trace-api04-001", tenantId, "g-1", "h-1", "c-1", "s-1",
            "d-1", "sp-1", "u-99", List.of("specialist"), "pkg-2026.06",
            "LIS", null
        );
    }

    private TerminologyCandidateConfirmRequest confirmRequest(Boolean acknowledged, String reason) {
        return new TerminologyCandidateConfirmRequest(
            "req-api04-confirm", "trace-api04-confirm", "t-1", "g-1", "h-1", "c-1", "s-1",
            "d-1", "sp-1", "u-99", List.of("specialist"), "pkg-2026.06",
            "专家逐条确认", null, acknowledged, reason
        );
    }

    private TerminologyCandidateBatchConfirmRequest batchConfirmRequest(List<Long> candidateIds) {
        return new TerminologyCandidateBatchConfirmRequest(
            "req-api04-batch", "trace-api04-batch", "t-1", "g-1", "h-1", "c-1", "s-1",
            "d-1", "sp-1", "u-99", List.of("specialist"), "pkg-2026.06",
            candidateIds, "批量确认"
        );
    }

    private BuildTerminologyPackageRequest buildPackageRequest() {
        return new BuildTerminologyPackageRequest(
            "req-api04-package", "trace-api04-package", "t-1", "g-1", "h-1", "c-1", "s-1",
            "d-1", "sp-1", "u-99", List.of("it-ops"), "pkg-2026.06",
            "PKG-LAB-CARD", "2026.05.25", "DEPARTMENT", "CARD", "检验映射包"
        );
    }

    private ResolveConflictRequest resolveConflictRequest(String resolutionNote) {
        return new ResolveConflictRequest(
            "req-api04-conflict", "trace-api04-conflict", "t-1", "g-1", "h-1", "c-1", "s-1",
            "d-1", "sp-1", "u-99", List.of("specialist"), "pkg-2026.06",
            resolutionNote
        );
    }

    private PublishTerminologyPackageRequest publishPackageRequest(PackageReleaseMode mode, String reason, String grayScopeJson) {
        return new PublishTerminologyPackageRequest(
            "req-api04-publish", "trace-api04-publish", "t-1", "g-1", "h-1", "c-1", "s-1",
            "d-1", "sp-1", "u-99", List.of("it-ops"), "pkg-2026.06",
            mode, reason, grayScopeJson
        );
    }

    private RollbackTerminologyPackageRequest rollbackPackageRequest(Long targetPackageId, String reason) {
        return new RollbackTerminologyPackageRequest(
            "req-api04-rollback", "trace-api04-rollback", "t-1", "g-1", "h-1", "c-1", "s-1",
            "d-1", "sp-1", "u-99", List.of("it-ops"), "pkg-2026.06",
            targetPackageId, reason
        );
    }

    private MappingCandidate candidate(Long id, MappingCandidateStatus status, TermRiskLevel riskLevel) {
        Instant now = Instant.now();
        return new MappingCandidate(
            id, "t-1", 1L, 2L, 0.96, MappingCandidateSource.RULE,
            riskLevel, "同义词 + 单位一致", false, status, null,
            null, null, now, "system", now, "system"
        );
    }
}
