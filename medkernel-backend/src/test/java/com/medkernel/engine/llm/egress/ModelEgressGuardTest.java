package com.medkernel.engine.llm.egress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 出域数据最小化与外调安全闸单元测试（LLM-03 FR-1/2）。
 */
class ModelEgressGuardTest {

    private ModelEgressWhitelistRepository whitelistRepo;
    private ModelEgressApprovalRepository approvalRepo;
    private ModelEgressEvidenceRepository evidenceRepo;
    private ModelEgressGuard guard;

    @BeforeEach
    void setUp() {
        whitelistRepo = mock(ModelEgressWhitelistRepository.class);
        approvalRepo = mock(ModelEgressApprovalRepository.class);
        evidenceRepo = mock(ModelEgressEvidenceRepository.class);
        guard = new ModelEgressGuard(whitelistRepo, approvalRepo, evidenceRepo);
    }

    private void whitelist(String allowedFields, String sensitivity) {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        when(whitelistRepo.findByTenantIdAndCapabilityCode("tenant-1", "knowledge.extract"))
            .thenReturn(Optional.of(new ModelEgressWhitelist(
                1L, "tenant-1", "knowledge.extract", allowedFields, sensitivity,
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
}
