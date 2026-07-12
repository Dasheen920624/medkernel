package com.medkernel.engine.knowledge.delivery;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.crypto.SmCryptoService;
import org.springframework.stereotype.Service;

/** 完整包资产来源事实的规范编码、摘要和回读入口。 */
@Service
public class FullPackageProvenanceCodec {

    private final CanonicalJson canonicalJson;
    private final SmCryptoService crypto;

    public FullPackageProvenanceCodec(ObjectMapper json, SmCryptoService crypto) {
        this.canonicalJson = new CanonicalJson(json);
        this.crypto = crypto;
    }

    /** 生成不重复保存正文的规范来源文档。 */
    public EncodedProvenance encode(PortableAssetDocument source) {
        if (source == null) {
            throw invalid("完整包资产来源事实不能为空");
        }
        byte[] bytes = canonicalJson.encode(FullPackageProvenanceDocument.from(source));
        return new EncodedProvenance(
            new String(bytes, StandardCharsets.UTF_8),
            "sm3:" + HexFormat.of().formatHex(crypto.sm3(bytes)));
    }

    /** 回读规范来源文档；非规范 JSON 会明确拒绝。 */
    public FullPackageProvenanceDocument decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw invalid("完整包资产来源事实正文不能为空");
        }
        return canonicalJson.decodeCanonical(
            encoded.getBytes(StandardCharsets.UTF_8),
            FullPackageProvenanceDocument.class);
    }

    /** 同时返回规范 JSON 与覆盖其全部字节的 SM3 摘要。 */
    public record EncodedProvenance(String json, String digest) {
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }
}
