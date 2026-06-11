package com.medkernel.engine.terminology;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.medkernel.engine.security.RoleCode;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.engine.versioning.PlatformAuthority;
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
    private EffectiveTermMappingResolver effectiveMappings;
    private MappingCandidateRepository candidateRepository;
    private MappingConflictRepository conflictRepository;
    private HighRiskRuleRepository highRiskRuleRepository;
    private TerminologyService service;

    @BeforeEach
    void setUp() {
        standardTermRepository = Mockito.mock(StandardTermRepository.class);
        localTermRepository = Mockito.mock(LocalTermRepository.class);
        mappingRepository = Mockito.mock(TermMappingRepository.class);
        effectiveMappings = Mockito.mock(EffectiveTermMappingResolver.class);
        candidateRepository = Mockito.mock(MappingCandidateRepository.class);
        conflictRepository = Mockito.mock(MappingConflictRepository.class);
        highRiskRuleRepository = Mockito.mock(HighRiskRuleRepository.class);
        service = new TerminologyService(
            standardTermRepository,
            localTermRepository,
            mappingRepository,
            effectiveMappings,
            candidateRepository,
            conflictRepository,
            highRiskRuleRepository
        );
        when(highRiskRuleRepository.findActiveByTenantIdAndCategory(any(), any())).thenReturn(List.of());
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-1"), "u-99"));
        authenticate(RoleCode.ORGANIZATION_ADMIN);
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void registerStandardTermIsIdempotentBySystemCodeAndVersion() {
        StandardTerm existing = new StandardTerm(
            200L, "t-1", "LOINC", "2823-3", TermCategory.LAB, "旧血清钾",
            "旧血清钾", "2026.06", StandardTermStatus.ACTIVE, null,
            "旧证据", Instant.parse("2026-06-01T00:00:00Z"), "seed",
            Instant.parse("2026-06-01T00:00:00Z"), "seed"
        );
        when(standardTermRepository.findByTenantIdAndStandardSystemAndTermCodeAndVersionNo(
            "t-1", "LOINC", "2823-3", "2026.06"
        )).thenReturn(Optional.of(existing));
        when(standardTermRepository.save(any(StandardTerm.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StandardTerm saved = service.registerStandardTerm(new StandardTermRegistrationRequest(
            "req-term-standard", "trace-term-standard", "t-1", "g-1", "h-1", "c-1", "s-1",
            "d-1", "sp-1", "u-99", List.of("integration-operator"), "pkg-2026.06",
            "LOINC", "2823-3", TermCategory.LAB, "血清钾", "血清钾|血钾|K",
            "2026.06", 88L, "演练标准字典登记"
        ));

        assertThat(saved.id()).isEqualTo(200L);
        assertThat(saved.displayName()).isEqualTo("血清钾");
        assertThat(saved.sourceVersionId()).isEqualTo(88L);
        assertThat(saved.createdBy()).isEqualTo("seed");
        assertThat(saved.updatedBy()).isEqualTo("u-99");
    }

    @Test
    void registerLocalTermKeepsMappedStatusWhenRefreshingSeenTerm() {
        LocalTerm existing = new LocalTerm(
            100L, "t-1", "LIS", "K001", TermCategory.LAB, "旧血钾",
            "旧血钾", "dept-lab", LocalTermStatus.MAPPED,
            Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-02T00:00:00Z"),
            Instant.parse("2026-06-01T00:00:00Z"), "seed",
            Instant.parse("2026-06-02T00:00:00Z"), "seed"
        );
        when(localTermRepository.findByTenantIdAndSourceSystemAndLocalCodeAndCategory(
            "t-1", "LIS", "K001", TermCategory.LAB
        )).thenReturn(Optional.of(existing));
        when(localTermRepository.save(any(LocalTerm.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalTerm saved = service.registerLocalTerm(new LocalTermRegistrationRequest(
            "req-term-local", "trace-term-local", "t-1", "g-1", "h-1", "c-1", "s-1",
            "d-1", "sp-1", "u-99", List.of("integration-operator"), "pkg-2026.06",
            "LIS", "K001", TermCategory.LAB, "血钾", "血钾|K", "dept-lab"
        ));

        assertThat(saved.id()).isEqualTo(100L);
        assertThat(saved.localName()).isEqualTo("血钾");
        assertThat(saved.status()).isEqualTo(LocalTermStatus.MAPPED);
        assertThat(saved.firstSeenAt()).isEqualTo(existing.firstSeenAt());
        assertThat(saved.updatedBy()).isEqualTo("u-99");
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
    void pageStandardTermsPreservesSourceVersionAndEvidenceTrace() {
        StandardTerm traced = new StandardTerm(
            2L, "t-1", "ICD-10", "I63.900", TermCategory.DIAGNOSIS, "脑梗死", "naogengsi",
            "ICD-10-2026A", StandardTermStatus.ACTIVE, 88L,
            "来源：国家医保疾病诊断编码 2026A；锚点：I63.900",
            Instant.now(), "system", Instant.now(), "system"
        );
        List<String> standardSources = standardSources();
        when(standardTermRepository.countByTenantIdsFilter(
            standardSources, "ICD-10", "DIAGNOSIS", "ACTIVE", "%脑梗死%"))
            .thenReturn(1L);
        when(standardTermRepository.pageByTenantIdsFilter(
            eq(standardSources), eq("t-1"), eq("ICD-10"), eq("DIAGNOSIS"), eq("ACTIVE"), eq("%脑梗死%"),
            anyInt(), anyInt()
        )).thenReturn(List.of(traced));

        PageResponse<StandardTerm> page = service.pageStandardTerms(
            new PageRequest(1, 20, null),
            new StandardTermFilter("ICD-10", TermCategory.DIAGNOSIS, StandardTermStatus.ACTIVE, "脑梗死")
        );

        assertThat(page.items())
            .singleElement()
            .satisfies(term -> {
                assertThat(term.versionNo()).isEqualTo("ICD-10-2026A");
                assertThat(term.sourceVersionId()).isEqualTo(88L);
                assertThat(term.evidenceText()).contains("国家医保疾病诊断编码", "I63.900");
            });
    }

    @Test
    void confirmCandidateCreatesMappingAndMarksCandidateConfirmed() {
        MappingCandidate candidate = candidate(10L, MappingCandidateStatus.PENDING);
        when(candidateRepository.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(candidate));
        when(localTermRepository.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(localTerm(1L)));
        when(standardTermRepository.findFirstByTenantIdsAndId(standardSources(), "t-1", 2L))
            .thenReturn(Optional.of(standardTerm(2L, TermCategory.LAB)));
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
        ArgumentCaptor<LocalTerm> savedLocalTerm = ArgumentCaptor.forClass(LocalTerm.class);
        verify(localTermRepository).save(savedLocalTerm.capture());
        assertThat(savedLocalTerm.getValue().status()).isEqualTo(LocalTermStatus.MAPPED);
        assertThat(savedLocalTerm.getValue().updatedBy()).isEqualTo("u-99");
    }

    @Test
    void confirmCandidateAcceptsPlatformStandardTermAsTenantMappingTarget() {
        MappingCandidate candidate = candidate(10L, MappingCandidateStatus.PENDING, TermRiskLevel.LOW);
        List<String> standardSources = standardSources();
        when(candidateRepository.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(candidate));
        when(localTermRepository.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(localTerm(1L)));
        when(standardTermRepository.findFirstByTenantIdsAndId(standardSources, "t-1", 2L))
            .thenReturn(Optional.of(standardTerm(
                2L,
                PlatformAuthority.PLATFORM_TENANT_ID,
                "LOINC",
                "718-7",
                TermCategory.LAB,
                "血红蛋白",
                "血红蛋白"
            )));
        when(mappingRepository.findByTenantIdAndLocalTermIdAndStandardTermId("t-1", 1L, 2L))
            .thenReturn(Optional.empty());
        when(mappingRepository.save(any(TermMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(candidateRepository.save(any(MappingCandidate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TermMapping mapping = service.confirmCandidate(
            10L,
            confirmRequest(false, null)
        );

        assertThat(mapping.tenantId()).isEqualTo("t-1");
        assertThat(mapping.standardTermId()).isEqualTo(2L);
        assertThat(mapping.status()).isEqualTo(TermMappingStatus.CONFIRMED);
    }

    @Test
    void confirmCandidateRejectsStandardTermOutsideEffectiveSources() {
        MappingCandidate candidate = candidate(10L, MappingCandidateStatus.PENDING, TermRiskLevel.LOW);
        List<String> standardSources = standardSources();
        when(candidateRepository.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(candidate));
        when(localTermRepository.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(localTerm(1L)));
        when(standardTermRepository.findFirstByTenantIdsAndId(standardSources, "t-1", 2L))
            .thenReturn(Optional.empty());
        when(mappingRepository.findByTenantIdAndLocalTermIdAndStandardTermId("t-1", 1L, 2L))
            .thenReturn(Optional.empty());
        when(mappingRepository.save(any(TermMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(candidateRepository.save(any(MappingCandidate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.confirmCandidate(
                10L,
                confirmRequest(false, null)
            ))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("标准字典 id=2")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.NOT_FOUND);

        verify(mappingRepository, Mockito.never()).save(any(TermMapping.class));
    }

    @Test
    void confirmCandidateRegistersOneToManyAndManyToOneConflicts() {
        MappingCandidate candidate = candidate(10L, MappingCandidateStatus.PENDING, TermRiskLevel.LOW);
        when(candidateRepository.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(candidate));
        when(localTermRepository.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(localTerm(
            1L, "HIS-DIAG", "DIA-001", TermCategory.DIAGNOSIS, "急性脑梗死", "急性脑梗死"
        )));
        when(standardTermRepository.findFirstByTenantIdsAndId(standardSources(), "t-1", 2L))
            .thenReturn(Optional.of(standardTerm(
            2L, "ICD-10", "I63.900", TermCategory.DIAGNOSIS, "脑梗死", "脑梗死"
        )));
        when(mappingRepository.findByTenantIdAndLocalTermIdAndStandardTermId("t-1", 1L, 2L))
            .thenReturn(Optional.empty());
        when(mappingRepository.findByTenantIdAndLocalTermIdAndStatus("t-1", 1L, TermMappingStatus.CONFIRMED))
            .thenReturn(List.of(mapping(101L, 1L, 3L, TermCategory.DIAGNOSIS, TermRiskLevel.MEDIUM)));
        when(mappingRepository.findByTenantIdAndStandardTermIdAndStatus("t-1", 2L, TermMappingStatus.CONFIRMED))
            .thenReturn(List.of(mapping(102L, 4L, 2L, TermCategory.DIAGNOSIS, TermRiskLevel.LOW)));
        when(mappingRepository.save(any(TermMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(candidateRepository.save(any(MappingCandidate.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conflictRepository.save(any(MappingConflict.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.confirmCandidate(10L, confirmRequest(false, null));

        ArgumentCaptor<MappingConflict> conflictCaptor = ArgumentCaptor.forClass(MappingConflict.class);
        verify(conflictRepository, Mockito.times(2)).save(conflictCaptor.capture());
        assertThat(conflictCaptor.getAllValues())
            .extracting(MappingConflict::conflictType)
            .containsExactlyInAnyOrder(MappingConflictType.ONE_TO_MANY, MappingConflictType.MANY_TO_ONE);
        assertThat(conflictCaptor.getAllValues())
            .allSatisfy(conflict -> {
                assertThat(conflict.status()).isEqualTo(MappingConflictStatus.OPEN);
                assertThat(conflict.localTermId()).isEqualTo(1L);
                assertThat(conflict.standardTermId()).isEqualTo(2L);
                assertThat(conflict.description()).contains("待人工裁决");
            });

        ArgumentCaptor<MappingCandidate> savedCandidate = ArgumentCaptor.forClass(MappingCandidate.class);
        verify(candidateRepository).save(savedCandidate.capture());
        assertThat(savedCandidate.getValue().conflictFlag()).isTrue();
        assertThat(savedCandidate.getValue().status()).isEqualTo(MappingCandidateStatus.CONFIRMED);
    }

    @Test
    void confirmCandidateRejectsCategoryMismatch() {
        MappingCandidate candidate = candidate(10L, MappingCandidateStatus.PENDING);
        when(candidateRepository.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(candidate));
        when(localTermRepository.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(localTerm(1L)));
        when(standardTermRepository.findFirstByTenantIdsAndId(standardSources(), "t-1", 2L))
            .thenReturn(Optional.of(standardTerm(2L, TermCategory.DRUG)));

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
        when(standardTermRepository.findFirstByTenantIdsAndId(standardSources(), "t-1", 2L))
            .thenReturn(Optional.of(standardTerm(2L, TermCategory.LAB)));
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
        when(standardTermRepository.findFirstByTenantIdsAndId(standardSources(), "t-1", 2L))
            .thenReturn(Optional.of(standardTerm(2L, TermCategory.LAB)));
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
    void evaluateCoverageUsesPlatformStandardTermAndActivePackageMappingCount() {
        List<String> standardSources = standardSources();
        when(standardTermRepository.findFirstByTenantIdsAndStandardSystemAndTermCodeAndStatus(
            standardSources, "t-1", "LOINC", "718-7", StandardTermStatus.ACTIVE))
            .thenReturn(Optional.of(standardTerm(
                2L,
                PlatformAuthority.PLATFORM_TENANT_ID,
                "LOINC",
                "718-7",
                TermCategory.LAB,
                "血红蛋白",
                "血红蛋白"
            )));
        when(effectiveMappings.countByStandardCode("t-1", "LOINC", "718-7")).thenReturn(1);

        List<MappingCoverageItem> items = service.evaluateCoverage("LOINC", List.of("718-7"));

        assertThat(items)
            .singleElement()
            .satisfies(item -> {
                assertThat(item.code()).isEqualTo("718-7");
                assertThat(item.status()).isEqualTo(MappingCoverageItem.COVERED);
                assertThat(item.mappedLocalCount()).isEqualTo(1);
            });
    }

    @Test
    void requiresTenantContext() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.empty(), null));

        assertThatThrownBy(() -> service.pageMappings(PageRequest.defaults(), MappingFilter.empty()))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.TENANT_CONTEXT_MISSING);
    }

    private static LocalTerm localTerm(Long id) {
        return localTerm(id, "LIS", "LIS-TNT", TermCategory.LAB, "肌钙蛋白T", "肌钙蛋白t");
    }

    private static LocalTerm localTerm(Long id,
                                       String sourceSystem,
                                       String localCode,
                                       TermCategory category,
                                       String localName,
                                       String normalizedName) {
        Instant now = Instant.now();
        return new LocalTerm(
            id, "t-1", sourceSystem, localCode, category, localName, normalizedName,
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

    private static StandardTerm standardTerm(Long id, TermCategory category) {
        return standardTerm(id, "LOINC", "718-7", category, "血红蛋白", "血红蛋白");
    }

    private static StandardTerm standardTerm(Long id,
                                             String standardSystem,
                                             String termCode,
                                             TermCategory category,
                                             String displayName,
                                             String normalizedName) {
        return standardTerm(id, "t-1", standardSystem, termCode, category, displayName, normalizedName);
    }

    private static StandardTerm standardTerm(Long id,
                                             String tenantId,
                                             String standardSystem,
                                             String termCode,
                                             TermCategory category,
                                             String displayName,
                                             String normalizedName) {
        Instant now = Instant.now();
        return new StandardTerm(
            id, tenantId, standardSystem, termCode, category, displayName, normalizedName,
            "2.78", StandardTermStatus.ACTIVE, null, standardSystem, now, "system", now, "system"
        );
    }

    private static HighRiskRule highRiskRule(Long id,
                                             String ruleCode,
                                             HighRiskRuleType ruleType,
                                             TermCategory category,
                                             String leftTerms,
                                             String rightTerms,
                                             String unitTerms,
                                             Double scaleRatio,
                                             String evidenceText) {
        Instant now = Instant.now();
        return new HighRiskRule(
            id, "SYSTEM", ruleCode, ruleType, category, leftTerms, rightTerms, unitTerms,
            scaleRatio, evidenceText, HighRiskRuleStatus.ACTIVE, now, "system", now, "system"
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
        return mapping(id, 1L, 2L, TermCategory.LAB, TermRiskLevel.HIGH, status);
    }

    private TermMapping mapping(Long id,
                                Long localTermId,
                                Long standardTermId,
                                TermCategory category,
                                TermRiskLevel riskLevel) {
        return mapping(id, localTermId, standardTermId, category, riskLevel, TermMappingStatus.CONFIRMED);
    }

    private TermMapping mapping(Long id,
                                Long localTermId,
                                Long standardTermId,
                                TermCategory category,
                                TermRiskLevel riskLevel,
                                TermMappingStatus status) {
        Instant now = Instant.now();
        return new TermMapping(
            id, "t-1", localTermId, standardTermId, "LIS", category, 0.96,
            riskLevel, status, "同义词 + 单位一致", "u-99", now,
            now, "system", now, "system"
        );
    }

    private void authenticate(RoleCode role) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "u-99",
                "n/a",
                List.of(new SimpleGrantedAuthority(role.authority()))
            )
        );
    }

    @Test
    void generateCandidatesCreatesRuleCandidatesFromSemanticAlias() {
        LocalTerm local = localTerm(1L); // "肌钙蛋白T" / normalizedName = "肌钙蛋白t"
        StandardTerm standardMatch = new StandardTerm(
            2L, "t-1", "LOINC", "6598-7", TermCategory.LAB, "Cardiac troponin T",
            "ctnt|cardiac troponin t|肌钙蛋白t", "2.78", StandardTermStatus.ACTIVE,
            null, "LOINC 来源别名：cTnT；中文同义词：肌钙蛋白T",
            Instant.now(), "system", Instant.now(), "system"
        );
        StandardTerm standardMismatchCategory = new StandardTerm(
            3L, "t-1", "LOINC", "718-8", TermCategory.DRUG, "肌钙蛋白", "肌钙蛋白",
            "2.78", StandardTermStatus.ACTIVE, null, "LOINC", Instant.now(), "system", Instant.now(), "system"
        );

        when(localTermRepository.findByTenantIdAndSourceSystemAndStatus("t-1", "LIS", LocalTermStatus.UNMAPPED))
            .thenReturn(List.of(local));
        when(standardTermRepository.findByTenantIdsAndStatus(standardSources(), "t-1", StandardTermStatus.ACTIVE))
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
                assertThat(candidate.semanticMatchScore()).isEqualTo(0.96);
                assertThat(candidate.highRiskFlag()).isFalse();
                assertThat(candidate.source()).isEqualTo(MappingCandidateSource.RULE);
            });

        ArgumentCaptor<MappingCandidate> candidateCaptor = ArgumentCaptor.forClass(MappingCandidate.class);
        verify(candidateRepository).save(candidateCaptor.capture());
        MappingCandidate created = candidateCaptor.getValue();
        assertThat(created.localTermId()).isEqualTo(1L);
        assertThat(created.standardTermId()).isEqualTo(2L);
        assertThat(created.status()).isEqualTo(MappingCandidateStatus.PENDING);
        assertThat(created.confidence()).isEqualTo(0.96);
        assertThat(created.riskLevel()).isEqualTo(TermRiskLevel.LOW);
        assertThat(created.candidateSource()).isEqualTo(MappingCandidateSource.RULE);
        assertThat(created.evidenceText()).contains("确定性语义匹配", "同义词/缩写");
        assertThat(created.evidenceText()).doesNotContain("LCS");
    }

    @Test
    void generateCandidatesWhenSemanticAssistDisabledUsesOnlyExactCode() {
        LocalTerm exactLocal = localTerm(
            1L, "LIS", "718-7", TermCategory.LAB, "院内血色素", "yuanneixuesesu"
        );
        LocalTerm aliasLocal = localTerm(3L);
        StandardTerm exactStandard = standardTerm(
            2L, "LOINC", "718-7", TermCategory.LAB, "血红蛋白", "hgb|hemoglobin"
        );
        StandardTerm aliasStandard = new StandardTerm(
            4L, "t-1", "LOINC", "6598-7", TermCategory.LAB, "Cardiac troponin T",
            "ctnt|cardiac troponin t|肌钙蛋白t", "2.78", StandardTermStatus.ACTIVE,
            null, "LOINC 来源别名：cTnT；中文同义词：肌钙蛋白T",
            Instant.now(), "system", Instant.now(), "system"
        );

        when(localTermRepository.findByTenantIdAndSourceSystemAndStatus("t-1", "LIS", LocalTermStatus.UNMAPPED))
            .thenReturn(List.of(exactLocal, aliasLocal));
        when(standardTermRepository.findByTenantIdsAndStatus(standardSources(), "t-1", StandardTermStatus.ACTIVE))
            .thenReturn(List.of(exactStandard, aliasStandard));
        when(candidateRepository.findByTenantIdAndLocalTermIdAndStandardTermIdAndStatus(
            eq("t-1"), eq(1L), eq(2L), eq(MappingCandidateStatus.PENDING)
        )).thenReturn(Optional.empty());
        when(candidateRepository.save(any(MappingCandidate.class))).thenAnswer(inv -> inv.getArgument(0));

        TerminologyCandidateGenerationResponse response = service.generateCandidates(
            candidateGenerationRequest("t-1", false)
        );

        assertThat(response.generatedCount()).isEqualTo(1);
        assertThat(response.candidates())
            .singleElement()
            .satisfies(candidate -> {
                assertThat(candidate.localTermId()).isEqualTo(1L);
                assertThat(candidate.standardTermId()).isEqualTo(2L);
                assertThat(candidate.semanticMatchScore()).isEqualTo(1.0);
                assertThat(candidate.evidenceText()).contains("精确编码");
            });
        verify(candidateRepository).save(any(MappingCandidate.class));
        verify(highRiskRuleRepository, Mockito.never()).findActiveByTenantIdAndCategory(any(), any());
    }

    @Test
    void generateCandidatesPrioritizesExactCodeAsSemanticEvidence() {
        LocalTerm local = localTerm(
            1L, "LIS", "718-7", TermCategory.LAB, "院内血色素", "yuanneixuesesu"
        );
        StandardTerm standardMatch = standardTerm(
            2L, "LOINC", "718-7", TermCategory.LAB, "血红蛋白", "hgb|hemoglobin"
        );

        when(localTermRepository.findByTenantIdAndSourceSystemAndStatus("t-1", "LIS", LocalTermStatus.UNMAPPED))
            .thenReturn(List.of(local));
        when(standardTermRepository.findByTenantIdsAndStatus(standardSources(), "t-1", StandardTermStatus.ACTIVE))
            .thenReturn(List.of(standardMatch));
        when(candidateRepository.findByTenantIdAndLocalTermIdAndStandardTermIdAndStatus(
            eq("t-1"), eq(1L), eq(2L), eq(MappingCandidateStatus.PENDING)
        )).thenReturn(Optional.empty());
        when(candidateRepository.save(any(MappingCandidate.class))).thenAnswer(inv -> inv.getArgument(0));

        TerminologyCandidateGenerationResponse response = service.generateCandidates(candidateGenerationRequest("t-1"));

        assertThat(response.generatedCount()).isEqualTo(1);
        ArgumentCaptor<MappingCandidate> candidateCaptor = ArgumentCaptor.forClass(MappingCandidate.class);
        verify(candidateRepository).save(candidateCaptor.capture());
        MappingCandidate created = candidateCaptor.getValue();
        assertThat(created.confidence()).isEqualTo(1.0);
        assertThat(created.riskLevel()).isEqualTo(TermRiskLevel.LOW);
        assertThat(created.evidenceText()).contains("确定性语义匹配", "精确编码");
        assertThat(created.evidenceText()).doesNotContain("同义词/缩写", "LCS");
    }

    @Test
    void generateCandidatesCreatesMediumRiskCandidateFromCodeFamilyOnly() {
        LocalTerm local = localTerm(
            1L, "LIS", "I63900A", TermCategory.DIAGNOSIS, "院内脑血管事件", "yuanneinaoxueguanshijian"
        );
        StandardTerm standardMatch = standardTerm(
            2L, "ICD-10", "I63.900", TermCategory.DIAGNOSIS, "脑梗死", "naogengsi"
        );

        when(localTermRepository.findByTenantIdAndSourceSystemAndStatus("t-1", "LIS", LocalTermStatus.UNMAPPED))
            .thenReturn(List.of(local));
        when(standardTermRepository.findByTenantIdsAndStatus(standardSources(), "t-1", StandardTermStatus.ACTIVE))
            .thenReturn(List.of(standardMatch));
        when(candidateRepository.findByTenantIdAndLocalTermIdAndStandardTermIdAndStatus(
            eq("t-1"), eq(1L), eq(2L), eq(MappingCandidateStatus.PENDING)
        )).thenReturn(Optional.empty());
        when(candidateRepository.save(any(MappingCandidate.class))).thenAnswer(inv -> inv.getArgument(0));

        TerminologyCandidateGenerationResponse response = service.generateCandidates(candidateGenerationRequest("t-1"));

        assertThat(response.generatedCount()).isEqualTo(1);
        ArgumentCaptor<MappingCandidate> candidateCaptor = ArgumentCaptor.forClass(MappingCandidate.class);
        verify(candidateRepository).save(candidateCaptor.capture());
        MappingCandidate created = candidateCaptor.getValue();
        assertThat(created.confidence()).isEqualTo(0.82);
        assertThat(created.riskLevel()).isEqualTo(TermRiskLevel.MEDIUM);
        assertThat(created.evidenceText()).contains("确定性语义匹配", "编码族");
        assertThat(created.evidenceText()).doesNotContain("同义词/缩写", "LCS");
    }

    @Test
    void generateCandidatesMarksSameLocalMultiStandardCandidatesAsOpenConflict() {
        LocalTerm local = localTerm(
            1L, "HIS-DIAG", "DIA-001", TermCategory.DIAGNOSIS, "急性脑梗死", "急性脑梗死"
        );
        StandardTerm icdA = standardTerm(
            2L, "ICD-10", "I63.900", TermCategory.DIAGNOSIS, "脑梗死", "急性脑梗死|脑梗死"
        );
        StandardTerm icdB = standardTerm(
            3L, "ICD-10", "I63.901", TermCategory.DIAGNOSIS, "脑梗死急性期", "急性脑梗死|脑梗死急性期"
        );

        when(localTermRepository.findByTenantIdAndSourceSystemAndStatus("t-1", "LIS", LocalTermStatus.UNMAPPED))
            .thenReturn(List.of(local));
        when(standardTermRepository.findByTenantIdsAndStatus(standardSources(), "t-1", StandardTermStatus.ACTIVE))
            .thenReturn(List.of(icdA, icdB));
        when(candidateRepository.findByTenantIdAndLocalTermIdAndStandardTermIdAndStatus(
            eq("t-1"), eq(1L), eq(2L), eq(MappingCandidateStatus.PENDING)
        )).thenReturn(Optional.empty());
        when(candidateRepository.findByTenantIdAndLocalTermIdAndStandardTermIdAndStatus(
            eq("t-1"), eq(1L), eq(3L), eq(MappingCandidateStatus.PENDING)
        )).thenReturn(Optional.empty());
        when(candidateRepository.save(any(MappingCandidate.class))).thenAnswer(inv -> inv.getArgument(0));
        when(conflictRepository.save(any(MappingConflict.class))).thenAnswer(inv -> inv.getArgument(0));

        TerminologyCandidateGenerationResponse response = service.generateCandidates(candidateGenerationRequest("t-1"));

        assertThat(response.generatedCount()).isEqualTo(2);
        ArgumentCaptor<MappingCandidate> candidateCaptor = ArgumentCaptor.forClass(MappingCandidate.class);
        verify(candidateRepository, Mockito.times(2)).save(candidateCaptor.capture());
        assertThat(candidateCaptor.getAllValues())
            .allSatisfy(saved -> {
                assertThat(saved.conflictFlag()).isTrue();
                assertThat(saved.status()).isEqualTo(MappingCandidateStatus.PENDING);
            });

        ArgumentCaptor<MappingConflict> conflictCaptor = ArgumentCaptor.forClass(MappingConflict.class);
        verify(conflictRepository).save(conflictCaptor.capture());
        assertThat(conflictCaptor.getValue().conflictType()).isEqualTo(MappingConflictType.ONE_TO_MANY);
        assertThat(conflictCaptor.getValue().localTermId()).isEqualTo(1L);
        assertThat(conflictCaptor.getValue().standardTermId()).isNull();
        assertThat(conflictCaptor.getValue().status()).isEqualTo(MappingConflictStatus.OPEN);
        assertThat(conflictCaptor.getValue().description()).contains("2 个标准候选", "待人工裁决");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("highRiskNearNegativeCases")
    void generateCandidatesMarksHighRiskNearNegativeCandidates(
            String caseName,
            LocalTerm local,
            StandardTerm standard,
            HighRiskRule rule,
            String expectedEvidence) {
        when(localTermRepository.findByTenantIdAndSourceSystemAndStatus("t-1", "LIS", LocalTermStatus.UNMAPPED))
            .thenReturn(List.of(local));
        when(standardTermRepository.findByTenantIdsAndStatus(standardSources(), "t-1", StandardTermStatus.ACTIVE))
            .thenReturn(List.of(standard));
        when(highRiskRuleRepository.findActiveByTenantIdAndCategory("t-1", local.category()))
            .thenReturn(List.of(rule));
        when(candidateRepository.findByTenantIdAndLocalTermIdAndStandardTermIdAndStatus(
            eq("t-1"), eq(local.id()), eq(standard.id()), eq(MappingCandidateStatus.PENDING)
        )).thenReturn(Optional.empty());
        when(candidateRepository.save(any(MappingCandidate.class))).thenAnswer(inv -> inv.getArgument(0));

        TerminologyCandidateGenerationResponse response = service.generateCandidates(candidateGenerationRequest("t-1"));

        assertThat(response.generatedCount()).isEqualTo(1);
        assertThat(response.candidates())
            .singleElement()
            .satisfies(candidate -> {
                assertThat(candidate.highRiskFlag()).isTrue();
                assertThat(candidate.riskLevel()).isEqualTo(TermRiskLevel.HIGH);
                assertThat(candidate.evidenceText()).contains(expectedEvidence, "禁止批量确认", "二次确认");
            });

        ArgumentCaptor<MappingCandidate> candidateCaptor = ArgumentCaptor.forClass(MappingCandidate.class);
        verify(candidateRepository).save(candidateCaptor.capture());
        MappingCandidate created = candidateCaptor.getValue();
        assertThat(created.riskLevel()).isEqualTo(TermRiskLevel.HIGH);
        assertThat(created.evidenceText()).contains("高危近似判别", expectedEvidence);
        assertThat(created.evidenceText()).doesNotContain("LCS");
    }

    @Test
    void generateCandidatesDoesNotTreatShortLatinFragmentsAsKNaRisk() {
        LocalTerm local = localTerm(
            1L, "LIS", "DRUG-KET", TermCategory.DRUG, "酮咯酸注射液", "ketorolac"
        );
        StandardTerm standard = standardTerm(
            2L, "YPBM", "NAPROXEN", TermCategory.DRUG, "萘普生片", "naproxen"
        );

        when(localTermRepository.findByTenantIdAndSourceSystemAndStatus("t-1", "LIS", LocalTermStatus.UNMAPPED))
            .thenReturn(List.of(local));
        when(standardTermRepository.findByTenantIdsAndStatus(standardSources(), "t-1", StandardTermStatus.ACTIVE))
            .thenReturn(List.of(standard));
        when(highRiskRuleRepository.findActiveByTenantIdAndCategory("t-1", TermCategory.DRUG))
            .thenReturn(List.of(highRiskRule(
                2L, "MED-C1-K-NA", HighRiskRuleType.MUTUALLY_EXCLUSIVE_TERMS, TermCategory.DRUG,
                "钾|k|k+|potassium|氯化钾|kcl", "钠|na|na+|sodium|氯化钠|nacl", null, null,
                "钾/钠高危近似"
            )));

        TerminologyCandidateGenerationResponse response = service.generateCandidates(candidateGenerationRequest("t-1"));

        assertThat(response.generatedCount()).isZero();
        assertThat(response.candidates()).isEmpty();
        verify(candidateRepository, Mockito.never()).save(any(MappingCandidate.class));
    }

    @Test
    void generateCandidatesDoesNotUseCharacterOverlapAsSemanticEvidence() {
        LocalTerm local = localTerm(1L); // 与“血红蛋白”有“蛋白”字符重合，但医学语义不同
        StandardTerm hemoglobin = standardTerm(2L, TermCategory.LAB);

        when(localTermRepository.findByTenantIdAndSourceSystemAndStatus("t-1", "LIS", LocalTermStatus.UNMAPPED))
            .thenReturn(List.of(local));
        when(standardTermRepository.findByTenantIdsAndStatus(standardSources(), "t-1", StandardTermStatus.ACTIVE))
            .thenReturn(List.of(hemoglobin));
        when(candidateRepository.findByTenantIdAndLocalTermIdAndStandardTermIdAndStatus(
            eq("t-1"), eq(1L), eq(2L), eq(MappingCandidateStatus.PENDING)
        )).thenReturn(Optional.empty());
        when(candidateRepository.save(any(MappingCandidate.class))).thenAnswer(inv -> inv.getArgument(0));

        TerminologyCandidateGenerationResponse response = service.generateCandidates(candidateGenerationRequest("t-1"));

        assertThat(response.generatedCount()).isZero();
        assertThat(response.candidates()).isEmpty();
        verify(candidateRepository, Mockito.never()).save(any(MappingCandidate.class));
    }

    private static Stream<Arguments> highRiskNearNegativeCases() {
        return Stream.of(
            Arguments.of(
                "肌钙蛋白 T/I 强制高危",
                localTerm(1L, "LIS", "LIS-TNT", TermCategory.LAB, "肌钙蛋白T", "ctnt|肌钙蛋白t"),
                standardTerm(2L, "LOINC", "42757-5", TermCategory.LAB, "Cardiac troponin I", "ctni|肌钙蛋白i"),
                highRiskRule(
                    1L, "MED-C1-TROPONIN-TI", HighRiskRuleType.MUTUALLY_EXCLUSIVE_TERMS, TermCategory.LAB,
                    "肌钙蛋白t|ctnt|troponint|tnt", "肌钙蛋白i|ctni|troponini|tni", null, null,
                    "肌钙蛋白 T/I 高危近似"
                ),
                "肌钙蛋白 T/I"
            ),
            Arguments.of(
                "钾/钠强制高危",
                localTerm(1L, "LIS", "LIS-K", TermCategory.DRUG, "氯化钾注射液", "kcl|氯化钾"),
                standardTerm(2L, "YPBM", "YP-NA", TermCategory.DRUG, "氯化钠注射液", "nacl|氯化钠"),
                highRiskRule(
                    2L, "MED-C1-K-NA", HighRiskRuleType.MUTUALLY_EXCLUSIVE_TERMS, TermCategory.DRUG,
                    "钾|k|k+|potassium|氯化钾|kcl", "钠|na|na+|sodium|氯化钠|nacl", null, null,
                    "钾/钠高危近似"
                ),
                "钾/钠"
            ),
            Arguments.of(
                "血钠/血清钾强制高危",
                localTerm(1L, "LIS", "NA001", TermCategory.LAB, "血钠", "血钠|Na|sodium"),
                standardTerm(2L, "LOINC", "2823-3", TermCategory.LAB, "血清钾", "血清钾|血钾|K|potassium"),
                highRiskRule(
                    6L, "MED-C1-K-NA", HighRiskRuleType.MUTUALLY_EXCLUSIVE_TERMS, null,
                    "钾|k|k+|potassium|氯化钾|kcl", "钠|na|na+|sodium|氯化钠|nacl", null, null,
                    "钾/钠高危近似"
                ),
                "钾/钠"
            ),
            Arguments.of(
                "左/右强制高危",
                localTerm(1L, "LIS", "PROC-L", TermCategory.PROCEDURE, "左肾切除术", "左肾切除"),
                standardTerm(2L, "ICD-9-CM-3", "55.5101", TermCategory.PROCEDURE, "右肾切除术", "右肾切除"),
                highRiskRule(
                    3L, "MED-C1-LEFT-RIGHT", HighRiskRuleType.MUTUALLY_EXCLUSIVE_TERMS, null,
                    "左|left", "右|right", null, null, "左/右部位高危近似"
                ),
                "左/右"
            ),
            Arguments.of(
                "剂量 10 倍量级强制高危",
                localTerm(1L, "LIS", "DRUG-10", TermCategory.DRUG, "阿托伐他汀 10mg 片", "阿托伐他汀10mg"),
                standardTerm(2L, "YPBM", "DRUG-100", TermCategory.DRUG, "阿托伐他汀 100mg 片", "阿托伐他汀100mg"),
                highRiskRule(
                    4L, "MED-C1-DOSE-10X", HighRiskRuleType.DOSE_MAGNITUDE, TermCategory.DRUG,
                    "", "", "mg|毫克", 10.0, "剂量量级 10 倍高危近似"
                ),
                "剂量量级"
            ),
            Arguments.of(
                "胰岛素 U/mL 强制高危",
                localTerm(1L, "LIS", "INS-100", TermCategory.DRUG, "胰岛素 100U/mL", "insulin|100u/ml"),
                standardTerm(2L, "YPBM", "INS", TermCategory.DRUG, "胰岛素注射液", "insulin|胰岛素"),
                highRiskRule(
                    5L, "MED-C1-INSULIN-UML", HighRiskRuleType.UNIT_STRENGTH, TermCategory.DRUG,
                    "胰岛素|insulin", "", "u/ml|iu/ml|单位/ml|单位每毫升", null,
                    "胰岛素 U/mL 单位高危近似"
                ),
                "胰岛素 U/mL"
            )
        );
    }

    private static List<String> standardSources() {
        return List.of(PlatformAuthority.PLATFORM_TENANT_ID);
    }

    private TerminologyCandidateGenerationRequest candidateGenerationRequest(String tenantId) {
        return candidateGenerationRequest(tenantId, null);
    }

    private TerminologyCandidateGenerationRequest candidateGenerationRequest(String tenantId, Boolean semanticAssistEnabled) {
        return new TerminologyCandidateGenerationRequest(
            "req-api04-001", "trace-api04-001", tenantId, "g-1", "h-1", "c-1", "s-1",
            "d-1", "sp-1", "u-99", List.of("knowledge-governor"), "pkg-2026.06",
            "LIS", null, semanticAssistEnabled
        );
    }

    private TerminologyCandidateConfirmRequest confirmRequest(Boolean acknowledged, String reason) {
        return new TerminologyCandidateConfirmRequest(
            "req-api04-confirm", "trace-api04-confirm", "t-1", "g-1", "h-1", "c-1", "s-1",
            "d-1", "sp-1", "u-99", List.of("knowledge-governor"), "pkg-2026.06",
            "专家逐条确认", null, acknowledged, reason
        );
    }

    private TerminologyCandidateBatchConfirmRequest batchConfirmRequest(List<Long> candidateIds) {
        return new TerminologyCandidateBatchConfirmRequest(
            "req-api04-batch", "trace-api04-batch", "t-1", "g-1", "h-1", "c-1", "s-1",
            "d-1", "sp-1", "u-99", List.of("knowledge-governor"), "pkg-2026.06",
            candidateIds, "批量确认"
        );
    }

    private ResolveConflictRequest resolveConflictRequest(String resolutionNote) {
        return new ResolveConflictRequest(
            "req-api04-conflict", "trace-api04-conflict", "t-1", "g-1", "h-1", "c-1", "s-1",
            "d-1", "sp-1", "u-99", List.of("knowledge-governor"), "pkg-2026.06",
            resolutionNote
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
