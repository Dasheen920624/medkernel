package com.medkernel.engine.knowledge.authority;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** 完整医疗资源包的公开 SM2 签名信封，不含任何私钥或访问凭据。 */
public record PackageSignatureEnvelope(
    String authorityId,
    String issuerInstanceId,
    String keyId,
    String rootFingerprint,
    long releaseSequence,
    String manifestDigest,
    String certificateChainPem,
    Instant signedAt,
    String signatureBase64
) {

    private static final String CONTRACT = "MEDKERNEL-MKP-SIGNATURE-V1";

    byte[] canonicalPayload() {
        String payload = CONTRACT + "\n"
            + field("authorityId", authorityId)
            + field("issuerInstanceId", issuerInstanceId)
            + field("keyId", keyId)
            + field("rootFingerprint", rootFingerprint)
            + field("releaseSequence", Long.toString(releaseSequence))
            + field("manifestDigest", manifestDigest)
            + field("signedAt", signedAt == null ? null : signedAt.toString());
        return payload.getBytes(StandardCharsets.UTF_8);
    }

    private String field(String name, String value) {
        String normalized = value == null ? "" : value;
        return name + "=" + normalized.getBytes(StandardCharsets.UTF_8).length
            + ":" + normalized + "\n";
    }
}
