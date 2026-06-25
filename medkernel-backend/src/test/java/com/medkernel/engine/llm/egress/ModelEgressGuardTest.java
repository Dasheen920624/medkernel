package com.medkernel.engine.llm.egress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 出域数据最小化与外调安全闸单元测试（LLM-03 FR-1/2）。
 */
class ModelEgressGuardTest {

    private ModelEgressWhitelistRepository whitelistRepo;
    private ModelEgressConfirmationRepository confirmationRepo;
    private ModelEgressEvidenceRepository evidenceRepo;
    private ModelEgressGuard guard;

    @BeforeEach
    void setUp() {
        whitelistRepo = mock(ModelEgressWhitelistRepository.class);
        confirmationRepo = mock(ModelEgressConfirmationRepository.class);
        evidenceRepo = mock(ModelEgressEvidenceRepository.class);
        guard = new ModelEgressGuard(whitelistRepo, confirmationRepo, evidenceRepo);
    }

    private void whitelist(String allowedFields, String sensitivity) {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        when(whitelistRepo.findByTenantIdAndCapabilityCode("tenant-1", "knowledge.extract"))
            .thenReturn(Optional.of(new ModelEgressWhitelist(
                1L, "tenant-1", "knowledge.extract", allowedFields, sensitivity,
                now, "system", now, "system")));
    }

    private void policy(
            String allowedFields,
            String sensitivity,
            String desensitizationRules,
            String confirmationThreshold) {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        when(whitelistRepo.findByTenantIdAndCapabilityCode("tenant-1", "knowledge.extract"))
            .thenReturn(Optional.of(new ModelEgressWhitelist(
                1L, "tenant-1", "knowledge.extract", allowedFields, sensitivity,
                desensitizationRules, confirmationThreshold, "Y",
                now, "system", now, "system")));
    }

    @Test
    void stripsNonWhitelistedFields() {
        whitelist("[\"clinicalText\"]", "LOW");

        ModelEgressGuard.EgressPreparation prep = guard.prepareEgress(
            "tenant-1", "knowledge.extract",
            "{\"clinicalText\":\"主诉发热三天\",\"patientName\":\"张三\"}",
            "task-1", "ollama-local");

        assertThat(prep.payload()).contains("clinicalText");
        assertThat(prep.payload()).doesNotContain("patientName");
        assertThat(prep.payload()).doesNotContain("张三");
        assertThat(prep.egressFields()).containsExactly("clinicalText");
    }

