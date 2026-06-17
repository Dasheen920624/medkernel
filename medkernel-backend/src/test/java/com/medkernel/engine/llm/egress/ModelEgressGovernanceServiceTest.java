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
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 出域治理管理服务单元测试（LLM-03 白名单/审批维护）。
 */
class ModelEgressGovernanceServiceTest {

    private ModelEgressWhitelistRepository whitelistRepo;
    private ModelEgressApprovalRepository approvalRepo;
    private AuditRecorder auditRecorder;
    private ModelEgressGovernanceService service;

    @BeforeEach
    void setUp() {
        whitelistRepo = mock(ModelEgressWhitelistRepository.class);
        approvalRepo = mock(ModelEgressApprovalRepository.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new ModelEgressGovernanceService(whitelistRepo, approvalRepo, auditRecorder);
        RequestContext.restore(new RequestContext.Snapshot("trace-1", OrgScope.tenant("tenant-1"), "compliance-001"));
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
            "保存模型出域白名单 knowledge.extract");
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
        assertThat(saved.approvalThresholdLevel()).isEqualTo("MEDIUM");
        assertThat(saved.guardrailLockedFlag()).isEqualTo("Y");
        verify(auditRecorder).record(AuditAction.UPDATE, "mk_llm_egress_whitelist", "knowledge.extract",
            "保存模型出域白名单 knowledge.extract");
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
    void decideApproval_recordsApprovedRecordWithApproverAndAudits() {
        when(approvalRepo.save(any(ModelEgressApproval.class))).thenAnswer(i -> i.getArgument(0));

        ModelEgressApproval approval = service.decideApproval(
            new ModelEgressApprovalRequest("knowledge.extract", "hash-abc", "APPROVED"));

        assertThat(approval.tenantId()).isEqualTo("tenant-1");
        assertThat(approval.status()).isEqualTo("APPROVED");
        assertThat(approval.approver()).isEqualTo("compliance-001");
        assertThat(approval.payloadHash()).isEqualTo("hash-abc");
        verify(auditRecorder).record(any(AuditAction.class), org.mockito.ArgumentMatchers.eq("mk_llm_egress_approval"),
            org.mockito.ArgumentMatchers.eq("hash-abc"), any());
    }
}
