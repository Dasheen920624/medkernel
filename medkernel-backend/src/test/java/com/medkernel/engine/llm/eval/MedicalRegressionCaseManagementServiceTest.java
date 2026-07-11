package com.medkernel.engine.llm.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MedicalRegressionCaseManagementServiceTest {

    private MedicalRegressionCaseRepository repository;
    private AuditRecorder auditRecorder;
    private MedicalRegressionCaseManagementService service;

    @BeforeEach
    void setUp() {
        repository = mock(MedicalRegressionCaseRepository.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new MedicalRegressionCaseManagementService(repository, auditRecorder);
        RequestContext.restore(new RequestContext.Snapshot("trace-regression", OrgScope.tenant("tenant-1"), "quality-001"));
        when(repository.save(any(MedicalRegressionCase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findAllByTenantIdAndCapabilityCodeAndCaseInputOrderByUpdatedAtDesc(any(), any(), any()))
            .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void createsCaseWithStructuredSourceReferenceAndNormalizedCapability() {
        MedicalRegressionCaseRequest request = request("Rule.Draft", "2026.1", "source-version:77#dose-limit");

        MedicalRegressionCase created = service.create(request);

        assertThat(created.tenantId()).isEqualTo("tenant-1");
        assertThat(created.capabilityCode()).isEqualTo("rule.draft");
        assertThat(created.sourceReference()).isEqualTo("source-version:77#dose-limit");
        assertThat(created.caseVersion()).isEqualTo("2026.1");
        assertThat(created.enabledFlag()).isEqualTo("Y");
        assertThat(created.createdBy()).isEqualTo("quality-001");
        verify(repository).save(argThat(saved -> "rule.draft".equals(saved.capabilityCode())
            && "source-version:77#dose-limit".equals(saved.sourceReference())));
    }

    @Test
    void createsAiQualityCaseWithTermsForbiddenAssertionsAndMinScore() {
        MedicalRegressionCaseRequest request = new MedicalRegressionCaseRequest(
            "recommendation.draft",
            "terminology",
            "请输出推荐解释",
            "建议人工复核",
            List.of("慢性肾脏病"),
            List.of("虚构医保编码 ZZZ-2026"),
            80,
            null,
            true,
            "2026.06",
            "source-version:77#term",
            true);

        MedicalRegressionCase created = service.create(request);

        assertThat(created.caseDomain()).isEqualTo("terminology");
        assertThat(created.expectedTermsJson()).contains("慢性肾脏病");
        assertThat(created.forbiddenAssertionsJson()).contains("虚构医保编码 ZZZ-2026");
        assertThat(created.minScore()).isEqualTo(80);
    }

    @Test
    void rejectsCaseWithoutRealSourceReference() {
        MedicalRegressionCaseRequest request = request("rule.draft", "2026.1", "TODO");

        assertThatThrownBy(() -> service.create(request))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsCaseWhoseSourceReferenceStillContainsPlaceholderToken() {
        MedicalRegressionCaseRequest request = request("rule.draft", "2026.1", "source://todo/dose-limit");

        assertThatThrownBy(() -> service.create(request))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void enablesAndDisablesOnlyCurrentTenantCases() {
        MedicalRegressionCase existing = caseRow(9L, "tenant-1", "Y");
        when(repository.findByIdAndTenantId(9L, "tenant-1")).thenReturn(Optional.of(existing));

        MedicalRegressionCase disabled = service.setEnabled(9L, false);

        assertThat(disabled.enabledFlag()).isEqualTo("N");
        assertThat(disabled.updatedBy()).isEqualTo("quality-001");
        verify(repository).save(argThat(saved -> saved.id().equals(9L) && "N".equals(saved.enabledFlag())));
    }

    @Test
    void rejectsEnableDisableAcrossTenantBoundary() {
        when(repository.findByIdAndTenantId(9L, "tenant-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setEnabled(9L, false))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void bulkImportPreservesEachCaseVersion() {
        MedicalRegressionCaseBulkImportRequest request = new MedicalRegressionCaseBulkImportRequest(List.of(
            request("rule.draft", "2026.1", "source-version:77#dose-limit"),
            request("knowledge.extract", "2026.2", "source-version:88#extract")));

        List<MedicalRegressionCase> imported = service.bulkImport(request);

        assertThat(imported)
            .extracting(MedicalRegressionCase::caseVersion)
            .containsExactly("2026.1", "2026.2");
        verify(repository).save(argThat(saved -> "knowledge.extract".equals(saved.capabilityCode())
            && "source-version:88#extract".equals(saved.sourceReference())));
    }

    @Test
    void bulkImportUpdatesExistingCaseByCapabilityAndInputWithoutDuplicating() {
        MedicalRegressionCase existing = caseRow(17L, "tenant-1", "N");
        when(repository.findAllByTenantIdAndCapabilityCodeAndCaseInputOrderByUpdatedAtDesc(
            "tenant-1",
            "rule.draft",
            "请依据真实来源判断候选知识是否必须阻断。"))
            .thenReturn(List.of(existing));
        MedicalRegressionCaseBulkImportRequest request = new MedicalRegressionCaseBulkImportRequest(List.of(
            request("rule.draft", "2026.9", "source-version:99#dose-limit")));

        List<MedicalRegressionCase> imported = service.bulkImport(request);

        assertThat(imported).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(17L);
            assertThat(item.caseVersion()).isEqualTo("2026.9");
            assertThat(item.sourceReference()).isEqualTo("source-version:99#dose-limit");
            assertThat(item.enabledFlag()).isEqualTo("Y");
            assertThat(item.createdBy()).isEqualTo("seed");
            assertThat(item.updatedBy()).isEqualTo("quality-001");
        });
        verify(repository).save(argThat(saved -> saved.id().equals(17L)
            && "source-version:99#dose-limit".equals(saved.sourceReference())));
    }

    @Test
    void bulkImportKeepsLatestCaseAndDisablesOlderDuplicates() {
        MedicalRegressionCase newest = caseRow(18L, "tenant-1", "Y");
        MedicalRegressionCase older = caseRow(17L, "tenant-1", "Y");
        when(repository.findAllByTenantIdAndCapabilityCodeAndCaseInputOrderByUpdatedAtDesc(
            "tenant-1",
            "rule.draft",
            "请依据真实来源判断候选知识是否必须阻断。"))
            .thenReturn(List.of(newest, older));
        MedicalRegressionCaseBulkImportRequest request = new MedicalRegressionCaseBulkImportRequest(List.of(
            request("rule.draft", "2026.10", "source-version:100#dose-limit")));

        MedicalRegressionCase imported = service.bulkImport(request).getFirst();

        assertThat(imported.id()).isEqualTo(18L);
        verify(repository).save(argThat(saved -> saved.id().equals(18L)
            && "Y".equals(saved.enabledFlag())
            && "source-version:100#dose-limit".equals(saved.sourceReference())));
        verify(repository).save(argThat(saved -> saved.id().equals(17L)
            && "N".equals(saved.enabledFlag())
            && "quality-001".equals(saved.updatedBy())));
    }

    @Test
    void listsEnabledCasesWithoutRequiringCapabilityFilter() {
        when(repository.findByTenantIdAndEnabledFlagOrderByUpdatedAtDesc("tenant-1", "Y"))
            .thenReturn(List.of(caseRow(11L, "tenant-1", "Y")));

        List<MedicalRegressionCase> cases = service.list(null, "Y");

        assertThat(cases).singleElement()
            .extracting(MedicalRegressionCase::id)
            .isEqualTo(11L);
    }

    @Test
    void listsAllCasesWhenNoFilterIsProvided() {
        when(repository.findByTenantIdOrderByUpdatedAtDesc("tenant-1"))
            .thenReturn(List.of(caseRow(12L, "tenant-1", "Y")));

        List<MedicalRegressionCase> cases = service.list(null, null);

        assertThat(cases).singleElement()
            .extracting(MedicalRegressionCase::id)
            .isEqualTo(12L);
    }

    private static MedicalRegressionCaseRequest request(String capabilityCode, String caseVersion, String sourceReference) {
        return new MedicalRegressionCaseRequest(
            capabilityCode,
            "general",
            "请依据真实来源判断候选知识是否必须阻断。",
            "必须阻断",
            List.of(),
            List.of(),
            100,
            "DOSE_LIMIT",
            true,
            caseVersion,
            sourceReference,
            true);
    }

    private static MedicalRegressionCase caseRow(Long id, String tenantId, String enabledFlag) {
        Instant now = Instant.parse("2026-06-16T00:00:00Z");
        return new MedicalRegressionCase(id, tenantId, "rule.draft", "rule", "输入", "期望",
            "[]", "[]", 100, "DOSE_LIMIT", "source-version:77#dose-limit", "Y", "2026.1", enabledFlag,
            now, "seed", now, "seed");
    }
}
