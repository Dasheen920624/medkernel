package com.medkernel.compliance.masking;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.shared.security.DataAccessLevel;
import com.medkernel.shared.security.ResolvedDataScope;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecordCommand;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MaskingServiceTest {

    private MaskingRuleRepository repository;
    private AuditRecorder auditRecorder;
    private MaskingService service;

    @BeforeEach
    void setUp() {
        repository = mock(MaskingRuleRepository.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new MaskingService(repository, auditRecorder);
    }

    @Test
    void maskFailsClosedWhenSensitiveFieldHasNoActiveRule() {
        when(repository.findActiveRule("t-1", "clinical_case", "patientPhone", "AI_REVIEW"))
            .thenReturn(Optional.empty());
        when(repository.findActiveRule("t-1", "clinical_case", "patientPhone", "DEFAULT"))
            .thenReturn(Optional.empty());
        MaskingRequest request = new MaskingRequest(
            "t-1",
            "clinical_case",
            "AI_REVIEW",
            Map.of("patientPhone", "13800138000", "diagnosisName", "心律失常"),
            List.of("patientPhone"));

        ApiException ex = catchThrowableOfType(() -> service.mask(desensitizedScope(), request), ApiException.class);

        assertThat(ex.errorCode()).isEqualTo(ErrorCode.DATA_SCOPE_DENIED);
        assertThat(ex.getMessage()).contains("脱敏规则未配置").contains("patientPhone");
    }

    @Test
    void maskAppliesScenarioRuleForDesensitizedScopeAndDoesNotLeakRawValue() {
        when(repository.findActiveRule("t-1", "clinical_case", "patientPhone", "AI_REVIEW"))
            .thenReturn(Optional.of(activeRule("patientPhone", "AI_REVIEW", MaskingStrategy.KEEP_LAST, 0, 4)));
        MaskingRequest request = new MaskingRequest(
            "t-1",
            "Clinical Case",
            "ai review",
            Map.of("patientPhone", "13800138000", "diagnosisName", "心律失常"),
            List.of(" patientPhone "));

        MaskingResult result = service.mask(desensitizedScope(), request);

        assertThat(result.rawAllowed()).isFalse();
        assertThat(result.maskedFields()).containsExactly("patientPhone");
        assertThat(result.values())
            .containsEntry("patientPhone", "*******8000")
            .containsEntry("diagnosisName", "心律失常");
    }

    @Test
    void maskFallsBackToDefaultRuleWhenScenarioRuleIsMissing() {
        when(repository.findActiveRule("t-1", "clinical_case", "patientPhone", "AI_REVIEW"))
            .thenReturn(Optional.empty());
        when(repository.findActiveRule("t-1", "clinical_case", "patientPhone", "DEFAULT"))
            .thenReturn(Optional.of(activeRule("patientPhone", "DEFAULT", MaskingStrategy.FIXED, 0, 0)));
        MaskingRequest request = new MaskingRequest(
            "t-1",
            "clinical_case",
            "AI_REVIEW",
            Map.of("patientPhone", "13800138000"),
            List.of("patientPhone"));

        MaskingResult result = service.mask(desensitizedScope(), request);

        assertThat(result.values()).containsEntry("patientPhone", "***");
        assertThat(result.maskedFields()).containsExactly("patientPhone");
    }

    @Test
    void maskPreservesNullSensitiveFieldWithoutLeakingOrCrashing() {
        when(repository.findActiveRule("t-1", "clinical_case", "patientPhone", "DEFAULT"))
            .thenReturn(Optional.of(activeRule("patientPhone", "DEFAULT", MaskingStrategy.KEEP_LAST, 0, 4)));
        Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("diagnosisName", "心律失常");
        values.put("patientPhone", null);
        MaskingRequest request = new MaskingRequest(
            "t-1",
            "clinical_case",
            null,
            values,
            List.of("patientPhone"));

        MaskingResult result = service.mask(desensitizedScope(), request);

        assertThat(result.rawAllowed()).isFalse();
        assertThat(result.maskedFields()).containsExactly("patientPhone");
        assertThat(result.values()).containsEntry("patientPhone", null);
    }

    @Test
    void maskAllowsRawOnlyWhenScopeHasRawDataAndRuleExists() {
        when(repository.findActiveRule("t-1", "clinical_case", "patientPhone", "DEFAULT"))
            .thenReturn(Optional.of(activeRule("patientPhone", "DEFAULT", MaskingStrategy.KEEP_LAST, 0, 4)));
        MaskingRequest request = new MaskingRequest(
            "t-1",
            "clinical_case",
            null,
            Map.of("patientPhone", "13800138000"),
            List.of("patientPhone"));

        MaskingResult result = service.mask(rawScope(), request);

        assertThat(result.rawAllowed()).isTrue();
        assertThat(result.maskedFields()).isEmpty();
        assertThat(result.values()).containsEntry("patientPhone", "13800138000");
    }

    @Test
    void listRulesReturnsTenantScopedPageInsteadOfUnboundedList() {
        MaskingRule row = activeRule("patientPhone", "DEFAULT", MaskingStrategy.KEEP_LAST, 0, 4);
        when(repository.countRules("t-1", "clinical_case", "patientPhone")).thenReturn(25L);
        when(repository.pageRules("t-1", "clinical_case", "patientPhone", 10, 10)).thenReturn(List.of(row));

        PageResponse<MaskingRuleResponse> page = service.listRules(
            "t-1", "Clinical Case", "patientPhone", new PageRequest(2, 10, null));

        assertThat(page.items()).extracting(MaskingRuleResponse::ruleId)
            .containsExactly("mask-clinical-case-patientPhone-default");
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.total()).isEqualTo(25);
        assertThat(page.hasNext()).isTrue();
        verify(repository).countRules("t-1", "clinical_case", "patientPhone");
        verify(repository).pageRules("t-1", "clinical_case", "patientPhone", 10, 10);
    }

    @Test
    void upsertRuleNormalizesScenarioAndRecordsPermissionChangeAudit() {
        when(repository.findByTenantIdAndResourceTypeAndFieldNameAndScenarioCode(
            "t-1", "clinical_case", "patientPhone", "DEFAULT"))
            .thenReturn(Optional.empty());
        when(repository.save(any(MaskingRule.class)))
            .thenAnswer(invocation -> invocation.<MaskingRule>getArgument(0).withId(11L));
        MaskingRuleRequest request = new MaskingRuleRequest(
            "Clinical Case",
            " patientPhone ",
            " ",
            MaskingStrategy.KEEP_LAST,
            "*",
            0,
            4,
            MaskingRuleStatus.ACTIVE,
            "SYS-06 PR2 后端脱敏规则基线",
            null);

        MaskingRuleResponse response = service.upsertRule("t-1", request, "admin-1");

        assertThat(response.ruleId()).isEqualTo("mask-clinical-case-patientPhone-default");
        assertThat(response.resourceType()).isEqualTo("clinical_case");
        assertThat(response.fieldName()).isEqualTo("patientPhone");
        assertThat(response.scenarioCode()).isEqualTo("DEFAULT");
        ArgumentCaptor<AuditRecordCommand> audit = ArgumentCaptor.forClass(AuditRecordCommand.class);
        verify(auditRecorder).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.PERMISSION_CHANGE);
        assertThat(audit.getValue().targetType()).isEqualTo("mk_compliance_masking_rule");
        assertThat(audit.getValue().summary()).contains("SYS-06 PR2 后端脱敏规则基线");
    }

    private ResolvedDataScope desensitizedScope() {
        return new ResolvedDataScope(
            DataAccessLevel.HOSPITAL,
            new OrgScope("t-1", "g-1", "h-1", null, null, "cardiology", null),
            true);
    }

    private ResolvedDataScope rawScope() {
        return new ResolvedDataScope(
            DataAccessLevel.HOSPITAL,
            new OrgScope("t-1", "g-1", "h-1", null, null, "cardiology", null),
            false);
    }

    private MaskingRule activeRule(
            String fieldName, String scenarioCode, MaskingStrategy strategy, Integer prefixKeep, Integer suffixKeep) {
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        return new MaskingRule(
            1L,
            "mask-clinical-case-" + fieldName + "-" + scenarioCode.toLowerCase(),
            "t-1",
            "clinical_case",
            fieldName,
            scenarioCode,
            strategy.name(),
            "*",
            prefixKeep,
            suffixKeep,
            "ACTIVE",
            1L,
            now,
            "admin-1",
            now,
            "admin-1",
            "trace-test");
    }
}
