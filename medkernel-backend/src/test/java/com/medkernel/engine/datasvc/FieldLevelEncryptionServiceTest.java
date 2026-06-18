package com.medkernel.engine.datasvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.crypto.SmCryptoService;

/**
 * 引擎数据服务层 · D3/D4 字段级加密服务测试（DATASVC-01 T6.4）。
 *
 * <p>验证患者相关字段进入数据服务层时只保存 SM4 密文、不可逆检索 hash 和字段分级元数据；
 * 返回值与审计摘要不含明文，D5 仍禁入。
 */
class FieldLevelEncryptionServiceTest {

    private EngineDataEncryptedFieldRepository encryptedFields;
    private EngineDataFieldPolicyRepository fieldPolicies;
    private AuditRecorder auditRecorder;
    private FieldLevelEncryptionService service;

    @BeforeEach
    void setUp() {
        encryptedFields = mock(EngineDataEncryptedFieldRepository.class);
        fieldPolicies = mock(EngineDataFieldPolicyRepository.class);
        auditRecorder = mock(AuditRecorder.class);
        when(encryptedFields.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            EngineDataEncryptedField field = invocation.getArgument(0);
            return new EngineDataEncryptedField(11L, field.tenantId(), field.scopeKey(), field.fieldName(),
                field.dataLevel(), field.cipherText(), field.cipherAlgorithm(), field.keyRef(), field.searchHash(),
                field.createdAt(), field.createdBy(), field.traceId());
        });
        when(fieldPolicies.findByTenantIdAndFieldPath("tenant-1", "clinical-context.patientRef"))
            .thenReturn(Optional.empty());
        when(fieldPolicies.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new FieldLevelEncryptionService(encryptedFields, fieldPolicies, new SmCryptoService(),
            () -> "unit-test-field-encryption-secret-at-least-32-bytes", auditRecorder);
        RequestContext.restore(new RequestContext.Snapshot("trace-encrypt-1", OrgScope.tenant("tenant-1"), "user-1"));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void encryptField_persistsCiphertextHashAndClassificationMetadataWithoutPlaintextLeak() {
        EncryptedFieldReceipt receipt = service.encryptField(
            "clinical-context", "patientRef", EngineDataLevel.D4, "Patient/真实患者-001");

        assertThat(receipt.storedId()).isEqualTo(11L);
        assertThat(receipt.dataLevel()).isEqualTo(EngineDataLevel.D4);
        assertThat(receipt.searchHash()).startsWith("sm3:");
        assertThat(receipt.keyRef()).isEqualTo("datasvc-field-sm4:v1");
        assertThat(receipt.cipherTextLength()).isPositive();
        assertThat(receipt.toString()).doesNotContain("真实患者");

        ArgumentCaptor<EngineDataEncryptedField> fieldCaptor =
            ArgumentCaptor.forClass(EngineDataEncryptedField.class);
        verify(encryptedFields).save(fieldCaptor.capture());
        EngineDataEncryptedField saved = fieldCaptor.getValue();
        assertThat(saved.tenantId()).isEqualTo("tenant-1");
        assertThat(saved.scopeKey()).isEqualTo("clinical-context");
        assertThat(saved.fieldName()).isEqualTo("patientRef");
        assertThat(saved.dataLevel()).isEqualTo(EngineDataLevel.D4);
        assertThat(saved.cipherText()).startsWith("sm4:v1:");
        assertThat(saved.cipherText()).doesNotContain("真实患者");
        assertThat(saved.searchHash()).startsWith("sm3:");
        assertThat(saved.searchHash()).doesNotContain("真实患者");
        assertThat(saved.traceId()).isEqualTo("trace-encrypt-1");

        ArgumentCaptor<EngineDataFieldPolicy> policyCaptor =
            ArgumentCaptor.forClass(EngineDataFieldPolicy.class);
        verify(fieldPolicies).save(policyCaptor.capture());
        EngineDataFieldPolicy policy = policyCaptor.getValue();
        assertThat(policy.fieldPath()).isEqualTo("clinical-context.patientRef");
        assertThat(policy.dataLevel()).isEqualTo(EngineDataLevel.D4);
        assertThat(policy.encryptionRequiredFlag()).isEqualTo("Y");
        assertThat(policy.status()).isEqualTo("ACTIVE");

        ArgumentCaptor<String> auditDetailCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditRecorder).record(eq(AuditAction.CREATE), eq("mk_engine_data_encrypted_field"),
            eq("clinical-context.patientRef"), auditDetailCaptor.capture());
        assertThat(auditDetailCaptor.getValue())
            .contains("searchHash=sm3:")
            .doesNotContain("真实患者");
    }

    @Test
    void encryptField_rejectsD5BeforePersisting() {
        assertThatThrownBy(() -> service.encryptField(
            "clinical-context", "idCard", EngineDataLevel.D5, "330101199001011234"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.AGENT_PATIENT_DATA_FORBIDDEN);

        verify(encryptedFields, never()).save(org.mockito.ArgumentMatchers.any());
        verify(fieldPolicies, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void encryptField_updatesExistingPolicyWithoutDowngradingEncryptionRequirement() {
        Instant createdAt = Instant.parse("2026-06-16T00:00:00Z");
        when(fieldPolicies.findByTenantIdAndFieldPath("tenant-1", "clinical-context.encounterRef"))
            .thenReturn(Optional.of(new EngineDataFieldPolicy(3L, "tenant-1", "clinical-context.encounterRef",
                EngineDataLevel.D3, "Y", "SERVICE_INTERNAL_ONLY", "ACTIVE",
                createdAt, "old-user", createdAt, "old-user", "old-trace")));

        service.encryptField("clinical-context", "encounterRef", EngineDataLevel.D4, "Encounter/住院-001");

        ArgumentCaptor<EngineDataFieldPolicy> policyCaptor =
            ArgumentCaptor.forClass(EngineDataFieldPolicy.class);
        verify(fieldPolicies).save(policyCaptor.capture());
        EngineDataFieldPolicy updated = policyCaptor.getValue();
        assertThat(updated.id()).isEqualTo(3L);
        assertThat(updated.dataLevel()).isEqualTo(EngineDataLevel.D4);
        assertThat(updated.encryptionRequiredFlag()).isEqualTo("Y");
        assertThat(updated.createdAt()).isEqualTo(createdAt);
        assertThat(updated.updatedBy()).isEqualTo("user-1");
        assertThat(updated.traceId()).isEqualTo("trace-encrypt-1");
    }
}
