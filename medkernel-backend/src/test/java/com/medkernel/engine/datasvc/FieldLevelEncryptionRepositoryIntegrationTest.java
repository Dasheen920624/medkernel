package com.medkernel.engine.datasvc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * DATASVC-01 T6.4 字段级加密仓储集成测试。
 *
 * <p>对真实 H2 验证 {@code mk_engine_data_encrypted_field}/{@code mk_engine_data_field_policy}
 * 建表、enum 映射、租户过滤和不可逆 hash 查询可执行。
 */
@SpringBootTest
@ActiveProfiles("dev")
class FieldLevelEncryptionRepositoryIntegrationTest {

    private static final String TENANT = "tenant-field-enc-it";

    @Autowired
    private EngineDataEncryptedFieldRepository encryptedFields;

    @Autowired
    private EngineDataFieldPolicyRepository fieldPolicies;

    @Test
    void encryptedFieldAndPolicyPersistWithTenantScopedLookup() {
        String suffix = UUID.randomUUID().toString();
        EngineDataEncryptedField field = encryptedFields.save(new EngineDataEncryptedField(
            null, TENANT, "clinical-context-" + suffix, "patientRef", EngineDataLevel.D4,
            "sm4:v1:cipher", FieldLevelEncryptionService.CIPHER_ALGORITHM,
            FieldLevelEncryptionService.KEY_REF, "sm3:" + "a".repeat(64),
            Instant.now(), "user-it", "trace-it"));
        EngineDataFieldPolicy policy = fieldPolicies.save(new EngineDataFieldPolicy(
            null, TENANT, "clinical-context-" + suffix + ".patientRef", EngineDataLevel.D4,
            "Y", "SERVICE_INTERNAL_ONLY", "ACTIVE",
            Instant.now(), "user-it", Instant.now(), "user-it", "trace-it"));

        assertThat(field.id()).isNotNull();
        assertThat(policy.id()).isNotNull();
        assertThat(encryptedFields.findByTenantIdAndScopeKeyOrderByIdAsc(
            TENANT, "clinical-context-" + suffix))
            .singleElement()
            .satisfies(saved -> {
                assertThat(saved.dataLevel()).isEqualTo(EngineDataLevel.D4);
                assertThat(saved.cipherText()).startsWith("sm4:v1:");
                assertThat(saved.searchHash()).startsWith("sm3:");
            });
        assertThat(encryptedFields.findByTenantIdAndSearchHash("tenant-other", field.searchHash())).isEmpty();
        assertThat(fieldPolicies.findByTenantIdAndFieldPath(TENANT, policy.fieldPath()))
            .contains(policy);
        assertThat(fieldPolicies.findByTenantIdAndDataLevelAndStatus(TENANT, EngineDataLevel.D4, "ACTIVE"))
            .extracting(EngineDataFieldPolicy::fieldPath)
            .contains(policy.fieldPath());
    }
}
