package com.medkernel.engine.rule;

import org.springframework.stereotype.Service;

import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionDraftUpdateCommand;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.AssetVersionService;
import com.medkernel.engine.versioning.VersionedAssetPort;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 规则域接入统一资产版本底座的薄适配器。
 *
 * <p>规则身份与 DSL 内容登记为 {@link VersionedAssetType#RULE}，发布、激活、回滚继续复用
 * SYS-04 通用版本服务，避免规则域保留第二套生命周期语义。
 */
@Service
public class RuleVersionedAssetAdapter implements VersionedAssetPort {

    private final AssetVersionService delegate;

    public RuleVersionedAssetAdapter(AssetVersionService delegate) {
        this.delegate = delegate;
    }

    @Override
    public AssetVersion registerDraft(AssetVersionRegisterCommand command) {
        if (command.assetType() != null && command.assetType() != VersionedAssetType.RULE) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "规则版本适配器只允许登记 RULE 资产");
        }
        return delegate.registerDraft(new AssetVersionRegisterCommand(
            command.tenantId(),
            VersionedAssetType.RULE,
            command.assetIdentity(),
            command.versionNo(),
            command.organizationScope(),
            command.applicableScope(),
            command.content(),
            command.contentHash(),
            command.sourceRef(),
            command.createdBy(),
            command.traceId(),
            command.safetyPolicy(),
            command.overridePolicy()
        ));
    }

    @Override
    public AssetVersion updateDraft(AssetVersionDraftUpdateCommand command) {
        return delegate.updateDraft(command);
    }

    @Override
    public AssetVersion publish(String tenantId, String versionId, String actor) {
        return delegate.publish(tenantId, versionId, actor);
    }

    @Override
    public AssetVersion activate(String tenantId, String versionId, String actor) {
        return delegate.activate(tenantId, versionId, actor);
    }
}
