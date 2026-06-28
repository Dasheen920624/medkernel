package com.medkernel.engine.llm.egress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 外调治理管理服务单元测试（LLM-03 允许范围/责任确认维护）。
 */
class ModelEgressGovernanceServiceTest {

    private ModelEgressWhitelistRepository whitelistRepo;
    private ModelEgressConfirmationRepository confirmationRepo;
    private AuditRecorder auditRecorder;
    private ModelEgressGovernanceService service;

    @BeforeEach
    void setUp() {
        whitelistRepo = mock(ModelEgressWhitelistRepository.class);
        confirmationRepo = mock(ModelEgressConfirmationRepository.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new ModelEgressGovernanceService(whitelistRepo, confirmationRepo, auditRecorder);
        RequestContext.restore(new RequestContext.Snapshot("trace-1", OrgScope.tenant("tenant-1"), "operator-001"));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void upsertWhitelist_persistsAllowedFieldsAsJsonArrayAndAudits() {
        when(whitelistRepo.findByTenantIdAndCapabilityCode("tenant-1", "knowledge.extract"))
            .thenReturn(Optional.empty());
        when(whitelistRepo.save(any(ModelEgressWhitelist.class))).thenAnswer(i -> i.getArgument(0));

        ModelEgressWhitelist saved = service.upsertWhitelist("knowledge.extract",
            new ModelEgressWhitelistUpsertRequest(List.of("clinicalText", "ageYears"), "HIGH"));

        assertThat(saved.tenantId()).isEqualTo("tenant-1");
        assertThat(saved.allowedFields()).isEqualTo("[\"clinicalText\",\"ageYears\"]");
        assertThat(saved.sensitivityLevel()).isEqualTo("HIGH");
        verify(auditRecorder).record(AuditAction.UPDATE, "mk_llm_egress_whitelist", "knowledge.extract",
            "保存模型外调允许范围 knowledge.extract");
    }

    @Test
    void upsertWhitelist_persistsPolicyRulesThresholdAndLockedGuardrail() {
        when(whitelistRepo.findByTenantIdAndCapabilityCode("tenant-1", "knowledge.extract"))
            .thenReturn(Optional.empty());
        when(whitelistRepo.save(any(ModelEgressWhitelist.class))).thenAnswer(i -> i.getArgument(0));
        Map<String, String> rules = new LinkedHashMap<>();
        rules.put("clinicalText", "GENERALIZE");
        rules.put("ageYears", "NONE");

        ModelEgressWhitelist saved = service.upsertWhitelist("knowledge.extract",
            new ModelEgressWhitelistUpsertRequest(List.of("clinicalText", "ageYears"), "MEDIUM",
                rules, "MEDIUM"));

        assertThat(saved.desensitizationRules()).isEqualTo("{\"clinicalText\":\"GENERALIZE\",\"ageYears\":\"NONE\"}");
        assertThat(saved.confirmationThresholdLevel()).isEqualTo("MEDIUM");
        assertThat(saved.guardrailLockedFlag()).isEqualTo("Y");
        verify(auditRecorder).record(AuditAction.UPDATE, "mk_llm_egress_whitelist", "knowledge.extract",
            "保存模型外调允许范围 knowledge.extract");
    }

    @Test
    void upsertWhitelist_rejectsUnknownSensitivityLevel() {
        assertThatThrownBy(() -> service.upsertWhitelist("knowledge.extract",
                new ModelEgressWhitelistUpsertRequest(List.of("clinicalText"), "EXTREME")))
            .isInstanceOf(ApiException.class);
        verify(whitelistRepo, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void upsertWhitelist_rejectsUnknownDesensitizationOperator() {
        assertThatThrownBy(() -> service.upsertWhitelist("knowledge.extract",
                new ModelEgressWhitelistUpsertRequest(List.of("clinicalText"), "LOW",
                    Map.of("clinicalText", "RAW"), "HIGH")))
            .isInstanceOf(ApiException.class);
        verify(whitelistRepo, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void confirmEgress_recordsCurrentOperatorPurposeAndAudits() {
        when(confirmationRepo.save(any(ModelEgressConfirmation.class))).thenAnswer(i -> i.getArgument(0));

        ModelEgressConfirmation confirmation = service.confirmEgress(
            new ModelEgressConfirmationRequest(
                "knowledge.extract", "hash-abc", "生成机构知识草稿，仅使用已脱敏字段"));

        assertThat(confirmation.tenantId()).isEqualTo("tenant-1");
        assertThat(confirmation.confirmedBy()).isEqualTo("operator-001");
        assertThat(confirmation.payloadHash()).isEqualTo("hash-abc");
        assertThat(confirmation.purpose()).isEqualTo("生成机构知识草稿，仅使用已脱敏字段");
        verify(auditRecorder).record(any(AuditAction.class),
            org.mockito.ArgumentMatchers.eq("mk_llm_egress_confirmation"),
            org.mockito.ArgumentMatchers.eq("hash-abc"), any());
    }

    @Test
    void listConfirmations_returnsTenantPageForAuditReview() {
        when(confirmationRepo.countByTenantId("tenant-1")).thenReturn(1L);
        when(confirmationRepo.pageByTenantId("tenant-1", 0, 20))
            .thenReturn(List.of(new ModelEgressConfirmation(
                7L,
                "tenant-1",
                "clinical.explanation",
                "sha256:payload-001",
                "向患者解释检查结果，仅使用已脱敏字段",
                "operator-001",
                java.time.Instant.parse("2026-06-25T19:45:00Z"),
                java.time.Instant.parse("2026-06-25T19:45:00Z"),
                "operator-001",
                java.time.Instant.parse("2026-06-25T19:45:00Z"),
                "operator-001"
            )));

        PageResponse<ModelEgressConfirmation> page = service.listConfirmations(new PageRequest(1, 20, null));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.capabilityCode()).isEqualTo("clinical.explanation");
            assertThat(item.payloadHash()).isEqualTo("sha256:payload-001");
            assertThat(item.purpose()).contains("检查结果");
            assertThat(item.confirmedBy()).isEqualTo("operator-001");
        });
    }
}
