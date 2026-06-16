package com.medkernel.engine.knowledge.production.gate;

import org.springframework.stereotype.Component;

import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.shared.hash.Sha256ContentHash;

/**
 * 门禁：内容格式与指纹真实性（AIK-STD-05，FR-1 格式）。
 *
 * <p>资产类型/内容非空，且内容指纹为 64 位小写十六进制 SHA-256 并真实等于内容指纹（禁伪造，铁律 #1）。
 */
@Component
public class ContentFormatGate implements CandidateGate {

    public static final String CODE = "CONTENT_FORMAT";
    private static final String SHA256_HEX = "^[0-9a-f]{64}$";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public GateItemResult evaluate(KnowledgeAssetEnvelope candidate, GateContext context) {
        if (candidate.assetType() == null) {
            return GateItemResult.fail(CODE, "资产类型缺失");
        }
        if (candidate.payload() == null || candidate.payload().isBlank()) {
            return GateItemResult.fail(CODE, "候选内容为空");
        }
        String contentHash = candidate.contentHash();
        if (contentHash == null || !contentHash.matches(SHA256_HEX)) {
            return GateItemResult.fail(CODE, "内容指纹格式非法（须 64 位小写十六进制 SHA-256）");
        }
        String real = Sha256ContentHash.sha256(candidate.payload(), "候选内容为空");
        if (!real.equals(contentHash)) {
            return GateItemResult.fail(CODE, "内容指纹与内容不一致（疑似伪造）");
        }
        return GateItemResult.pass(CODE);
    }
}
