package com.medkernel.engine.knowledge.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.medkernel.engine.knowledge.KnowledgeVersionService;
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

/** 诊断知识服务：标准新增映射+审计、测试病例发布门禁（命中一致放行/不一致 ENG_DX_006）、置信策略回退/缺失 ENG_DX_005。 */
class DiagnosisKnowledgeServiceTest {

    private DiagnosisCriterionRepository criteria;
    private DiagnosisDifferentialRepository differentials;
    private DiagnosisCarePointerRepository carePointers;
    private DiagnosisTestCaseRepository testCases;
    private DiagnosisConfidencePolicyRepository policies;
    private AuditEventPublisher audit;
    private KnowledgeVersionService knowledgeVersions;
    private DiagnosisKnowledgeService service;

    private final DiagnosisConfidencePolicy defaultPolicy = new DiagnosisConfidencePolicy(
        1L, "t-1", "DEFAULT", 2, true, 1, null, "system", null, "system", null);

    @BeforeEach
    void setUp() {
        criteria = mock(DiagnosisCriterionRepository.class);
        differentials = mock(DiagnosisDifferentialRepository.class);
        carePointers = mock(DiagnosisCarePointerRepository.class);
        testCases = mock(DiagnosisTestCaseRepository.class);
        policies = mock(DiagnosisConfidencePolicyRepository.class);
        audit = mock(AuditEventPublisher.class);
        knowledgeVersions = mock(KnowledgeVersionService.class);
        // 命中核心用真实实现，门禁测试才有意义。
        DiagnosisMatcher matcher = new DiagnosisMatcher(new DiagnosisConfidenceEvaluator());
        service = new DiagnosisKnowledgeService(criteria, differentials, carePointers, testCases,
            policies, matcher, audit, knowledgeVersions);

        when(criteria.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // 非主租户：策略回退到平台主源 t-1 DEFAULT。
        when(policies.findByTenantIdAndScopeKey("t-dept", "DEFAULT")).thenReturn(Optional.empty());
        when(policies.findByTenantIdAndScopeKey("t-1", "DEFAULT")).thenReturn(Optional.of(defaultPolicy));

        RequestContext.restore(new RequestContext.Snapshot(
            "trace-dx", OrgScope.tenant("t-dept"), "doctor-1"));
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void addCriterionPersistsWithContextAndAudits() {
        service.addCriterion(10L, new DiagnosisCriterionRequest(
            "FEVER", DiagnosisDirection.REQUIRED, DiagnosisWeight.MAJOR, null, null, null));

        ArgumentCaptor<DiagnosisCriterion> cap = ArgumentCaptor.forClass(DiagnosisCriterion.class);
        verify(criteria).save(cap.capture());
        DiagnosisCriterion saved = cap.getValue();
        assertThat(saved.tenantId()).isEqualTo("t-dept");
        assertThat(saved.diagnosisVersionId()).isEqualTo(10L);
        assertThat(saved.findingTermCode()).isEqualTo("FEVER");
        assertThat(saved.direction()).isEqualTo(DiagnosisDirection.REQUIRED);
        assertThat(saved.weight()).isEqualTo(DiagnosisWeight.MAJOR);
        assertThat(saved.createdBy()).isEqualTo("doctor-1");
        assertThat(saved.traceId()).isEqualTo("trace-dx");
        verify(audit).publish(eq(AuditAction.CREATE), eq("mk_diagnosis_criterion"), any(), any());
    }

    @Test
    void publishGatePassesWhenTestCaseMatchesRecomputedConfidence() {
        stubVersionCriteria();
        when(testCases.findByTenantIdAndDiagnosisVersionId("t-dept", 10L))
            .thenReturn(List.of(testCase("CASE-1", "FEVER,COUGH", DiagnosisConfidence.STRONG)));

        assertThatCode(() -> service.publishGate(10L)).doesNotThrowAnyException();
    }

    @Test
    void publishGateThrowsDx006WhenExpectedMismatchesRecomputed() {
        stubVersionCriteria();
        // 命中实得 STRONG，但期望写成 WEAK → 门禁必须阻断发布。
        when(testCases.findByTenantIdAndDiagnosisVersionId("t-dept", 10L))
            .thenReturn(List.of(testCase("CASE-1", "FEVER,COUGH", DiagnosisConfidence.WEAK)));

        assertThatThrownBy(() -> service.publishGate(10L))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(ErrorCode.ENG_DX_006);
    }

    @Test
    void publishGateThrowsDx005WhenNoConfidencePolicy() {
        when(policies.findByTenantIdAndScopeKey("t-1", "DEFAULT")).thenReturn(Optional.empty());
        stubVersionCriteria();
        when(testCases.findByTenantIdAndDiagnosisVersionId("t-dept", 10L))
            .thenReturn(List.of(testCase("CASE-1", "FEVER,COUGH", DiagnosisConfidence.STRONG)));

        assertThatThrownBy(() -> service.publishGate(10L))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(ErrorCode.ENG_DX_005);
    }

    private void stubVersionCriteria() {
        when(criteria.findByTenantIdAndDiagnosisVersionId("t-dept", 10L)).thenReturn(List.of(
            criterion("FEVER", DiagnosisDirection.REQUIRED, DiagnosisWeight.MAJOR),
            criterion("COUGH", DiagnosisDirection.SUPPORTING, DiagnosisWeight.MAJOR)));
    }

    private DiagnosisCriterion criterion(String code, DiagnosisDirection dir, DiagnosisWeight w) {
        Instant now = Instant.now();
        return new DiagnosisCriterion(null, "t-dept", 10L, code, dir, w, null, null, null,
            now, "doctor-1", now, "doctor-1", "trace-dx");
    }

    private DiagnosisTestCase testCase(String code, String findings, DiagnosisConfidence expected) {
        Instant now = Instant.now();
        return new DiagnosisTestCase(null, "t-dept", 10L, code, findings, 7L, expected,
            now, "doctor-1", now, "doctor-1", "trace-dx");
    }
}
