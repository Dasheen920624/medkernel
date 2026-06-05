package com.medkernel.compliance.evidence.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.bouncycastle.jcajce.provider.digest.SM3;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 医疗合规可信存证证据快照实体 (EvidenceSnapshot) - Spring Data JDBC Record 风格。
 */
@Table("evidence_snapshot")
public record EvidenceSnapshot(
    @Id Long id,
    @Column("evidence_id") String evidenceId,
    @Column("tenant_id") String tenantId,
    @Column("trace_id") String traceId,
    @Column("evidence_type") String evidenceType,
    @Column("action") String action,
    @Column("subject_type") String subjectType,
    @Column("subject_id") String subjectId,
    @Column("evidence_summary") String evidenceSummary,
    @Column("payload_snapshot") String payloadSnapshot,
    @Column("payload_hash") String payloadHash,
    @Column("file_uri") String fileUri,
    @Column("file_digest") String fileDigest,
    @Column("signature_algorithm") String signatureAlgorithm,
    @Column("signature_value") String signatureValue,
    @Column("signer_public_key") String signerPublicKey,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy
) {
    public static final String SIGNATURE_ALGORITHM = "SM3_WITH_SM2";
    public static final String DIGEST_PREFIX = "sm3:";

    /**
     * 根据当前内容计算 SM3 数字指纹以防篡改。
     */
    public String calculateHash() {
        return sm3Digest(canonicalPayload());
    }

    /**
     * 计算证据文件内容的 SM3 摘要。
     */
    public String calculateFileDigest() {
        return sm3Digest(payloadSnapshot == null ? "" : payloadSnapshot);
    }

    /**
     * 证据签名规范串，签名和验签必须使用同一内容。
     */
    public String signaturePayload() {
        return String.join("|",
            nullToEmpty(evidenceId),
            nullToEmpty(tenantId),
            nullToEmpty(traceId),
            nullToEmpty(evidenceType),
            nullToEmpty(action),
            nullToEmpty(subjectType),
            nullToEmpty(subjectId),
            nullToEmpty(evidenceSummary),
            nullToEmpty(payloadSnapshot),
            nullToEmpty(fileUri),
            nullToEmpty(fileDigest)
        );
    }

    /**
     * 校验当前指纹是否未遭篡改。
     */
    public boolean isValid() {
        return calculateHash().equals(payloadHash)
            && (fileDigest == null || calculateFileDigest().equals(fileDigest));
    }

    private String canonicalPayload() {
        return String.join("|",
            nullToEmpty(evidenceId),
            nullToEmpty(tenantId),
            nullToEmpty(createdBy),
            nullToEmpty(payloadSnapshot)
        );
    }

    private static String sm3Digest(String text) {
        try {
            SM3.Digest digest = new SM3.Digest();
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(DIGEST_PREFIX);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("SM3 摘要计算失败", e);
        }
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
    }
}