    @Test
    void blocksWhenWhitelistDoesNotMatchAnyPayloadField() {
        whitelist("[\"clinicalText\"]", "LOW");

        assertThatThrownBy(() -> guard.prepareEgress(
                "tenant-1", "knowledge.extract",
                "{\"prompt\":\"不得绕过允许范围的原始文本\"}",
                "task-empty", "claude"))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).errorCode())
            .isEqualTo(ErrorCode.ENG_LLM_006);
        verify(evidenceRepo, never()).save(any());
    }

    @Test
    void blocksWhenGuardrailLockHasBeenTamperedOff() {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        when(whitelistRepo.findByTenantIdAndCapabilityCode("tenant-1", "knowledge.extract"))
            .thenReturn(Optional.of(new ModelEgressWhitelist(
                1L, "tenant-1", "knowledge.extract", "[\"prompt\"]", "LOW",
                "{}", "HIGH", "N", now, "system", now, "system")));

        assertThatThrownBy(() -> guard.prepareEgress(
                "tenant-1", "knowledge.extract", "{\"prompt\":\"文本\"}", "task-lock", "claude"))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).errorCode())
            .isEqualTo(ErrorCode.ENG_LLM_006);
        verify(evidenceRepo, never()).save(any());
    }

    @Test
    void desensitizesWhitelistedFieldsBeforeEgress() {
        whitelist("[\"clinicalText\"]", "LOW");

        ModelEgressGuard.EgressPreparation prep = guard.prepareEgress(
            "tenant-1", "knowledge.extract",
            "{\"clinicalText\":\"联系电话13988888888请回访\"}",
            "task-1", "ollama-local");

        // FR-2 脱敏强制：手机号出域前必须掩码，绝不裸送
        assertThat(prep.payload()).contains("139****8888");
        assertThat(prep.payload()).doesNotContain("13988888888");
    }

    @Test
    void publicInternetPromptMasksCorePatientSensitiveInformationBeforeExternalModelUse() {
        policy("[\"prompt\"]", "LOW", "{\"prompt\":\"MASK_ALL\"}", "HIGH");

        ModelEgressGuard.EgressPreparation prep = guard.prepareEgress(
            "tenant-1", "knowledge.extract",
            "{\"prompt\":\"患者：张三，身份证号110101199001011234，手机号13988888888，"
                + "邮箱zhangsan@example.com，住址：北京市东城区测试路 1 号，病历号MR-20260625001。"
                + "请生成仅供医生确认的解释。\"}",
            "task-public-patient", "openai-compatible");

        assertThat(prep.payload())
            .doesNotContain("张三")
            .doesNotContain("110101199001011234")
            .doesNotContain("13988888888")
            .doesNotContain("zhangsan@example.com")
            .doesNotContain("北京市东城区测试路")
            .doesNotContain("MR-20260625001")
            .contains("139****8888")
            .contains("110101********1234")
            .contains("住址：[已屏蔽]");
        assertThat(prep.egressFields()).containsExactly("prompt");
    }

    @Test
    void defaultMaskAllRecursivelyProtectsStructuredPayload() {
        whitelist("[\"clinicalContext\"]", "LOW");

        ModelEgressGuard.EgressPreparation prep = guard.prepareEgress(
            "tenant-1", "knowledge.extract",
            "{\"clinicalContext\":{\"patientName\":\"张三\",\"phone\":\"13988888888\","
                + "\"ageYears\":72,\"confirmed\":true,\"notes\":[\"身份证110101199001011234\"]}}",
            "task-structured", "claude");

        assertThat(prep.payload())
            .doesNotContain("张三")
            .doesNotContain("13988888888")
            .doesNotContain("110101199001011234")
            .contains("\"patientName\":null")
            .contains("\"phone\":null")
            .contains("\"ageYears\":null")
            .contains("\"confirmed\":null");
    }

    @Test
    void appliesConfiguredDesensitizationRulesBeforeEgress() {
        policy("[\"clinicalText\",\"ageYears\"]", "LOW",
            "{\"clinicalText\":\"GENERALIZE\",\"ageYears\":\"NONE\"}", "HIGH");

        ModelEgressGuard.EgressPreparation prep = guard.prepareEgress(
            "tenant-1", "knowledge.extract",
            "{\"clinicalText\":\"患者张三主诉发热三天\",\"ageYears\":72,\"patientName\":\"张三\"}",
            "task-1", "ollama-local");

        assertThat(prep.payload()).contains("\"clinicalText\":\"[已泛化]\"");
        assertThat(prep.payload()).contains("\"ageYears\":72");
        assertThat(prep.payload()).doesNotContain("患者张三");
        assertThat(prep.payload()).doesNotContain("patientName");
    }

    @Test
    void noneOperatorStillMasksCoreSensitiveTextBeforePublicEgress() {
        policy("[\"prompt\",\"ageYears\",\"idLast4\"]", "LOW",
            "{\"prompt\":\"NONE\",\"ageYears\":\"NONE\",\"idLast4\":\"NONE\"}", "HIGH");

        ModelEgressGuard.EgressPreparation prep = guard.prepareEgress(
            "tenant-1", "knowledge.extract",
            "{\"prompt\":\"患者：张三，身份证号110101199001011234，手机号13988888888，"
                + "住址：北京市东城区测试路 1 号，请结合病情生成解释。\","
                + "\"ageYears\":72,\"idLast4\":\"1234\"}",
            "task-none-public", "openai-compatible");

        assertThat(prep.payload())
            .contains("\"ageYears\":72")
            .contains("\"idLast4\":null")
            .contains("110101********1234")
            .contains("139****8888")
            .contains("住址：[已屏蔽]")
            .doesNotContain("张三")
            .doesNotContain("110101199001011234")
            .doesNotContain("13988888888")
            .doesNotContain("北京市东城区测试路");
    }

    @Test
    void configuredNullifyRuleClearsWhitelistedField() {
        policy("[\"clinicalText\"]", "LOW", "{\"clinicalText\":\"NULLIFY\"}", "HIGH");

        ModelEgressGuard.EgressPreparation prep = guard.prepareEgress(
            "tenant-1", "knowledge.extract",
            "{\"clinicalText\":\"患者张三主诉发热三天\"}",
            "task-1", "ollama-local");

        assertThat(prep.payload()).contains("\"clinicalText\":null");
        assertThat(prep.payload()).doesNotContain("张三");
    }

    @Test
    void missingWhitelist_blocksWithEngLlm006() {
        when(whitelistRepo.findByTenantIdAndCapabilityCode("tenant-1", "knowledge.extract"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.prepareEgress(
                "tenant-1", "knowledge.extract", "{\"clinicalText\":\"x\"}", "task-1", "claude"))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(ErrorCode.ENG_LLM_006);
    }

    @Test
    void highSensitivityWithoutConfirmation_blocksWithEngLlm007() {
        whitelist("[\"clinicalText\"]", "HIGH");
        when(confirmationRepo.findFirstByTenantIdAndCapabilityCodeAndPayloadHashOrderByIdDesc(
                any(), any(), any()))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.prepareEgress(
                "tenant-1", "knowledge.extract", "{\"clinicalText\":\"主诉发热\"}", "task-1", "claude"))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(ErrorCode.ENG_LLM_007);
    }

    @Test
    void configuredMediumConfirmationThresholdRequiresConfirmation() {
        policy("[\"clinicalText\"]", "MEDIUM", "{\"clinicalText\":\"MASK_ALL\"}", "MEDIUM");
        when(confirmationRepo.findFirstByTenantIdAndCapabilityCodeAndPayloadHashOrderByIdDesc(
                any(), any(), any()))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.prepareEgress(
                "tenant-1", "knowledge.extract", "{\"clinicalText\":\"主诉发热\"}", "task-1", "claude"))
            .isInstanceOf(ApiException.class)
            .extracting(e -> ((ApiException) e).errorCode())
            .isEqualTo(ErrorCode.ENG_LLM_007);
    }

    @Test
    void lowSensitivity_passesWithoutConfirmation() {
        whitelist("[\"clinicalText\"]", "LOW");

        ModelEgressGuard.EgressPreparation prep = guard.prepareEgress(
            "tenant-1", "knowledge.extract", "{\"clinicalText\":\"主诉发热\"}", "task-1", "ollama-local");

        assertThat(prep.egressFields()).containsExactly("clinicalText");
    }

    @Test
    void confirmedHighSensitivity_passes() {
        whitelist("[\"clinicalText\"]", "HIGH");
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        when(confirmationRepo.findFirstByTenantIdAndCapabilityCodeAndPayloadHashOrderByIdDesc(
                any(), any(), any()))
            .thenReturn(Optional.of(new ModelEgressConfirmation(
                9L, "tenant-1", "knowledge.extract", "hash-x",
                "生成机构知识草稿", "operator-001", now,
                now, "operator-001", now, "operator-001")));

        ModelEgressGuard.EgressPreparation prep = guard.prepareEgress(
            "tenant-1", "knowledge.extract", "{\"clinicalText\":\"主诉发热\"}", "task-1", "claude");

        assertThat(prep.egressFields()).containsExactly("clinicalText");
    }

    @Test
    void recordsEgressEvidenceOnSuccess() {
        whitelist("[\"clinicalText\"]", "LOW");

        ModelEgressGuard.EgressPreparation prep = guard.prepareEgress(
            "tenant-1", "knowledge.extract", "{\"clinicalText\":\"主诉发热\"}", "task-77", "ollama-local");

        // FR-5：成功出域留证——字段清单 + 脱敏后 hash + 目标 provider + taskId
        verify(evidenceRepo).save(argThat(e ->
            "tenant-1".equals(e.tenantId())
                && "knowledge.extract".equals(e.capabilityCode())
                && "task-77".equals(e.taskId())
                && "ollama-local".equals(e.providerCode())
                && "[\"clinicalText\"]".equals(e.egressFields())
                && e.confirmationId() == null
                && prep.desensitizedHash().equals(e.desensitizedHash())));
    }
}
