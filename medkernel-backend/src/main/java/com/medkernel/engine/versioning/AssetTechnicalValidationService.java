package com.medkernel.engine.versioning;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.ids.Ulid;

/**
 * 发布前同步技术验证服务。
 *
 * <p>每次发布都重新校验稳定身份、可恢复正文和内容哈希，并保存绑定精确版本的证据。
 * 依赖闭包必须由平台或医院的完整发布清单统一校验，避免原子发布中的多个新版本被逐项误判。
 * 医学内容本身仍由各资产类型的正文校验器负责，本服务不建立第二套审核状态机。
 */
@Service
public class AssetTechnicalValidationService {

    private final AssetIdentityRepository identities;
    private final AssetVersionContentRepository contents;
    private final AssetValidationRecordRepository records;
    private final Clock clock;

    @Autowired
    public AssetTechnicalValidationService(
            AssetIdentityRepository identities,
            AssetVersionContentRepository contents,
            AssetValidationRecordRepository records) {
        this(identities, contents, records, Clock.systemUTC());
    }

    AssetTechnicalValidationService(
            AssetIdentityRepository identities,
            AssetVersionContentRepository contents,
            AssetValidationRecordRepository records,
            Clock clock) {
        this.identities = identities;
        this.contents = contents;
        this.records = records;
        this.clock = clock;
    }

    /**
     * 同步重跑发布前技术验证并记录成功证据。
     */
    @Transactional
    public AssetValidationRecord validateForPublish(
            AssetVersion version,
            String actor,
            String traceId) {
        if (version == null) {
            throw validation("资产版本不能为空");
        }
        if (version.status() != AssetVersionStatus.DRAFT) {
            throw new ApiException(ErrorCode.CONFLICT, "只有草稿版本可以执行发布验证");
        }
        if (!version.assetType().isRuntimeConfiguration()) {
            throw validation("非运行配置资产不能进入发布验证");
        }
        AssetIdentity identity = identities
            .findByTenantIdAndAssetTypeAndAssetIdentity(
                required(version.tenantId(), "租户"),
                version.assetType(),
                required(version.assetIdentity(), "资产身份"))
            .orElseThrow(() -> new ApiException(
                ErrorCode.CONFLICT, "资产版本缺少稳定身份登记"));
        if (identity.status() != AssetIdentityStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONFLICT, "退役资产身份不能发布新版本");
        }
        validateRecoverableContent(version);
        Instant now = clock.instant();
        return records.save(new AssetValidationRecord(
            null,
            "validation-" + Ulid.newUlid(),
            version.tenantId(),
            version.versionId(),
            version.contentHash(),
            true,
            "同步技术验证通过：稳定身份和正文哈希有效；依赖闭包由完整发布清单校验",
            now,
            required(actor, "验证人"),
            blankToNull(traceId)
        ));
    }

    private void validateRecoverableContent(AssetVersion version) {
        if (!version.assetType().usesUnifiedContentStore()) {
            return;
        }
        AssetVersionContent content = contents
            .findByTenantIdAndVersionId(version.tenantId(), version.versionId())
            .orElseThrow(() -> new ApiException(
                ErrorCode.CONFLICT, "配置资产缺少可恢复正文，禁止发布"));
        if (content.contentJson() == null || content.contentJson().isBlank()) {
            throw new ApiException(
                ErrorCode.CONFLICT, "配置资产缺少可恢复正文，禁止发布");
        }
        String actualHash = VersionContentHash.resolve(content.contentJson(), null);
        if (!actualHash.equals(version.contentHash())
                || !actualHash.equals(content.contentHash())) {
            throw new ApiException(
                ErrorCode.CONFLICT, "配置资产正文哈希与版本不一致，禁止发布");
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw validation(label + "不能为空");
        }
        return value.trim();
    }

    private static ApiException validation(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
