package com.medkernel.engine.factory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.hash.Sha256ContentHash;

/**
 * 统一资产信封 schema 校验闸（AIK-STD-01，FR-3/4）。
 *
 * <p>{@link KnowledgeAssetEnvelope} 入既有版本/审核/替换链前过本闸：收集全部违规一次性结构化拒收，
 * 不合格不入库。校验类型无关（FR-4：新增 {@code VersionedAssetType} 不改本类、不破既有）。
 * 落点：核心 §7 来源可溯（无源拒收）· 铁律 #1 真实性（内容指纹真实，禁伪造）· 铁律 #5（生产器只产候选态）。
 */
@Service
public class KnowledgeAssetSchemaValidator {

    /** 生产器允许的候选态（铁律 #5：AI 只产候选不产事实，禁直接 PUBLISHED/ACTIVE）。 */
    private static final Set<AssetVersionStatus> CANDIDATE_STATUSES =
        Set.of(AssetVersionStatus.DRAFT);

    private static final String SHA256_HEX = "^[0-9a-f]{64}$";

    /**
     * 校验资产信封；不合格抛 {@link ApiException}（{@link ErrorCode#BAD_REQUEST}），消息列全部违规，不泄漏内部。
     */
    public void validate(KnowledgeAssetEnvelope envelope) {
        List<String> violations = new ArrayList<>();
        if (envelope == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "资产信封不能为空");
        }

        if (envelope.assetType() == null) {
            violations.add("资产类型不能为空");
        }
        if (isBlank(envelope.assetIdentity())) {
            violations.add("资产身份不能为空");
        }
        if (isBlank(envelope.subject())) {
            violations.add("资产主题不能为空");
        }
        if (isBlank(envelope.versionLabel())) {
            violations.add("版本标签不能为空");
        }

        validateSources(envelope, violations);

        if (envelope.trustLevel() == null) {
            violations.add("可信分级不能为空");
        }
        if (envelope.riskLevel() == null) {
            violations.add("风险级别不能为空");
        }
        if (isBlank(envelope.orgScope())) {
            violations.add("组织作用域不能为空");
        }
        if (isBlank(envelope.payload())) {
            violations.add("资产内容不能为空");
        }

        validateLifecycle(envelope, violations);
        validateContentHash(envelope, violations);

        if (!violations.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "资产信封校验不合格：" + String.join("；", violations));
        }
    }

    private void validateSources(KnowledgeAssetEnvelope envelope, List<String> violations) {
        List<AssetSourceRef> sources = envelope.sources();
        if (sources == null || sources.isEmpty()) {
            violations.add("来源不能为空（无源资产拒收）");
            return;
        }
        for (AssetSourceRef source : sources) {
            if (source == null || isBlank(source.sourceRef()) || source.authorityLevel() == null) {
                violations.add("来源引用不完整（须含来源标识与权威分级）");
                return;
            }
        }
    }

    private void validateLifecycle(KnowledgeAssetEnvelope envelope, List<String> violations) {
        AssetVersionStatus status = envelope.lifecycleStatus();
        if (status == null) {
            violations.add("生命周期状态不能为空");
        } else if (!CANDIDATE_STATUSES.contains(status)) {
            violations.add("生命周期状态只能是候选态（DRAFT），生产器不得直接产已发布事实");
        }
    }

    private void validateContentHash(KnowledgeAssetEnvelope envelope, List<String> violations) {
        String contentHash = envelope.contentHash();
        if (isBlank(contentHash)) {
            violations.add("内容指纹不能为空");
            return;
        }
        if (!contentHash.matches(SHA256_HEX)) {
            violations.add("内容指纹格式非法（须 64 位小写十六进制 SHA-256）");
            return;
        }
        if (!isBlank(envelope.payload())) {
            String real = Sha256ContentHash.sha256(envelope.payload(), "资产内容不能为空");
            if (!real.equals(contentHash)) {
                violations.add("内容指纹与内容不一致（疑似伪造，禁止）");
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
