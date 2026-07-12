package com.medkernel.engine.knowledge.delivery;

import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.knowledge.authority.PackageSignatureEnvelope;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import org.springframework.stereotype.Service;

/** 医疗资源包公开签名信封的规范 JSON 编解码器。 */
@Service
public class PackageSignatureEnvelopeCodec {

    private static final Pattern STABLE_ID =
        Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private static final Pattern SM3 = Pattern.compile("sm3:[0-9a-f]{64}");

    private final CanonicalJson canonicalJson;

    public PackageSignatureEnvelopeCodec(ObjectMapper json) {
        this.canonicalJson = new CanonicalJson(json);
    }

    /** 编码不含私钥的规范签名信封。 */
    public byte[] encode(PackageSignatureEnvelope envelope) {
        return canonicalJson.encode(validate(envelope));
    }

    /** 解码并拒绝非规范或缺失公开签名事实的信封。 */
    public PackageSignatureEnvelope decode(byte[] bytes) {
        return validate(canonicalJson.decodeCanonical(bytes, PackageSignatureEnvelope.class));
    }

    private PackageSignatureEnvelope validate(PackageSignatureEnvelope envelope) {
        if (envelope == null
                || !stable(envelope.authorityId())
                || !stable(envelope.issuerInstanceId())
                || !stable(envelope.keyId())
                || !digest(envelope.rootFingerprint())
                || envelope.releaseSequence() <= 0
                || !digest(envelope.manifestDigest())
                || envelope.certificateChainPem() == null
                || envelope.certificateChainPem().isBlank()
                || envelope.signedAt() == null
                || blank(envelope.signatureBase64())) {
            throw new ApiException(
                ErrorCode.VALIDATION_FAILED,
                "医疗资源包签名信封缺少规范公开身份、摘要、证书链或 SM2 签名");
        }
        return envelope;
    }

    private boolean stable(String value) {
        return value != null
            && value.equals(value.trim())
            && STABLE_ID.matcher(value).matches();
    }

    private boolean digest(String value) {
        return value != null && SM3.matcher(value).matches();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank() || !value.equals(value.trim());
    }
}
