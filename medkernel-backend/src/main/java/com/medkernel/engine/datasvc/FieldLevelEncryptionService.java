package com.medkernel.engine.datasvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.crypto.SmCryptoService;

/**
 * 引擎数据服务层 D3/D4 字段级加密服务（DATASVC-01 FR-2）。
 *
 * <p>只接收最小字段值，写入独立加密账本和字段分级元数据；审计摘要只记录字段路径、数据级别、hash 与 keyRef，
 * 不记录明文或密文本体。D5 重要个人信息仍禁止进入数据服务/CLI/MCP/模型输入。
 */
@Service
public class FieldLevelEncryptionService {

    static final String CIPHER_ALGORITHM = "SM4/ECB/PKCS5Padding";
    static final String KEY_REF = "datasvc-field-sm4:v1";
    private static final int SM4_KEY_BYTES = 16;
    private static final String ACTIVE = "ACTIVE";
    private static final String ENCRYPTION_REQUIRED = "Y";
    private static final String SERVICE_INTERNAL_ONLY = "SERVICE_INTERNAL_ONLY";

    private final EngineDataEncryptedFieldRepository encryptedFields;
    private final EngineDataFieldPolicyRepository fieldPolicies;
    private final SmCryptoService crypto;
    private final FieldEncryptionKeyResolver keyResolver;
    private final AuditRecorder auditRecorder;

    public FieldLevelEncryptionService(
            EngineDataEncryptedFieldRepository encryptedFields,
            EngineDataFieldPolicyRepository fieldPolicies,
            SmCryptoService crypto,
            FieldEncryptionKeyResolver keyResolver,
            AuditRecorder auditRecorder) {
        this.encryptedFields = encryptedFields;
        this.fieldPolicies = fieldPolicies;
        this.crypto = crypto;
        this.keyResolver = keyResolver;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 加密并持久化一个 D3/D4 字段。返回值不含明文或密文本体。
     */
    @Transactional
    public EncryptedFieldReceipt encryptField(
            String scopeKey, String fieldName, EngineDataLevel dataLevel, String plaintext) {
        String tenantId = requireTenant();
        String safeScope = requireText(scopeKey, "scopeKey");
        String safeField = requireText(fieldName, "fieldName");
        String safePlaintext = requireText(plaintext, "plaintext");
        EngineDataLevel safeLevel = requireEncryptableLevel(dataLevel);
        Instant now = Instant.now();
        String actor = RequestContext.currentUserId().orElse("system");
        String traceId = RequestContext.currentTraceId();
        String fieldPath = safeScope + "." + safeField;
        String cipherText = encrypt(safePlaintext);
        String searchHash = searchHash(tenantId, safeField, safePlaintext);

        EngineDataEncryptedField saved = encryptedFields.save(new EngineDataEncryptedField(
            null, tenantId, safeScope, safeField, safeLevel, cipherText, CIPHER_ALGORITHM, KEY_REF,
            searchHash, now, actor, traceId));
        upsertFieldPolicy(tenantId, fieldPath, safeLevel, now, actor, traceId);
        auditRecorder.record(AuditAction.CREATE, "mk_engine_data_encrypted_field", fieldPath,
            "字段级加密写入 level=" + safeLevel + " keyRef=" + KEY_REF
                + " searchHash=" + searchHash + " traceId=" + traceId);
        return new EncryptedFieldReceipt(saved.id(), safeScope, safeField, safeLevel, searchHash,
            KEY_REF, CIPHER_ALGORITHM, cipherText.length(), now);
    }

    private void upsertFieldPolicy(String tenantId, String fieldPath, EngineDataLevel dataLevel,
            Instant now, String actor, String traceId) {
        EngineDataFieldPolicy policy = fieldPolicies.findByTenantIdAndFieldPath(tenantId, fieldPath)
            .map(existing -> new EngineDataFieldPolicy(
                existing.id(), existing.tenantId(), existing.fieldPath(), dataLevel,
                ENCRYPTION_REQUIRED, SERVICE_INTERNAL_ONLY, ACTIVE,
                existing.createdAt(), existing.createdBy(), now, actor, traceId))
            .orElseGet(() -> new EngineDataFieldPolicy(
                null, tenantId, fieldPath, dataLevel, ENCRYPTION_REQUIRED, SERVICE_INTERNAL_ONLY, ACTIVE,
                now, actor, now, actor, traceId));
        fieldPolicies.save(policy);
    }

    private String encrypt(String plaintext) {
        try {
            byte[] cipher = crypto.sm4Encrypt(sm4Key(), plaintext.getBytes(StandardCharsets.UTF_8));
            return "sm4:v1:" + crypto.base64Encode(cipher);
        } catch (Exception exception) {
            throw new IllegalStateException("D3/D4 字段级加密失败", exception);
        }
    }

    private String searchHash(String tenantId, String fieldName, String plaintext) {
        String canonical = tenantId + "|" + fieldName.toLowerCase(Locale.ROOT) + "|" + plaintext;
        return "sm3:" + crypto.sm3Hex(canonical);
    }

    private byte[] sm4Key() {
        byte[] digest = crypto.sm3(
            ("medkernel:datasvc:field-encryption:" + keyResolver.resolve()).getBytes(StandardCharsets.UTF_8));
        byte[] key = new byte[SM4_KEY_BYTES];
        System.arraycopy(digest, 0, key, 0, key.length);
        return key;
    }

    private EngineDataLevel requireEncryptableLevel(EngineDataLevel level) {
        if (level == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "字段级加密必须指定数据分级");
        }
        if (level == EngineDataLevel.D5) {
            throw new ApiException(ErrorCode.AGENT_PATIENT_DATA_FORBIDDEN,
                "D5 重要个人信息禁入数据服务/CLI/MCP/模型输入");
        }
        if (level != EngineDataLevel.D3 && level != EngineDataLevel.D4) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "字段级加密仅适用于 D3/D4，当前为 " + level);
        }
        return level;
    }

    private String requireTenant() {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "字段级加密必须携带租户上下文");
        }
        return tenantId;
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "字段级加密参数 " + name + " 不能为空");
        }
        return value.trim();
    }
}
